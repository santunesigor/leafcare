#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/../.." && pwd)
test_container="leafcare-admin-test-${$}"
trap 'docker rm -f "$test_container" >/dev/null 2>&1 || true' EXIT
docker run --rm -d --name "$test_container" -e POSTGRES_HOST_AUTH_METHOD=trust -v "$repo_root/supabase:/workspace:ro" postgres:18 >/dev/null
for attempt in {1..30}; do
 if docker exec "$test_container" pg_isready -U postgres >/dev/null 2>&1; then break; fi
 sleep 1
done
docker exec "$test_container" psql -U postgres -v ON_ERROR_STOP=1 -f /workspace/tests/bootstrap.sql >/dev/null
for migration in "$repo_root"/supabase/migrations/*.sql; do
 docker exec "$test_container" psql -U postgres -v ON_ERROR_STOP=1 -f "/workspace/migrations/$(basename "$migration")" >/dev/null
done
docker exec "$test_container" psql -U postgres -v ON_ERROR_STOP=1 -f /workspace/tests/admin.sql >/dev/null
# Validate the parameterized first-admin bootstrap and an idempotent repeat.
docker exec "$test_container" psql -U postgres -v ON_ERROR_STOP=1 -c "INSERT INTO auth.users(id,email,email_confirmed_at) VALUES('00000000-0000-0000-0000-000000000009','bootstrap@test.local',now())" >/dev/null
for attempt in 1 2; do
 docker exec "$test_container" psql -U postgres -v ON_ERROR_STOP=1 -v superadmin_email=bootstrap@test.local -f /workspace/scripts/bootstrap-superadmin.sql >/dev/null
done
docker exec "$test_container" psql -U postgres -v ON_ERROR_STOP=1 -c "DO \$\$ BEGIN IF (SELECT count(*) FROM public.admin_audit WHERE action='bootstrap')<>1 OR NOT EXISTS(SELECT 1 FROM public.account_controls WHERE role='superadmin') THEN RAISE EXCEPTION 'Bootstrap failed'; END IF; END \$\$" >/dev/null
echo 'PostgreSQL: migrations and superadmin/RLS scenarios passed.'

#!/usr/bin/env bash
set -euo pipefail
# Isolated PostgreSQL 18/WASM fallback when Docker is unavailable; no remote connection.
repo_root=$(cd "$(dirname "$0")/../.." && pwd)
test_directory=$(mktemp -d /tmp/leafcare-pglite.XXXXXX)
trap 'rm -rf "$test_directory"' EXIT
npm install --prefix "$test_directory" --no-save --ignore-scripts @electric-sql/pglite@0.5.10 >/dev/null
cp "$repo_root/supabase/tests/run-pglite.mjs" "$test_directory/run.mjs"
node "$test_directory/run.mjs" "$repo_root/supabase"

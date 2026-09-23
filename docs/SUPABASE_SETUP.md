# LeafCare — Supabase MVP Setup

This document describes how to create/connect the Supabase project and apply the migrations for the LeafCare MVP.

---

## 1. Create / Connect Supabase Project

### Option A: New Project (Recommended for MVP)

1. Go to https://supabase.com/dashboard
2. Click **New Project**
3. Choose organization
4. Enter:
   - **Name**: `leafcare-mvp` (or your preference)
   - **Database Password**: Generate strong password, save securely
   - **Region**: Choose closest to your users
5. Wait for project to be ready (~2 minutes)

### Option B: Existing Project

If you already have a project, ensure it's on the `feature/mvp-cloud` branch equivalent.

---

## 2. Public Variables Needed by Android App

The Android app will need these **public** values (safe to include in build config):

| Variable | Description | Example |
|----------|-------------|---------|
| `SUPABASE_URL` | Project REST API URL | `https://xxxxxxxx.supabase.co` |
| `SUPABASE_ANON_KEY` | Anonymous public key (JWT) | `eyJhbGciOiJIUzI1NiIs...` |

**Where to find them:**
- Dashboard → Settings → API → Project URL / anon public key

**NEVER put these in the repo:**
- `service_role` key (bypasses RLS)
- Database password
- JWT secret
- Any other secret

---

## 3. Apply Migrations

### Using Supabase CLI (Recommended)

```bash
# Install CLI if not present
# npm install -g supabase  # or via scoop/brew/etc.

# Login
supabase login

# Link to your project (get Project Ref from Dashboard URL)
supabase link --project-ref YOUR_PROJECT_REF

# Push migrations
supabase db push
```

### Using Dashboard SQL Editor

If CLI is not available, apply migrations manually in order:

1. Dashboard → SQL Editor → New Query
2. Copy contents of each migration file in order:
   - `20250923000001_initial_schema.sql`
   - `20250923000002_auto_profile_creation.sql`
   - `20250923000003_storage_analysis_photos.sql` (policies only)
3. Run each separately
4. Verify no errors

---

## 4. Create Storage Bucket

The `analysis-photos` bucket **cannot be created via SQL migration**. Create it via:

### Dashboard
1. Storage → Create bucket
2. Name: `analysis-photos`
3. **Private** bucket (not public)
4. Save

### CLI
```bash
supabase storage create analysis-photos --private
```

### Management API
POST to `/storage/v1/bucket` with `{ "name": "analysis-photos", "public": false }`

---

## 5. Verify RLS and Bucket

### Verify RLS is Enabled
```sql
-- Run in SQL Editor
SELECT schemaname, tablename, rowsecurity
FROM pg_tables
WHERE schemaname = 'public'
AND tablename IN ('profiles', 'analyses');
-- Should show rowsecurity = true for both
```

### Verify Policies
```sql
SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual
FROM pg_policies
WHERE schemaname = 'public'
AND tablename IN ('profiles', 'analyses');
```

### Verify Storage Bucket
```sql
SELECT id, name, public
FROM storage.buckets
WHERE name = 'analysis-photos';
-- Should show public = false
```

### Verify Storage Policies
```sql
SELECT bucket_id, name, definition
FROM storage.policies
WHERE bucket_id = 'analysis-photos';
```

---

## 6. Test User Isolation (Conceptual)

Create two test users (User A, User B) via Dashboard Auth or signup flow.

**As User A:**
```sql
-- Should succeed
INSERT INTO profiles (id, display_name) VALUES (auth.uid(), 'User A');
INSERT INTO analyses (id, user_id, class_id, display_name, scientific_name, confidence, top3, inconclusive, threshold, inference_ms, model_sha256, app_version)
VALUES (uuid_generate_v4(), auth.uid(), 'test', 'Test', 'Test', 0.9, '[]', false, 0.7, 100, 'sha256', '1.0');

-- Should return User A's data only
SELECT * FROM profiles;
SELECT * FROM analyses;
```

**As User B:**
- Same queries should return only User B's data (empty initially)
- Attempting to INSERT with `user_id = User A's ID` should fail (policy violation)

---

## 7. Secrets That Never Enter the Android App

| Secret | Where Used | Never In App |
|--------|------------|--------------|
| `service_role` key | Backend admin, CI/CD | ✅ |
| Database password | Supabase Dashboard, migrations | ✅ |
| JWT secret | Supabase internal | ✅ |
| Dashboard access tokens | Personal/CI only | ✅ |

Only `SUPABASE_URL` and `SUPABASE_ANON_KEY` go into the Android app (via `local.properties` or BuildConfig, not hardcoded).

---

## 8. Next Steps After Setup

1. Verify all migrations applied successfully
2. Verify RLS policies work with two test users
3. Verify storage bucket is private and policies work
4. Document the `SUPABASE_URL` and `SUPABASE_ANON_KEY` for Android integration (Phase 3)
5. Proceed to Phase 3: Authentication & Profile
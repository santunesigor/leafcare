# LeafCare — Supabase MVP Setup

This document describes how to create/connect the Supabase project and apply the migrations for the LeafCare MVP.

---

## 1. Real Supabase Project (leafcare)

**Project Name:** `leafcare`
**Project Ref:** `nhkqfanjfcivcbndivav`
**Region:** `sa-east-1`
**URL:** `https://nhkqfanjfcivcbndivav.supabase.co`

> ⚠️ **CRITICAL:** This is the **only** Supabase project for LeafCare.
> The `nossoduo` project is **completely separate** and must **never** be connected to LeafCare.
> Do not use credentials from `nossoduo` for LeafCare development.

---

## 2. Public Variables Needed by Android App

The Android app will need these **public** values (safe to include in build config):

| Variable | Description | Example |
|----------|-------------|---------|
| `SUPABASE_URL` | Project REST API URL | `https://nhkqfanjfcivcbndivav.supabase.co` |
| `SUPABASE_PUBLISHABLE_KEY` | Publishable key (JWT, ex-anon) | `eyJhbGciOiJIUzI1NiIs...` |

**Where to find them:**
- Dashboard → Settings → API → Project URL / publishable key

> Nota: o app atual usa `SUPABASE_PUBLISHABLE_KEY` (alias legado
> `SUPABASE_ANON_KEY` mantido no `BuildConfig` apontando para a mesma chave).

**NEVER put these in the repo:**
- `service_role` key (bypasses RLS)
- Database password
- JWT secret
- Any other secret

---

## 3. Migrations (Aligned with Remote)

The following migrations are already applied to the remote project and must match locally:

| Migration File | Description |
|----------------|-------------|
| `20260923121620_initial_schema.sql` | Creates `profiles` and `analyses` tables with RLS |
| `20260923121632_auto_profile_creation.sql` | Creates trigger for automatic profile on signup |
| `20260923121659_storage_analysis_photos.sql` | Creates private `analysis-photos` bucket and storage policies |
| `20260923121807_security_hardening.sql` | Revokes function execution from PUBLIC/anon/authenticated; recreates policies with `TO authenticated` and `(select auth.uid())` |

### Applying Migrations (if setting up a new project)

#### Using Supabase CLI (Recommended)

```bash
# Install CLI if not present
# npm install -g supabase  # or via scoop/brew/etc.

# Login
supabase login

# Link to your project (get Project Ref from Dashboard URL)
supabase link --project-ref nhkqfanjfcivcbndivav

# Push migrations
supabase db push
```

#### Using Dashboard SQL Editor

If CLI is not available, apply migrations manually in order:

1. Dashboard → SQL Editor → New Query
2. Copy contents of each migration file in order:
   - `20260923121620_initial_schema.sql`
   - `20260923121632_auto_profile_creation.sql`
   - `20260923121659_storage_analysis_photos.sql` (includes bucket creation + policies)
   - `20260923121807_security_hardening.sql`
3. Run each separately
4. Verify no errors

---

## 4. Storage Bucket

The `analysis-photos` bucket **already exists** on the remote project as PRIVATE.

The migration `20260923121659_storage_analysis_photos.sql` includes idempotent bucket creation:

```sql
INSERT INTO storage.buckets (id, name, public)
VALUES ('analysis-photos', 'analysis-photos', false)
ON CONFLICT (id)
DO UPDATE SET public = false;
```

This ensures reproducible installations.

**Policies** (in the same migration) enforce:
- Path pattern: `analysis-photos/{user_id}/{analysis_id}.jpg`
- Only authenticated users can INSERT/SELECT/UPDATE/DELETE in their own folder
- All policies use `TO authenticated`

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
-- Policies should show roles = {authenticated} and qual using (select auth.uid())
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
-- Should show 4 policies for INSERT/SELECT/UPDATE/DELETE with TO authenticated
```

---

## 6. Security Hardening (Already Applied)

The migration `20260923121807_security_hardening.sql` enforces:

- `handle_new_user()` - REVOKED from PUBLIC, anon, authenticated; GRANTED to `supabase_auth_admin` only
- `handle_updated_at()` - REVOKED from PUBLIC, anon, authenticated
- All profiles/analyses policies use `TO authenticated` and `(select auth.uid())`

This prevents direct function execution by client roles while allowing triggers to work.

---

## 7. Android Configuration

### local.properties (not versioned)

The Android app reads Supabase configuration from `local.properties` → `BuildConfig`:

```properties
# local.properties (create from local.properties.example)
SUPABASE_PUBLISHABLE_KEY=your_actual_publishable_key_here
```

`SUPABASE_URL` is hardcoded in BuildConfig as `https://nhkqfanjfcivcbndivav.supabase.co` since it's fixed for this project.

### Accessing in Code

```kotlin
// In Kotlin code
val url = BuildConfig.SUPABASE_URL
val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
```

### local.properties.example

See `android-app/local.properties.example` for the template.

---

## 8. Secrets That Never Enter the Android App

| Secret | Where Used | Never In App |
|--------|------------|--------------|
| `service_role` key | Backend admin, CI/CD | ✅ |
| Database password | Supabase Dashboard, migrations | ✅ |
| JWT secret | Supabase internal | ✅ |
| Dashboard access tokens | Personal/CI only | ✅ |

Only `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` go into the Android app (via `local.properties` → `BuildConfig`, not hardcoded).

---

## 9. Next Steps After Setup

1. Verify all migrations applied successfully
2. Verify RLS policies work with two test users
3. Verify storage bucket is private and policies work
4. Document the `SUPABASE_URL` and `SUPABASE_ANON_KEY` for Android integration (Phase 3)
5. Proceed to Phase 3: Authentication & Profile
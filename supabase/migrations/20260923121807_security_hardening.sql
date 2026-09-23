-- LeafCare MVP - Security Hardening
-- This migration reflects the security hardening already applied to the remote project.
-- It revokes direct execution of trigger functions from PUBLIC/anon/authenticated
-- and ensures policies use (select auth.uid()) with TO authenticated.

-- ============================================
-- REVOKE EXECUTE ON TRIGGER FUNCTIONS
-- ============================================

-- handle_new_user must not be executable by anon/authenticated directly
-- It runs as supabase_auth_admin via the auth.users trigger
REVOKE ALL ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.handle_new_user() TO supabase_auth_admin;

-- handle_updated_at must not be executable by anon/authenticated directly
-- It runs as the table owner via BEFORE UPDATE triggers
REVOKE ALL ON FUNCTION public.handle_updated_at() FROM PUBLIC, anon, authenticated;

-- ============================================
-- RECREATE PROFILES POLICIES WITH TO authenticated AND (select auth.uid())
-- ============================================

-- Drop existing policies
DROP POLICY IF EXISTS "Users can view own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can insert own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;

-- Recreate with TO authenticated and (select auth.uid())
CREATE POLICY "Users can view own profile"
    ON public.profiles
    FOR SELECT
    TO authenticated
    USING ((select auth.uid()) = id);

CREATE POLICY "Users can insert own profile"
    ON public.profiles
    FOR INSERT
    TO authenticated
    WITH CHECK ((select auth.uid()) = id);

CREATE POLICY "Users can update own profile"
    ON public.profiles
    FOR UPDATE
    TO authenticated
    USING ((select auth.uid()) = id)
    WITH CHECK ((select auth.uid()) = id);

-- ============================================
-- RECREATE ANALYSES POLICIES WITH TO authenticated AND (select auth.uid())
-- ============================================

-- Drop existing policies
DROP POLICY IF EXISTS "Users can view own analyses" ON public.analyses;
DROP POLICY IF EXISTS "Users can insert own analyses" ON public.analyses;
DROP POLICY IF EXISTS "Users can update own analyses" ON public.analyses;
DROP POLICY IF EXISTS "Users can delete own analyses" ON public.analyses;

-- Recreate with TO authenticated and (select auth.uid())
CREATE POLICY "Users can view own analyses"
    ON public.analyses
    FOR SELECT
    TO authenticated
    USING ((select auth.uid()) = user_id);

CREATE POLICY "Users can insert own analyses"
    ON public.analyses
    FOR INSERT
    TO authenticated
    WITH CHECK ((select auth.uid()) = user_id);

CREATE POLICY "Users can update own analyses"
    ON public.analyses
    FOR UPDATE
    TO authenticated
    USING ((select auth.uid()) = user_id)
    WITH CHECK ((select auth.uid()) = user_id);

CREATE POLICY "Users can delete own analyses"
    ON public.analyses
    FOR DELETE
    TO authenticated
    USING ((select auth.uid()) = user_id);
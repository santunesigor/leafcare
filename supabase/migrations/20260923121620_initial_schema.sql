-- LeafCare MVP - Initial Schema
-- Creates profiles and analyses tables with RLS

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Function to handle updated_at timestamps
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- Profiles table
CREATE TABLE public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Enable RLS on profiles
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Profiles policies: users can only access their own profile
CREATE POLICY "Users can view own profile"
    ON public.profiles
    FOR SELECT
    USING (auth.uid() = id);

CREATE POLICY "Users can insert own profile"
    ON public.profiles
    FOR INSERT
    WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update own profile"
    ON public.profiles
    FOR UPDATE
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- Trigger for profiles updated_at
CREATE TRIGGER profiles_updated_at
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();

-- Analyses table
CREATE TABLE public.analyses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    class_id TEXT NOT NULL,
    display_name TEXT NOT NULL,
    scientific_name TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    top3 JSONB NOT NULL,
    inconclusive BOOLEAN NOT NULL,
    threshold DOUBLE PRECISION NOT NULL,
    inference_ms DOUBLE PRECISION NOT NULL,
    model_sha256 TEXT NOT NULL,
    app_version TEXT NOT NULL,
    photo_path TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

-- Enable RLS on analyses
ALTER TABLE public.analyses ENABLE ROW LEVEL SECURITY;

-- Analyses policies: users can only access their own analyses
CREATE POLICY "Users can view own analyses"
    ON public.analyses
    FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own analyses"
    ON public.analyses
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own analyses"
    ON public.analyses
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own analyses"
    ON public.analyses
    FOR DELETE
    USING (auth.uid() = user_id);

-- Trigger for analyses updated_at
CREATE TRIGGER analyses_updated_at
    BEFORE UPDATE ON public.analyses
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();

-- Index for efficient querying
CREATE INDEX idx_analyses_user_id_created_at ON public.analyses (user_id, created_at DESC);
CREATE INDEX idx_analyses_deleted_at ON public.analyses (deleted_at) WHERE deleted_at IS NOT NULL;
-- LeafCare MVP - Private Storage for Analysis Photos
-- This migration documents the storage bucket and policies needed.
-- Note: Storage buckets are created via Supabase Dashboard or API, not standard SQL.
-- The following shows the equivalent configuration for documentation and reproducibility.

-- ============================================
-- STORAGE BUCKET: analysis-photos
-- ============================================
-- Bucket must be created as PRIVATE via:
-- 1. Supabase Dashboard → Storage → Create bucket "analysis-photos" (Private)
-- 2. Or via Supabase CLI: supabase storage create analysis-photos --private
-- 3. Or via Management API

-- ============================================
-- STORAGE POLICIES
-- ============================================
-- These policies ensure users can only access their own files in:
-- analysis-photos/{user_id}/{analysis_id}.jpg

-- Policy: Users can upload their own photos
-- Path pattern: {user_id}/*
CREATE POLICY "Users can upload own analysis photos"
    ON storage.objects
    FOR INSERT
    WITH CHECK (
        bucket_id = 'analysis-photos'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

-- Policy: Users can view their own photos
CREATE POLICY "Users can view own analysis photos"
    ON storage.objects
    FOR SELECT
    USING (
        bucket_id = 'analysis-photos'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

-- Policy: Users can update their own photos
CREATE POLICY "Users can update own analysis photos"
    ON storage.objects
    FOR UPDATE
    USING (
        bucket_id = 'analysis-photos'
        AND auth.uid()::text = (storage.foldername(name))[1]
    )
    WITH CHECK (
        bucket_id = 'analysis-photos'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

-- Policy: Users can delete their own photos
CREATE POLICY "Users can delete own analysis photos"
    ON storage.objects
    FOR DELETE
    USING (
        bucket_id = 'analysis-photos'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

-- ============================================
-- APPLICATION NOTES
-- ============================================
-- File naming convention: {analysis_id}.jpg
-- Full path: analysis-photos/{user_id}/{analysis_id}.jpg
--
-- Android app will use Supabase Storage client with anon key
-- to upload/download files after user authentication.
-- Service role key must NEVER be embedded in the Android app.
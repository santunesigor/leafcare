-- LeafCare MVP - Private Storage for Analysis Photos
-- This migration creates the storage bucket and policies for analysis photos.
-- The bucket creation is idempotent to allow reproducible installations.

-- ============================================
-- STORAGE BUCKET: analysis-photos (PRIVATE)
-- ============================================
-- Create bucket if not exists, ensure it remains private
INSERT INTO storage.buckets (id, name, public)
VALUES ('analysis-photos', 'analysis-photos', false)
ON CONFLICT (id)
DO UPDATE SET public = false;

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
    TO authenticated
    WITH CHECK (
        bucket_id = 'analysis-photos'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

-- Policy: Users can view their own photos
CREATE POLICY "Users can view own analysis photos"
    ON storage.objects
    FOR SELECT
    TO authenticated
    USING (
        bucket_id = 'analysis-photos'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

-- Policy: Users can update their own photos
CREATE POLICY "Users can update own analysis photos"
    ON storage.objects
    FOR UPDATE
    TO authenticated
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
    TO authenticated
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
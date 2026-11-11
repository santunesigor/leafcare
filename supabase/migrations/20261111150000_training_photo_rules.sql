-- Training copies follow the same rules as analysis photos, and revoked
-- copies are really removed:
-- 1. A user can see the objects in their own training-photos folder. Postgres
--    applies SELECT policies to a DELETE with a WHERE clause, so without this a
--    revoked copy (no longer "authorized") was silently kept: DELETE matched 0 rows.
-- 2. A training copy can only be stored for an active, conclusive analysis of
--    an account allowed to send (not paused, not pending deletion, after the
--    upload cutoff). Inconclusive results stay on the device.
--    Removing a training copy stays allowed while paused (revocation), unlike
--    analysis tombstones, which wait for release as specified.

CREATE POLICY "Users see own training photo folder" ON storage.objects
  FOR SELECT TO authenticated USING (
    bucket_id = 'training-photos'
    AND (SELECT auth.uid())::text = (storage.foldername(name))[1]
  );

CREATE FUNCTION public.can_send_training_photo(object_name text) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.analyses a
    WHERE a.user_id = auth.uid()
      AND object_name = a.user_id::text || '/' || a.id::text || '.jpg'
      AND a.deleted_at IS NULL
      AND NOT a.inconclusive
      AND public.account_can_send(a.user_id, a.created_at)
  )
$$;
REVOKE ALL ON FUNCTION public.can_send_training_photo(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.can_send_training_photo(text) TO authenticated;

CREATE POLICY send_training_photo_insert ON storage.objects AS RESTRICTIVE FOR INSERT TO authenticated
  WITH CHECK (bucket_id <> 'training-photos' OR public.can_send_training_photo(name));
CREATE POLICY send_training_photo_update ON storage.objects AS RESTRICTIVE FOR UPDATE TO authenticated
  USING (bucket_id <> 'training-photos' OR public.account_can_send((SELECT auth.uid())))
  WITH CHECK (bucket_id <> 'training-photos' OR public.can_send_training_photo(name));

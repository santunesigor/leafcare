BEGIN;
CREATE FUNCTION pg_temp.assert_true(value boolean, message text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF value IS DISTINCT FROM true THEN RAISE EXCEPTION 'Assertion failed: %', message; END IF; END $$;
SELECT pg_temp.assert_true((SELECT NOT public FROM storage.buckets WHERE id='tla-dataset'),'tla-dataset is private');
INSERT INTO auth.users(id,email) VALUES ('95000000-0000-0000-0000-000000000001','tla@test.local');
INSERT INTO storage.objects(bucket_id,name) VALUES('tla-dataset','frog_eye/IMG_0001.JPG');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','95000000-0000-0000-0000-000000000001',true);
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM storage.objects WHERE bucket_id='tla-dataset'),'app users cannot list or read TLA images');
DO $$ BEGIN
 BEGIN INSERT INTO storage.objects(bucket_id,name) VALUES('tla-dataset','frog_eye/IMG_0002.JPG'); RAISE EXCEPTION 'TLA upload by app user accepted'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
DELETE FROM storage.objects WHERE bucket_id='tla-dataset';
RESET ROLE;
SELECT pg_temp.assert_true((SELECT count(*)=1 FROM storage.objects WHERE bucket_id='tla-dataset'),'app users cannot delete TLA images');
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM public.training_photos_to_delete() WHERE object_name LIKE 'frog_eye/%'),'TLA images are never cleanup candidates');
ROLLBACK;

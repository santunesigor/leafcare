BEGIN;
CREATE FUNCTION pg_temp.assert_true(value boolean, message text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF value IS DISTINCT FROM true THEN RAISE EXCEPTION 'Assertion failed: %', message; END IF; END $$;
INSERT INTO auth.users(id,email) VALUES
 ('90000000-0000-0000-0000-000000000001','consent@test.local'),
 ('90000000-0000-0000-0000-000000000002','other@test.local');
INSERT INTO public.analyses(id,user_id,class_id,display_name,scientific_name,confidence,top3,inconclusive,threshold,inference_ms,model_sha256,app_version)
 VALUES
 ('90000000-0000-0000-0000-000000000003','90000000-0000-0000-0000-000000000001','frog_eye','Olho-de-rã','Cercospora',0.8,'[]',false,0.5,3,'test-model','test'),
 ('90000000-0000-0000-0000-000000000004','90000000-0000-0000-0000-000000000002','frog_eye','Olho-de-rã','Cercospora',0.8,'[]',false,0.5,3,'test-model','test');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','90000000-0000-0000-0000-000000000001',true);
DO $$ BEGIN
 BEGIN INSERT INTO storage.objects(bucket_id,name) VALUES('training-photos','90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000003.jpg'); RAISE EXCEPTION 'training upload without consent accepted'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
INSERT INTO public.training_consents(user_id,terms_version,granted) VALUES(auth.uid(),'1.2',false);
DO $$ BEGIN
 BEGIN UPDATE public.training_consents SET granted=true WHERE user_id=auth.uid(); RAISE EXCEPTION 'consent enabled before current legal acceptance'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN INSERT INTO public.training_photo_authorizations(user_id,analysis_id,terms_version,authorized)
   VALUES(auth.uid(),'90000000-0000-0000-0000-000000000004','1.2',false); RAISE EXCEPTION 'cross-account analysis authorization accepted';
 EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
-- Superseded documents (1.3) and the superseded training version (1.1) no longer authorize uploads.
INSERT INTO public.legal_document_acceptances(user_id,terms_version,privacy_version)
VALUES('90000000-0000-0000-0000-000000000001','1.3','1.3');
SET LOCAL ROLE authenticated;
DO $$ BEGIN
 BEGIN UPDATE public.training_consents SET granted=true WHERE user_id=auth.uid(); RAISE EXCEPTION 'consent enabled with superseded documents'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
-- Accepting documents 1.4 is the consent.
SELECT public.accept_current_legal_documents('1.4','1.4');
DO $$ BEGIN
 BEGIN INSERT INTO public.training_photo_authorizations(user_id,analysis_id,terms_version,authorized)
   VALUES(auth.uid(),'90000000-0000-0000-0000-000000000003','1.1',true); RAISE EXCEPTION 'superseded training version authorized';
 EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
INSERT INTO public.training_photo_authorizations(user_id,analysis_id,terms_version,authorized)
 VALUES(auth.uid(),'90000000-0000-0000-0000-000000000003','1.2',true);
INSERT INTO storage.objects(bucket_id,name) VALUES('training-photos','90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000003.jpg');
SELECT pg_temp.assert_true((SELECT count(*)=1 FROM storage.objects WHERE bucket_id='training-photos'),'current global and per-photo choices permit own photo upload');
DO $$ BEGIN
 BEGIN INSERT INTO storage.objects(bucket_id,name) VALUES('training-photos','90000000-0000-0000-0000-000000000002/90000000-0000-0000-0000-000000000004.jpg'); RAISE EXCEPTION 'cross-account training upload accepted'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
-- A revocation is accepted with any version, including a superseded one still stored on a device.
UPDATE public.training_consents SET granted=false, terms_version='1.1' WHERE user_id=auth.uid();
UPDATE public.training_photo_authorizations SET authorized=false, terms_version='1.1' WHERE user_id=auth.uid();
DELETE FROM storage.objects WHERE bucket_id='training-photos' AND name='90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000003.jpg';
RESET ROLE;
-- Counted outside RLS: as the user, a filtered count would always read 0.
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM storage.objects WHERE bucket_id='training-photos'),'revoked training copy is really deleted');

-- Training copies only for active, conclusive analyses of accounts allowed to send.
INSERT INTO public.analyses(id,user_id,class_id,display_name,scientific_name,confidence,top3,inconclusive,threshold,inference_ms,model_sha256,app_version)
 VALUES
 ('90000000-0000-0000-0000-000000000005','90000000-0000-0000-0000-000000000001','frog_eye','Olho-de-rã','Cercospora',0.8,'[]',false,0.5,3,'test-model','test'),
 ('90000000-0000-0000-0000-000000000006','90000000-0000-0000-0000-000000000001','frog_eye','Olho-de-rã','Cercospora',0.3,'[]',true,0.5,3,'test-model','test'),
 ('90000000-0000-0000-0000-000000000007','90000000-0000-0000-0000-000000000001','frog_eye','Olho-de-rã','Cercospora',0.8,'[]',false,0.5,3,'test-model','test'),
 ('90000000-0000-0000-0000-000000000008','90000000-0000-0000-0000-000000000001','frog_eye','Olho-de-rã','Cercospora',0.8,'[]',false,0.5,3,'test-model','test');
INSERT INTO storage.objects(bucket_id,name) VALUES('analysis-photos','90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000005.jpg');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','90000000-0000-0000-0000-000000000001',true);
SELECT public.accept_current_legal_documents('1.4','1.4');
INSERT INTO public.training_photo_authorizations(user_id,analysis_id,terms_version,authorized)
 SELECT auth.uid(), id, '1.2', true FROM public.analyses
 WHERE id IN ('90000000-0000-0000-0000-000000000005','90000000-0000-0000-0000-000000000006','90000000-0000-0000-0000-000000000007','90000000-0000-0000-0000-000000000008');
UPDATE public.analyses SET deleted_at=now() WHERE id='90000000-0000-0000-0000-000000000007';
DO $$ BEGIN
 BEGIN INSERT INTO storage.objects(bucket_id,name) VALUES('training-photos','90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000006.jpg'); RAISE EXCEPTION 'inconclusive training upload accepted'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN INSERT INTO storage.objects(bucket_id,name) VALUES('training-photos','90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000007.jpg'); RAISE EXCEPTION 'deleted analysis training upload accepted'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
INSERT INTO storage.objects(bucket_id,name) VALUES('training-photos','90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000005.jpg');
RESET ROLE;
UPDATE public.account_controls SET uploads_enabled=false WHERE user_id='90000000-0000-0000-0000-000000000001';
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','90000000-0000-0000-0000-000000000001',true);
DO $$ BEGIN
 BEGIN INSERT INTO storage.objects(bucket_id,name) VALUES('training-photos','90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000008.jpg'); RAISE EXCEPTION 'paused training upload accepted'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN UPDATE public.analyses SET deleted_at=now() WHERE id='90000000-0000-0000-0000-000000000008'; IF FOUND THEN RAISE EXCEPTION 'paused tombstone accepted'; END IF; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
-- Paused accounts can still remove training copies (revocation).
DELETE FROM storage.objects WHERE bucket_id='training-photos' AND name='90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000005.jpg';
RESET ROLE;
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM storage.objects WHERE bucket_id='training-photos'),'paused account can delete its training copies');
ROLLBACK;

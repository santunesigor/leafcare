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
INSERT INTO public.training_consents(user_id,terms_version,granted) VALUES(auth.uid(),'1.1',false);
DO $$ BEGIN
 BEGIN UPDATE public.training_consents SET granted=true WHERE user_id=auth.uid(); RAISE EXCEPTION 'consent enabled before current legal acceptance'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN INSERT INTO public.training_photo_authorizations(user_id,analysis_id,terms_version,authorized)
   VALUES(auth.uid(),'90000000-0000-0000-0000-000000000004','1.1',false); RAISE EXCEPTION 'cross-account analysis authorization accepted';
 EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
INSERT INTO public.legal_document_acceptances(user_id,terms_version,privacy_version)
VALUES('90000000-0000-0000-0000-000000000001','1.3','1.3');
SET LOCAL ROLE authenticated;
UPDATE public.training_consents SET granted=true WHERE user_id=auth.uid();
INSERT INTO public.training_photo_authorizations(user_id,analysis_id,terms_version,authorized)
 VALUES(auth.uid(),'90000000-0000-0000-0000-000000000003','1.1',true);
INSERT INTO storage.objects(bucket_id,name) VALUES('training-photos','90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000003.jpg');
SELECT pg_temp.assert_true((SELECT count(*)=1 FROM storage.objects WHERE bucket_id='training-photos'),'current global and per-photo choices permit own photo upload');
DO $$ BEGIN
 BEGIN INSERT INTO storage.objects(bucket_id,name) VALUES('training-photos','90000000-0000-0000-0000-000000000002/90000000-0000-0000-0000-000000000004.jpg'); RAISE EXCEPTION 'cross-account training upload accepted'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
UPDATE public.training_consents SET granted=false WHERE user_id=auth.uid();
DELETE FROM storage.objects WHERE bucket_id='training-photos' AND name='90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000003.jpg';
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM storage.objects WHERE bucket_id='training-photos'),'revoked user can delete own training object');
RESET ROLE;
ROLLBACK;

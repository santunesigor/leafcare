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
-- Simulate an object that must remain deletable after the user revokes.
INSERT INTO storage.objects(bucket_id,name,owner) VALUES('training-photos','90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000003.jpg','90000000-0000-0000-0000-000000000001');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','90000000-0000-0000-0000-000000000001',true);
DO $$ BEGIN
 BEGIN INSERT INTO storage.objects(bucket_id,name) VALUES('training-photos','90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000003.jpg'); RAISE EXCEPTION 'training upload without consent accepted'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
INSERT INTO public.training_consents(user_id,terms_version,granted) VALUES(auth.uid(),'1.0',false);
DO $$ BEGIN
 BEGIN UPDATE public.training_consents SET granted=true WHERE user_id=auth.uid(); RAISE EXCEPTION 'consent enabled before terms exist'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN INSERT INTO public.training_photo_authorizations(user_id,analysis_id,terms_version,authorized)
   VALUES(auth.uid(),'90000000-0000-0000-0000-000000000004','1.0',false); RAISE EXCEPTION 'cross-account analysis authorization accepted';
 EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
INSERT INTO public.training_photo_authorizations(user_id,analysis_id,terms_version,authorized)
 VALUES(auth.uid(),'90000000-0000-0000-0000-000000000003','1.0',false);
DO $$ BEGIN
 BEGIN UPDATE public.training_photo_authorizations SET authorized=true WHERE analysis_id='90000000-0000-0000-0000-000000000003'; RAISE EXCEPTION 'photo authorization enabled before terms exist'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN INSERT INTO storage.objects(bucket_id,name) VALUES('training-photos','90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000003.jpg'); RAISE EXCEPTION 'training upload accepted while disabled'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
UPDATE public.training_consents SET granted=false WHERE user_id=auth.uid();
DELETE FROM storage.objects WHERE bucket_id='training-photos' AND name='90000000-0000-0000-0000-000000000001/90000000-0000-0000-0000-000000000003.jpg';
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM storage.objects WHERE bucket_id='training-photos'),'revoked user can delete own training object');
RESET ROLE;
ROLLBACK;

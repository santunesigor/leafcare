BEGIN;
CREATE FUNCTION pg_temp.assert_true(value boolean, message text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF value IS DISTINCT FROM true THEN RAISE EXCEPTION 'Assertion failed: %',message; END IF; END $$;
INSERT INTO auth.users(id,email,email_confirmed_at) VALUES
 ('00000000-0000-0000-0000-000000000001','admin@test.local',now()),
 ('00000000-0000-0000-0000-000000000002','user@test.local',now());
UPDATE public.account_controls SET role='superadmin' WHERE user_id='00000000-0000-0000-0000-000000000001';
INSERT INTO public.analyses(id,user_id,class_id,display_name,scientific_name,confidence,top3,inconclusive,threshold,inference_ms,model_sha256,app_version)
 VALUES('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','frog_eye','Olho-de-rã','Cercospora',0.8,'[]',false,0.5,3,'test-model','test');
INSERT INTO storage.objects(bucket_id,name,owner) VALUES('analysis-photos','00000000-0000-0000-0000-000000000002/00000000-0000-0000-0000-000000000003.jpg','00000000-0000-0000-0000-000000000002');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
SELECT pg_temp.assert_true((SELECT count(*)=1 FROM public.account_controls),'only own controls');
SELECT pg_temp.assert_true(public.my_account_policy()->>'role'='user','own role');
DO $$ BEGIN
 BEGIN PERFORM public.admin_command('00000000-0000-0000-0000-000000000001','dashboard'); RAISE EXCEPTION 'RPC exposed'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN UPDATE public.account_controls SET role='superadmin'; RAISE EXCEPTION 'role escalation'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN INSERT INTO public.photo_reviews(analysis_id,status) VALUES('00000000-0000-0000-0000-000000000003','confirmed'); RAISE EXCEPTION 'review exposed'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
SET LOCAL ROLE service_role;
DO $$ BEGIN
 BEGIN PERFORM public.admin_command('00000000-0000-0000-0000-000000000002','dashboard'); RAISE EXCEPTION 'nonadmin authorized'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
SELECT pg_temp.assert_true(public.admin_command('00000000-0000-0000-0000-000000000001','dashboard')->>'photos'='1','dashboard counts');
SELECT pg_temp.assert_true(jsonb_array_length(public.admin_command('00000000-0000-0000-0000-000000000001','photos','{"status":"pending","class_id":"","model":"","user_id":"","from":"","until":"","min_confidence":""}'))=1,'photo filters allow empty fields');
SELECT public.admin_command('00000000-0000-0000-0000-000000000001','review','{"id":"00000000-0000-0000-0000-000000000003","version":0,"status":"corrected","class_id":"healthy","note":"review"}');
SELECT pg_temp.assert_true((SELECT class_id='frog_eye' FROM public.analyses LIMIT 1),'original prediction preserved');
SELECT pg_temp.assert_true((SELECT class_id='healthy' AND version=1 FROM public.photo_reviews LIMIT 1),'human correction saved');
DO $$ BEGIN
 BEGIN PERFORM public.admin_command('00000000-0000-0000-0000-000000000001','review','{"id":"00000000-0000-0000-0000-000000000003","version":0,"status":"confirmed"}'); RAISE EXCEPTION 'stale overwrite'; EXCEPTION WHEN serialization_failure THEN NULL; END;
 BEGIN PERFORM public.admin_command('00000000-0000-0000-0000-000000000001','role','{"id":"00000000-0000-0000-0000-000000000001","role":"user"}'); RAISE EXCEPTION 'last admin removed'; EXCEPTION WHEN raise_exception THEN IF SQLERRM='last admin removed' THEN RAISE; END IF; END;
 BEGIN PERFORM public.admin_command('00000000-0000-0000-0000-000000000001','delete_begin','{"id":"00000000-0000-0000-0000-000000000001","confirmation":"admin@test.local"}'); RAISE EXCEPTION 'self deleted'; EXCEPTION WHEN raise_exception THEN IF SQLERRM='self deleted' THEN RAISE; END IF; END;
END $$;
SELECT public.admin_command('00000000-0000-0000-0000-000000000001','pause','{"id":"00000000-0000-0000-0000-000000000002","enabled":false}');
RESET ROLE;
SET LOCAL ROLE authenticated;
SELECT pg_temp.assert_true(NOT public.account_can_send(auth.uid()),'pause enforced');
DO $$ BEGIN
 BEGIN INSERT INTO public.analyses(id,user_id,class_id,display_name,scientific_name,confidence,top3,inconclusive,threshold,inference_ms,model_sha256,app_version)
 VALUES(gen_random_uuid(),auth.uid(),'healthy','Saudável','',0.8,'[]',false,0.5,3,'test','test'); RAISE EXCEPTION 'paused insert succeeded'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN INSERT INTO storage.objects(bucket_id,name) VALUES('analysis-photos',auth.uid()::text||'/00000000-0000-0000-0000-000000000003.jpg'); RAISE EXCEPTION 'paused photo succeeded'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
SELECT pg_temp.assert_true((SELECT count(*)=1 FROM public.analyses),'paused reads allowed');
DO $$ DECLARE affected integer; BEGIN
 UPDATE public.analyses SET deleted_at=now(); GET DIAGNOSTICS affected=ROW_COUNT;
 PERFORM pg_temp.assert_true(affected=0,'paused tombstone blocked');
 DELETE FROM public.analyses; GET DIAGNOSTICS affected=ROW_COUNT;
 PERFORM pg_temp.assert_true(affected=0,'paused analysis delete blocked');
 DELETE FROM storage.objects; GET DIAGNOSTICS affected=ROW_COUNT;
 PERFORM pg_temp.assert_true(affected=0,'paused storage delete blocked');
END $$;
SELECT pg_temp.assert_true((SELECT count(*)=1 FROM storage.objects),'paused photo reads allowed');
RESET ROLE;
SET LOCAL ROLE service_role;
SELECT public.admin_command('00000000-0000-0000-0000-000000000001','pause','{"id":"00000000-0000-0000-0000-000000000002","enabled":true}');
SELECT pg_temp.assert_true(NOT public.account_can_send('00000000-0000-0000-0000-000000000002',now()-interval '1 day'),'old records excluded after resume');
SELECT pg_temp.assert_true(public.account_can_send('00000000-0000-0000-0000-000000000002',clock_timestamp()+interval '1 second'),'new records allowed after resume');
SELECT public.admin_command('00000000-0000-0000-0000-000000000001','review','{"id":"00000000-0000-0000-0000-000000000003","version":1,"status":"rejected","note":"foto inadequada"}');
SELECT pg_temp.assert_true((SELECT count(*)=1 FROM storage.objects),'rejection keeps photo');
SELECT pg_temp.assert_true((SELECT count(*)=2 FROM public.admin_audit WHERE action='review'),'review audit history');
SELECT public.admin_command('00000000-0000-0000-0000-000000000001','delete_begin','{"id":"00000000-0000-0000-0000-000000000002","confirmation":"user@test.local"}');
SELECT pg_temp.assert_true((SELECT deletion_pending FROM public.account_controls WHERE user_id='00000000-0000-0000-0000-000000000002'),'deletion resumable marker');
-- A demoted actor loses access immediately, independent of JWT claims.
RESET ROLE;
INSERT INTO auth.users(id,email) VALUES('00000000-0000-0000-0000-000000000004','second@test.local');
UPDATE public.account_controls SET role='superadmin' WHERE user_id='00000000-0000-0000-0000-000000000004';
SET LOCAL ROLE service_role;
SELECT public.admin_command('00000000-0000-0000-0000-000000000004','role','{"id":"00000000-0000-0000-0000-000000000001","role":"user"}');
DO $$ BEGIN
 BEGIN PERFORM public.admin_command('00000000-0000-0000-0000-000000000001','dashboard'); RAISE EXCEPTION 'revoked role accepted'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN PERFORM public.admin_command('00000000-0000-0000-0000-000000000004','review','{"id":"00000000-0000-0000-0000-000000000003","version":2,"status":"corrected","class_id":"invalid"}'); RAISE EXCEPTION 'invalid class accepted'; EXCEPTION WHEN raise_exception THEN IF SQLERRM='invalid class accepted' THEN RAISE; END IF; END;
 BEGIN PERFORM public.admin_command('00000000-0000-0000-0000-000000000004','review','{"id":"00000000-0000-0000-0000-000000000003","version":2,"status":"uncertain","note":"  "}'); RAISE EXCEPTION 'empty reason accepted'; EXCEPTION WHEN raise_exception THEN IF SQLERRM='empty reason accepted' THEN RAISE; END IF; END;
END $$;
SELECT pg_temp.assert_true(NOT public.account_can_send('00000000-0000-0000-0000-000000000004',now()+interval '1 day'),'future clock rejected');
RESET ROLE;
-- Simulate successful auth deletion after storage cleanup, followed by a lost response.
DELETE FROM storage.objects WHERE bucket_id='analysis-photos';
DELETE FROM auth.users WHERE id='00000000-0000-0000-0000-000000000002';
SET LOCAL ROLE service_role;
SELECT pg_temp.assert_true(public.admin_command('00000000-0000-0000-0000-000000000004','delete_begin','{"id":"00000000-0000-0000-0000-000000000002"}')->>'gone'='true','finished deletion can be retried');
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM public.photo_reviews),'account deletion cascades reviews');
SELECT pg_temp.assert_true((SELECT count(*)=2 FROM public.admin_audit WHERE action='review'),'deletion preserves audit');
ROLLBACK;

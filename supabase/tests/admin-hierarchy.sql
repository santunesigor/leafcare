BEGIN;
CREATE FUNCTION pg_temp.assert_hierarchy(value boolean, message text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF value IS DISTINCT FROM true THEN RAISE EXCEPTION 'Assertion failed: %',message; END IF; END $$;
INSERT INTO auth.users(id,email,email_confirmed_at) VALUES
 ('10000000-0000-0000-0000-000000000001','owner@test.local',now()),
 ('10000000-0000-0000-0000-000000000002','super@test.local',now()),
 ('10000000-0000-0000-0000-000000000003','peer@test.local',now()),
 ('10000000-0000-0000-0000-000000000004','reviewer@test.local',now()),
 ('10000000-0000-0000-0000-000000000005','user@test.local',now());
UPDATE public.account_controls SET role=CASE right(user_id::text,1) WHEN '1' THEN 'owner' WHEN '2' THEN 'superadmin' WHEN '3' THEN 'superadmin' WHEN '4' THEN 'admin' ELSE 'user' END;
INSERT INTO public.analyses(id,user_id,class_id,display_name,scientific_name,confidence,top3,inconclusive,threshold,inference_ms,model_sha256,app_version)
 VALUES('10000000-0000-0000-0000-000000000006','10000000-0000-0000-0000-000000000005','frog_eye','Olho-de-rã','Cercospora',0.8,'[]',false,0.5,3,'test-model','test');
INSERT INTO storage.objects(bucket_id,name,owner) VALUES('analysis-photos','10000000-0000-0000-0000-000000000005/10000000-0000-0000-0000-000000000006.jpg','10000000-0000-0000-0000-000000000005');
DO $$ BEGIN
 BEGIN UPDATE public.account_controls SET role='owner' WHERE user_id='10000000-0000-0000-0000-000000000002'; RAISE EXCEPTION 'second owner accepted'; EXCEPTION WHEN unique_violation THEN NULL; END;
END $$;
SET LOCAL ROLE service_role;
DO $$ DECLARE op text; target text; actor text; BEGIN
 FOREACH actor IN ARRAY ARRAY['10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000003'] LOOP
  FOREACH target IN ARRAY ARRAY['10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000003'] LOOP
   FOREACH op IN ARRAY ARRAY['role','pause','delete_begin','email_target'] LOOP
    BEGIN
     PERFORM public.admin_command(actor::uuid,op,jsonb_build_object('id',target,'role','user','enabled',false,'confirmation','owner@test.local'));
     RAISE EXCEPTION 'protected account accepted: % %',op,target;
    EXCEPTION WHEN SQLSTATE 'LC403' THEN NULL; END;
   END LOOP;
   FOREACH op IN ARRAY ARRAY['recovery','reinvite','delete'] LOOP
    BEGIN
     PERFORM public.admin_command(actor::uuid,'authorize',jsonb_build_object('operation',op,'id',target));
     RAISE EXCEPTION 'external protected action accepted';
    EXCEPTION WHEN SQLSTATE 'LC403' THEN NULL; END;
   END LOOP;
  END LOOP;
 END LOOP;
 FOREACH op IN ARRAY ARRAY['users','invite','invite_link','role','pause','delete_begin','email_target','record_event'] LOOP
  BEGIN
   PERFORM public.admin_command('10000000-0000-0000-0000-000000000004','authorize',jsonb_build_object('operation',op,'id','10000000-0000-0000-0000-000000000005','role','superadmin'));
   RAISE EXCEPTION 'reviewer account management accepted';
  EXCEPTION WHEN SQLSTATE 'LC403' THEN NULL; END;
 END LOOP;
 FOREACH actor IN ARRAY ARRAY['10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000004'] LOOP
  BEGIN PERFORM public.admin_command(actor::uuid,'authorize'); RAISE EXCEPTION 'old edge authorization accepted'; EXCEPTION WHEN SQLSTATE 'LC403' THEN NULL; END;
 END LOOP;
 FOREACH op IN ARRAY ARRAY['invite','invite_link'] LOOP
  FOREACH target IN ARRAY ARRAY['owner@test.local','peer@test.local',' SUPER@test.local '] LOOP
   BEGIN
    PERFORM public.admin_command('10000000-0000-0000-0000-000000000002','authorize',jsonb_build_object('operation',op,'email',target,'id','10000000-0000-0000-0000-000000000005'));
    RAISE EXCEPTION 'protected email invite accepted';
   EXCEPTION WHEN SQLSTATE 'LC403' THEN NULL; END;
  END LOOP;
 END LOOP;
 BEGIN PERFORM public.admin_command('10000000-0000-0000-0000-000000000002','role','{"id":"10000000-0000-0000-0000-000000000005","role":"superadmin"}'); RAISE EXCEPTION 'superadmin granted peer'; EXCEPTION WHEN SQLSTATE 'LC403' THEN NULL; END;
 BEGIN PERFORM public.admin_command('10000000-0000-0000-0000-000000000001','role','{"id":"10000000-0000-0000-0000-000000000001","role":"user"}'); RAISE EXCEPTION 'owner demoted'; EXCEPTION WHEN SQLSTATE 'LC403' THEN NULL; END;
 BEGIN PERFORM public.admin_command('10000000-0000-0000-0000-000000000001','role','{"id":"10000000-0000-0000-0000-000000000005","role":"owner"}'); RAISE EXCEPTION 'ownership transferred'; EXCEPTION WHEN SQLSTATE 'LC403' THEN NULL; END;
END $$;
SELECT public.admin_command('10000000-0000-0000-0000-000000000002','role','{"id":"10000000-0000-0000-0000-000000000005","role":"admin"}');
SELECT pg_temp.assert_hierarchy((SELECT role='admin' FROM public.account_controls WHERE user_id='10000000-0000-0000-0000-000000000005'),'superadmin can promote reviewer');
SELECT public.admin_command('10000000-0000-0000-0000-000000000001','role','{"id":"10000000-0000-0000-0000-000000000005","role":"superadmin"}');
SELECT public.admin_command('10000000-0000-0000-0000-000000000001','role','{"id":"10000000-0000-0000-0000-000000000005","role":"user"}');
SELECT public.admin_command('10000000-0000-0000-0000-000000000004','review','{"id":"10000000-0000-0000-0000-000000000006","version":0,"status":"corrected","class_id":"healthy"}');
SELECT pg_temp.assert_hierarchy((SELECT class_id='frog_eye' FROM public.analyses LIMIT 1),'reviewer preserves inference');
SELECT pg_temp.assert_hierarchy(public.admin_command('10000000-0000-0000-0000-000000000004','dashboard')->'statuses'->>'corrected'='1','overview counts human statuses');
SELECT pg_temp.assert_hierarchy(public.admin_command('10000000-0000-0000-0000-000000000004','dashboard')->>'users' IS NULL,'reviewer cannot see account totals');
SELECT pg_temp.assert_hierarchy(jsonb_array_length(public.admin_command('10000000-0000-0000-0000-000000000004','audit'))=1,'reviewer sees only review audit');
SELECT pg_temp.assert_hierarchy((SELECT bool_and((entry->>'can_manage')::boolean=(entry->>'role' IN ('user','admin'))) FROM jsonb_array_elements(public.admin_command('10000000-0000-0000-0000-000000000002','users')) entry),'manageable flags match hierarchy');
SELECT public.admin_command('10000000-0000-0000-0000-000000000002','role','{"id":"10000000-0000-0000-0000-000000000004","role":"user"}');
DO $$ BEGIN
 BEGIN PERFORM public.admin_command('10000000-0000-0000-0000-000000000004','photos'); RAISE EXCEPTION 'revoked reviewer accepted'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
SELECT pg_temp.assert_hierarchy(public.my_account_policy()->>'role'='owner' AND public.my_account_policy()->>'hierarchy_version'='1','owner policy advertised');
DO $$ BEGIN
 BEGIN PERFORM public.admin_authorize('10000000-0000-0000-0000-000000000001','role','{}'); RAISE EXCEPTION 'helper exposed'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
ROLLBACK;

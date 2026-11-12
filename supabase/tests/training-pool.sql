-- training_pool(): only copies allowed by Terms/Privacy v1.4 or v1.5 become 'ready';
-- v1.5 copies are kept indefinitely. training_photos_to_delete(): expired v1.4
-- copies and copies no longer allowed (analysis, consent, authorization, account).
BEGIN;
CREATE FUNCTION pg_temp.assert_true(value boolean, message text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF value IS DISTINCT FROM true THEN RAISE EXCEPTION 'Assertion failed: %', message; END IF; END $$;
CREATE FUNCTION pg_temp.state(analysis uuid) RETURNS text LANGUAGE sql AS $$
 SELECT coalesce((SELECT state FROM public.training_pool() WHERE analysis_id=analysis), 'absent') $$;
CREATE FUNCTION pg_temp.listed(analysis uuid) RETURNS boolean LANGUAGE sql AS $$
 SELECT EXISTS(SELECT 1 FROM public.training_photos_to_delete() WHERE object_name LIKE '%/'||analysis::text||'.jpg') $$;

INSERT INTO auth.users(id,email,email_confirmed_at) VALUES
 ('a0000000-0000-0000-0000-000000000001','reviewer@test.local',now()),
 ('a0000000-0000-0000-0000-000000000002','producer@test.local',now()),
 ('a0000000-0000-0000-0000-000000000003','revoked@test.local',now());
UPDATE public.account_controls SET role='superadmin' WHERE user_id='a0000000-0000-0000-0000-000000000001';

-- Producer and revoked user accepted documents v1.4 on 2026-11-01.
INSERT INTO public.legal_document_acceptances(user_id,terms_version,privacy_version,accepted_at) VALUES
 ('a0000000-0000-0000-0000-000000000002','1.4','1.4','2026-11-01T00:00:00Z'),
 ('a0000000-0000-0000-0000-000000000003','1.4','1.4','2026-11-01T00:00:00Z');
INSERT INTO public.training_consents(user_id,terms_version,granted) VALUES
 ('a0000000-0000-0000-0000-000000000002','1.2',true),
 ('a0000000-0000-0000-0000-000000000003','1.1',false);

-- b1 confirmed, b2 corrected, b3 not reviewed, b4 doubtful, b5 analyzed before the
-- acceptance, b6 inconclusive, b7 deleted, b8 copy older than 90 days, b9 revoked user.
INSERT INTO public.analyses(id,user_id,created_at,class_id,display_name,scientific_name,confidence,top3,inconclusive,threshold,inference_ms,model_sha256,app_version,deleted_at) VALUES
 ('b0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000002','2026-11-05','frog_eye','x','x',0.8,'[]',false,0.5,3,'model-a','t',NULL),
 ('b0000000-0000-0000-0000-000000000002','a0000000-0000-0000-0000-000000000002','2026-11-05','frog_eye','x','x',0.8,'[]',false,0.5,3,'model-a','t',NULL),
 ('b0000000-0000-0000-0000-000000000003','a0000000-0000-0000-0000-000000000002','2026-11-05','brown_spot','x','x',0.8,'[]',false,0.5,3,'model-a','t',NULL),
 ('b0000000-0000-0000-0000-000000000004','a0000000-0000-0000-0000-000000000002','2026-11-05','frog_eye','x','x',0.8,'[]',false,0.5,3,'model-a','t',NULL),
 ('b0000000-0000-0000-0000-000000000005','a0000000-0000-0000-0000-000000000002','2026-10-20','frog_eye','x','x',0.8,'[]',false,0.5,3,'model-a','t',NULL),
 ('b0000000-0000-0000-0000-000000000006','a0000000-0000-0000-0000-000000000002','2026-11-05','frog_eye','x','x',0.3,'[]',true,0.5,3,'model-a','t',NULL),
 ('b0000000-0000-0000-0000-000000000007','a0000000-0000-0000-0000-000000000002','2026-11-05','frog_eye','x','x',0.8,'[]',false,0.5,3,'model-a','t',now()),
 ('b0000000-0000-0000-0000-000000000008','a0000000-0000-0000-0000-000000000002','2026-11-05','frog_eye','x','x',0.8,'[]',false,0.5,3,'model-a','t',NULL),
 ('b0000000-0000-0000-0000-000000000009','a0000000-0000-0000-0000-000000000003','2026-11-05','frog_eye','x','x',0.8,'[]',false,0.5,3,'model-a','t',NULL);
INSERT INTO public.training_photo_authorizations(user_id,analysis_id,terms_version,authorized)
 SELECT user_id, id, CASE WHEN user_id='a0000000-0000-0000-0000-000000000003' THEN '1.1' ELSE '1.2' END,
        user_id<>'a0000000-0000-0000-0000-000000000003'
 FROM public.analyses WHERE id::text LIKE 'b0000000%';
INSERT INTO storage.objects(bucket_id,name,created_at)
 SELECT 'training-photos', user_id::text||'/'||id::text||'.jpg',
        CASE WHEN id='b0000000-0000-0000-0000-000000000008' THEN now()-interval '91 days' ELSE now()-interval '80 days' END
 FROM public.analyses WHERE id::text LIKE 'b0000000%';
-- Active analyses also have the history photo the reviewers see.
INSERT INTO storage.objects(bucket_id,name)
 SELECT 'analysis-photos', user_id::text||'/'||id::text||'.jpg' FROM public.analyses
 WHERE id::text LIKE 'b0000000%' AND deleted_at IS NULL;
INSERT INTO public.photo_reviews(analysis_id,status,class_id) VALUES
 ('b0000000-0000-0000-0000-000000000001','confirmed','frog_eye'),
 ('b0000000-0000-0000-0000-000000000002','corrected','healthy'),
 ('b0000000-0000-0000-0000-000000000004','uncertain',NULL),
 ('b0000000-0000-0000-0000-000000000005','confirmed','frog_eye'),
 ('b0000000-0000-0000-0000-000000000006','confirmed','frog_eye'),
 ('b0000000-0000-0000-0000-000000000009','confirmed','frog_eye');

-- Only the trusted pipeline may read the pool.
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','a0000000-0000-0000-0000-000000000002',true);
DO $$ BEGIN
 BEGIN PERFORM public.training_pool(); RAISE EXCEPTION 'training pool exposed to users'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
SET LOCAL ROLE service_role;

SELECT pg_temp.assert_true(pg_temp.state('b0000000-0000-0000-0000-000000000001')='ready','confirmed copy is ready');
SELECT pg_temp.assert_true((SELECT label_class_id='frog_eye' AND predicted_class_id='frog_eye' FROM public.training_pool() WHERE analysis_id='b0000000-0000-0000-0000-000000000001'),'confirmed label is the prediction');
SELECT pg_temp.assert_true(pg_temp.state('b0000000-0000-0000-0000-000000000002')='ready','corrected copy is ready');
SELECT pg_temp.assert_true((SELECT label_class_id='healthy' AND predicted_class_id='frog_eye' FROM public.training_pool() WHERE analysis_id='b0000000-0000-0000-0000-000000000002'),'corrected label wins over the prediction');
SELECT pg_temp.assert_true(pg_temp.state('b0000000-0000-0000-0000-000000000003')='awaiting_review','unreviewed copy waits for review');
SELECT pg_temp.assert_true(pg_temp.state('b0000000-0000-0000-0000-000000000004')='excluded','doubtful copy is excluded');
SELECT pg_temp.assert_true(pg_temp.state('b0000000-0000-0000-0000-000000000005')='ineligible','photo analyzed before accepting v1.4 is not used');
SELECT pg_temp.assert_true(pg_temp.state('b0000000-0000-0000-0000-000000000006')='ineligible','inconclusive analysis is not used');
SELECT pg_temp.assert_true(pg_temp.state('b0000000-0000-0000-0000-000000000007')='ineligible','deleted analysis is not used');
SELECT pg_temp.assert_true(pg_temp.state('b0000000-0000-0000-0000-000000000008')='absent','copy older than 90 days is not listed');
SELECT pg_temp.assert_true(pg_temp.state('b0000000-0000-0000-0000-000000000009')='ineligible','revoked consent is not used');
SELECT pg_temp.assert_true((SELECT expires_at=uploaded_at+interval '90 days' FROM public.training_pool() WHERE analysis_id='b0000000-0000-0000-0000-000000000001'),'expiry follows the 90-day retention');

-- An account being deleted leaves the pool immediately.
RESET ROLE;
UPDATE public.account_controls SET deletion_pending=true WHERE user_id='a0000000-0000-0000-0000-000000000002';
SET LOCAL ROLE service_role;
SELECT pg_temp.assert_true(pg_temp.state('b0000000-0000-0000-0000-000000000001')='ineligible','deletion in progress removes the copy from the pool');
SELECT pg_temp.assert_true(pg_temp.listed('b0000000-0000-0000-0000-000000000001'),'cleanup removes copies of an account being deleted');
RESET ROLE;
UPDATE public.account_controls SET deletion_pending=false WHERE user_id='a0000000-0000-0000-0000-000000000002';
SET LOCAL ROLE service_role;

-- Admin panel: summary, filter and order follow the pool.
SELECT pg_temp.assert_true((SELECT t->>'ready'='2' AND t->>'awaiting_review'='1' AND t->>'excluded'='1' AND t->>'copies'='4'
  FROM (SELECT public.admin_command('a0000000-0000-0000-0000-000000000001','dashboard')->'training' t) s),'dashboard training summary');
SELECT pg_temp.assert_true((SELECT t->'ready_classes'->>'healthy'='1' AND t->'ready_classes'->>'frog_eye'='1'
  FROM (SELECT public.admin_command('a0000000-0000-0000-0000-000000000001','dashboard')->'training' t) s),'ready copies counted by reviewed class');
SELECT pg_temp.assert_true((SELECT t->>'expiring_soon'='1' FROM (SELECT public.admin_command('a0000000-0000-0000-0000-000000000001','dashboard')->'training' t) s),'unreviewed copy 10 days from expiring is flagged');
SELECT pg_temp.assert_true((SELECT count(*)=4 FROM jsonb_array_elements(public.admin_command('a0000000-0000-0000-0000-000000000001','photos','{"training":"true","status":""}'))),'training filter lists only copies that can still be used');
SELECT pg_temp.assert_true((SELECT e->>'id'='b0000000-0000-0000-0000-000000000003' FROM jsonb_array_elements(public.admin_command('a0000000-0000-0000-0000-000000000001','photos','{"status":""}')) WITH ORDINALITY x(e,n) WHERE n=1),'pending training copy is reviewed first');
SELECT pg_temp.assert_true((SELECT bool_and(e ? 'training_expires_at' AND e->>'training_expires_at' IS NOT NULL) FROM jsonb_array_elements(public.admin_command('a0000000-0000-0000-0000-000000000001','photos','{"training":"true","status":""}')) e),'training rows carry their expiry');
SELECT pg_temp.assert_true((SELECT e->>'training_expires_at' IS NULL FROM jsonb_array_elements(public.admin_command('a0000000-0000-0000-0000-000000000001','photos','{"status":""}')) e WHERE e->>'id'='b0000000-0000-0000-0000-000000000005'),'ineligible copy is not flagged as training');

-- Terms v1.5: copies of analyses made after the first v1.5 acceptance are kept
-- indefinitely; earlier copies keep the 90-day promise of v1.4.
RESET ROLE;
INSERT INTO auth.users(id,email,email_confirmed_at) VALUES ('a0000000-0000-0000-0000-000000000004','permanent@test.local',now());
INSERT INTO public.legal_document_acceptances(user_id,terms_version,privacy_version,accepted_at) VALUES
 ('a0000000-0000-0000-0000-000000000004','1.4','1.4',now()-interval '300 days'),
 ('a0000000-0000-0000-0000-000000000004','1.5','1.5',now()-interval '250 days');
INSERT INTO public.training_consents(user_id,terms_version,granted) VALUES ('a0000000-0000-0000-0000-000000000004','1.2',true);
-- c1 after v1.5, uploaded 200 days ago and confirmed; c2 after v1.5, unreviewed;
-- c3 analyzed under v1.4, uploaded 60 days ago; c4 analyzed under v1.4, uploaded 91 days ago.
INSERT INTO public.analyses(id,user_id,created_at,class_id,display_name,scientific_name,confidence,top3,inconclusive,threshold,inference_ms,model_sha256,app_version) VALUES
 ('c0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000004',now()-interval '200 days','frog_eye','x','x',0.8,'[]',false,0.5,3,'model-a','t'),
 ('c0000000-0000-0000-0000-000000000002','a0000000-0000-0000-0000-000000000004',now()-interval '100 days','frog_eye','x','x',0.8,'[]',false,0.5,3,'model-a','t'),
 ('c0000000-0000-0000-0000-000000000003','a0000000-0000-0000-0000-000000000004',now()-interval '270 days','frog_eye','x','x',0.8,'[]',false,0.5,3,'model-a','t'),
 ('c0000000-0000-0000-0000-000000000004','a0000000-0000-0000-0000-000000000004',now()-interval '280 days','frog_eye','x','x',0.8,'[]',false,0.5,3,'model-a','t');
INSERT INTO public.training_photo_authorizations(user_id,analysis_id,terms_version,authorized)
 SELECT user_id,id,'1.2',true FROM public.analyses WHERE id::text LIKE 'c0000000%';
INSERT INTO storage.objects(bucket_id,name,created_at)
 SELECT 'training-photos', user_id::text||'/'||id::text||'.jpg', CASE id
   WHEN 'c0000000-0000-0000-0000-000000000001' THEN now()-interval '200 days'
   WHEN 'c0000000-0000-0000-0000-000000000002' THEN now()-interval '100 days'
   WHEN 'c0000000-0000-0000-0000-000000000003' THEN now()-interval '60 days'
   ELSE now()-interval '91 days' END
 FROM public.analyses WHERE id::text LIKE 'c0000000%';
INSERT INTO storage.objects(bucket_id,name)
 SELECT 'analysis-photos', user_id::text||'/'||id::text||'.jpg' FROM public.analyses WHERE id::text LIKE 'c0000000%';
-- Copies without a known analysis follow the 90-day rule.
INSERT INTO storage.objects(bucket_id,name,created_at) VALUES
 ('training-photos','a0000000-0000-0000-0000-000000000004/ffffffff-0000-0000-0000-000000000001.jpg',now()-interval '95 days'),
 ('training-photos','a0000000-0000-0000-0000-000000000004/ffffffff-0000-0000-0000-000000000002.jpg',now()-interval '10 days');
INSERT INTO public.photo_reviews(analysis_id,status,class_id) VALUES ('c0000000-0000-0000-0000-000000000001','confirmed','frog_eye');

-- Only the trusted cleanup may list copies to delete.
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','a0000000-0000-0000-0000-000000000004',true);
DO $$ BEGIN
 BEGIN PERFORM public.training_photos_to_delete(); RAISE EXCEPTION 'copies to delete exposed to users'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
SET LOCAL ROLE service_role;

SELECT pg_temp.assert_true(pg_temp.state('c0000000-0000-0000-0000-000000000001')='ready','v1.5 copy older than 90 days stays ready');
SELECT pg_temp.assert_true((SELECT expires_at IS NULL FROM public.training_pool() WHERE analysis_id='c0000000-0000-0000-0000-000000000001'),'v1.5 copy never expires');
SELECT pg_temp.assert_true(pg_temp.state('c0000000-0000-0000-0000-000000000002')='awaiting_review','unreviewed v1.5 copy waits for review');
SELECT pg_temp.assert_true((SELECT expires_at=uploaded_at+interval '90 days' FROM public.training_pool() WHERE analysis_id='c0000000-0000-0000-0000-000000000003'),'copy analyzed under v1.4 keeps 90 days after v1.5');
SELECT pg_temp.assert_true(pg_temp.state('c0000000-0000-0000-0000-000000000004')='absent','expired v1.4 copy leaves the pool');
SELECT pg_temp.assert_true((SELECT array_agg(object_name ORDER BY object_name) FROM public.training_photos_to_delete())=ARRAY[
  'a0000000-0000-0000-0000-000000000002/b0000000-0000-0000-0000-000000000006.jpg',
  'a0000000-0000-0000-0000-000000000002/b0000000-0000-0000-0000-000000000007.jpg',
  'a0000000-0000-0000-0000-000000000002/b0000000-0000-0000-0000-000000000008.jpg',
  'a0000000-0000-0000-0000-000000000003/b0000000-0000-0000-0000-000000000009.jpg',
  'a0000000-0000-0000-0000-000000000004/c0000000-0000-0000-0000-000000000004.jpg',
  'a0000000-0000-0000-0000-000000000004/ffffffff-0000-0000-0000-000000000001.jpg',
  'a0000000-0000-0000-0000-000000000004/ffffffff-0000-0000-0000-000000000002.jpg'],
  'cleanup lists inconclusive, deleted, expired, revoked and orphan copies only');
SELECT pg_temp.assert_true(NOT pg_temp.listed('c0000000-0000-0000-0000-000000000001'),'ready v1.5 copy is kept');
SELECT pg_temp.assert_true(NOT pg_temp.listed('c0000000-0000-0000-0000-000000000003'),'v1.4 copy within 90 days is kept');

-- Admin panel: permanent copies are never "expiring" and come after dated ones.
SELECT pg_temp.assert_true((SELECT t->>'expiring_soon'='1' AND t->>'ready'='3' FROM (SELECT public.admin_command('a0000000-0000-0000-0000-000000000001','dashboard')->'training' t) s),'permanent copies are not expiring');
SELECT pg_temp.assert_true((SELECT array_agg(e->>'id' ORDER BY n) FROM jsonb_array_elements(public.admin_command('a0000000-0000-0000-0000-000000000001','photos','{"status":""}')) WITH ORDINALITY x(e,n) WHERE n<=3)=ARRAY[
  'b0000000-0000-0000-0000-000000000003','c0000000-0000-0000-0000-000000000003','c0000000-0000-0000-0000-000000000002'],'pending copies by expiry, permanent last');
SELECT pg_temp.assert_true((SELECT e->>'training_expires_at' IS NULL AND e->>'training_state'='ready' FROM jsonb_array_elements(public.admin_command('a0000000-0000-0000-0000-000000000001','photos','{"status":""}')) e WHERE e->>'id'='c0000000-0000-0000-0000-000000000001'),'permanent copy has no expiry in the panel');

-- Revoking one photo lists only that copy.
RESET ROLE;
UPDATE public.training_photo_authorizations SET authorized=false WHERE analysis_id='c0000000-0000-0000-0000-000000000002';
SET LOCAL ROLE service_role;
SELECT pg_temp.assert_true(pg_temp.listed('c0000000-0000-0000-0000-000000000002'),'revoked photo authorization is cleaned up');
SELECT pg_temp.assert_true(NOT pg_temp.listed('c0000000-0000-0000-0000-000000000001'),'other authorized copies are kept');
-- A missing authorization also lists the copy.
RESET ROLE;
DELETE FROM public.training_photo_authorizations WHERE analysis_id='c0000000-0000-0000-0000-000000000003';
SET LOCAL ROLE service_role;
SELECT pg_temp.assert_true(pg_temp.listed('c0000000-0000-0000-0000-000000000003'),'copy without authorization is cleaned up');

-- Revoking the consent excludes and lists every copy of the account, even
-- those the revoking device does not know about.
RESET ROLE;
UPDATE public.training_consents SET granted=false WHERE user_id='a0000000-0000-0000-0000-000000000004';
SET LOCAL ROLE service_role;
SELECT pg_temp.assert_true(pg_temp.state('c0000000-0000-0000-0000-000000000001')='ineligible','revoked v1.5 copy is not used');
SELECT pg_temp.assert_true(pg_temp.listed('c0000000-0000-0000-0000-000000000001'),'revoked consent is cleaned up');

-- Accepting again keeps copies whose photo authorization is still granted.
RESET ROLE;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','a0000000-0000-0000-0000-000000000004',true);
SELECT public.accept_current_legal_documents('1.5','1.5');
RESET ROLE;
SET LOCAL ROLE service_role;
SELECT pg_temp.assert_true(NOT pg_temp.listed('c0000000-0000-0000-0000-000000000001'),'re-accepted user keeps authorized copies');
SELECT pg_temp.assert_true(pg_temp.listed('c0000000-0000-0000-0000-000000000002'),'revoked photo stays listed after re-acceptance');
SELECT pg_temp.assert_true(pg_temp.state('c0000000-0000-0000-0000-000000000001')='ready','re-accepted copy is ready again');
ROLLBACK;

BEGIN;
CREATE FUNCTION pg_temp.assert_true(value boolean, message text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF value IS DISTINCT FROM true THEN RAISE EXCEPTION 'Assertion failed: %', message; END IF; END $$;

INSERT INTO auth.users(id,email,raw_user_meta_data) VALUES
 ('91000000-0000-0000-0000-000000000001','accepted@test.local','{"terms_version":"1.0","privacy_version":"1.0","display_name":"Accepted"}'),
 ('91000000-0000-0000-0000-000000000002','legacy@test.local','{"display_name":"Legacy"}'),
 ('91000000-0000-0000-0000-000000000003','accepted-v11@test.local','{"terms_version":"1.1","privacy_version":"1.1","display_name":"Accepted 1.1"}'),
 ('91000000-0000-0000-0000-000000000004','accepted-v12@test.local','{"terms_version":"1.2","privacy_version":"1.2","display_name":"Accepted 1.2"}'),
 ('91000000-0000-0000-0000-000000000005','accepted-v13@test.local','{"terms_version":"1.3","privacy_version":"1.3","display_name":"Accepted 1.3"}'),
 ('91000000-0000-0000-0000-000000000006','accepted-v14@test.local','{"terms_version":"1.4","privacy_version":"1.4","display_name":"Accepted 1.4"}');

SELECT pg_temp.assert_true(
 (SELECT terms_version='1.0' AND privacy_version='1.0' AND accepted_at IS NOT NULL
  FROM public.legal_document_acceptances WHERE user_id='91000000-0000-0000-0000-000000000001'),
 'signup saves accepted versions with server timestamp');
SELECT pg_temp.assert_true(
 NOT EXISTS(SELECT 1 FROM public.legal_document_acceptances WHERE user_id='91000000-0000-0000-0000-000000000002'),
 'accounts created without the current versions have no acceptance receipt');
SELECT pg_temp.assert_true(
 (SELECT terms_version='1.1' AND privacy_version='1.1' AND accepted_at IS NOT NULL
  FROM public.legal_document_acceptances WHERE user_id='91000000-0000-0000-0000-000000000003'),
 'signup saves version 1.1 acceptance');
SELECT pg_temp.assert_true(
 (SELECT terms_version='1.2' AND privacy_version='1.2' AND accepted_at IS NOT NULL
  FROM public.legal_document_acceptances WHERE user_id='91000000-0000-0000-0000-000000000004'),
 'signup saves version 1.2 acceptance');
SELECT pg_temp.assert_true(
 (SELECT terms_version='1.3' AND privacy_version='1.3' AND accepted_at IS NOT NULL
  FROM public.legal_document_acceptances WHERE user_id='91000000-0000-0000-0000-000000000005'),
 'signup saves version 1.3 acceptance');

SELECT pg_temp.assert_true(
 (SELECT terms_version='1.4' AND privacy_version='1.4' FROM public.legal_document_acceptances WHERE user_id='91000000-0000-0000-0000-000000000006'),
 'signup saves version 1.4 acceptance');
SELECT pg_temp.assert_true(
 (SELECT granted AND terms_version='1.2' FROM public.training_consents WHERE user_id='91000000-0000-0000-0000-000000000006'),
 'signup with documents 1.4 records the training consent');
SELECT pg_temp.assert_true(
 NOT EXISTS(SELECT 1 FROM public.training_consents WHERE user_id='91000000-0000-0000-0000-000000000005'),
 'signup with older documents records no training consent');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','91000000-0000-0000-0000-000000000001',true);
SELECT pg_temp.assert_true((SELECT count(*)=1 FROM public.legal_document_acceptances),'user can read own receipt');
SELECT public.accept_current_legal_documents('1.4','1.4');
SELECT public.accept_current_legal_documents('1.4','1.4');
SELECT pg_temp.assert_true(
 (SELECT count(*)=1 FROM public.legal_document_acceptances
  WHERE user_id=auth.uid() AND terms_version='1.4' AND privacy_version='1.4' AND accepted_at IS NOT NULL),
 'existing user explicitly appends one server-timestamped current receipt');
SELECT pg_temp.assert_true(
 (SELECT granted AND terms_version='1.2' FROM public.training_consents WHERE user_id=auth.uid()),
 'accepting documents 1.4 grants the training consent');
UPDATE public.training_consents SET granted=false WHERE user_id=auth.uid();
SELECT pg_temp.assert_true((SELECT NOT granted FROM public.training_consents WHERE user_id=auth.uid()),'user can revoke');
SELECT public.accept_current_legal_documents('1.4','1.4');
SELECT pg_temp.assert_true((SELECT granted FROM public.training_consents WHERE user_id=auth.uid()),'accepting again after revoking grants again');
DO $$ BEGIN
 BEGIN INSERT INTO public.legal_document_acceptances(user_id,terms_version,privacy_version)
   VALUES(auth.uid(),'1.0','1.0'); RAISE EXCEPTION 'client inserted acceptance'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN UPDATE public.legal_document_acceptances SET terms_version='0' WHERE user_id=auth.uid(); RAISE EXCEPTION 'client changed acceptance'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN DELETE FROM public.legal_document_acceptances WHERE user_id=auth.uid(); RAISE EXCEPTION 'client deleted acceptance'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN PERFORM public.accept_current_legal_documents('1.3','1.3'); RAISE EXCEPTION 'accepted obsolete legal version'; EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $$;
RESET ROLE;
SELECT set_config('request.jwt.claim.sub','',true);
DO $$ BEGIN
 BEGIN PERFORM public.accept_current_legal_documents('1.4','1.4'); RAISE EXCEPTION 'anonymous acceptance succeeded'; EXCEPTION WHEN invalid_authorization_specification THEN NULL; END;
END $$;
ROLLBACK;

BEGIN;
CREATE FUNCTION pg_temp.assert_true(value boolean, message text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF value IS DISTINCT FROM true THEN RAISE EXCEPTION 'Assertion failed: %', message; END IF; END $$;

INSERT INTO auth.users(id,email,raw_user_meta_data) VALUES
 ('91000000-0000-0000-0000-000000000001','accepted@test.local','{"terms_version":"1.0","privacy_version":"1.0","display_name":"Accepted"}'),
 ('91000000-0000-0000-0000-000000000002','legacy@test.local','{"display_name":"Legacy"}');

SELECT pg_temp.assert_true(
 (SELECT terms_version='1.0' AND privacy_version='1.0' AND accepted_at IS NOT NULL
  FROM public.legal_document_acceptances WHERE user_id='91000000-0000-0000-0000-000000000001'),
 'signup saves accepted versions with server timestamp');
SELECT pg_temp.assert_true(
 NOT EXISTS(SELECT 1 FROM public.legal_document_acceptances WHERE user_id='91000000-0000-0000-0000-000000000002'),
 'accounts created without the current versions have no acceptance receipt');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','91000000-0000-0000-0000-000000000001',true);
SELECT pg_temp.assert_true((SELECT count(*)=1 FROM public.legal_document_acceptances),'user can read own receipt');
DO $$ BEGIN
 BEGIN INSERT INTO public.legal_document_acceptances(user_id,terms_version,privacy_version)
   VALUES(auth.uid(),'1.0','1.0'); RAISE EXCEPTION 'client inserted acceptance'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN UPDATE public.legal_document_acceptances SET terms_version='0' WHERE user_id=auth.uid(); RAISE EXCEPTION 'client changed acceptance'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN DELETE FROM public.legal_document_acceptances WHERE user_id=auth.uid(); RAISE EXCEPTION 'client deleted acceptance'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
ROLLBACK;

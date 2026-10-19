-- Existing accounts can append an immutable receipt after an explicit in-app acceptance.
CREATE OR REPLACE FUNCTION public.accept_current_legal_documents(
    accepted_terms_version text,
    accepted_privacy_version text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    accepting_user_id uuid := auth.uid();
BEGIN
    IF accepting_user_id IS NULL THEN
        RAISE EXCEPTION 'authentication required' USING ERRCODE = '28000';
    END IF;

    IF accepted_terms_version <> '1.2' OR accepted_privacy_version <> '1.2' THEN
        RAISE EXCEPTION 'unsupported legal document version' USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.legal_document_acceptances (user_id, terms_version, privacy_version)
    VALUES (accepting_user_id, accepted_terms_version, accepted_privacy_version)
    ON CONFLICT (user_id, terms_version, privacy_version) DO NOTHING;
END;
$$;

REVOKE ALL ON FUNCTION public.accept_current_legal_documents(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.accept_current_legal_documents(text, text) TO authenticated;

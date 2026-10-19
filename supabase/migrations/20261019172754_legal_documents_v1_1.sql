ALTER TABLE public.legal_document_acceptances
    DROP CONSTRAINT legal_document_acceptances_terms_version_check,
    DROP CONSTRAINT legal_document_acceptances_privacy_version_check;

ALTER TABLE public.legal_document_acceptances
    ADD CONSTRAINT legal_document_acceptances_terms_version_check CHECK (terms_version IN ('1.0', '1.1')),
    ADD CONSTRAINT legal_document_acceptances_privacy_version_check CHECK (privacy_version IN ('1.0', '1.1'));

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    accepted_terms_version text := NEW.raw_user_meta_data->>'terms_version';
    accepted_privacy_version text := NEW.raw_user_meta_data->>'privacy_version';
BEGIN
    INSERT INTO public.profiles (id, display_name)
    VALUES (NEW.id, NEW.raw_user_meta_data->>'display_name');

    IF accepted_terms_version IN ('1.0', '1.1')
       AND accepted_privacy_version IN ('1.0', '1.1')
       AND accepted_terms_version = accepted_privacy_version THEN
        INSERT INTO public.legal_document_acceptances (user_id, terms_version, privacy_version)
        VALUES (NEW.id, accepted_terms_version, accepted_privacy_version);
    END IF;

    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.handle_new_user() TO supabase_auth_admin;

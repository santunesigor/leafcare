-- Immutable evidence of the legal documents accepted when an account is created.
CREATE TABLE public.legal_document_acceptances (
    acceptance_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    terms_version text NOT NULL CHECK (terms_version = '1.0'),
    privacy_version text NOT NULL CHECK (privacy_version = '1.0'),
    accepted_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, terms_version, privacy_version)
);

ALTER TABLE public.legal_document_acceptances ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.legal_document_acceptances FROM anon, authenticated;
GRANT SELECT ON public.legal_document_acceptances TO authenticated;

CREATE POLICY "Users can view own legal document acceptance"
    ON public.legal_document_acceptances
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = user_id);

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

    IF accepted_terms_version = '1.0' AND accepted_privacy_version = '1.0' THEN
        INSERT INTO public.legal_document_acceptances (user_id, terms_version, privacy_version)
        VALUES (NEW.id, accepted_terms_version, accepted_privacy_version);
    END IF;

    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.handle_new_user() TO supabase_auth_admin;

-- Legal documents v1.4: using LeafCare requires accepting the Terms of Use and
-- Privacy Policy, which now include the use of analyzed photos for model
-- training (training consent version 1.2). Accepting records both the legal
-- receipt and the training consent in one server-timestamped step.
-- Revocations are always accepted, whatever the version, so a withdrawal can
-- never be blocked by a policy mismatch.

ALTER TABLE public.legal_document_acceptances
  DROP CONSTRAINT legal_document_acceptances_terms_version_check,
  DROP CONSTRAINT legal_document_acceptances_privacy_version_check;

ALTER TABLE public.legal_document_acceptances
  ADD CONSTRAINT legal_document_acceptances_terms_version_check CHECK (terms_version IN ('1.0', '1.1', '1.2', '1.3', '1.4')),
  ADD CONSTRAINT legal_document_acceptances_privacy_version_check CHECK (privacy_version IN ('1.0', '1.1', '1.2', '1.3', '1.4'));

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
    IF accepted_terms_version <> '1.4' OR accepted_privacy_version <> '1.4' THEN
        RAISE EXCEPTION 'unsupported legal document version' USING ERRCODE = '22023';
    END IF;
    INSERT INTO public.legal_document_acceptances (user_id, terms_version, privacy_version)
    VALUES (accepting_user_id, accepted_terms_version, accepted_privacy_version)
    ON CONFLICT (user_id, terms_version, privacy_version) DO NOTHING;
    -- Accepting v1.4 is the training consent; accepting again after a
    -- revocation grants it again.
    INSERT INTO public.training_consents (user_id, terms_version, granted)
    VALUES (accepting_user_id, '1.2', true)
    ON CONFLICT (user_id) DO UPDATE SET terms_version = '1.2', granted = true;
END;
$$;
REVOKE ALL ON FUNCTION public.accept_current_legal_documents(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.accept_current_legal_documents(text, text) TO authenticated;

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

    IF accepted_terms_version IN ('1.0', '1.1', '1.2', '1.3', '1.4')
       AND accepted_privacy_version IN ('1.0', '1.1', '1.2', '1.3', '1.4')
       AND accepted_terms_version = accepted_privacy_version THEN
        INSERT INTO public.legal_document_acceptances (user_id, terms_version, privacy_version)
        VALUES (NEW.id, accepted_terms_version, accepted_privacy_version);
        IF accepted_terms_version = '1.4' THEN
            INSERT INTO public.training_consents (user_id, terms_version, granted)
            VALUES (NEW.id, '1.2', true);
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP POLICY "Users create own current training consent" ON public.training_consents;
DROP POLICY "Users update own current training consent" ON public.training_consents;
CREATE POLICY "Users create own current training consent" ON public.training_consents
  FOR INSERT TO authenticated WITH CHECK (
    (SELECT auth.uid()) = user_id
    AND (NOT granted OR (terms_version = '1.2' AND EXISTS (
      SELECT 1 FROM public.legal_document_acceptances l
      WHERE l.user_id = (SELECT auth.uid()) AND l.terms_version = '1.4' AND l.privacy_version = '1.4'
    )))
  );
CREATE POLICY "Users update own current training consent" ON public.training_consents
  FOR UPDATE TO authenticated USING ((SELECT auth.uid()) = user_id)
  WITH CHECK (
    (SELECT auth.uid()) = user_id
    AND (NOT granted OR (terms_version = '1.2' AND EXISTS (
      SELECT 1 FROM public.legal_document_acceptances l
      WHERE l.user_id = (SELECT auth.uid()) AND l.terms_version = '1.4' AND l.privacy_version = '1.4'
    )))
  );

DROP POLICY "Users create own photo training choices" ON public.training_photo_authorizations;
DROP POLICY "Users update own photo training choices" ON public.training_photo_authorizations;
CREATE POLICY "Users create own photo training choices" ON public.training_photo_authorizations
  FOR INSERT TO authenticated WITH CHECK (
    (SELECT auth.uid()) = user_id
    AND EXISTS (
      SELECT 1 FROM public.analyses a
      WHERE a.id = analysis_id AND a.user_id = (SELECT auth.uid())
    )
    AND (NOT authorized OR (terms_version = '1.2' AND EXISTS (
      SELECT 1 FROM public.training_consents c
      WHERE c.user_id = (SELECT auth.uid()) AND c.granted AND c.terms_version = '1.2'
    ) AND EXISTS (
      SELECT 1 FROM public.legal_document_acceptances l
      WHERE l.user_id = (SELECT auth.uid()) AND l.terms_version = '1.4' AND l.privacy_version = '1.4'
    )))
  );
CREATE POLICY "Users update own photo training choices" ON public.training_photo_authorizations
  FOR UPDATE TO authenticated USING ((SELECT auth.uid()) = user_id)
  WITH CHECK (
    (SELECT auth.uid()) = user_id
    AND EXISTS (
      SELECT 1 FROM public.analyses a
      WHERE a.id = analysis_id AND a.user_id = (SELECT auth.uid())
    )
    AND (NOT authorized OR (terms_version = '1.2' AND EXISTS (
      SELECT 1 FROM public.training_consents c
      WHERE c.user_id = (SELECT auth.uid()) AND c.granted AND c.terms_version = '1.2'
    ) AND EXISTS (
      SELECT 1 FROM public.legal_document_acceptances l
      WHERE l.user_id = (SELECT auth.uid()) AND l.terms_version = '1.4' AND l.privacy_version = '1.4'
    )))
  );

-- Training copies: the object path binds the signed-in owner, the analysis and
-- the current consent (v1.2) given by accepting documents v1.4.
DROP POLICY "Users upload explicitly authorized training photos" ON storage.objects;
DROP POLICY "Users read own authorized training photos" ON storage.objects;
DROP POLICY "Users replace own authorized training photos" ON storage.objects;
CREATE POLICY "Users upload explicitly authorized training photos" ON storage.objects
  FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'training-photos'
    AND EXISTS (
      SELECT 1
      FROM public.training_photo_authorizations p
      JOIN public.training_consents c ON c.user_id = p.user_id
      JOIN public.analyses a ON a.id = p.analysis_id AND a.user_id = p.user_id
      WHERE p.user_id = (SELECT auth.uid())
        AND p.authorized AND p.terms_version = '1.2'
        AND c.granted AND c.terms_version = '1.2'
        AND EXISTS (SELECT 1 FROM public.legal_document_acceptances l
          WHERE l.user_id = p.user_id AND l.terms_version = '1.4' AND l.privacy_version = '1.4')
        AND name = p.user_id::text || '/' || p.analysis_id::text || '.jpg'
    )
  );
CREATE POLICY "Users read own authorized training photos" ON storage.objects
  FOR SELECT TO authenticated USING (
    bucket_id = 'training-photos'
    AND EXISTS (
      SELECT 1
      FROM public.training_photo_authorizations p
      JOIN public.training_consents c ON c.user_id = p.user_id
      WHERE p.user_id = (SELECT auth.uid())
        AND p.authorized AND p.terms_version = '1.2'
        AND c.granted AND c.terms_version = '1.2'
        AND EXISTS (SELECT 1 FROM public.legal_document_acceptances l
          WHERE l.user_id = p.user_id AND l.terms_version = '1.4' AND l.privacy_version = '1.4')
        AND name = p.user_id::text || '/' || p.analysis_id::text || '.jpg'
    )
  );
CREATE POLICY "Users replace own authorized training photos" ON storage.objects
  FOR UPDATE TO authenticated USING (
    bucket_id = 'training-photos'
    AND EXISTS (
      SELECT 1 FROM public.training_photo_authorizations p
      JOIN public.training_consents c ON c.user_id = p.user_id
      WHERE p.user_id = (SELECT auth.uid()) AND p.authorized
        AND p.terms_version = '1.2' AND c.granted AND c.terms_version = '1.2'
        AND EXISTS (SELECT 1 FROM public.legal_document_acceptances l
          WHERE l.user_id = p.user_id AND l.terms_version = '1.4' AND l.privacy_version = '1.4')
        AND name = p.user_id::text || '/' || p.analysis_id::text || '.jpg'
    )
  ) WITH CHECK (
    bucket_id = 'training-photos'
    AND EXISTS (
      SELECT 1 FROM public.training_photo_authorizations p
      JOIN public.training_consents c ON c.user_id = p.user_id
      WHERE p.user_id = (SELECT auth.uid()) AND p.authorized
        AND p.terms_version = '1.2' AND c.granted AND c.terms_version = '1.2'
        AND EXISTS (SELECT 1 FROM public.legal_document_acceptances l
          WHERE l.user_id = p.user_id AND l.terms_version = '1.4' AND l.privacy_version = '1.4')
        AND name = p.user_id::text || '/' || p.analysis_id::text || '.jpg'
    )
  );

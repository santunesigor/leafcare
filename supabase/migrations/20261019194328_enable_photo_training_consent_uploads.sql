-- Current in-app legal documents: v1.3. Existing users must accept in-app.
ALTER TABLE public.legal_document_acceptances
  DROP CONSTRAINT legal_document_acceptances_terms_version_check,
  DROP CONSTRAINT legal_document_acceptances_privacy_version_check;

ALTER TABLE public.legal_document_acceptances
  ADD CONSTRAINT legal_document_acceptances_terms_version_check CHECK (terms_version IN ('1.0', '1.1', '1.2', '1.3')),
  ADD CONSTRAINT legal_document_acceptances_privacy_version_check CHECK (privacy_version IN ('1.0', '1.1', '1.2', '1.3'));

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
    IF accepted_terms_version <> '1.3' OR accepted_privacy_version <> '1.3' THEN
        RAISE EXCEPTION 'unsupported legal document version' USING ERRCODE = '22023';
    END IF;
    INSERT INTO public.legal_document_acceptances (user_id, terms_version, privacy_version)
    VALUES (accepting_user_id, accepted_terms_version, accepted_privacy_version)
    ON CONFLICT (user_id, terms_version, privacy_version) DO NOTHING;
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

    IF accepted_terms_version IN ('1.0', '1.1', '1.2', '1.3')
       AND accepted_privacy_version IN ('1.0', '1.1', '1.2', '1.3')
       AND accepted_terms_version = accepted_privacy_version THEN
        INSERT INTO public.legal_document_acceptances (user_id, terms_version, privacy_version)
        VALUES (NEW.id, accepted_terms_version, accepted_privacy_version);
    END IF;

    RETURN NEW;
END;
$$;

-- Keep decision timestamps authoritative in the database.
CREATE OR REPLACE FUNCTION public.stamp_training_consent()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  NEW.decided_at := clock_timestamp();
  NEW.updated_at := NEW.decided_at;
  RETURN NEW;
END;
$$;
CREATE OR REPLACE FUNCTION public.stamp_training_photo_authorization()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  NEW.decided_at := clock_timestamp();
  RETURN NEW;
END;
$$;
CREATE TRIGGER training_consents_stamp_decision
  BEFORE INSERT OR UPDATE ON public.training_consents
  FOR EACH ROW EXECUTE FUNCTION public.stamp_training_consent();
CREATE TRIGGER training_photo_authorizations_stamp_decision
  BEFORE INSERT OR UPDATE ON public.training_photo_authorizations
  FOR EACH ROW EXECUTE FUNCTION public.stamp_training_photo_authorization();

-- Replace the original deny-all-for-grants policies with current, scoped opt-in.
DROP POLICY "Users manage own training consent" ON public.training_consents;
CREATE POLICY "Users read own training consent" ON public.training_consents
  FOR SELECT TO authenticated USING ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users create own current training consent" ON public.training_consents
  FOR INSERT TO authenticated WITH CHECK (
    (SELECT auth.uid()) = user_id AND terms_version = '1.1'
    AND (NOT granted OR EXISTS (
      SELECT 1 FROM public.legal_document_acceptances l
      WHERE l.user_id = (SELECT auth.uid()) AND l.terms_version = '1.3' AND l.privacy_version = '1.3'
    ))
  );
CREATE POLICY "Users update own current training consent" ON public.training_consents
  FOR UPDATE TO authenticated USING ((SELECT auth.uid()) = user_id)
  WITH CHECK (
    (SELECT auth.uid()) = user_id AND terms_version = '1.1'
    AND (NOT granted OR EXISTS (
      SELECT 1 FROM public.legal_document_acceptances l
      WHERE l.user_id = (SELECT auth.uid()) AND l.terms_version = '1.3' AND l.privacy_version = '1.3'
    ))
  );

DROP POLICY "Users manage own photo training choices" ON public.training_photo_authorizations;
CREATE POLICY "Users read own photo training choices" ON public.training_photo_authorizations
  FOR SELECT TO authenticated USING ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users create own photo training choices" ON public.training_photo_authorizations
  FOR INSERT TO authenticated WITH CHECK (
    (SELECT auth.uid()) = user_id
    AND terms_version = '1.1'
    AND EXISTS (
      SELECT 1 FROM public.analyses a
      WHERE a.id = analysis_id AND a.user_id = (SELECT auth.uid())
    )
    AND (
      NOT authorized OR EXISTS (
        SELECT 1 FROM public.training_consents c
        WHERE c.user_id = (SELECT auth.uid()) AND c.granted AND c.terms_version = '1.1'
      )
    )
    AND (NOT authorized OR EXISTS (
      SELECT 1 FROM public.legal_document_acceptances l
      WHERE l.user_id = (SELECT auth.uid()) AND l.terms_version = '1.3' AND l.privacy_version = '1.3'
    ))
  );
CREATE POLICY "Users update own photo training choices" ON public.training_photo_authorizations
  FOR UPDATE TO authenticated USING ((SELECT auth.uid()) = user_id)
  WITH CHECK (
    (SELECT auth.uid()) = user_id
    AND terms_version = '1.1'
    AND EXISTS (
      SELECT 1 FROM public.analyses a
      WHERE a.id = analysis_id AND a.user_id = (SELECT auth.uid())
    )
    AND (
      NOT authorized OR EXISTS (
        SELECT 1 FROM public.training_consents c
        WHERE c.user_id = (SELECT auth.uid()) AND c.granted AND c.terms_version = '1.1'
      )
    )
    AND (NOT authorized OR EXISTS (
      SELECT 1 FROM public.legal_document_acceptances l
      WHERE l.user_id = (SELECT auth.uid()) AND l.terms_version = '1.3' AND l.privacy_version = '1.3'
    ))
  );

-- The object path must bind the signed-in owner, analysis and current opt-ins.
CREATE POLICY "Users upload explicitly authorized training photos" ON storage.objects
  FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'training-photos'
    AND EXISTS (
      SELECT 1
      FROM public.training_photo_authorizations p
      JOIN public.training_consents c ON c.user_id = p.user_id
      JOIN public.analyses a ON a.id = p.analysis_id AND a.user_id = p.user_id
      WHERE p.user_id = (SELECT auth.uid())
        AND p.authorized AND p.terms_version = '1.1'
        AND c.granted AND c.terms_version = '1.1'
        AND EXISTS (SELECT 1 FROM public.legal_document_acceptances l
          WHERE l.user_id = p.user_id AND l.terms_version = '1.3' AND l.privacy_version = '1.3')
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
        AND p.authorized AND p.terms_version = '1.1'
        AND c.granted AND c.terms_version = '1.1'
        AND EXISTS (SELECT 1 FROM public.legal_document_acceptances l
          WHERE l.user_id = p.user_id AND l.terms_version = '1.3' AND l.privacy_version = '1.3')
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
        AND p.terms_version = '1.1' AND c.granted AND c.terms_version = '1.1'
        AND EXISTS (SELECT 1 FROM public.legal_document_acceptances l
          WHERE l.user_id = p.user_id AND l.terms_version = '1.3' AND l.privacy_version = '1.3')
        AND name = p.user_id::text || '/' || p.analysis_id::text || '.jpg'
    )
  ) WITH CHECK (
    bucket_id = 'training-photos'
    AND EXISTS (
      SELECT 1 FROM public.training_photo_authorizations p
      JOIN public.training_consents c ON c.user_id = p.user_id
      WHERE p.user_id = (SELECT auth.uid()) AND p.authorized
        AND p.terms_version = '1.1' AND c.granted AND c.terms_version = '1.1'
        AND EXISTS (SELECT 1 FROM public.legal_document_acceptances l
          WHERE l.user_id = p.user_id AND l.terms_version = '1.3' AND l.privacy_version = '1.3')
        AND name = p.user_id::text || '/' || p.analysis_id::text || '.jpg'
    )
  );

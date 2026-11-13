-- Legal documents v1.5: training copies form a cumulative training set and are
-- kept indefinitely; they are removed only by revocation, deletion of the
-- analysis or deletion of the account.
--
-- - Accepting documents is still the training consent (record version 1.2).
-- - Training is allowed by a v1.4 or v1.5 acceptance (accepted_training_terms).
-- - Per-copy retention (training_copy_expires_at): a copy is permanent when its
--   analysis was made at/after the owner's first v1.5 acceptance; any other
--   copy keeps the v1.4 promise and expires 90 days after upload.
-- - The daily cleanup asks the database which copies must go
--   (training_photos_to_delete): expired ones and, as a server-side safety net
--   for permanent copies, those whose analysis, consent, authorization or
--   account no longer allows keeping them.

BEGIN;

ALTER TABLE public.legal_document_acceptances
  DROP CONSTRAINT legal_document_acceptances_terms_version_check,
  DROP CONSTRAINT legal_document_acceptances_privacy_version_check;

ALTER TABLE public.legal_document_acceptances
  ADD CONSTRAINT legal_document_acceptances_terms_version_check CHECK (terms_version IN ('1.0', '1.1', '1.2', '1.3', '1.4', '1.5')),
  ADD CONSTRAINT legal_document_acceptances_privacy_version_check CHECK (privacy_version IN ('1.0', '1.1', '1.2', '1.3', '1.4', '1.5'));

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
    IF accepted_terms_version <> '1.5' OR accepted_privacy_version <> '1.5' THEN
        RAISE EXCEPTION 'unsupported legal document version' USING ERRCODE = '22023';
    END IF;
    INSERT INTO public.legal_document_acceptances (user_id, terms_version, privacy_version)
    VALUES (accepting_user_id, accepted_terms_version, accepted_privacy_version)
    ON CONFLICT (user_id, terms_version, privacy_version) DO NOTHING;
    -- Accepting the documents is the training consent; accepting again after a
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

    IF accepted_terms_version IN ('1.0', '1.1', '1.2', '1.3', '1.4', '1.5')
       AND accepted_privacy_version IN ('1.0', '1.1', '1.2', '1.3', '1.4', '1.5')
       AND accepted_terms_version = accepted_privacy_version THEN
        INSERT INTO public.legal_document_acceptances (user_id, terms_version, privacy_version)
        VALUES (NEW.id, accepted_terms_version, accepted_privacy_version);
        IF accepted_terms_version IN ('1.4', '1.5') THEN
            INSERT INTO public.training_consents (user_id, terms_version, granted)
            VALUES (NEW.id, '1.2', true);
        END IF;
    END IF;

    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.handle_new_user() TO supabase_auth_admin;

-- Whether the signed-in user accepted documents that include training (v1.4 or v1.5).
CREATE FUNCTION public.accepted_training_terms() RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.legal_document_acceptances l
    WHERE l.user_id = auth.uid()
      AND (l.terms_version, l.privacy_version) IN (('1.4', '1.4'), ('1.5', '1.5'))
  )
$$;
REVOKE ALL ON FUNCTION public.accepted_training_terms() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.accepted_training_terms() TO authenticated;

DROP POLICY "Users create own current training consent" ON public.training_consents;
DROP POLICY "Users update own current training consent" ON public.training_consents;
CREATE POLICY "Users create own current training consent" ON public.training_consents
  FOR INSERT TO authenticated WITH CHECK (
    (SELECT auth.uid()) = user_id
    AND (NOT granted OR (terms_version = '1.2' AND (SELECT public.accepted_training_terms())))
  );
CREATE POLICY "Users update own current training consent" ON public.training_consents
  FOR UPDATE TO authenticated USING ((SELECT auth.uid()) = user_id)
  WITH CHECK (
    (SELECT auth.uid()) = user_id
    AND (NOT granted OR (terms_version = '1.2' AND (SELECT public.accepted_training_terms())))
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
    ) AND (SELECT public.accepted_training_terms())))
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
    ) AND (SELECT public.accepted_training_terms())))
  );

-- Training copies: the object path binds the signed-in owner, the analysis and
-- the current consent (1.2) given by accepting documents v1.4 or v1.5.
DROP POLICY "Users upload explicitly authorized training photos" ON storage.objects;
DROP POLICY "Users read own authorized training photos" ON storage.objects;
DROP POLICY "Users replace own authorized training photos" ON storage.objects;
CREATE POLICY "Users upload explicitly authorized training photos" ON storage.objects
  FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'training-photos'
    AND (SELECT public.accepted_training_terms())
    AND EXISTS (
      SELECT 1
      FROM public.training_photo_authorizations p
      JOIN public.training_consents c ON c.user_id = p.user_id
      JOIN public.analyses a ON a.id = p.analysis_id AND a.user_id = p.user_id
      WHERE p.user_id = (SELECT auth.uid())
        AND p.authorized AND p.terms_version = '1.2'
        AND c.granted AND c.terms_version = '1.2'
        AND name = p.user_id::text || '/' || p.analysis_id::text || '.jpg'
    )
  );
CREATE POLICY "Users read own authorized training photos" ON storage.objects
  FOR SELECT TO authenticated USING (
    bucket_id = 'training-photos'
    AND (SELECT public.accepted_training_terms())
    AND EXISTS (
      SELECT 1
      FROM public.training_photo_authorizations p
      JOIN public.training_consents c ON c.user_id = p.user_id
      WHERE p.user_id = (SELECT auth.uid())
        AND p.authorized AND p.terms_version = '1.2'
        AND c.granted AND c.terms_version = '1.2'
        AND name = p.user_id::text || '/' || p.analysis_id::text || '.jpg'
    )
  );
CREATE POLICY "Users replace own authorized training photos" ON storage.objects
  FOR UPDATE TO authenticated USING (
    bucket_id = 'training-photos'
    AND (SELECT public.accepted_training_terms())
    AND EXISTS (
      SELECT 1 FROM public.training_photo_authorizations p
      JOIN public.training_consents c ON c.user_id = p.user_id
      WHERE p.user_id = (SELECT auth.uid()) AND p.authorized
        AND p.terms_version = '1.2' AND c.granted AND c.terms_version = '1.2'
        AND name = p.user_id::text || '/' || p.analysis_id::text || '.jpg'
    )
  ) WITH CHECK (
    bucket_id = 'training-photos'
    AND (SELECT public.accepted_training_terms())
    AND EXISTS (
      SELECT 1 FROM public.training_photo_authorizations p
      JOIN public.training_consents c ON c.user_id = p.user_id
      WHERE p.user_id = (SELECT auth.uid()) AND p.authorized
        AND p.terms_version = '1.2' AND c.granted AND c.terms_version = '1.2'
        AND name = p.user_id::text || '/' || p.analysis_id::text || '.jpg'
    )
  );

-- Retention of one training copy: NULL (kept indefinitely) when the analysis
-- was made at/after the owner's first v1.5 acceptance; otherwise the v1.4
-- promise, 90 days after upload. A copy without a known analysis expires too.
CREATE FUNCTION public.training_copy_expires_at(owner uuid, analyzed_at timestamptz, uploaded_at timestamptz)
RETURNS timestamptz
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT CASE
    WHEN analyzed_at >= (
      SELECT min(l.accepted_at) FROM public.legal_document_acceptances l
      WHERE l.user_id = owner AND l.terms_version = '1.5' AND l.privacy_version = '1.5'
    ) THEN NULL
    ELSE uploaded_at + interval '90 days'
  END
$$;
REVOKE ALL ON FUNCTION public.training_copy_expires_at(uuid, timestamptz, timestamptz) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.training_copy_expires_at(uuid, timestamptz, timestamptz) TO service_role;

-- Same columns and states as before. Eligible analyses come after the first
-- v1.4 or v1.5 acceptance; expired v1.4 copies are no longer listed.
CREATE OR REPLACE FUNCTION public.training_pool()
RETURNS TABLE(
  analysis_id uuid, user_id uuid, object_path text, uploaded_at timestamptz, expires_at timestamptz,
  predicted_class_id text, confidence double precision, model_sha256 text, analyzed_at timestamptz,
  review_status text, label_class_id text, state text
)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT a.id, a.user_id, o.name, o.created_at, x.expires_at,
  a.class_id, a.confidence, a.model_sha256, a.created_at,
  coalesce(r.status,'pending'), r.class_id,
  CASE
   WHEN a.deleted_at IS NOT NULL OR a.inconclusive THEN 'ineligible'
   WHEN NOT coalesce(p.authorized,false) OR p.terms_version<>'1.2' THEN 'ineligible'
   WHEN NOT coalesce(c.granted,false) OR c.terms_version<>'1.2' THEN 'ineligible'
   WHEN acc.accepted_at IS NULL OR a.created_at<acc.accepted_at THEN 'ineligible'
   WHEN coalesce(ac.deletion_pending,false) THEN 'ineligible'
   WHEN r.status IS NULL THEN 'awaiting_review'
   WHEN r.status IN ('confirmed','corrected') AND r.class_id IS NOT NULL THEN 'ready'
   ELSE 'excluded'
  END
 FROM storage.objects o
 JOIN public.analyses a ON o.name=a.user_id::text||'/'||a.id::text||'.jpg'
 CROSS JOIN LATERAL (SELECT public.training_copy_expires_at(a.user_id, a.created_at, o.created_at) AS expires_at) x
 LEFT JOIN public.training_photo_authorizations p ON p.user_id=a.user_id AND p.analysis_id=a.id
 LEFT JOIN public.training_consents c ON c.user_id=a.user_id
 LEFT JOIN LATERAL (
  SELECT min(l.accepted_at) AS accepted_at FROM public.legal_document_acceptances l
  WHERE l.user_id=a.user_id AND (l.terms_version, l.privacy_version) IN (('1.4','1.4'),('1.5','1.5'))
 ) acc ON true
 LEFT JOIN public.account_controls ac ON ac.user_id=a.user_id
 LEFT JOIN public.photo_reviews r ON r.analysis_id=a.id
 WHERE o.bucket_id='training-photos' AND (x.expires_at IS NULL OR x.expires_at>now())
$$;
REVOKE ALL ON FUNCTION public.training_pool() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.training_pool() TO service_role;

-- Object names the daily cleanup must remove from training-photos: expired
-- copies, copies without an active conclusive analysis, and copies whose
-- consent or photo authorization was revoked (or is missing) or whose account
-- is being deleted. Uploads require an authorized authorization row and a
-- granted consent first, so a copy being sent is never listed.
CREATE FUNCTION public.training_photos_to_delete()
RETURNS TABLE(object_name text)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT o.name
 FROM storage.objects o
 LEFT JOIN public.analyses a ON o.name=a.user_id::text||'/'||a.id::text||'.jpg'
 LEFT JOIN public.training_consents c ON c.user_id=a.user_id
 LEFT JOIN public.training_photo_authorizations p ON p.user_id=a.user_id AND p.analysis_id=a.id
 LEFT JOIN public.account_controls ac ON ac.user_id=a.user_id
 WHERE o.bucket_id='training-photos'
   AND (a.id IS NULL
     OR a.deleted_at IS NOT NULL
     OR a.inconclusive
     OR NOT coalesce(c.granted,false)
     OR NOT coalesce(p.authorized,false)
     OR coalesce(ac.deletion_pending,false)
     OR public.training_copy_expires_at(a.user_id, a.created_at, o.created_at)<=now())
 ORDER BY o.created_at, o.name
$$;
REVOKE ALL ON FUNCTION public.training_photos_to_delete() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.training_photos_to_delete() TO service_role;

COMMIT;

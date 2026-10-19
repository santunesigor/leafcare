-- Explicit opt-in for the separate private training dataset.
CREATE TABLE public.training_consents (
  user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  terms_version TEXT NOT NULL,
  granted BOOLEAN NOT NULL DEFAULT FALSE,
  decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.training_consents ENABLE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE ON public.training_consents TO authenticated, service_role;
CREATE POLICY "Users manage own training consent" ON public.training_consents
  FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id AND NOT granted);

CREATE TABLE public.training_photo_authorizations (
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  analysis_id UUID NOT NULL REFERENCES public.analyses(id) ON DELETE CASCADE,
  terms_version TEXT NOT NULL,
  authorized BOOLEAN NOT NULL DEFAULT FALSE,
  decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, analysis_id),
  FOREIGN KEY (user_id) REFERENCES public.training_consents(user_id) ON DELETE CASCADE
);
ALTER TABLE public.training_photo_authorizations ENABLE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE ON public.training_photo_authorizations TO authenticated, service_role;
CREATE POLICY "Users manage own photo training choices" ON public.training_photo_authorizations
  FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (
    auth.uid() = user_id AND EXISTS (
      SELECT 1 FROM public.analyses a WHERE a.id = analysis_id AND a.user_id = auth.uid()
    ) AND NOT authorized
  );

INSERT INTO storage.buckets (id, name, public)
VALUES ('training-photos', 'training-photos', false)
ON CONFLICT (id) DO UPDATE SET public = false;

-- No authenticated INSERT policy is created yet. Training uploads stay denied
-- until the retention period and official Terms/Privacy URLs are published.
-- Only the uploader can remove their objects, including after revocation.
CREATE POLICY "Users delete own training photos" ON storage.objects
  FOR DELETE TO authenticated USING (
    bucket_id = 'training-photos' AND auth.uid()::text = (storage.foldername(name))[1]
  );
-- Deliberately no authenticated SELECT policy: only trusted service_role pipeline access.

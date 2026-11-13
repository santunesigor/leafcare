-- Private bucket for the TLA base dataset (696 images, one folder per disease,
-- same layout as machine-learning/data/raw). Together with training-photos it
-- keeps the whole training set in Supabase, so any trusted machine can rebuild
-- data/raw with `retrain.py sync`.
--
-- No storage policy is created: app users (anon/authenticated) can neither read
-- nor write it; only the retraining pipeline with the service key can. It is a
-- separate bucket because the daily cleanup removes training-photos objects that
-- have no analysis.

INSERT INTO storage.buckets (id, name, public)
VALUES ('tla-dataset', 'tla-dataset', false)
ON CONFLICT (id) DO UPDATE SET public = false;

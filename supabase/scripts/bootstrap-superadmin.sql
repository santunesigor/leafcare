-- Creates the protected owner. Run only after the hierarchy migration, as the database owner, against the intended project.
-- psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v superadmin_email='account@example.com' -f supabase/scripts/bootstrap-superadmin.sql
BEGIN;
SELECT set_config('leafcare.bootstrap_email', :'superadmin_email', true);
DO $$
DECLARE target uuid;
BEGIN
 PERFORM pg_advisory_xact_lock(761032);
 SELECT id INTO STRICT target FROM auth.users
 WHERE lower(email)=lower(current_setting('leafcare.bootstrap_email')) AND email_confirmed_at IS NOT NULL;
 IF EXISTS(SELECT 1 FROM public.account_controls WHERE role='owner' AND user_id<>target) THEN
  RAISE EXCEPTION 'Já existe proprietário; conceda acesso pelo aplicativo';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM public.account_controls WHERE user_id=target AND role='owner') THEN
  UPDATE public.account_controls SET role='owner' WHERE user_id=target AND NOT deletion_pending;
  IF NOT FOUND THEN RAISE EXCEPTION 'Conta indisponível'; END IF;
  INSERT INTO public.admin_audit(actor_id,target_id,action) VALUES(target,target,'bootstrap');
 END IF;
EXCEPTION WHEN no_data_found THEN RAISE EXCEPTION 'Conta cadastrada e confirmada não encontrada';
END $$;
COMMIT;

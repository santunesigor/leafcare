-- Observação é opcional para todas as decisões de revisão. Preserva ACL e demais validações.
BEGIN;
CREATE OR REPLACE FUNCTION public.admin_command(actor_id uuid, operation text, payload jsonb DEFAULT '{}') RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE target uuid; answer jsonb; current_review public.photo_reviews; original public.analyses;
 wanted text; selected_class text; page_no integer:=greatest(0,coalesce((payload->>'page')::integer,0));
 classes text[]:=ARRAY['anthracnose','black_shank','brown_spot','cmv','frog_eye','genetic_abnormality','healthy','nematodes','potato_tuber_moth','pvy','sunscald','target_spot','tmv','tswv','weather_fleck','wildfire'];
BEGIN
 -- Serialize privileged mutations to protect last-admin checks and review versions.
 IF operation IN ('role','pause','review','delete_begin') THEN PERFORM pg_advisory_xact_lock(761032); END IF;
 IF NOT EXISTS(SELECT 1 FROM public.account_controls WHERE user_id=actor_id AND role='superadmin' AND NOT deletion_pending) THEN
  RAISE EXCEPTION 'Acesso administrativo negado' USING ERRCODE='42501'; END IF;
 IF operation='authorize' THEN RETURN '{}'; END IF;
 IF payload ? 'id' THEN target:=(payload->>'id')::uuid; END IF;
 IF operation='dashboard' THEN
  RETURN jsonb_build_object('users',(SELECT count(*) FROM public.account_controls),
   'paused',(SELECT count(*) FROM public.account_controls WHERE NOT uploads_enabled),
   'photos',(SELECT count(*) FROM storage.objects o JOIN public.analyses a ON o.name=a.user_id::text||'/'||a.id::text||'.jpg' WHERE o.bucket_id='analysis-photos' AND a.deleted_at IS NULL),
   'reviewed',(SELECT count(*) FROM public.photo_reviews r JOIN public.analyses a ON a.id=r.analysis_id JOIN storage.objects o ON o.bucket_id='analysis-photos' AND o.name=a.user_id::text||'/'||a.id::text||'.jpg' WHERE a.deleted_at IS NULL),
   'classes',(SELECT coalesce(jsonb_object_agg(class_id,n),'{}') FROM (SELECT a.class_id,count(*) n FROM public.analyses a JOIN storage.objects o ON o.bucket_id='analysis-photos' AND o.name=a.user_id::text||'/'||a.id::text||'.jpg' WHERE a.deleted_at IS NULL GROUP BY a.class_id) t),
   'models',(SELECT coalesce(jsonb_object_agg(model_sha256,n),'{}') FROM (SELECT a.model_sha256,count(*) n FROM public.analyses a JOIN storage.objects o ON o.bucket_id='analysis-photos' AND o.name=a.user_id::text||'/'||a.id::text||'.jpg' WHERE a.deleted_at IS NULL GROUP BY a.model_sha256) t));
 ELSIF operation='users' THEN
  SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]') INTO answer FROM (
   SELECT u.id,u.email,u.created_at,u.email_confirmed_at,u.last_sign_in_at,p.display_name,c.role,c.uploads_enabled,c.deletion_pending,
    (SELECT count(*) FROM public.analyses a WHERE a.user_id=u.id AND a.deleted_at IS NULL) analyses,
    (SELECT count(*) FROM storage.objects o WHERE o.bucket_id='analysis-photos' AND split_part(o.name,'/',1)=u.id::text) photos
   FROM auth.users u JOIN public.account_controls c ON c.user_id=u.id LEFT JOIN public.profiles p ON p.id=u.id
   WHERE coalesce(u.email,'')||' '||coalesce(p.display_name,'') ILIKE '%'||coalesce(payload->>'search','')||'%'
   ORDER BY u.created_at DESC,u.id LIMIT 20 OFFSET page_no*20) t;
 ELSIF operation IN ('photos','photo') THEN
  SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]') INTO answer FROM (
   SELECT a.*,u.email,r.status AS review_status,r.class_id AS reviewed_class,r.note,r.reviewer_id,r.reviewed_at,coalesce(r.version,0) AS review_version,
    a.user_id::text||'/'||a.id::text||'.jpg' AS object_path
   FROM public.analyses a JOIN auth.users u ON u.id=a.user_id
   JOIN storage.objects o ON o.bucket_id='analysis-photos' AND o.name=a.user_id::text||'/'||a.id::text||'.jpg'
   LEFT JOIN public.photo_reviews r ON r.analysis_id=a.id
   WHERE a.deleted_at IS NULL AND (target IS NULL OR a.id=target)
    AND (coalesce(payload->>'class_id','')='' OR a.class_id=payload->>'class_id')
    AND (coalesce(payload->>'user_id','')='' OR a.user_id::text=payload->>'user_id')
    AND (coalesce(payload->>'model','')='' OR a.model_sha256=payload->>'model')
    AND (coalesce(payload->>'from','')='' OR a.created_at>=(payload->>'from')::timestamptz)
    AND (coalesce(payload->>'until','')='' OR a.created_at<(payload->>'until')::timestamptz+interval '1 day')
    AND a.confidence>=coalesce(nullif(payload->>'min_confidence','')::float8,0)
    AND (coalesce(payload->>'status','')='' OR coalesce(r.status,'pending')=payload->>'status')
   ORDER BY (r.analysis_id IS NOT NULL),a.created_at DESC,a.id LIMIT 20 OFFSET page_no*20) t;
  IF operation='photo' THEN RETURN coalesce(answer->0,'{}'); END IF;
 ELSIF operation='audit' THEN
  SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]') INTO answer FROM (
   SELECT * FROM public.admin_audit WHERE target IS NULL OR target_id=target ORDER BY id DESC LIMIT 20 OFFSET page_no*20) t;
 ELSIF operation='review' THEN
  SELECT * INTO original FROM public.analyses WHERE id=target AND deleted_at IS NULL;
  IF NOT FOUND OR NOT EXISTS(SELECT 1 FROM storage.objects WHERE bucket_id='analysis-photos' AND name=original.user_id::text||'/'||target::text||'.jpg') THEN RAISE EXCEPTION 'Foto indisponível'; END IF;
  SELECT * INTO current_review FROM public.photo_reviews WHERE analysis_id=target FOR UPDATE;
  IF coalesce(current_review.version,0)<>coalesce((payload->>'version')::integer,-1) THEN RAISE EXCEPTION 'Revisão mudou; recarregue antes de salvar' USING ERRCODE='40001'; END IF;
  wanted:=payload->>'status'; selected_class:=payload->>'class_id';
  IF wanted='confirmed' THEN selected_class:=original.class_id; END IF;
  IF wanted IN ('confirmed','corrected') AND NOT coalesce(selected_class=ANY(classes),false) THEN RAISE EXCEPTION 'Classe inválida'; END IF;
  IF wanted IN ('uncertain','rejected') THEN
   selected_class:=NULL;
  END IF;
  INSERT INTO public.photo_reviews(analysis_id,status,class_id,note,reviewer_id,version)
   VALUES(target,wanted,selected_class,coalesce(payload->>'note',''),actor_id,coalesce(current_review.version,0)+1)
   ON CONFLICT(analysis_id) DO UPDATE SET status=excluded.status,class_id=excluded.class_id,note=excluded.note,reviewer_id=excluded.reviewer_id,reviewed_at=now(),version=excluded.version;
  INSERT INTO public.admin_audit(actor_id,target_id,action,details) VALUES(actor_id,target,'review',jsonb_build_object('before',to_jsonb(current_review),'after',(SELECT to_jsonb(r) FROM public.photo_reviews r WHERE r.analysis_id=target)));
  RETURN '{}';
 ELSIF operation IN ('role','pause','delete_begin') THEN
  PERFORM 1 FROM public.account_controls WHERE user_id=target FOR UPDATE;
  IF NOT FOUND THEN
   IF operation='delete_begin' AND EXISTS(SELECT 1 FROM public.admin_audit WHERE target_id=target AND action='delete_begin') THEN RETURN '{"gone":true}'; END IF;
   RAISE EXCEPTION 'Conta indisponível'; END IF;
  IF operation<>'delete_begin' AND EXISTS(SELECT 1 FROM public.account_controls WHERE user_id=target AND deletion_pending) THEN RAISE EXCEPTION 'Exclusão em andamento'; END IF;
  IF operation IN ('role','delete_begin') AND
   ((operation='delete_begin' AND actor_id=target) OR
    (EXISTS(SELECT 1 FROM public.account_controls WHERE user_id=target AND role='superadmin')
      AND (operation='delete_begin' OR payload->>'role'='user')
      AND (SELECT count(*) FROM public.account_controls WHERE role='superadmin' AND NOT deletion_pending)<=1)) THEN
   RAISE EXCEPTION 'Não é permitido excluir a própria conta ou remover o último superadmin'; END IF;
  IF operation='role' THEN
   UPDATE public.account_controls SET role=payload->>'role' WHERE user_id=target AND NOT deletion_pending;
  ELSIF operation='pause' THEN
   UPDATE public.account_controls SET uploads_enabled=(payload->>'enabled')::boolean,
    upload_cutoff_at=CASE WHEN (payload->>'enabled')::boolean AND NOT uploads_enabled THEN clock_timestamp() ELSE upload_cutoff_at END
    WHERE user_id=target AND NOT deletion_pending;
  ELSE
   IF NOT EXISTS(SELECT 1 FROM auth.users WHERE id=target AND lower(email)=lower(payload->>'confirmation')) THEN RAISE EXCEPTION 'Confirme o e-mail da conta'; END IF;
   UPDATE public.account_controls SET deletion_pending=true,uploads_enabled=false WHERE user_id=target;
  END IF;
  INSERT INTO public.admin_audit(actor_id,target_id,action,details) VALUES(actor_id,target,operation,payload-'confirmation');
  RETURN '{}';
 ELSIF operation='email_target' THEN
  SELECT jsonb_build_object('email',email,'confirmed',email_confirmed_at IS NOT NULL) INTO answer FROM auth.users WHERE id=target;
 ELSIF operation='record_event' THEN
  IF payload->>'action' NOT IN ('invite','reinvite','recovery','delete_complete','delete_failed') THEN RAISE EXCEPTION 'Evento inválido'; END IF;
  INSERT INTO public.admin_audit(actor_id,target_id,action) VALUES(actor_id,target,payload->>'action'); RETURN '{}';
 ELSE RAISE EXCEPTION 'Operação desconhecida'; END IF;
 RETURN coalesce(answer,'{}');
END $$;
REVOKE ALL ON FUNCTION public.admin_command(uuid,text,jsonb) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.admin_command(uuid,text,jsonb) TO service_role;

COMMIT;

# Design

## Context

Cada análise já salva inconclusive, confidence, threshold, class_id e UUID. O Storage usa `{user_id}/{analysis_id}.jpg`. O envio atual não filtra o resultado e o payload inicial usa um nome local como se fosse caminho remoto.

## Goals / Non-Goals

Enviar somente fotos de previsões aceitas e permitir consultar suas classes. Preservar histórico/offline, retries e fotos remotas antigas. Não coletar dataset, recalibrar modelo, migrar schema Supabase nem apagar objetos existentes.

## Decisions

Filtrar a fila de upload por inconclusive=false e confidence>=threshold, com valores válidos entre zero e um. Revalidar no runner antes de ler/enviar bytes. Introduzir estado PhotoSyncState.LOCAL_ONLY para fotos que não serão enviadas; enum continua na coluna TEXT existente, sem migração estrutural.

O payload inicial usa photo_path=null enquanto não há upload confirmado. Manter caminho determinístico atual quando foto já é remota. Restore de análise sem caminho remoto usa LOCAL_ONLY; caso uma foto apareça depois no servidor, promover apenas o estado da foto sem sobrescrever a análise local. Exclusão e download de fotos antigas permanecem pelo caminho atual.

Identificar classe com JOIN de storage.objects e public.analyses pelo user_id/UUID; oferecer SQL de leitura na documentação, cobrindo caminhos antigos. A classe é previsão do modelo, não rótulo validado por especialista.

## Risks / Trade-offs

Fotos inconclusivas só existem no aparelho onde foram feitas; reinstalação/limpeza pode perder a foto, embora a análise sincronizada seja restaurada. APKs antigos ainda podem enviar inconclusivas; a regra é do cliente atualizado. Teste real Supabase depende de sessão/aparelho e não é certificado pelos fakes.

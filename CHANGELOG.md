# Changelog — LeafCare MVP

Formato por fase do `docs/MVP_ROADMAP.md`. Sem versão/tag final ainda
(atual: `versionCode 2`, `versionName 0.3.0`).

## Fase 7 — QA e segurança (técnico)

- Isolamento de dados entre contas (wipe na troca + worker aborta em troca/logout)
- `WorkManagerInitializer` padrão removido (evita double-init)
- Lint `lintDebug` verde (estilo em `values-v27`)
- Auditoria: segredos, RLS, migrations 1→2→3, sync, offline, storage local, erros de UI
- Físicos seguem pendentes (ver `docs/FINAL_QA_CHECKLIST.md`)

## Fases 4–6 — Sincronização

- Conta Supabase Auth (cadastro, login, logout, recovery OTP in-app, sessão persistida)
- Room v3 como fonte da UI; sync engine + WorkManager (upsert idempotente, retry, tombstones)
- Fotos privadas (`analysis-photos/{user_id}/{analysis_id}.jpg`, `photo_path`, `REMOTE_ONLY`)
- Restore multi-device por UUID; isolamento entre contas
- Sanitização de erros (nunca vaza HTTP/tokens); config via `local.properties` com trava de build

## Visual V3

- Telas Auth alinhadas ao V3; launcher adaptativo com as folhas reais
- Transições em escala (linguagem do modal de ajuda); header com perfil; saudação com nome

## Base (Fases 0–2)

- Classificação local MobileNetV3Small (77,67% top-1); histórico Room; câmera/galeria
- Schema Supabase versionado com RLS e bucket privado

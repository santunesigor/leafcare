## 1. Supabase

- [x] 1.1 Migração `20261112180000_legal_documents_v1_5_permanent_training.sql`: aceite v1.5, `accepted_training_terms()`, `training_copy_expires_at()`, `training_pool()` sem corte de 90 dias e `training_photos_to_delete()`
- [x] 1.2 Edge Function `cleanup-training-photos` apagando o que `training_photos_to_delete()` lista
- [x] 1.3 Testes SQL (`legal-acceptance`, `training-consent`, `training-pool`) e Deno; `run-local.sh`, `run-pglite` e Deno passando
- [x] 1.4 Aplicar a migração e publicar a função em produção antes de distribuir o APK (`supabase db push` e `functions deploy`, 12/11/2026)

## 2. Android

- [x] 2.1 Documentos v1.5 (Termos §7, Política §7 e §10), card de aceite e Privacidade e fotos
- [x] 2.2 Selo "Treino" sem dias para cópias permanentes e texto do bloco de treinamento
- [x] 2.3 Remover código, recursos e dependências sem uso
- [x] 2.4 `testDebugUnitTest`, `verifyModelAssets`, `lintDebug` e APK debug (versionCode 27)
- [ ] 2.5 Testar no aparelho, com a migração aplicada: reaceite v1.5 de uma conta v1.4, cadastro novo, uso offline com aceite no aparelho, revogação e novo aceite (inclui a pendência 2.6 de `training-consent-by-terms`)

## 3. Pipeline

- [x] 3.1 `retrain.py` com `setup`, `status`, `sync`, `run` e `promote`
- [x] 3.2 Espelho `data/field/` pseudonimizado e sincronizado com `training_pool()`
- [x] 3.3 Conjunto TLA + campo com split estável por pessoa
- [x] 3.4 Professor DINOv2-B treinado no ciclo, aluno com a receita atual, TFLite com paridade, replay e recomendação
- [x] 3.5 Remover experimentos, benchmarks, Keras/ensemble, exportador de 30 dias e systemd
- [x] 3.6 Testes unitários, de fumaça e CI com `requirements.txt` leve
- [x] 3.7 Replay tolera só mudanças de aceitação de imagens a até 1e-4 do limiar (achado no primeiro ciclo real)

## 4. Documentação

- [x] 4.1 Plano único em `docs/RETREINAMENTO.md`
- [x] 4.2 Machine Learning, Supabase, Superadmin, contexto, arquitetura, README, CHANGELOG e `openspec/config.yaml`

## 5. Primeiro ciclo real

- [x] 5.1 Importar o TLA em `data/raw/` (696 imagens conferidas com o manifesto) e recriar `.cache/retrain-venv` com GPU
- [x] 5.2 Primeiro ciclo real `2026-11-retreino-1` (TLA + 1 foto de campo): concluído, não recomendado por falta de teste de campo e não promovido

## 1. Supabase

- [x] 1.1 Migração `20261113120000_tla_dataset_bucket.sql` e teste `tla-dataset.sql` (`run-local.sh` e PGlite)
- [x] 1.2 Aplicar em produção e preencher o bucket (`retrain.py tla-upload`, 696 imagens)

## 2. Pipeline

- [x] 2.1 `ensure_tla` em `sync`/`run`, com conferência pelo manifesto, e `tla-upload`
- [x] 2.2 Cópia local das fotos do app por doença, movendo em mudança de rótulo
- [x] 2.3 Testes unitários e de fumaça; `data/raw/` reconstruída do bucket e idêntica ao original

## 3. Documentação

- [x] 3.1 `docs/RETREINAMENTO.md` (onde as fotos ficam), Supabase, Machine Learning, README de ML e CHANGELOG

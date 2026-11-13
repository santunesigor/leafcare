## Why

O TLA ficava só em `data/raw/` de um computador, importado de ZIPs que quase se perderam, enquanto as fotos do app ficavam no Supabase. Retreinar em outra máquina exigia achar os ZIPs originais. A cópia local das fotos do app também não seguia a organização por doença do TLA.

## What Changes

- Bucket privado `tla-dataset` no Supabase com as 696 imagens do TLA, uma pasta por doença, igual a `data/raw/`. O app não lê nem grava nele.
- `retrain.py sync` e `run` baixam do bucket o TLA que faltar ou estiver alterado, conferindo cada imagem com o manifesto. `retrain.py tla-upload` preenche o bucket uma vez.
- A cópia local das fotos do app fica em `data/field/<doença>/` e acompanha mudanças de rótulo.
- As fotos do app continuam por conta em `training-photos`, porque revogação e exclusão dependem desse caminho.

## Capabilities

### Modified Capabilities
- `model-retraining`: origem do TLA e organização da cópia local.

## Impact

- Supabase: migração `20261113120000_tla_dataset_bucket.sql` e teste `tla-dataset.sql`.
- Machine learning: `training/sync.py`, `retrain.py` e testes.
- Sem mudança no app, no modelo ou nas regras de consentimento.

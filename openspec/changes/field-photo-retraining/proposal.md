## Why

Os Termos v1.4 tornaram o uso das fotos analisadas para treinamento condição de uso, mas não havia como transformar essas fotos em um modelo novo. O exportador ainda exigia o consentimento da versão 1.1 e, com a v1.4 (consentimento 1.2), não exportaria nenhuma foto. A revisão no admin também não distinguia as fotos que têm cópia de treino: em produção, nenhuma das 2 cópias tinha revisão, enquanto 12 fotos de histórico revisadas não podem treinar. Faltava ainda documentar como treinar de novo o modelo definido (MobileNetV4 Small destilado de DINOv2-B).

## What Changes

- Supabase: `training_pool()` (somente `service_role`) define em um só lugar quais cópias podem entrar em um conjunto, seguindo os Termos v1.4. O painel admin recebe o resumo de treino, a validade de cada cópia e o filtro `training=true`; a fila prioriza cópias de treino pendentes que vencem antes.
- Android (admin): bloco "Treinamento do modelo" no Resumo, selo "Treino · N dias" na fila e na galeria, e filtro "Só cópias de treino".
- Pipeline: o exportador passa a usar `training_pool()`, agrupa as fotos por pessoa com uma chave descartada a cada exportação e grava o manifesto v2. Novo pacote `machine-learning/retraining/` para montar o ciclo (TLA + campo, `field_test` por pessoa), treinar com a receita definida, converter para TFLite com paridade e promover com replay no pré-processamento do app.
- Documentação: `docs/RETREINAMENTO.md`, com o ciclo completo, as regras e os critérios de promoção.

## Capabilities

### New Capabilities
- `model-retraining`: ciclo de retreino com fotos de campo, do conjunto à promoção explícita.

### Modified Capabilities
- `photo-training-consent`: a elegibilidade passa a seguir a v1.4 (foto analisada após o aceite, consentimento 1.2, conta fora de exclusão) e define o rótulo de treino.
- `superadmin`: a revisão mostra e prioriza as cópias de treino.

## Impact

- Supabase: migração `20261112120000_training_pool.sql` (nova função e `admin_command` redefinida); teste `training-pool.sql`.
- Android: `AdminCards`, `AdminScreen`, `ReviewQueue`, `PhotoGallery`.
- Machine learning: `export_authorized_training.py`, `retraining/`, testes. Nenhuma mudança no modelo do app: promover um candidato exige decisão explícita.

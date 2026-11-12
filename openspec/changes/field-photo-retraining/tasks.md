## 1. Supabase

- [x] 1.1 Migração `20261112120000_training_pool.sql` (`training_pool()` e dados de treino em `admin_command`)
- [x] 1.2 Teste `training-pool.sql` em `run-local.sh`
- [x] 1.3 Rodar `supabase/tests/run-local.sh`
- [x] 1.4 Aplicar a migração em produção

## 2. Android (admin)

- [x] 2.1 Bloco "Treinamento do modelo" no Resumo com "Revisar cópias"
- [x] 2.2 Selo "Treino · N dias" na fila e na galeria
- [x] 2.3 Filtro "Só cópias de treino"
- [x] 2.4 Testes Robolectric

## 3. Pipeline

- [x] 3.1 Exportador sobre `training_pool()`, manifesto v2 e grupos por pessoa
- [x] 3.2 `retraining.dataset` (TLA + campo, `field_test`, linha de base)
- [x] 3.3 `retraining.train` com a receita definida
- [x] 3.4 `retraining.export_model` e `retraining.promote`
- [x] 3.5 Testes unitários e de fumaça (treino destilado, TFLite e replay)

## 4. Documentação

- [x] 4.1 `docs/RETREINAMENTO.md`
- [x] 4.2 Atualizar Machine Learning, Supabase, Superadmin, contexto e README

## 5. Primeiro ciclo real

- [ ] 5.1 Recuperar ou treinar de novo o professor DINOv2-B e guardá-lo como asset de release
- [ ] 5.2 Revisar as cópias de treino e rodar o primeiro ciclo

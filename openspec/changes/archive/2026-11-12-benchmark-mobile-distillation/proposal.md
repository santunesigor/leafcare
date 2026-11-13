# Proposal

## Why

O DINOv2 é grande para o Android. Comparar alunos leves e destilação permite avaliar transferência de conhecimento sem substituir o ensemble funcionando.

## What Changes

- Runner isolado para MobileNetV3Small, MobileNetV4 Small, FastViT-T8 e EfficientViT-M2/M3, com controles supervisionados e destilação.
- Artefatos novos, receitas fixadas antes da execução, métricas por classe, calibração, exportação experimental e relatório separado.
- Conferência SHA256 de assets, modelo atual, manifesto e benchmarks anteriores.

## Capabilities

### New Capabilities

Nenhuma alteração de contrato do produto; tooling experimental (`skip_specs: true`).

### Modified Capabilities

Nenhuma. O contrato `model-bundle` e a inferência offline permanecem preservados.

## Impact

Somente experimentos, seus artefatos e documentação própria. Sem mudanças no Android, Supabase, dataset ou receitas integradas. APK debug continua com o ensemble atual.

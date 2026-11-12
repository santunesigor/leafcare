# Proposal

## Why

O usuário escolheu o MobileNetV4 Small destilado para substituir o ensemble, reduzir o bundle e continuar o projeto com o aluno treinado. A release v1.1.2 preserva o ensemble anterior para rollback.

## What Changes

- Integrar o TFLite selecionado da época 15, sem novo treinamento e sem alterar dataset/classes/split.
- Reproduzir resize bicúbico antialias com lado menor 256 e recorte central 224 no Android e Python; normalização ImageNet e calibração permanecem no grafo.
- Atualizar contrato, metadados, fixtures, CLI, testes e documentação do modelo atual; conservar resultados históricos em seus diretórios.
- Gerar APK 1.2.0 (versionCode 7), publicar release de teste na branch e abrir PR para main.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `model-bundle`: rede única MobileNetV4 Small destilada, schema 2, preprocessing bicúbico e temperatura/limiar do aluno.

## Impact

Assets Android, validação/preprocessing local, bundle e comandos Python, documentação e versão. Sem alterações em Auth, Room, sync ou Supabase. APK continua offline após autenticação. Release anterior e benchmarks preservados.

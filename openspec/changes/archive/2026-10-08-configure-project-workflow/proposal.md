# Proposal

## Why

O repositório já possui contratos críticos de classificação offline e sincronização, mas não tinha um fluxo versionado de especificações. A organização do ML também precisava separar o modelo distribuído de relatórios, legado e evidências antigas antes do release.

## What Changes

- Inicializar OpenSpec 1.14.1 com o esquema `spec-driven`, perfil core e integração Codex.
- Configurar contexto, idioma e regras alinhados ao LeafCare.
- Registrar specs de referência do comportamento existente: análise offline, bundle e sincronização por conta.
- Documentar o uso e validar specs/changes na CI.
- Consolidar a organização já solicitada: relatórios em `docs/model-reports/ensemble/`, capturas referenciadas em `docs/archive/ui/`, scripts antigos em `machine-learning/legacy/` e remoção de sete evidências sem consumidores.

## Capabilities

### New Capabilities

Nenhuma capacidade nova do aplicativo. As três specs são uma linha de base do comportamento implementado, não requisitos novos desta configuração.

### Modified Capabilities

Nenhuma alteração de comportamento. Esta change usa `skip_specs: true` porque o escopo é tooling, organização de arquivos e documentação.

## Impact

OpenSpec, skills Codex, instruções/documentação do projeto e workflow de CI. O pipeline do ensemble só muda o destino de seus relatórios; o teste do avaliador antigo usa o novo import. O modelo, treinamento, dataset, assets Android e schema Supabase permanecem preservados. `benchmark_artifacts/` permanece integralmente nos mesmos caminhos.

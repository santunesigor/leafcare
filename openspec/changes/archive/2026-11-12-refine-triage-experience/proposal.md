# Proposal

## Why

A aba Triagem abre diretamente a fila e não permite acompanhar o trabalho. O gesto vertical troca a foto durante a inspeção e os botões precisam de hierarquia e textos mais claros.

## What Changes

- Criar painel de triagens com totais recebidos, pendências, revisadas, progresso e acesso às seleções por situação/classe.
- Trocar o feed vertical por avaliação estável de uma foto com navegação explícita, mantendo avanço após salvar.
- Aprimorar botões, estados de carregamento/erro/vazio e apresentação da previsão, preservando verde, fonte Inter e identidade do app.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `superadmin`: painel dedicado e navegação explícita na revisão de fotos.

## Impact

Somente interface/estado administrativo Android, testes Compose/ViewModel e documentação. Reutiliza operações existentes; sem mudanças no backend, schema, modelo, autenticação, sincronização ou classificação offline.

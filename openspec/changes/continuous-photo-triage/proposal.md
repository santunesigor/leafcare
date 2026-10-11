# Proposal

## Why

A triagem exige abrir fotos individualmente e os botões do diálogo podem ficar fora da área visível no aparelho. Uma fila contínua permite revisar várias fotos sem voltar à listagem.

## What Changes

- Exibir uma foto por vez, com navegação vertical e alternativa por botão.
- Manter ações visíveis, avançar após salvar e carregar novos lotes automaticamente.
- Oferecer avaliação detalhada e filtros em painéis com conteúdo rolável e rodapé fixo.
- Preservar filtros, versão da revisão e foto atual em falhas de salvamento.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `superadmin`: fila contínua de revisão, ações acessíveis e paginação automática na triagem.

## Impact

UI Compose e estado administrativo Android, testes locais e documentação do fluxo. Reutiliza a API existente; não altera backend, schema, autenticação, sincronização, inferência, modelo ou dataset. Administração continua exigindo conexão.

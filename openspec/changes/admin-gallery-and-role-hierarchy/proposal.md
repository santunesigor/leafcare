# Proposal

## Why

Consultas de avaliações e classes abrem a fila de triagem em vez de permitir comparar fotos. O resumo repete a triagem e qualquer superadmin pode remover outro, incluindo a conta responsável pelo projeto.

## What Changes

- Galeria privada de fotos em duas colunas, com filtros, paginação e detalhe de consulta; avaliação fica na fila própria.
- Resumo com pendências, resultados por situação e atalhos úteis; modelos aparecem abreviados, sem expansão de identificadores.
- Hierarquia de usuário, admin (revisão), superadmin (gestão de contas inferiores) e proprietário protegido (gestão de superadmins).
- **BREAKING**: operações administrativas passam a respeitar hierarquia no servidor; nenhum administrador pode excluir, rebaixar, pausar ou recuperar o acesso de um superior ou par. O proprietário não pode ser alterado pelo app.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `superadmin`: consulta em galeria, resumo operacional e autorização por hierarquia.

## Impact

Compose/ViewModel administrativos, Edge Function, migration incremental de permissões e totais do painel, bootstrap e testes SQL/Android/Edge. O pedido de diferenciar permissões autoriza preparar o schema necessário; publicação em produção é etapa separada. Inferência, modelo, dataset e histórico offline permanecem independentes. APK debug entregue após validação.

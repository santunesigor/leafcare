# Design

## Context

Veja `proposal.md` para motivação/escopo. A CLI 1.14.1 está disponível e a aplicação já possui documentação técnica e testes. Os contratos de produto serão registrados a partir do código existente, sem comportamento novo.

## Goals / Non-Goals

**Goals:** deixar configuração e workflows Codex disponíveis após clone, manter as regras do LeafCare no contexto, verificar specs na CI e tornar a organização do ML legível.

**Non-Goals:** alterar pesos/receita/dataset/schema, iniciar treino, limpar checkpoints locais, refatorar módulos Android ou instalar integrações de ferramentas não utilizadas.

## Decisions

- Usar o esquema padrão e seis skills core. Schemas próprios e configurações globais não são necessários para este fluxo.
- Manter três capacidades focadas como linha de base: análise offline, bundle do modelo e dados/sincronização por conta. Esta change declara `skip_specs: true` porque a configuração não muda seus requisitos.
- Fixar a CLI em 1.14.1 com Node.js 22 na CI, incluindo validação de tarefas arquivadas. Não usar uma versão flutuante de `npx`.
- Separar relatórios atuais em `docs/model-reports/ensemble/`, capturas usadas em `docs/archive/ui/` e scripts antigos em `legacy/`. Preservar os consumidores do bundle e todos os benchmarks; atualizar imports, destino de relatórios e links associados.

## Risks / Trade-offs

- Skills/config são instruções para agentes, não controles de acesso → manter as regras e validações existentes e respeitar a autorização do usuário.
- Specs de referência podem divergir no futuro → consultar código/testes e atualizar apenas os contratos afetados.
- Movimentação de arquivos pode deixar referências quebradas → verificar os links, testar imports/CLIs e as suítes Python.
- Exportação completa do ensemble não é executada nesta organização → testar o código existente, revisar o novo destino de relatórios e registrar esse limite; manter checksums do modelo.

## Migration Plan

As skills e a configuração são versionadas na branch com a organização. Após clone, instalar a CLI e reabrir a sessão Codex se necessário. Reversão pode restaurar os caminhos e imports anteriores pelos commits; não há migração de dados nem operação no backend.

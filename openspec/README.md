# OpenSpec no LeafCare

Configuração local ao repositório, com **OpenSpec 1.14.1**, esquema **spec-driven** e perfil **core** para Codex. Os textos são em português; headings e keywords estruturais permanecem em inglês para compatibilidade com a CLI. O contexto e as regras do projeto ficam em [config.yaml](config.yaml).

```text
openspec/
├── config.yaml       # contexto e regras por artefato/operação
├── specs/            # contratos de referência do comportamento existente
│   ├── offline-analysis/
│   ├── model-bundle/
│   └── account-data-sync/
└── changes/
    └── archive/      # histórico de trabalho concluído

.agents/skills/       # seis workflows gerados para Codex
```

## Usar no Codex

As skills versionadas são `openspec-propose`, `openspec-explore`, `openspec-apply-change`, `openspec-update-change`, `openspec-sync-specs` e `openspec-archive-change`. Use `$openspec-propose` para preparar uma mudança e `$openspec-apply-change` quando quiser implementá-la. A proposta é uma etapa de planejamento; as skills geradas descrevem o fluxo de cada operação.

Após clonar, instale a CLI conforme [Desenvolvimento](../docs/DEVELOPMENT.md). Abra o projeto no Codex; reabra a sessão se as novas skills ainda não aparecerem. Não é necessário reinicializar o projeto nem configurar todas as ferramentas de IA.

Novas funcionalidades e alterações de contrato usam uma change. Correções rotineiras e ajustes de documentação podem ser feitos diretamente. Se uma change registrada só altera tooling/refatoração/documentação, declare `skip_specs: true` em sua `.openspec.yaml`, sem inventar mudanças no comportamento do app.

## Usar pela CLI

Execute na raiz do repositório:

```bash
openspec list --specs
openspec new change nome-da-mudanca
openspec status --change nome-da-mudanca
openspec instructions proposal --change nome-da-mudanca
```

Depois de preencher a proposta, consulte `openspec instructions specs`, `design` e `tasks`, sempre com `--change nome-da-mudanca`. Os templates e o contexto são retornados pela CLI. Specs de change usam seções delta (`ADDED`, `MODIFIED`, `REMOVED`); specs principais usam `## Requirements`.

```bash
openspec validate --all --strict --no-interactive
openspec validate --archived --strict --no-interactive
openspec archive nome-da-mudanca
```

Arquive após concluir e validar as tarefas; o comando sincroniza deltas nas specs principais. Para tooling sem deltas, use `openspec archive nome-da-mudanca --skip-specs`. A CI verifica specs/changes e tarefas de arquivos históricos com a versão fixada da CLI.

## Referências e limites

- [Análise offline](specs/offline-analysis/spec.md), [bundle do modelo](specs/model-bundle/spec.md) e [sincronização por conta](specs/account-data-sync/spec.md) descrevem o comportamento já implementado, com links para código/testes. Não são evidência de teste manual ou medição de desempenho em aparelho.
- [AGENTS.md](../AGENTS.md) e os limites do [contexto do projeto](../docs/AI_CONTEXT.md) continuam aplicáveis. Specs não concedem autorização para mudar modelo, dataset, schema, publicar ou fazer merge.
- A configuração não muda settings globais do OpenSpec nem requer stores, schemas próprios ou extensões adicionais.
- Para atualizar as skills, atualize conscientemente a versão da CLI e execute `openspec update --help` antes de usar `openspec update`; revise os arquivos gerados e a versão fixada na CI juntos.

Documentação oficial: [CLI](https://github.com/Fission-AI/OpenSpec/blob/main/docs/cli.md) e [configuração por projeto](https://github.com/Fission-AI/OpenSpec/blob/main/docs/customization.md).

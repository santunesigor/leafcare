# Tasks

## 1. Organização do repositório

- [x] 1.1 Separar relatórios, legado e capturas; verificar os links e entradas `python -m legacy.train/evaluate --help`.
- [x] 1.2 Preservar assets e benchmarks; verificar SHA-256 dos 194 arquivos correspondentes.
- [x] 1.3 Validar imports e contratos após a organização: 37 testes principais e 29 experimentais aprovados; bundle válido.

## 2. Configuração OpenSpec

- [x] 2.1 Inicializar Codex/core e escrever contexto/regras; conferir as seis skills e a configuração local.
- [x] 2.2 Registrar três specs do comportamento existente e documentar comandos em README, AGENTS e Desenvolvimento.
- [x] 2.3 Integrar validação à CI e executar os comandos `openspec validate --all --strict --no-interactive` e `openspec validate --archived --strict --no-interactive`.
- [x] 2.4 Verificar injeção de contexto/regras, saúde do root e links dos novos documentos.

## 3. Integração

- [x] 3.1 Executar verificação dos assets/build debug e disponibilizar APK; distinguir tarefas em cache e QA em aparelho não executado.
- [x] 3.2 Revisar diff/staging e validar preservação final dos assets/benchmarks antes de criar commits.

## Workflow follow-up

- Arquivar esta change concluída sem deltas de produto e validar o arquivo histórico.
- Criar commits focados, publicar apenas `chore/openspec-repository-cleanup` e abrir PR para `main`, conforme autorização do usuário.

## 1. Supabase

- [x] 1.1 Criar a migração v1.4 (aceite + consentimento, políticas v1.4/v1.2, revogação com qualquer versão)
- [x] 1.2 Atualizar `legal-acceptance.sql` e `training-consent.sql` e incluir `training-consent.sql` em `run-local.sh`
- [x] 1.3 Rodar `supabase/tests/run-local.sh`
- [x] 1.4 Aplicar a migração no projeto de produção (`supabase db push`, 11/11/2026)

## 2. Android

- [x] 2.1 Versões 1.4 / 1.2 e textos dos documentos
- [x] 2.2 Gate sem "continuar offline sem aceitar"; offline só com consentimento local
- [x] 2.3 Remover o modal da câmera e autorizar cada análise automaticamente
- [x] 2.4 Privacidade e fotos: informação e revogação com confirmação
- [x] 2.5 Testes de unidade do gate
- [x] 2.6 Teste do fluxo completo no aparelho transferido para `cumulative-training-dataset` (tarefa 2.5), que cobre o mesmo aceite e revogação com os documentos v1.5

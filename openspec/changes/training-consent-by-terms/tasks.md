## 1. Supabase

- [x] 1.1 Criar a migração v1.4 (aceite + consentimento, políticas v1.4/v1.2, revogação com qualquer versão)
- [x] 1.2 Atualizar `legal-acceptance.sql` e `training-consent.sql` e incluir `training-consent.sql` em `run-local.sh`
- [x] 1.3 Rodar `supabase/tests/run-local.sh`
- [ ] 1.4 Aplicar a migração no projeto de produção

## 2. Android

- [x] 2.1 Versões 1.4 / 1.2 e textos dos documentos
- [x] 2.2 Gate sem "continuar offline sem aceitar"; offline só com consentimento local
- [x] 2.3 Remover o modal da câmera e autorizar cada análise automaticamente
- [x] 2.4 Privacidade e fotos: informação e revogação com confirmação
- [x] 2.5 Testes de unidade do gate
- [ ] 2.6 Testar o fluxo completo no aparelho com a migração aplicada

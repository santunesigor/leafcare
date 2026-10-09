# Administração do LeafCare

APK 1.3.0 (versionCode 9), change `android-superadmin`, branch `feat/superadmin`. Depende do PR #7 (`feat/conclusive-photo-sync`). O modelo continua o MobileNetV4 Small destilado da versão 1.2.0.

## Publicação e primeira conta

A implementação não ativa permissões nem modifica o Supabase de produção automaticamente. Publicar o backend exige autorização e conferência do projeto de destino. A primeira conta escolhida pelo proprietário é fornecida ao bootstrap por parâmetro; nenhum e-mail de administrador fica no APK.

1. Aplicar as migrations versionadas no projeto correto, incluindo `20261009090000_superadmin.sql`, pelo fluxo de migrations do Supabase. Conferir primeiro o histórico remoto; não reaplicar migrations antigas já instaladas.
2. Publicar `supabase/functions/leafcare-admin` com `supabase functions deploy leafcare-admin --project-ref <projeto> --no-verify-jwt`. A função valida o Bearer token com `auth.getUser` e consulta o papel atual antes de usar o cliente privilegiado. `verify_jwt=false` evita a verificação legada do gateway, sem liberar acesso anônimo.
3. Conferir `leafcare://auth/reset-password` na lista de redirects permitidos do Auth e o envio de e-mails de convite/recuperação. URL e `SUPABASE_SERVICE_ROLE_KEY` são variáveis do ambiente servidor Supabase; nunca configurá-las no Android.
4. Executar [bootstrap-superadmin.sql](../supabase/scripts/bootstrap-superadmin.sql) como proprietário do banco, com `superadmin_email` de uma conta cadastrada e confirmada. O script é transacional/idempotente e recusa criar outro administrador quando já existe um diferente. Para SQL Editor, substituir somente a primeira chamada `set_config` pelo e-mail escolhido; não salvar essa cópia no Git.
5. Instalar o APK, entrar com a conta escolhida e abrir **Perfil → Administração** online. Depois do bootstrap, conceder/revogar outras permissões pela tela de usuários.

A nova versão consulta `my_account_policy` antes de enviar dados. **Instalar sem publicar a migration deixa os envios em retry**, preservando o histórico local; restauração e classificação continuam independentes da administração. Aplicar o backend antes de distribuir o APK para uso normal.

## Operação

- **Visão geral:** usuários, envios pausados, fotos, revisadas/pendentes e distribuição da previsão original por classe/modelo.
- **Usuários:** busca por nome/e-mail, dados de cadastro/acesso, contagens, convite/reenvio, recuperação, papel, pausa/liberação e exclusão. Ações exigem confirmação; excluir exige digitar o e-mail. Autoexclusão e remoção do último superadmin são recusadas pelo servidor.
- **Triagem:** listas de 20, miniaturas e filtros por situação, classe original, modelo, UUID de usuário e intervalo de datas. Datas do filtro são dias UTC. Detalhe tem zoom, Top-3, confiança, usuário/modelo e revisão atual. Confirmar ou corrigir usa as 16 classes; duvidosa e rejeitada exigem motivo. Após salvar, abre a próxima pendente que atende aos demais filtros.
- **Atividades:** auditoria geral ou por conta/foto, incluindo alterações anteriores da revisão. Conflito entre revisores retorna erro e exige recarregar.

A avaliação humana fica em `photo_reviews`; a classe/confiança original em `analyses` não muda. Rejeitar não apaga fotos. Não há exportação, alteração do dataset, treinamento ou recalibração.

Pausa bloqueia novos envios e exclusões remotas, inclusive para APKs anteriores pelas políticas RLS. Login, inferência offline e leitura/restore continuam. Ao liberar, o servidor grava um corte: resultados anteriores/iguais ficam permanentemente locais; fotos pendentes antigas também não são enviadas. Novos resultados seguem a regra de foto conclusiva. Tombstones aguardam a liberação para excluir remotamente. O corte usa o `created_at` já existente e pressupõe relógio correto do aparelho; datas mais de cinco minutos no futuro são recusadas.

Exclusão marca a conta, bloqueia envios, remove objetos em lotes de 100 e só então remove Auth/perfil/análises/revisões. Falhas permitem **Retomar exclusão**; uma repetição após concluir é segura. Auditoria permanece. A exclusão remota não apaga cópias em aparelhos offline.

Administração requer conexão e papel confirmado no servidor. Estado e imagens ficam em memória, sem cache administrativo persistente, e são limpos ao trocar/sair da conta ou receber negação de acesso. Usuários comuns não escrevem controles, revisões ou auditoria diretamente, e a RPC administrativa só permite `service_role`.

## Validação

```bash
bash supabase/tests/run-local.sh
npx --yes deno check supabase/functions/leafcare-admin/index.ts
npx --yes deno test supabase/functions/leafcare-admin/handler_test.ts
cd android-app
./gradlew testDebugUnitTest verifyModelAssets lintDebug assembleDebug --max-workers=2
```

O teste SQL usa PostgreSQL 18 em Docker, sem porta exposta, com tabelas mínimas de Auth/Storage e migrations reais. Não substitui testes no serviço Supabase: e-mail real, gateway, armazenamento binário e Auth são simulados nos testes da Edge Function. Evidências e roteiro físico em [TESTING.md](TESTING.md).

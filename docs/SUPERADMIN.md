# Administração do LeafCare

APK 1.3.0 (versionCode 9), change `android-superadmin`, branch `feat/superadmin`. Depende do PR #7 (`feat/conclusive-photo-sync`). O modelo continua o MobileNetV4 Small destilado da versão 1.2.0.

## Estado da publicação

Backend publicado no projeto LeafCare em 2026-10-09, com autorização do proprietário: migration remota `20261009181010_superadmin`, função `leafcare-admin` ativa na versão 1 e primeira conta selecionada promovida pelo bootstrap. O nome do arquivo local foi alinhado à versão registrada pelo Supabase; o SQL é o mesmo validado anteriormente. Não reaplicar essa migration nesse projeto.

A verificação remota confirmou RLS nas três tabelas administrativas, RPC disponível apenas a `service_role`, política da conta superadmin e consultas do painel/listas. O endpoint real recusou sessão ausente/inválida com 401 e método incorreto com 405. Login e navegação autenticada no aparelho ainda precisam ser testados pelo proprietário; não foram enviados e-mails nem executadas revisões/exclusões de teste na produção.

As mudanças locais de 2026-10-13 acrescentam compartilhamento e observação opcional. Para ativá-las remotamente, é necessário aplicar **somente** `20261013120000_optional_review_note.sql` e publicar a nova `leafcare-admin`, após autorização. Essa migration substitui apenas a validação da função; não altera tabelas ou dados. Até a publicação, o servidor antigo recusa gerar links e salvar dúvida/rejeição sem motivo.

## Publicação e primeira conta

A implementação não ativa permissões nem modifica o Supabase de produção automaticamente; a publicação acima foi executada após autorização explícita. Publicar o backend exige autorização e conferência do projeto de destino. A primeira conta escolhida pelo proprietário é fornecida ao bootstrap por parâmetro; nenhum e-mail de administrador fica no APK.

1. Aplicar as migrations versionadas no projeto correto, incluindo `20261009181010_superadmin.sql`, pelo fluxo de migrations do Supabase. Conferir primeiro o histórico remoto; não reaplicar migrations antigas já instaladas.
2. Publicar `supabase/functions/leafcare-admin` com `supabase functions deploy leafcare-admin --project-ref <projeto> --no-verify-jwt`. A função valida o Bearer token com `auth.getUser` e consulta o papel atual antes de usar o cliente privilegiado. `verify_jwt=false` evita a verificação legada do gateway, sem liberar acesso anônimo.
3. Conferir `leafcare://auth/reset-password` na lista de redirects permitidos do Auth e o envio de e-mails de convite/recuperação. URL e `SUPABASE_SERVICE_ROLE_KEY` são variáveis do ambiente servidor Supabase; nunca configurá-las no Android.
4. Executar [bootstrap-superadmin.sql](../supabase/scripts/bootstrap-superadmin.sql) como proprietário do banco, com `superadmin_email` de uma conta cadastrada e confirmada. O script é transacional/idempotente e recusa criar outro administrador quando já existe um diferente. Para SQL Editor, substituir somente a primeira chamada `set_config` pelo e-mail escolhido; não salvar essa cópia no Git.
5. Instalar o APK, entrar com a conta escolhida e abrir **Administração ao lado do Perfil**, no cabeçalho do histórico online. Depois do bootstrap, conceder/revogar outras permissões pela tela de usuários.

A nova versão consulta `my_account_policy` antes de enviar dados. **Instalar sem publicar a migration deixa os envios em retry**, preservando o histórico local; restauração e classificação continuam independentes da administração. Aplicar o backend antes de distribuir o APK para uso normal.

## Operação

O acesso fica no botão circular com escudo ao lado do Perfil, no cabeçalho do histórico, somente para a conta autorizada. Consultas de permissão têm até três tentativas; falhas transitórias preservam a visibilidade já confirmada na sessão, mas negação explícita e troca de conta limpam o acesso. Cada operação continua conferindo o papel atual no servidor. O resumo e a aba Triagem abrem o painel de triagens: totais recebidos/revisados/pendentes, progresso e seleções por situação ou classe original. A avaliação mostra uma foto por vez, com navegação por botões e ações fixas no rodapé. As ações de conta são agrupadas no painel de detalhes, com confirmação na própria tela antes do envio.

- **Visão geral:** usuários, envios pausados, fotos, revisadas/pendentes e distribuição da previsão original por classe/modelo.
- **Usuários:** busca por nome/e-mail, dados de cadastro/acesso, contagens, convite/reenvio, recuperação, papel, pausa/liberação e exclusão. Em **Convidar usuário**, informe o e-mail e escolha **Compartilhar convite** para gerar um link de acesso e abrir o seletor nativo do Android (apps compatíveis instalados), ou **Enviar por e-mail**. O link é pessoal, abre a definição de senha com o LeafCare instalado e não é armazenado no app nem na auditoria. Ações exigem confirmação; excluir exige digitar o e-mail. Autoexclusão e remoção do último superadmin são recusadas pelo servidor.
- **Triagem:** use **Avaliar pendentes** no painel ou consulte as seleções por situação/classe. Na foto, **Anterior** e **Pular** navegam sem alterar a revisão; gestos verticais não trocam a foto. **Confirmar**, **Corrigir**, **Duvidosa** e **Rejeitar** aparecem em uma grade com botões equivalentes, no mesmo cartão da foto e da previsão. Confirmar salva a classe original e avança; as demais abrem a avaliação já selecionada. A observação é opcional em todos os casos; o ícone de anotação também permite acrescentá-la à confirmação. O painel tem campos roláveis e rodapé fixo com cancelar/salvar. **Detalhes** reúne Top-3, usuário, modelo, revisão atual e histórico; a lupa permite ampliar a foto. O botão de filtros oferece situação, classe original e intervalo de datas (dias UTC); **Mais filtros** revela modelo e UUID de usuário. A classe escolhida no painel permanece selecionada nos filtros. A fila carrega lotes de 20 automaticamente, respeita os filtros ao avançar e evita repetir fotos salvas na sessão. Conflito de versão exige **Recarregar revisão**. Atualizar a fila inicia outra sessão; fotos puladas continuam disponíveis. Voltar ao painel atualiza os totais online; falhas de conexão oferecem nova tentativa sem apresentar zeros como totais confirmados. Os atalhos de situação não exibem contagens, pois esse dado não é fornecido pelo backend atual.
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

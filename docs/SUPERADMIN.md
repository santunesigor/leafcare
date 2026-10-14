# Administração do LeafCare

Administração online no APK 1.3.0, branch `feat/superadmin`. Inferência e histórico local continuam funcionando offline após autenticação; o modelo permanece MobileNetV4 Small destilado. Fotos e dados administrativos não têm cache persistente no aparelho.

## Permissões

| Papel | Fotos e revisões | Gestão de contas | Gestão de permissões |
| --- | --- | --- | --- |
| Usuário | Histórico próprio | Não | Não |
| Admin | Consulta, triagem e atividades de revisão | Não | Não |
| Superadmin | Consulta e triagem | Usuários e admins | Concede/remove admin |
| Proprietário | Consulta e triagem | Usuários, admins e superadmins | Concede/remove admin e superadmin |

Existe somente um proprietário. Sua conta não pode ser rebaixada, excluída, pausada ou receber recuperação/convite por outro administrador. Superadmins também não podem alterar outros superadmins. Operações administrativas sobre a própria conta são recusadas. Recuperação pessoal continua disponível pelo fluxo normal de autenticação.

Essas regras são aplicadas no SQL e verificadas pela Edge Function antes de efeitos em Auth/Storage, inclusive para clientes antigos ou adulterados. A versão da política informa ao APK se o backend já suporta a hierarquia. Até a atualização do servidor, a gestão de contas fica indisponível no detalhe; a proteção remota completa depende da publicação abaixo.

## Operação

O acesso com escudo fica junto ao Perfil no cabeçalho do histórico, após confirmação online do papel. Falhas transitórias de rede preservam a visibilidade já confirmada na sessão; revogação e troca de conta limpam dados, imagens e cache em memória. Cada chamada verifica a permissão atual no servidor.

- **Resumo:** pendências, fotos recebidas/revisadas, resultados por situação e atalhos. Gestores veem totais de contas/envios pausados e acesso a usuários. As classes mais recebidas abrem suas fotos; modelos aparecem abreviados, com contagem e consulta de fotos, sem expansão do hash. Modelos de origem representam versões da previsão, não contas. Se o servidor antigo não fornecer contagens por situação, o app mostra indisponibilidade, sem inventar zeros.
- **Consultar fotos:** os atalhos de avaliações, classes e modelos abrem uma galeria de duas colunas. Filtros e páginas usam lotes de 20. Tocar uma foto abre consulta somente leitura com ampliação por dois dedos, Top-3, previsão original, avaliação humana, observação, data e identificação abreviada do revisor/modelo. Fechar conserva a página e os filtros. Falha de miniatura permite tentar novamente no cartão.
- **Triagem:** o painel reúne pendências, progresso e distribuição completa por previsão. **Avaliar pendentes** abre a fila de uma foto por vez. Confirmar salva a classe original e avança; corrigir, marcar duvidosa e rejeitar abrem avaliação com campos roláveis e rodapé fixo. Observação é opcional em todas as decisões. Anterior/Pular não salvam revisão. A fila carrega lotes automaticamente, respeita filtros e não repete fotos já salvas na sessão. Conflito de versão exige recarregar.
- **Usuários:** disponível para superadmin/proprietário, com busca, cadastro/acesso, totais, convite compartilhado ou por e-mail, pausa/liberação e exclusão retomável. O detalhe informa o papel e a proteção da conta; **Alterar papel** oferece somente níveis permitidos, com confirmação. Exclusão exige digitar o e-mail. O link compartilhado é pessoal e não fica em estado persistente nem auditoria.
- **Atividades:** gestores consultam todas as ações; admins consultam somente revisões. Alterações humanas anteriores ficam registradas. Identificadores são apresentados abreviados.

A revisão humana fica separada em `photo_reviews`; classe/confiança/modelo originais em `analyses` não mudam. Rejeitar não apaga a foto. Não há exportação, alteração do dataset, treinamento ou recalibração.

Pausar bloqueia novos envios e exclusões remotas pelas políticas RLS; login, uso local e restore continuam. Ao liberar, um corte mantém resultados antigos permanentemente locais; apenas novos resultados seguem a sincronização normal. Tombstones aguardam liberação. O corte depende do relógio do aparelho; datas mais de cinco minutos no futuro são recusadas.

Excluir marca a conta, bloqueia envios, remove objetos em lotes de 100 e depois remove Auth/perfil/análises/revisões. Falhas permitem retomar, e repetir após concluir é seguro. Auditoria permanece. Cópias em aparelhos offline não são removidas remotamente.

## Publicação e migração

A publicação registrada de 2026-10-09 instalou `20261009181010_superadmin` e a primeira versão de `leafcare-admin`. As mudanças de 2026-10-13 e desta revisão são locais; não houve publicação nem operação administrativa de teste na produção nesta execução.

Antes de usar a nova hierarquia em produção, conferir projeto e histórico e aplicar apenas migrations pendentes, em ordem:

1. `20261013120000_optional_review_note.sql`: observação opcional.
2. `20261014120000_admin_role_hierarchy.sql`: papéis, proteção de alvos e totais por situação.
3. Publicar a Edge Function atual: `supabase functions deploy leafcare-admin --project-ref <projeto> --no-verify-jwt`.
4. Instalar o novo APK. APKs antigos podem deixar de mostrar a administração para a conta promovida a proprietário; classificação e histórico próprios continuam independentes.

Entre a migration e a publicação da Edge, chamadas da função antiga são recusadas, pois ela não informa a operação e o alvo na autorização inicial. Publicar os dois componentes na mesma janela de atualização; essa recusa evita que convites contornem a proteção por e-mail.

A migration escolhe o proprietário pelo primeiro bootstrap registrado (actor igual a target), desde que ainda seja superadmin ativo. Sem bootstrap, aceita somente um superadmin existente. Vários superadmins sem identificação explícita, bootstrap indisponível ou único superadmin em exclusão interrompem a transação: o responsável do banco deve identificar/resolver a conta antes de tentar novamente. Não há e-mail ou UUID de proprietário fixo no APK ou SQL versionado.

Em instalação nova, [bootstrap-superadmin.sql](../supabase/scripts/bootstrap-superadmin.sql) cria o **proprietário** escolhido por parâmetro, com conta cadastrada e confirmada. É idempotente e recusa outro proprietário. O nome histórico do script e o parâmetro `superadmin_email` são preservados. Execute somente depois da migration de hierarquia, como responsável do banco, no projeto correto. Não executar novamente para trocar o proprietário.

Publicação remota exige autorização explícita. Credenciais de serviço ficam somente no servidor; RPCs administrativas são exclusivas de `service_role`, RLS permanece ativa e fotos ficam no bucket privado. Conferir redirects `leafcare://auth/confirm-email` e `leafcare://auth/reset-password` no Auth.

## Validação

```bash
bash supabase/tests/run-local.sh
# Alternativa sem Docker: Node/npm + PostgreSQL 18 em WASM, sem acesso remoto
bash supabase/tests/run-pglite.sh
npx --yes deno check supabase/functions/leafcare-admin/index.ts
npx --yes deno test supabase/functions/leafcare-admin/handler_test.ts
cd android-app
bash ./gradlew testDebugUnitTest verifyModelAssets lintDebug assembleDebug --max-workers=2
```

Os testes SQL aplicam migrations reais com tabelas mínimas de Auth/Storage; a alternativa PGlite verifica também upgrade do proprietário e falha atômica em caso ambíguo. Não substituem gateway, e-mail real, Storage binário ou QA em aparelho. Evidências desta revisão ficam em `openspec/changes/admin-gallery-and-role-hierarchy/validation.md`.

# Validação

Validações locais em 2026-10-14, branch `feat/superadmin`, sobre a base `5e82a50`, antes do commit de entrega.

## Backend

- `bash supabase/tests/run-pglite.sh`: passou no PostgreSQL 18.6 / PGlite 0.5.10, isolado, sem conexão de banco remota. Migrations reais, cenários anteriores de RLS/revisão/sync, nova hierarquia, bootstrap idempotente e upgrades por bootstrap/único superadmin; seleção ambígua falha atomicamente.
- Proprietário e pares protegidos contra papel, pausa, exclusão, recuperação, reenvio e geração de convite por e-mail. Admin sem gestão de contas; superadmin concede admin, proprietário concede superadmin. Revogação tem efeito no servidor; helper SQL não é exposto a authenticated.
- A migration recusa a autorização genérica da Edge antiga, evitando efeitos externos sem verificar operação/alvo durante a atualização.
- `npx --yes deno check supabase/functions/leafcare-admin/index.ts`: passou.
- `npx --yes deno test supabase/functions/leafcare-admin/handler_test.ts`: 14 passaram, nenhum falhou. Auth e Storage simulados; JWT verificado determina ator e ações protegidas não executam efeitos externos.
- Docker indisponível: `run-local.sh` não foi executado nesta sessão. O runner PGlite foi acrescentado para reproduzir os testes sem Docker.

## Android

- Compilação Kotlin passou. Testes visuais da administração passaram nas execuções iniciais, incluindo grade de duas colunas, consulta sem botões de avaliação, navegação por classe/situação, papel de admin sem usuários e proteção de conta superior.
- O teste novo de cache precisou executar as interações de ViewModel na thread principal; a execução isolada corrigida passou. Não houve supressão de assertivas.
- `testDebugUnitTest` completo: 279 passaram, nenhum falhou ou foi ignorado. Depois do ajuste final das miniaturas, `testDebugUnitTest --tests 'br.com.leafcare.admin.*'` repetiu os 54 testes administrativos com sucesso.
- `verifyModelAssets`: passou.
- `lintDebug`: passou, sem erros e com 24 warnings. O apontamento sobre a declaração do estado das miniaturas foi resolvido usando estado explícito e LaunchedEffect; nenhuma regra foi suprimida.
- `assembleDebug`: passou. Comando final: `bash ./gradlew testDebugUnitTest --tests 'br.com.leafcare.admin.*' verifyModelAssets lintDebug assembleDebug --max-workers=2`.
- Prévia inicial: `android-app/app/build/outputs/apk/administracao/leafcare-1.3.0-admin-galeria-debug.apk` (cópia do APK de distribuição, base 5e82a50 com as alterações locais desta change).
- SHA-256 do APK: `5bd40b24e11e7c9dc72345ee0ce47c86b6e9d6a24b8ea633ffe4b105c329a173`.
- Capturas em `android-app/app/build/reports/screenshots/`, incluindo `admin-galeria-duas-colunas.png`, `admin-galeria-detalhe.png` e `admin-resumo.png`. Usam fixtures/imagem de referência locais, sem dados de produção.

## OpenSpec e limites

- `openspec validate --all --strict --no-interactive`: 11 itens passaram, nenhum falhou.
- `openspec validate --archived --strict --no-interactive`: 2 itens passaram, nenhum falhou.
- Não houve alteração de modelo, treinamento, dataset ou fluxo de inferência/histórico offline.
- Sem instalação/QA em aparelho físico nesta sessão; o APK foi gerado e disponibilizado para teste do usuário.
- Durante essas validações não houve publicação no Supabase, convite/e-mail real, revisão/exclusão remota ou push. A hierarquia só fica ativa em produção após aplicar migrations pendentes e publicar a Edge atual; até lá, o APK informa indisponibilidade da gestão de contas e dos totais ausentes.

## Entrega versionada

O fluxo padrão de entrega usa `assembleDebug` após criar o commit, gerando `android-app/app/build/outputs/apk/distribution/debug/leafcare-1.3.0-<commit>.apk`. A prévia acima registra a validação anterior ao commit; o APK versionado é disponibilizado com o hash do commit enviado à branch `feat/superadmin`.

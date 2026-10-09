# Testes e QA

## Comandos

Python/ML, dentro de `machine-learning`:

```powershell
python -m pytest -q
python validate_bundle.py --require-model
```

Android, dentro de `android-app`:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat verifyModelAssets
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Com aparelho/emulador conectado, os testes instrumentados podem ser executados com `.\gradlew.bat connectedDebugAndroidTest`.

## Evidências históricas

O checklist físico anterior registrou uma rodada em dispositivo em 2026-09-29. Os itens abaixo são o estado registrado naquela rodada, não uma execução feita ao editar este documento.

- **Registrado como OK:** instalação limpa e ícone; cadastro e confirmação de e-mail; perfil; senha incorreta e recuperação de senha; análise online e offline; sincronização, retry e exclusão; reinstalação e restore de histórico/fotos; uso offline após restore; isolamento de contas no mesmo aparelho; conexão instável; importação JPEG/PNG; resultado inconclusivo; inspeção de RLS, bucket privado e trigger de perfil.
- **Somente visual:** login/logout e persistência de sessão.
- **Parcial:** câmera abriu e capturou; uma foto de baixa confiança mostrou “Inconclusivo”. O teste de negar permissão não tinha resultado registrado.
- **Não executado:** restore em segundo aparelho, por indisponibilidade de outro dispositivo.

Os quatro fluxos de Auth foram reportados pelo usuário como aprovados manualmente em dispositivo em 2026-09-29.

### Observações daquela rodada

O checklist também registrou: mensagem antiga de login incorreto não era limpa ao iniciar cadastro logo em seguida; após criar conta e sair, o app voltava à tela de criar conta; imagens dos cards do histórico às vezes demoravam; e havia um breve flash da tela de login ao restaurar sessão. A captura em baixa luz retornou cerca de 30% e “Inconclusivo”, sem aviso específico de pouca luz. Essas observações são históricas e precisam ser reproduzidas antes de tratá-las como defeitos atuais.

## Roteiro manual

Ao validar mudanças que afetem o app, cubra os fluxos relevantes:

- Auth: cadastro, confirmação, login/logout, recuperação, troca de senha e sessão persistida.
- Câmera e galeria: permissões, captura, JPEG/PNG, imagem grande e orientação EXIF.
- Inferência e histórico: Top-3, inconclusivo, persistência, busca, filtros e exclusão.
- Offline e sync: criar análise offline, reconectar, retry sem duplicação, foto, exclusão e restore.
- Contas: alternar usuários no aparelho e confirmar isolamento remoto com duas sessões.
- Segundo aparelho: quando disponível, validar restore e exclusões entre dispositivos.

Os relatórios XML antigos e o snapshot de ambiente sem consumidores foram removidos. Relatórios de novas execuções ficam em `android-app/app/build/test-results/` e nos artefatos da CI; não substituem o roteiro manual. As capturas históricas usadas na jornada do app ficam em `docs/archive/ui/`.

## Integração MobileNetV4 Small destilado — 2026-10-08

- Branch `feat/mobilenetv4-distilled`, APK 1.2.0/versionCode 7, schema 2.
- Replay da promoção: 104 imagens de validação, Top-1 idêntico e erro máximo de probabilidade 0,0000050664 contra logits do checkpoint selecionado. As 103 imagens de teste mantiveram Top-1 85,44%, Macro-F1 0,8083 e Top-3 97,09%. Não houve treino/recalibração ou nova seleção pelo teste.
- Python padrão: 39 testes passaram; testes explícitos da destilação/exportação: 9 passaram. `validate_bundle.py --require-model` e CLI TFLite executados com sucesso em TensorFlow 2.16.1.
- Android: 213 testes unitários passaram, zero falhas/skips, incluindo fixtures RGB sintéticas e replay pixel a pixel das 104 imagens decodificadas com `LEAFCARE_PARITY_DIR=/tmp/leafcare-distilled-parity`.
- Uma execução conjunta de testes/lint foi interrompida (exit 143); a validação final foi executada novamente com duas workers. Houve aviso de limpeza de diretório temporário do Robolectric em uma execução direcionada; ela terminou com sucesso.
- `verifyModelAssets`, `lintDebug` e `assembleDebug` concluídos com sucesso. O modelo dentro do APK foi extraído e conferido contra o SHA256 do aluno e metadata Python.
- OpenSpec: specs, changes ativas e arquivo validaram em modo estrito. Nenhum aparelho conectado via ADB; captura/galeria, atualização sobre v1.1.2 e latência completa no Galaxy A06 ainda precisam de teste no aparelho. Os testes JVM não executam inferência no telefone.

Relatórios atuais: [pasta do aluno](model-reports/mobilenetv4-distilled/). Relatórios do ensemble e benchmarks anteriores permanecem históricos.

Entrega: [pré-release v1.2.0](https://github.com/santunesigor/leafcare/releases/tag/v1.2.0), [APK leafcare-1.2.0-cca5514.apk](https://github.com/santunesigor/leafcare/releases/download/v1.2.0/leafcare-1.2.0-cca5514.apk) e [PR #6](https://github.com/santunesigor/leafcare/pull/6). APK/release correspondem ao commit de implementação `cca5514`; o commit posterior registra apenas a conclusão documental. SHA256 APK: `112a962b863fbcafc097cbeaf46c181d172e657b4438d98dba9817d3d00af37c`. A CI remota do PR é acompanhada no GitHub; os resultados acima são das execuções locais.

## Envio somente de fotos conclusivas — 2026-10-08

Branch `feat/conclusive-photo-sync`, APK 1.2.1/versionCode 8. Fotos novas ou retries antigos só enviam bytes quando inconclusive=false e confidence>=threshold da análise. Fotos abaixo do limiar permanecem no aparelho; a linha e classe continuam sincronizadas. Nenhum objeto antigo foi apagado e nenhuma mudança foi aplicada no schema/RLS Supabase.

48 testes direcionados de sincronização e a suíte completa de 220 testes Android passaram, zero falhas/skips. Casos: inconclusiva com retry antigo, confiança abaixo/igual ao limiar, proteção no runner, fila Room real, payload sem caminho remoto, associação classe/UUID e restore sem foto com associação posterior. `verifyModelAssets`, `lintDebug`, `assembleDebug` e OpenSpec estrito (ativo/specs e arquivo) passaram. SHA256 do modelo dentro do APK conferido, mantendo o mesmo MobileNetV4 destilado. Não houve teste com sessão Supabase real/aparelho nesta execução.

Consulta de classe por arquivo: [Supabase](SUPABASE.md#fotos-com-previsão-aceita-e-consulta-da-classe). Testar no aparelho: uma análise conclusiva envia foto; uma inconclusiva conserva histórico/foto local e não cria objeto; retry offline e restauração da análise sem foto não falham.

## Superadmin e triagem — 2026-10-09

Branch `feat/superadmin`, APK 1.3.0/versionCode 9, dependente do PR #7. Implementação e [publicação do backend](SUPERADMIN.md) são etapas separadas; produção não foi alterada nesta rodada.

- PostgreSQL 18 isolado: todas as migrations e cenários SQL passaram. Validados: acesso somente à própria política, proibição de elevar papel/gravar revisão diretamente, RPC inacessível ao cliente, negação a não-admin e papel revogado, revisão preservando classe original, versão conflitante, classe/motivo inválidos, último superadmin/autoexclusão, pausa bloqueando insert/update/delete em análises e Storage sem impedir leitura, corte na retomada, relógio futuro, rejeição mantendo foto, exclusão/retomada e auditoria preservada. Bootstrap parametrizado e repetição idempotente também passaram.
- Edge Function: `deno check` passou; 8 testes passaram. Cobrem JWT ausente/inválido, papel atual, ator não forjável, foto privada sem cache, conflito, convite/recuperação auditados, exclusão em lotes e falha parcial. Auth/Storage são simulados; não enviam e-mails nem apagam dados reais.
- Android: **227 testes passaram**, zero falhas/erros/skips, incluindo backlog permanentemente local, fotos antigas, política indisponível, pausa com leitura e tombstones, persistência Room após reabrir, convite para definir senha e entrada administrativa condicional no Perfil.
- `verifyModelAssets`, `lintDebug` e `assembleDebug` passaram. Lint: zero erros, 24 avisos em manifest, dependências e recursos existentes. O único TFLite dentro do APK foi extraído e seu SHA256 conferido contra o arquivo versionado, sem alteração do modelo.
- OpenSpec: 4 specs, 4 changes ativas e 1 arquivada passaram em modo estrito. Specs de administração e política de envio sincronizadas.
- Nenhum aparelho conectado via ADB. Teste físico e integração com Supabase publicado continuam pendentes; os testes locais não certificam entrega real de e-mail ou navegação no telefone.

Após publicar em ambiente autorizado: validar conta comum sem Administração; conta superadmin com as cinco telas; filtros/paginação/zoom; dois revisores concorrentes; convite e definição de senha; pausa com análise offline, retomada sem enviar acumulado e exclusões pendentes; exclusão retomável em conta descartável; revogação de papel, logout/troca de conta e perda de conexão. Conferir que rejeitar mantém a foto e que corrigir não altera a previsão exibida no histórico do proprietário.

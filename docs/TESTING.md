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

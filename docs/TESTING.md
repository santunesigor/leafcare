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

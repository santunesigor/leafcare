# LeafCare — Configuração local e roteiro completo de testes

Este documento explica como preparar um computador Windows do zero para validar o LeafCare localmente.

Objetivo:

- executar os testes de Machine Learning;
- validar o bundle do modelo;
- executar os testes Android;
- compilar o aplicativo;
- instalar o APK em um celular Android;
- executar os testes instrumentados;
- realizar a validação manual em dispositivo físico.

> O LeafCare não precisa de Docker para compilar, testar ou executar.
>
> Docker Desktop, WSL2 e virtualização não fazem parte dos requisitos deste projeto.

---

# 1. Requisitos

Instale:

- Git;
- Python 3.12;
- JDK 17;
- Android SDK;
- Android SDK Platform 35;
- Android SDK Platform-Tools;
- Android SDK Build-Tools;
- Android SDK Command-line Tools;
- driver USB do celular, se o Windows não reconhecer o aparelho corretamente.

Opcional, mas recomendado:

- Android Studio.

O Android Studio é a forma mais simples de instalar e gerenciar o Android SDK.

---

# 2. Clonar ou atualizar o repositório

Se ainda não possui o projeto:

```powershell
git clone https://github.com/santunesigor/leafcare.git
cd leafcare
```

Se estiver validando uma branch específica:

```powershell
git fetch origin
git switch refactor/pre-presentation-hardening
git pull
```

Confirme:

```powershell
git status
git branch --show-current
```

Antes de testar, o ideal é:

```text
working tree clean
```

---

# 3. Python 3.12

## 3.1 Verificar versões instaladas

No PowerShell:

```powershell
py -0p
```

O projeto deve ser executado com Python 3.12.

Confirme:

```powershell
py -3.12 --version
```

Esperado:

```text
Python 3.12.x
```

---

# 4. Criar ambiente virtual Python

Entre no diretório de Machine Learning:

```powershell
cd machine-learning
```

Crie a virtual environment:

```powershell
py -3.12 -m venv .venv
```

## Opção recomendada: usar o Python da venv diretamente

Não é obrigatório ativar a venv.

Use:

```powershell
.\.venv\Scripts\python.exe --version
```

Isso evita problemas com `ExecutionPolicy` do PowerShell.

---

# 5. Instalar dependências Python

Ainda dentro de:

```text
machine-learning/
```

execute:

```powershell
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

---

# 6. Testar a parte de Machine Learning

Execute:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

Resultado conhecido da versão validada:

```text
28 passed
```

Depois valide o bundle:

```powershell
.\.venv\Scripts\python.exe validate_bundle.py --require-model
```

Resultado esperado semelhante a:

```json
{
  "status": "model_bundle_valid",
  "classes": 16,
  "trained_model_present": true,
  "model_output_order_verified": true
}
```

Mensagens informativas do TensorFlow sobre `oneDNN` e `XNNPACK` não representam erro.

---

# 7. Java / JDK 17

Volte para a raiz:

```powershell
cd ..
```

Confirme:

```powershell
java -version
```

Esperado:

```text
openjdk version "17..."
```

Também verifique:

```powershell
Get-Command java
```

Se Java não for encontrado, instale JDK 17.

Uma opção adequada no Windows é Eclipse Temurin 17.

Com `winget`:

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

Depois feche e abra novamente o PowerShell.

---

# 8. Android SDK

O caminho padrão no Windows é:

```text
C:\Users\<usuario>\AppData\Local\Android\Sdk
```

No PowerShell:

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
Test-Path $sdk
```

Esperado:

```text
True
```

Se retornar `False`, instale o Android SDK.

---

# 9. Instalar Android SDK pelo Android Studio

Abra:

```text
Android Studio
→ More Actions
→ SDK Manager
```

Instale pelo menos:

## SDK Platforms

```text
Android 15 / API 35
Android SDK Platform 35
```

## SDK Tools

```text
Android SDK Build-Tools
Android SDK Platform-Tools
Android SDK Command-line Tools (latest)
```

Emulador não é obrigatório se o teste for feito em celular físico.

---

# 10. Configurar local.properties

Entre em:

```powershell
cd android-app
```

Crie o arquivo local:

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"

"sdk.dir=$($sdk -replace '\\','/')" |
Set-Content -Encoding ASCII .\local.properties
```

Confira:

```powershell
Get-Content .\local.properties
```

Exemplo:

```text
sdk.dir=C:/Users/igors/AppData/Local/Android/Sdk
```

## Importante

`local.properties` contém configuração específica da máquina e NÃO deve ser commitado.

Confira:

```powershell
git status
```

---

# 11. Testes Android locais

Dentro de:

```text
android-app/
```

execute os comandos separadamente.

## 11.1 Testes unitários

```powershell
.\gradlew.bat testDebugUnitTest
```

Esperado:

```text
BUILD SUCCESSFUL
```

## 11.2 Validação dos assets de ML

```powershell
.\gradlew.bat verifyModelAssets
```

Esperado:

```text
BUILD SUCCESSFUL
```

## 11.3 Build debug

```powershell
.\gradlew.bat assembleDebug
```

Esperado:

```text
BUILD SUCCESSFUL
```

APK gerado normalmente em:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

---

# 12. Resumo mínimo antes do teste físico

| Validação | Resultado esperado |
|---|---|
| Python pytest | PASS |
| validate_bundle.py | PASS |
| testDebugUnitTest | PASS |
| verifyModelAssets | PASS |
| assembleDebug | PASS |

Se algum deles falhar, não continue para a validação física sem entender o motivo.

---

# 13. Preparar o celular Android

No celular:

1. Abra **Configurações**.
2. Vá em **Sobre o telefone**.
3. Toque várias vezes em **Número da versão / Build number** até habilitar `Opções do desenvolvedor`.
4. Abra `Opções do desenvolvedor`.
5. Ative `Depuração USB`.
6. Conecte o aparelho ao computador via USB.
7. Aceite a autorização de depuração USB no celular.

---

# 14. Verificar ADB

No PowerShell:

```powershell
adb version
```

Se o comando não existir, use diretamente:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" version
```

Verifique o celular:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

Esperado:

```text
List of devices attached
XXXXXXXXXXXX    device
```

Se aparecer `unauthorized`, desbloqueie o celular e aceite a autorização.

---

# 15. Instalar o APK manualmente

Depois de:

```powershell
.\gradlew.bat assembleDebug
```

instale:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r `
".\app\build\outputs\apk\debug\app-debug.apk"
```

Esperado:

```text
Success
```

---

# 16. Executar testes instrumentados

Com celular conectado e autorizado:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Esperado:

```text
BUILD SUCCESSFUL
```

Esse teste não substitui a validação manual da câmera e da experiência real.

---

# 17. Teste manual obrigatório

Use também:

```text
docs/MANUAL_TESTS.md
```

No mínimo, validar:

## Inicialização

- app abre sem crash;
- histórico abre corretamente;
- modo avião não impede o uso.

## Permissões

- primeira solicitação de câmera funciona;
- negar permissão não derruba o app;
- conceder depois funciona;
- reabrir o aplicativo funciona.

## Câmera

- câmera abre;
- enquadramento aparece;
- captura funciona;
- rotação não quebra a tela;
- voltar da câmera funciona;
- flash funciona quando disponível.

## Galeria

- selecionar imagem funciona;
- cancelar seleção funciona;
- imagem grande não derruba o app;
- formato inválido recebe tratamento adequado.

## Inferência

- foto é analisada;
- resultado mostra top-3;
- confiança é exibida;
- estado inconclusivo funciona quando aplicável;
- tempo de inferência é exibido;
- repetir análise não causa crash.

## Histórico

- nova análise aparece;
- imagem permanece disponível;
- fechar e abrir app mantém registro;
- reiniciar aparelho mantém registro;
- busca funciona;
- filtros funcionam;
- excluir remove registro;
- excluir também remove arquivo da foto correspondente.

## Privacidade / offline

Teste em modo avião.

Confirme que:

- análise continua funcionando;
- histórico funciona;
- nenhuma tela exige login;
- nenhuma funcionalidade principal exige internet.

---

# 18. Teste de persistência

Faça:

1. uma análise;
2. confirme no histórico;
3. feche completamente o app;
4. abra novamente;
5. confirme o registro;
6. reinicie o celular;
7. abra novamente;
8. confirme novamente.

Isso valida, na prática:

```text
Room
+
armazenamento privado das fotos
```

---

# 19. Teste de inferência repetida

Como `LeafClassifier` reutiliza o Interpreter:

1. selecione uma imagem;
2. anote classe principal, confiança e top-3;
3. repita a análise da mesma imagem várias vezes;
4. verifique se os resultados permanecem compatíveis;
5. depois analise imagens diferentes em sequência.

Objetivo:

- detectar problemas de reutilização de buffer;
- detectar estado residual;
- confirmar estabilidade do Interpreter reutilizado.

---

# 20. Testar fotos ruins

Testar pelo menos:

- foto desfocada;
- pouca luz;
- folha distante;
- folha parcialmente fora do quadro;
- fundo complexo;
- foto muito clara.

O projeto ainda não possui rejeição robusta de OOD/qualidade.

O objetivo é observar e registrar comportamento, não afirmar que o sistema já resolve esses casos.

---

# 21. O que registrar durante o teste físico

Para cada problema encontrado:

```text
Aparelho:
Versão Android:
Passo:
Resultado esperado:
Resultado obtido:
Reproduzível:
Screenshot/log:
```

---

# 22. Logs Android

Limpar log:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -c
```

Depois de reproduzir um erro:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -d > leafcare-logcat.txt
```

Não commite logs temporários.

---

# 23. Problemas comuns

## PowerShell bloqueia Activate.ps1

Não é necessário ativar a venv.

Use:

```powershell
.\.venv\Scripts\python.exe
```

## Java não encontrado

Instale JDK 17 e confirme:

```powershell
java -version
```

## Android SDK não encontrado

Confirme:

```powershell
Test-Path "$env:LOCALAPPDATA\Android\Sdk"
```

e configure `android-app/local.properties`.

## adb não encontrado

Use:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

## Device unauthorized

Desbloqueie o celular e aceite a depuração USB.

Se necessário:

```powershell
adb kill-server
adb start-server
adb devices
```

## Primeira build demora

Gradle e Android SDK podem baixar dependências e Build Tools na primeira execução. Isso é normal.

---

# 24. Docker não é necessário

O LeafCare não depende de:

- Docker;
- Docker Desktop;
- WSL;
- Hyper-V;
- Kubernetes;
- containers.

Problemas de virtualização no Docker Desktop não impedem o build ou os testes do LeafCare.

---

# 25. Checklist final

```text
[ ] Python 3.12 configurado
[ ] Dependências Python instaladas
[ ] 28 testes Python passando
[ ] validate_bundle.py passando
[ ] JDK 17 instalado
[ ] Android SDK configurado
[ ] API 35 instalada
[ ] testDebugUnitTest passando
[ ] verifyModelAssets passando
[ ] assembleDebug passando
[ ] celular reconhecido pelo adb
[ ] connectedDebugAndroidTest passando
[ ] APK instalado
[ ] câmera testada
[ ] galeria testada
[ ] inferência testada
[ ] inferência repetida testada
[ ] histórico testado
[ ] persistência após reabrir app testada
[ ] persistência após reiniciar aparelho testada
[ ] exclusão testada
[ ] modo avião testado
[ ] MANUAL_TESTS.md executado
```

---

# 26. Resultado esperado antes do merge

```text
Python tests................ PASS
ML bundle validation........ PASS
Android unit tests.......... PASS
Model assets validation..... PASS
Android debug build......... PASS
Instrumented tests.......... PASS
Manual physical test........ PASS
Working tree................ CLEAN
```

Se um teste não puder ser executado, registre explicitamente:

```text
NOT RUN
```

Não substitua teste não executado por suposição.

---

# 27. Git após a validação

Confirme:

```powershell
git status
git branch --show-current
git log --oneline --decorate -12
```

Não versione:

- `.venv/`;
- `local.properties`;
- `build/`;
- APK gerado;
- logs temporários;
- configurações locais do Android SDK.

Depois da validação física, faça commit apenas se houver correção de bug ou atualização legítima da documentação.

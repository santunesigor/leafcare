# Desenvolvimento

## Android

Requisitos: JDK 17, Android SDK 35 e Gradle 8.9 pelo wrapper.

Na raiz do repositório, crie a configuração local:

```powershell
cd android-app
Copy-Item local.properties.example local.properties
```

Configure `sdk.dir` e `SUPABASE_PUBLISHABLE_KEY` em `android-app/local.properties`. Não compartilhe nem versione esse arquivo. Use somente a chave publicável; nunca use `service_role`.

Comandos no Windows/PowerShell, dentro de `android-app`:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat verifyModelAssets
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

O `assembleDebug` disponibiliza o APK para instalar em `app/build/outputs/apk/distribution/debug/leafcare-<versão>-<commit>.apk`. A versão vem do `versionName` e o commit é o hash curto do `HEAD` usado no build. Sem um checkout Git, o sufixo é `sem-git`. A CI publica esse arquivo com o mesmo nome.

O arquivo padrão `app/build/outputs/apk/debug/app-debug.apk` continua disponível para as ferramentas Android.

## Python e Machine Learning

Use Python 3.12, a partir da raiz:

```powershell
cd machine-learning
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python -m pytest -q
python validate_bundle.py --require-model
```

No Linux/macOS, use `python3.12 -m venv .venv` e ative com `source .venv/bin/activate`.

Mais comandos e detalhes do pipeline: [MACHINE_LEARNING.md](MACHINE_LEARNING.md). Testes e QA: [TESTING.md](TESTING.md).

# Desenvolvimento

Para o setup detalhado do Android, consulte [INSTALL.md](INSTALL.md). Este resumo registra os requisitos e comandos usados pelo projeto.

## Android

- JDK 17
- Android SDK 35 (compile/target); minSdk 26
- Gradle 8.9 pelo wrapper incluído

Na raiz do repositório:

```powershell
cd android-app
Copy-Item local.properties.example local.properties
```

Edite `android-app/local.properties` com `sdk.dir` do Android SDK e `SUPABASE_PUBLISHABLE_KEY=<chave-publicável-do-projeto-leafcare>`. Não coloque o valor da chave neste documento; nunca use `service_role`.

Comandos Android:

```powershell
.\gradlew.bat clean
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat verifyModelAssets
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

APK debug: `android-app/app/build/outputs/apk/debug/app-debug.apk`.

## Python e ML

Use Python 3.12. Na raiz:

```powershell
cd machine-learning
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python -m pytest -q
python validate_bundle.py --require-model
```

`validate_bundle.py --require-model` valida bundle, classes, ordem de saída e carregamento do modelo. A distribuição do modelo para Android também é verificada por `verifyModelAssets`.

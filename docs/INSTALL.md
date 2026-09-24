# LeafCare — Instalação reproduzível

## Requisitos

| Item | Versão/valor |
|---|---|
| JDK | 17 |
| Android SDK (compile/target) | 35 |
| minSdk | 26 (Android 8.0) |
| Gradle | 8.9 (via wrapper) |
| `local.properties` | `sdk.dir` + `SUPABASE_PUBLISHABLE_KEY` |
| Python (só ML) | 3.12 + `requirements.txt` |

## 1. Android

```powershell
cd android-app
Copy-Item local.properties.example local.properties
```

Edite `android-app/local.properties` (nunca commitado):

```properties
sdk.dir=C:/Users/<voce>/AppData/Local/Android/Sdk
SUPABASE_PUBLISHABLE_KEY=<publishable key do projeto leafcare>
```

> A chave é a **publishable/anon pública** (Dashboard → Settings → API).
> **Nunca** `service_role`, senha de banco ou JWT secret.
> Sem a chave, o build **falha de propósito** (`verifySupabaseConfig`).

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat verifyModelAssets
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

APK em `app/build/outputs/apk/debug/app-debug.apk` (~62 MB).

## 2. Instalar no aparelho

```powershell
<sdk>/platform-tools/adb.exe devices        # deve listar `device` (autorizado)
<sdk>/platform-tools/adb.exe install -r app/build/outputs/apk/debug/app-debug.apk
```

## 3. Machine Learning (opcional, só para reproduzir treino/validação)

```bash
cd machine-learning
python3.12 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python -m pytest -q
python validate_bundle.py --require-model
```

> Ambiente do mantenedor usa TensorFlow indisponível no Python 3.14 — use 3.12.

## 4. Backend (já provisionado)

Projeto `leafcare` (`sa-east-1`) com migrations em `supabase/migrations/`.
Detalhes em [SUPABASE_SETUP.md](SUPABASE_SETUP.md). Não crie outro projeto
(`nossoduo` é separado e nunca deve ser usado).

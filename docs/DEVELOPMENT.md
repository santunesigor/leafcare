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

## OpenSpec

O projeto usa OpenSpec **1.14.1** com Node.js **22** na CI. Para instalar a mesma versão localmente:

```bash
npm install -g @fission-ai/openspec@1.14.1
openspec validate --all --strict --no-interactive
openspec validate --archived --strict --no-interactive
```

O contexto/regras ficam em `openspec/config.yaml`; os workflows do Codex já estão versionados em `.agents/skills/`. Não é necessário executar `openspec init` após clonar. O [guia OpenSpec](../openspec/README.md) explica o fluxo, o uso das skills e as specs de referência.

### Modelo atual

O APK 1.2.0 usa MobileNetV4 Small destilado. Para validar o bundle: `python validate_bundle.py --require-model`; para promoção do checkpoint previamente selecionado: `python deploy_distilled.py --install`, com dados e caches locais. Receitas do ensemble são históricas, reproduzíveis no tag `v1.1.2`. Veja [Machine Learning](MACHINE_LEARNING.md).

## Backend administrativo

Para validar sem usar produção: `bash supabase/tests/run-local.sh` (Docker/PostgreSQL 18) e `npx --yes deno test supabase/functions/leafcare-admin/handler_test.ts` (Deno 2.9.6). Publicação e bootstrap separados em [SUPERADMIN.md](SUPERADMIN.md).

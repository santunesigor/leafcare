# Desenvolvimento

## Android

Requisitos: Android Studio, JDK 17, Android SDK 35 e Gradle 8.9 pelo wrapper.

Na raiz do repositório:

```bash
cd android-app
cp local.properties.example local.properties
```

Configure `sdk.dir` e `SUPABASE_PUBLISHABLE_KEY` em `android-app/local.properties`. Use somente a chave publicável no app. Não versione esse arquivo nem inclua `service_role` ou credenciais administrativas.

Comandos do wrapper, dentro de `android-app`:

```bash
bash ./gradlew compileDebugKotlin
bash ./gradlew testDebugUnitTest
bash ./gradlew verifyModelAssets
bash ./gradlew lintDebug
bash ./gradlew assembleDebug
```

No Windows, use `gradlew.bat` e os equivalentes no PowerShell. O APK padrão fica em `app/build/outputs/apk/debug/app-debug.apk`. `assembleDebug` também gera `app/build/outputs/apk/distribution/debug/leafcare-<versão>-<commit>.apk`; a CI publica esse artefato.

## Machine Learning

Use Python 3.12 no diretório `machine-learning/`:

```bash
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m pytest -q
python validate_bundle.py --require-model
```

O conjunto de dados completo e caches/checkpoints de experimentos não são distribuídos. Leia [Machine Learning](MACHINE_LEARNING.md) e [machine-learning/README](../machine-learning/README.md) antes de treinar ou promover um modelo.

## Supabase e testes locais

O app usa a chave publicável configurada localmente. Migrations aplicadas e contratos SQL ficam em `supabase/migrations/`; não aplique migrations ou altere dados de produção como parte de uma validação local.

```bash
bash supabase/tests/run-pglite.sh
```

Essa suíte executa cenários SQL em PostgreSQL WASM sem modificar o projeto remoto. O caminho com PostgreSQL local via Docker e testes da Edge Function está descrito em [Supabase](SUPABASE.md) e [Superadmin](SUPERADMIN.md).

## OpenSpec

O projeto usa OpenSpec 1.14.1 e o esquema `spec-driven`. Para atualizar as validações locais:

```bash
npm install -g @fission-ai/openspec@1.14.1
openspec validate --all --strict --no-interactive
openspec validate --archived --strict --no-interactive
```

O projeto já versiona `openspec/config.yaml` e os workflows Codex em `.agents/skills/`; não é necessário executar `openspec init` após clonar. O guia em [OpenSpec](../openspec/README.md) explica o fluxo e os requisitos dos artefatos.

## Publicação

Versão Android e `versionCode` ficam em `android-app/app/build.gradle.kts`. Releases públicos e APKs para teste ficam em [GitHub Releases](https://github.com/santunesigor/leafcare/releases). Antes de publicar, execute os testes aplicáveis, gere o APK de distribuição e confirme que ele corresponde ao commit da release.

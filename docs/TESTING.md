# Testes e QA

Use as verificações adequadas às partes alteradas. Os testes automatizados não substituem os fluxos que dependem de um aparelho ou de um projeto Supabase configurado.

## Android

Na pasta `android-app`:

```bash
bash ./gradlew testDebugUnitTest verifyModelAssets lintDebug assembleDebug
```

No Windows, execute os equivalentes com `gradlew.bat`. Com aparelho ou emulador conectado, rode também os testes instrumentados:

```bash
bash ./gradlew connectedDebugAndroidTest
```

## Machine Learning

Com Python 3.12 e dependências instaladas, no diretório `machine-learning/`:

```bash
python -m pytest -q
python validate_bundle.py --require-model
```

O treino e a promoção exigem dataset e caches locais; consulte [Machine Learning](MACHINE_LEARNING.md). Não promova um modelo como parte da validação de rotina.

## Backend e especificações

Na raiz do repositório:

```bash
bash supabase/tests/run-pglite.sh
npx --yes deno test supabase/functions/leafcare-admin/handler_test.ts
openspec validate --all --strict --no-interactive
openspec validate --archived --strict --no-interactive
```

Os cenários PGlite usam banco efêmero. Não use testes locais para aplicar migrations, gravar dados ou validar mudanças no projeto Supabase remoto.

## Checklist manual Android

- Cadastro, confirmação de e-mail, login, logout, recuperação de senha e retorno do deep link ao app.
- Câmera e galeria: permissões, captura, JPEG/PNG, imagem grande e orientação EXIF.
- Classificação e histórico: Top-3, resultado inconclusivo, persistência, busca, filtros e exclusão.
- Offline e sincronização: análise sem rede, retry sem duplicação, sincronização de fotos elegíveis, exclusão e restauração.
- Troca de contas no mesmo aparelho e isolamento do histórico.
- Treinamento opcional: opção desligada por padrão, recusa, autorização geral e por foto, revogação e continuidade da análise/histórico.
- Administração, quando aplicável: papel autorizado, revisão, filtros, conflitos e perda de acesso.

## Limites

Testes JVM e Robolectric não medem latência, qualidade da câmera ou comportamento de provedores no aparelho. Não afirmam que SMTP, deep links ou operações administrativas remotas foram testados; valide esses fluxos manualmente quando a mudança os afetar.

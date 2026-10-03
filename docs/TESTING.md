# Testes — LeafCare

Este documento concentra os comandos de validação e o roteiro manual essencial.

## Python / Machine Learning

```bash
cd machine-learning
python -m pytest -q
python validate_bundle.py --require-model
```

Os testes cobrem contrato do bundle, dataset, preprocessing, benchmark e integração TensorFlow quando disponível.

## Android unitário

```bash
cd android-app
./gradlew testDebugUnitTest
```

No Windows:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Modelo Android

```bash
./gradlew verifyModelAssets
```

Verifica consistência entre modelo, metadata e classes.

## Lint

```bash
./gradlew lintDebug
```

## Build

```bash
./gradlew assembleDebug
```

APK:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

## Instrumentados

Com dispositivo/emulador conectado:

```bash
./gradlew connectedDebugAndroidTest
```

## Roteiro manual mínimo

### Instalação e abertura

- instalar o APK em uma instalação limpa;
- abrir e confirmar ausência de crash;
- confirmar ícone e nome do app.

### Auth

- cadastrar conta nova;
- confirmar e-mail pelo deep link;
- sair e entrar novamente;
- testar senha incorreta;
- testar recuperação de senha;
- testar alteração de senha;
- fechar e reabrir o app para conferir persistência da sessão.

### Câmera

- conceder permissão;
- negar permissão;
- capturar imagem;
- testar rotação;
- testar troca de câmera quando disponível;
- testar flash quando disponível.

### Galeria

- selecionar JPEG válido;
- selecionar imagem grande;
- testar imagem com orientação EXIF;
- testar entrada inválida/corrompida quando possível.

### Inferência

- confirmar Top-3;
- confirmar tempo de inferência;
- confirmar classe e textos do catálogo;
- testar resultado abaixo do threshold;
- testar repetição de análises sem crash.

### Histórico local

- criar várias análises;
- fechar/reabrir o processo;
- confirmar persistência;
- pesquisar;
- filtrar;
- excluir;
- confirmar que a foto local acompanha o registro esperado.

### Offline

Depois de autenticar:

- desligar Wi-Fi e dados móveis;
- abrir histórico;
- capturar/selecionar imagem;
- executar classificação;
- salvar nova análise;
- fechar e reabrir o app;
- confirmar que o histórico continua disponível.

### Sincronização

- criar análise offline;
- reconectar;
- aguardar WorkManager;
- confirmar análise remota;
- confirmar foto no bucket;
- confirmar que não há duplicata após retry.

### Exclusão offline

- excluir análise sem internet;
- confirmar que ela desaparece da UI local;
- reconectar;
- confirmar aplicação da exclusão remota;
- conferir que restore não ressuscita o registro.

### Multi-device / restore

Quando houver dois aparelhos disponíveis:

- criar análises no aparelho A;
- aguardar sincronização;
- entrar com a mesma conta no aparelho B;
- confirmar restauração do histórico;
- confirmar carregamento posterior das fotos;
- excluir em um aparelho e validar comportamento no outro após sincronização.

### Isolamento de contas

- entrar com conta A e gerar dados;
- sair;
- entrar com conta B;
- confirmar que dados da conta A não aparecem;
- validar também o backend usando duas sessões separadas.

## Resultados exportados

Resultados de execuções anteriores são mantidos em:

```text
docs/testing/
```

Eles funcionam como evidência histórica e não substituem a execução dos testes após mudanças no código.

## Antes de apresentar uma nova versão

Execute no mínimo:

```bash
cd machine-learning
python -m pytest -q
python validate_bundle.py --require-model

cd ../android-app
./gradlew testDebugUnitTest
./gradlew verifyModelAssets
./gradlew lintDebug
./gradlew assembleDebug
```

E finalize com o roteiro manual em um aparelho físico sempre que a mudança afetar UI, câmera, autenticação, sync ou inferência.

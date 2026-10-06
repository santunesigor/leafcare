# LeafCare

## Sobre o aplicativo

O LeafCare é um aplicativo Android para triagem visual de doenças e alterações em folhas de fumo. Ele analisa fotos feitas com a câmera ou escolhidas na galeria. A classificação acontece no próprio aparelho; o app não envia a imagem a um serviço para obter a previsão.

> O LeafCare é uma ferramenta de triagem e não substitui diagnóstico agronômico ou avaliação profissional.

### Como funciona

1. O app prepara a imagem e a classifica localmente com um ensemble de dois MobileNetV3Small e um MobileNetV3Large, reunidos em um único TFLite.
2. Mostra até três hipóteses. Se a maior pontuação ficar abaixo do limiar calibrado definido no bundle, o resultado é apresentado como inconclusivo.
3. Salva a análise e o histórico no banco local Room.
4. Depois do primeiro acesso autenticado, câmera, galeria, classificação e histórico ficam disponíveis offline. Quando há conexão, o app sincroniza o histórico e as fotos com o Supabase em segundo plano.

O Supabase cuida da autenticação e da sincronização; ele não classifica as imagens.

### Dataset e modelo

O modelo foi treinado com 696 imagens em 16 classes, usando a seção TV3 bruta do dataset [TLA — Tobacco Leaf Abnormality](https://doi.org/10.3389/fpls.2024.1333236). O conjunto foi dividido em treino, validação e teste (70% / 15% / 15%), com separação por grupos para reduzir vazamento entre imagens relacionadas.

As imagens completas do dataset não são distribuídas neste repositório. O mapa de classes está em [`machine-learning/tla_class_map.yaml`](machine-learning/tla_class_map.yaml), o registro da importação em [`machine-learning/data/import_report.json`](machine-learning/data/import_report.json) e as fontes/licenças das imagens de referência em [`docs/legal/referencias_manifest.csv`](docs/legal/referencias_manifest.csv).

No teste registrado de 103 imagens, o ensemble integrado obteve 81,55% de acurácia Top-1, Macro-F1 de 0,7371 e acurácia Top-3 de 99,03%. O limiar calibrado de 0,631628 foi escolhido na validação; no teste, aceitou 82/103 previsões e acertou 76 delas (92,68%). É um teste interno pequeno, sem validação de campo; as pontuações não representam certeza agronômica. Mais detalhes estão em [Machine Learning](docs/MACHINE_LEARNING.md).

## Desenvolvimento

### Requisitos

- Android Studio, JDK 17 e Android SDK 35 para desenvolver e executar o app.
- Python 3.12 apenas para trabalhar no pipeline de Machine Learning.
- Uma chave publicável do Supabase para testar autenticação e sincronização. A classificação local não depende de conexão após o acesso autenticado.

### Configurar e executar o app

No Windows/PowerShell, crie a configuração local e preencha `sdk.dir` e `SUPABASE_PUBLISHABLE_KEY` em `android-app/local.properties`:

```powershell
cd android-app
Copy-Item local.properties.example local.properties
```

Abra `android-app` no Android Studio para executar em um emulador ou aparelho. Também é possível instalar a versão debug em um aparelho conectado:

```powershell
.\gradlew.bat installDebug
```

Não compartilhe nem versione `local.properties`. Use somente a chave publicável; nunca inclua `service_role` ou credenciais administrativas.

### Criar o APK e validar mudanças

Dentro de `android-app`, gere o APK debug com:

```powershell
.\gradlew.bat assembleDebug
```

O arquivo fica em `android-app/app/build/outputs/apk/debug/app-debug.apk`. Antes de enviar mudanças, rode os testes e as verificações relevantes:

```powershell
.\gradlew.bat testDebugUnitTest verifyModelAssets lintDebug
```

O código Android fica em `android-app/app/src/main/java/br/com/leafcare/`: `ui/` contém as telas, `auth/` a autenticação, `data/` persistência e sincronização, e `ml/` o processamento e a inferência de imagens. A explicação dos fluxos e das relações entre esses módulos está em [Arquitetura](docs/ARCHITECTURE.md).

### Treinar e integrar outro modelo

O dataset completo não está no repositório. Para treinar, disponibilize os dados locais esperados em `machine-learning/data/raw/` e use Python 3.12. No PowerShell:

```powershell
cd machine-learning
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python prepare_dataset.py --config config.yaml
python train_ensemble.py --train-only
python train_ensemble.py --export-only
python validate_bundle.py --require-model
```

A exportação atualiza o bundle usado pelo app em `android-app/app/src/main/assets/`. Depois de alterar o modelo, valide também o contrato Android:

```powershell
cd ../android-app
.\gradlew.bat verifyModelAssets testDebugUnitTest
```

Formato de entrada, ordem das classes, pré-processamento, threshold e exportação precisam permanecer consistentes entre Python e Android. O passo a passo e as restrições do pipeline estão em [Machine Learning](docs/MACHINE_LEARNING.md).

## Documentação do projeto

- [Desenvolvimento](docs/DEVELOPMENT.md): setup e comandos Android/Python.
- [Arquitetura](docs/ARCHITECTURE.md): módulos, banco local, inferência e sincronização.
- [Machine Learning](docs/MACHINE_LEARNING.md): dataset, contrato do modelo, métricas e pipeline de treino/exportação.
- [Jornada do Machine Learning](docs/explain/JORNADA_MACHINE_LEARNING.md): preparação dos dados, experimentos, decisão pelo ensemble e roadmap, em formato compatível com Obsidian.
- [Benchmark de modelos](docs/benchmarks/BENCHMARK_MODELOS.md): comparação histórica e experimentos DINOv2, MobileNetV4, TinyViT e MobileCLIP2.
- [Supabase](docs/SUPABASE.md): autenticação, migrations, RLS e Storage.
- [Testes e QA](docs/TESTING.md): validações automatizadas e evidências históricas.
- [Contexto e regras do projeto](docs/AI_CONTEXT.md): limites importantes para mudanças.
- [Licença da fonte Inter](docs/legal/Inter-OFL.txt) e [fontes das imagens](docs/legal/referencias_manifest.csv).
- [Histórico de versões](CHANGELOG.md).

## Estrutura

```text
android-app/       aplicativo Android
machine-learning/  dados de referência, pipeline e artefatos do modelo
supabase/          migrations do backend
docs/              arquitetura, setup e evidências de QA
samples/           imagens e fixtures de validação
```

# LeafCare

## Sobre o aplicativo

O LeafCare é um aplicativo Android para triagem visual de doenças e alterações em folhas de fumo. Ele analisa fotos feitas com a câmera ou escolhidas na galeria. A classificação acontece no próprio aparelho; o app não envia a imagem a um serviço para obter a previsão.

> O LeafCare é uma ferramenta de triagem e não substitui diagnóstico agronômico ou avaliação profissional.

### Como funciona

1. O app prepara a imagem e a classifica localmente com um MobileNetV4 Small destilado em um único TFLite de 9,70 MiB.
2. Mostra até três hipóteses. Se a maior pontuação ficar abaixo do limiar calibrado definido no bundle, o resultado é apresentado como inconclusivo.
3. Salva a análise e o histórico no banco local Room.
4. Depois do primeiro acesso autenticado, câmera, galeria, classificação e histórico ficam disponíveis offline. Quando há conexão, o app sincroniza o histórico e as fotos com o Supabase em segundo plano.

O Supabase cuida da autenticação e da sincronização; ele não classifica as imagens.

### Dataset e modelo

O modelo foi treinado com 696 imagens em 16 classes, usando a seção TV3 bruta do dataset [TLA — Tobacco Leaf Abnormality](https://doi.org/10.3389/fpls.2024.1333236). O conjunto foi dividido em treino, validação e teste (70% / 15% / 15%), com separação por grupos para reduzir vazamento entre imagens relacionadas.

As imagens completas do dataset não são distribuídas neste repositório. O mapa de classes está em [`machine-learning/tla_class_map.yaml`](machine-learning/tla_class_map.yaml), o registro da importação em [`machine-learning/data/import_report.json`](machine-learning/data/import_report.json) e as fontes/licenças das imagens de referência em [`docs/legal/referencias_manifest.csv`](docs/legal/referencias_manifest.csv).

No teste conhecido de 103 imagens, o aluno integrado obteve 85,44% de Top-1, Macro-F1 de 0,8083 e Top-3 de 97,09%. O limiar de 0,506676 foi escolhido na validação; no teste, aceitou 90/103 previsões e acertou 81 (90%). É um teste interno pequeno, sem validação de campo. O ensemble anterior está preservado na release `v1.1.2`. Mais detalhes estão em [Machine Learning](docs/MACHINE_LEARNING.md).

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

O APK para instalar fica em `android-app/app/build/outputs/apk/distribution/debug/leafcare-<versão>-<commit>.apk`, por exemplo `leafcare-1.2.0-<commit>.apk`. O nome é gerado automaticamente pelo `assembleDebug` e usado no download da CI. Antes de enviar mudanças, rode os testes e as verificações relevantes:

```powershell
.\gradlew.bat testDebugUnitTest verifyModelAssets lintDebug
```

O código Android fica em `android-app/app/src/main/java/br/com/leafcare/`: `ui/` contém as telas, `auth/` a autenticação, `data/` persistência e sincronização, e `ml/` o processamento e a inferência de imagens. A explicação dos fluxos e das relações entre esses módulos está em [Arquitetura](docs/ARCHITECTURE.md).

### Validar e integrar o modelo

O dataset completo não está no repositório. Para treinar, disponibilize os dados locais esperados em `machine-learning/data/raw/` e use Python 3.12. No PowerShell:

```powershell
cd machine-learning
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python predict.py ../samples/reference_frog_eye.jpg
# Com dados e caches do experimento disponíveis:
python deploy_distilled.py --install
python validate_bundle.py --require-model
```

A promoção do checkpoint selecionado atualiza o bundle usado pelo app em `android-app/app/src/main/assets/`. Depois de alterar o modelo, valide também o contrato Android:

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
- [Jornada do aplicativo](docs/explain/JORNADA_APLICATIVO.md): criação do Android, telas, fluxo offline, bancos, autenticação, sincronização e evolução com fotos do bucket, em formato compatível com Obsidian.
- [Benchmark de modelos](docs/benchmarks/BENCHMARK_MODELOS.md): comparação histórica e experimentos DINOv2, MobileNetV4, TinyViT e MobileCLIP2.
- [Supabase](docs/SUPABASE.md): autenticação, migrations, RLS e Storage.
- [Testes e QA](docs/TESTING.md): validações automatizadas e evidências históricas.
- [Contexto e regras do projeto](docs/AI_CONTEXT.md): limites importantes para mudanças.
- [OpenSpec](openspec/README.md): fluxo de mudanças, specs do comportamento atual e integração Codex.
- [Organização do ML](machine-learning/README.md) e [mapa do repositório](docs/explain/MAPA_REPOSITORIO.md): modelo atual, relatórios, legado e decisões de limpeza.
- [Licença da fonte Inter](docs/legal/Inter-OFL.txt) e [fontes das imagens](docs/legal/referencias_manifest.csv).
- [Histórico de versões](CHANGELOG.md).

## Estrutura

```text
android-app/       aplicativo Android
machine-learning/  dados de referência, pipeline e artefatos do modelo
supabase/          migrations do backend
docs/              arquitetura, setup e evidências de QA
samples/           imagens e fixtures de validação
openspec/          especificações e histórico de mudanças
.agents/skills/    workflows OpenSpec para Codex
```

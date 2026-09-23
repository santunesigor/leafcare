# Arquitetura do LeafCare

## 1. Visão geral

O LeafCare é um aplicativo Android nativo para **triagem visual offline** de doenças e alterações em folhas de fumo. O produtor captura ou seleciona uma foto e o modelo roda direto no celular — sem internet, sem backend, sem cadastro.

Características principais:

- **Android nativo**: Kotlin + Jetpack Compose, minSdk 26 (Android 8.0), targetSdk 35
- **100% offline**: Manifesto sem permissão `INTERNET`; nenhum backend, Firebase, Supabase, analytics ou telemetria
- **Classificação local**: TensorFlow Lite / LiteRT 1.4.0 com API `Interpreter`, float32, 2 threads
- **Persistência local**: Room/SQLite com `Flow` reativo; fotos em armazenamento privado (`filesDir/photos/`)
- **MVVM pragmático**: Injeção manual no `Application`; `ViewModel` expõe `StateFlow`/`Flow`; UI não acessa DAO, Room ou Classifier diretamente
- **Contrato de inferência idêntico Python ↔ Android**: center crop, resize bilinear half-pixel inteiro, 224×224, RGB float32 NHWC 0–255, sem normalização no app (Rescaling incorporado na MobileNetV3 via `include_preprocessing=True`)

### Diagrama geral

```
Usuário
  ↓
Compose UI (MainActivity, Navigation)
  ↓
LeafCareViewModel (estado, navegação, erros)
  ↓
AnalysisRepository (coordenação, Mutex, armazenamento)
  ├── Room (AnalysisDao, AnalysisEntity, Flow)
  ├── Arquivos privados (filesDir/photos/)
  └── LeafClassifier
          ↓
       LiteRT Interpreter (reutilizado, validado na inicialização)
          ↓
       leafcare.tflite (MobileNetV3Small, ~3.8 MB, float32)
```

---

## 2. Fluxo completo de uma análise

Passo a passo, do toque do usuário até o resultado na tela:

1. **Usuário abre câmera ou galeria** — `CameraScreen` (CameraX) ou seletor de documentos do sistema (`ACTION_OPEN_DOCUMENT`)
2. **Imagem capturada/selecionada** — Retorna `Uri` para o `ViewModel`
3. **Repository copia a imagem para armazenamento privado** — `AnalysisRepository.analyze()` escreve em `filesDir/photos/{id}.img` (limite 30 MB)
4. **ImageDecoder decodica** — `BitmapFactory` com `inPreferredColorSpace=SRGB`; limite 16 megapixels; correção EXIF (orientação 1–8, espelhamentos); transparência composta sobre branco
5. **PixelPreprocessor processa** — Center crop quadrado (`min(width, height)`); resize bilinear **half-pixel, aritmética inteira** para 224×224; saída RGB float32
6. **LeafClassifier executa inferência** — Tensor `float32 NHWC [1,224,224,3]` faixa **0–255** (não dividir por 255); `Interpreter` reutilizado (criado uma vez na inicialização); `run()` com buffers pré-alocados
7. **PredictionPolicy aplica política** — Softmax já vem do modelo; top-3 estável (ordem decrescente, empate = menor índice); **sem renormalização**; threshold do `model_metadata.json` (baseline 0.70); resultado inconclusivo se `max < threshold`
8. **DiseaseCatalog adiciona informações descritivas** — `diseases.json` mapeia `classId` → nome, nome científico, descrição, sintomas, condições favoráveis, orientação, `reviewed: false`
9. **AnalysisEntity persistida no Room** — ID, caminho relativo da foto, timestamp, classe principal, nomes, confiança, top-3 JSON, flag inconclusivo, threshold usado, tempo de inferência (ms), hash do modelo
10. **Foto permanece em armazenamento privado** — Room guarda apenas nome relativo; arquivo físico em `filesDir/photos/`
11. **Resultado mostrado na tela** — `ResultScreen` exibe classe principal, confiança, top-3, descrição, aviso de triagem, tempo
12. **Histórico observa dados via Flow** — `AnalysisDao.observeAll()` emite `Flow<List<AnalysisEntity>>`; `ViewModel` expõe via `stateIn`; `HistoryScreen` recompos automaticamente

### Diagrama do fluxo

```
Câmera / Galeria
      ↓
Uri → ViewModel.analyze()
      ↓
Repository.analyze() [Mutex.withLock]
      ↓
Copia para filesDir/photos/{id}.img (≤30 MB)
      ↓
ImageDecoder.decode(file)
  ├─ BitmapFactory (sRGB, inScaled=false)
  ├─ Limite 16 MP
  ├─ EXIF orientation 1–8
  └─ Alpha sobre branco
      ↓
PixelPreprocessor.toRgb()
  ├─ Center crop (min lado)
  ├─ Resize bilinear half-pixel integer 224×224
  └─ FloatArray RGB 0–255
      ↓
LeafClassifier.classify(bitmap)
  ├─ Input buffer NHWC [1,224,224,3] float32 0–255
  ├─ Interpreter.run() (reutilizado, 2 threads)
  └─ Output [1,16] float32 softmax
      ↓
PredictionPolicy.top3() + inconclusive()
  ├─ Top-3 estável, sem renormalização
  └─ Threshold do metadata (0.70 baseline)
      ↓
DiseaseCatalog.get(classId) → DiseaseInfo
      ↓
AnalysisEntity → Room (NonCancellable)
      ↓
Flow emite lista atualizada → HistoryScreen
```

---

## 3. Camada de apresentação

- **Jetpack Compose** — UI declarativa, Material3, `Navigation Compose` para rotas (`history`, `camera`, `result/{id}`)
- **MainActivity** — `ComponentActivity`, `enableEdgeToEdge()`, `setContent { LeafCareTheme { LeafCareApp() } }`
- **Navigation** — `NavHost` com 3 destinos; `NavController` navega via callbacks do `ViewModel`; `popUpTo("history")` evita pilha profunda
- **LeafCareViewModel** — `AndroidViewModel`; expõe `analyses: Flow<List<AnalysisEntity>>`, `ui: StateFlow<UiState>`, `results: Flow<String>` (navegação), `threshold: MutableStateFlow<Float>`; encapsula acesso ao `Repository` e `Catalog`
- **StateFlow / Flow** — Estado reativo unidirecional; `UiState(busy, error)` controla diálogos globais de carregamento e erro
- **Eventos/navegação** — `Channel<String>` no `ViewModel` emite ID da análise concluída; `LaunchedEffect` no `LeafCareApp` navega para `result/{id}`
- **UI não acessa diretamente detalhes internos do Repository após hardening** — `ViewModel` expõe apenas `getPhoto(name)`, `getModelError()`, `observeAnalysis(id)`, `getDiseaseInfo(classId)`; DAO, Room, Classifier, Mutex ficam privados no `Repository`

> **Terminologia**: "MVVM pragmático" ou "MVVM-style". Não é Clean Architecture (não há Use Cases, Entities de domínio puro, Data Sources abstratas, Boundaries). A injeção é manual no `Application`; o `ViewModel` conhece o `Repository` concreto.

---

## 4. ViewModel

`LeafCareViewModel` (`android-app/app/src/main/java/br/com/leafcare/ui/LeafCareViewModel.kt`) — responsabilidades reais:

| Responsabilidade | Implementação |
|---|---|
| **Expor estado reativo** | `analyses` (Flow do DAO via `stateIn`), `ui` (busy/error), `results` (Channel→Flow para navegação), `threshold` (MutableStateFlow) |
| **Intermediar UI ↔ Repository** | `analyze(uri)`, `delete(id, onDeleted)`, `getPhoto(name)`, `getModelError()`, `observeAnalysis(id)`, `getDiseaseInfo(classId)` |
| **Análise** | Valida `busy`; define `busy=true`; lança coroutine em `viewModelScope`; chama `repository.analyze(uri)`; emite ID no `Channel`; trata exceções; `finally` limpa `busy` e arquivo temporário |
| **Exclusão** | `repository.delete(id)` em `viewModelScope`; callback `onDeleted()` para fechar tela de resultado |
| **Consulta** | Delega ao `Repository`/`Catalog`; não expõe DAO nem Entity bruta |
| **Navegação** | Não navega diretamente; emite no `Channel`; `LeafCareApp` escuta e navega |
| **Erros** | `error(message)` / `clearError()` atualizam `UiState`; `MainActivity` renderiza `AlertDialog` global |

**Por que a UI não deve manipular DAO/Room/Classifier diretamente:**

- Acoplamento a detalhes de implementação (SQL, schemas, threads, buffers)
- Quebra de serialização do `Interpreter` (Mutex no Repository)
- Risco de leaks de `Bitmap` / `Interpreter` / cursores
- Dificulta testes (mock de `Repository` é simples; mock de DAO + Classifier + Mutex + buffers não é)
- `ViewModel` é o **único** ponto de orquestração de ciclo de vida (viewModelScope, NonCancellable, limpeza de temporários)

---

## 5. Repository

`AnalysisRepository` (`android-app/app/src/main/java/br/com/leafcare/data/AnalysisRepository.kt`) — coordenador central da análise:

- **Coordenação de análise** — `suspend fun analyze(uri): String` retorna ID da análise criada
- **Armazenamento da foto** — Copia stream da `Uri` (file ou content) para `filesDir/photos/{id}.img`; valida 30 MB durante cópia; `committed` flag garante limpeza se falhar antes do insert
- **Chamada ao classifier** — `ImageDecoder.decode(file)` → `PixelPreprocessor.toRgb()` → `classifier.classify(bitmap)` → `bitmap.recycle()` em `finally`
- **Persistência** — Monta `AnalysisEntity` com top-3 JSON, threshold, inferenceMs, modelHash; `dao.insert(entity)` em `withContext(NonCancellable)` para não perder gravação se usuário sair da tela
- **Exclusão** — `suspend fun delete(id)` apaga linha no Room **e** arquivo de foto atomicamente (mesmo Mutex)
- **NonCancellable onde aplicável** — Insert e delete usam `withContext(NonCancellable)` para garantir conclusão mesmo se coroutine pai for cancelada
- **Mutex** — `private val mutex = Mutex()`; `analyze()` e `delete()` executam `mutex.withLock { ... }`; serializa inferências (evita `Interpreter.run()` concorrente na mesma instância) e exclusões
- **Threshold** — Lê de `SharedPreferences` com fallback para `classifier.defaultThreshold` (vem do `model_metadata.json`); `setThreshold(value)` valida 0.5–0.95

> **Nota sobre thread-safety**: O Repository serializa as operações de análise com `Mutex`, evitando inferências concorrentes sobre a mesma instância reutilizada do `Interpreter`. A API `Interpreter` do LiteRT não garante segurança para `run()` concorrente; o Mutex é a estratégia adotada (alternativa: pool de interpreters, descartada por complexidade vs uso esporádico).

---

## 6. Persistência

- **Room** — `AppDatabase` versão **1**, `exportSchema = true` (schemas em `android-app/app/schemas/`)
- **AnalysisEntity** — Tabela `analyses`; PK `id` (UUID string); colunas: `photoName`, `createdAt`, `classId`, `displayName`, `scientificName`, `confidence`, `top3Json`, `inconclusive`, `threshold`, `inferenceMs`, `modelSha256`
- **AnalysisDao** — `observeAll(): Flow<List<AnalysisEntity>>` (ordenado por `createdAt DESC`); `observe(id)`; `get(id)` suspenso; `insert` (ABORT); `delete(id)`
- **Flow reativo** — `dao.observeAll()` emite lista completa a cada mudança; `ViewModel` usa `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())`
- **Arquivo da foto separado do banco** — Room guarda **apenas nome relativo** (`photoName`); arquivo físico em `filesDir/photos/{photoName}`; `Repository.photo(name)` valida `File(name).name == name` (evita path traversal)
- **Banco atualmente version = 1** — **Não há migrations implementadas**; `exportSchema = true` gera JSON para referência futura
- **Futuras alterações de schema exigirão migrations explícitas** — Room não usa `fallbackToDestructiveMigration`; qualquer mudança (nova coluna, índice, tabela) requer `Migration` declarada no builder

---

## 7. Machine Learning no Android

`LeafClassifier` (`android-app/app/src/main/java/br/com/leafcare/ml/LeafClassifier.kt`) — encapsula carregamento, validação e inferência:

- **Carrega assets na inicialização** (uma única vez por instância/processo):
  - `model_metadata.json` — contrato, seed, versões, hashes, `confidence_threshold`, `schema_version`, `resize`, `normalization`
  - `classes.json` — array ordenado de 16 identificadores exatos do dataset (ex.: `frog_eye`, `wildfire`, `anthracnose`...)
  - `leafcare.tflite` — modelo float32 MobileNetV3Small (~3.8 MB)
- **Validações rigorosas** (falham rápido se assets corrompidos/incompatíveis):
  - `metadata.status == "trained"` e existência do `.tflite`
  - `classes` ≥ 3, distintos, ordem idêntica ao `metadata.classes`
  - `classes_sha256` = SHA-256 de `"\n".join(classes)` UTF-8 sem newline final
  - `schema_version == 1`, `resize == "center_crop_bilinear_integer_v1"`, `normalization == "embedded_mobilenetv3_rescaling"`
  - `model_sha256` = SHA-256 do arquivo `.tflite`
  - Input shape `[1,224,224,3]`, dtype `FLOAT32`
  - Output shape `[1, classes.size]`, dtype `FLOAT32`
- **Cria Interpreter uma vez** — `Interpreter(modelBuffer, Options().setNumThreads(2))`
- **Reutiliza buffers** — `inputBuffer` (ByteBuffer direto 1×224×224×3×4 bytes) e `outputArray` (FloatArray[classes.size]) pré-alocados
- **defaultThreshold** — Lido do `metadata.confidence_threshold` (0.70 baseline)
- **Erros de disponibilidade ficam registrados** — `availabilityError: String?` exposto para UI (`ViewModel.getModelError()`); se não nulo, `classify()` lança `IllegalStateException`
- **Não há lifecycle management adicional** — `Interpreter` vive enquanto o processo vive; `close()` existe mas não é chamado automaticamente (Android mata processo); não há `onTrimMemory` ou reinicialização

> **Não invente lifecycle management que não existe**. O `LeafClassifier` é instanciado uma vez no `Application` (lazy) e vive até o fim do processo.

---

## 8. Contrato de pré-processamento

Fonte principal: `docs/INFERENCE_CONTRACT.md` (versão 1). O contrato **deve ser idêntico** entre Python (`leafcare/preprocessing.py`) e Kotlin (`ml/PixelPreprocessor.kt`).

### Especificação

| Etapa | Detalhe |
|---|---|
| **Formatos aceitos** | JPEG, PNG, BMP, WebP estáticos (single-frame) |
| **Correção EXIF** | `ExifInterface` / `ImageOps.exif_transpose`; orientação 1–8 inclusive espelhamentos (2, 4, 5, 7) |
| **Alpha sobre branco** | Fórmula: `(canal*a + 255*(255-a) + 127)//255` (inteiro, half-up) |
| **Center crop** | Lado `min(width, height)`; offsets inteiros `(width-side)//2`, `(height-side)//2` |
| **Resize exato** | 224×224; bilinear **half-pixel** com coordenadas `(2*x+1)*side - size` / `(2*size)`; borda replicada (`coerceIn` / `clip`); arredondamento half-up (`+ divisor/2`) |
| **Tensor final** | `float32 NHWC [1,224,224,3]` faixa **0–255** |
| **NÃO dividir por 255** | A camada `Rescaling(1/255)` da MobileNetV3 está **incorporada no modelo** via `include_preprocessing=True` no `build_model()` |
| **Golden fixture / paridade** | Teste golden compara SHA-256 do RGB final em ambas linguagens; exportação compara Keras vs TFLite em 20 imagens de validação (erro absoluto ≤ 0.0001, top-1 agreement 100%) |

### Por que esse contrato é crítico

Qualquer divergência entre treino e app **pode mudar a previsão**. O modelo aprendeu features com esse pré-processamento exato (incluindo o resize bilinear inteiro half-pixel e a faixa 0–255). Uma normalização extra no app (dividir por 255) aplicaria `Rescaling` duas vezes, alterando drasticamente a distribuição de entrada. Um resize diferente (Pillow `LANCZOS`, Bitmap `FILTER`, TF `bilinear` padrão) muda valores de pixel nas bordas do crop. O contrato garante **equivalência bit-a-bit** do tensor de entrada.

---

## 9. Política de resultado

`PredictionPolicy` (`android-app/app/src/main/java/br/com/leafcare/ml/Prediction.kt`):

- **Softmax** — Já vem do modelo (camada `Dense(16, softmax)`)
- **Top-3** — Índices ordenados por confiança decrescente; empate = menor índice primeiro (`sortedWith(compareByDescending { scores[it] }.thenBy { it })`); retorna 3 `Prediction(classId, confidence)` com **scores originais** (não renormalizados)
- **Ordem estável** — `sort` estável + tie-breaker por índice garante determinismo
- **Threshold** — `inconclusive(confidence, threshold) = confidence < threshold`; comparado como `float32` (inclusive igualdade)
- **Inconclusivo** — Se `max(prob) < threshold`, `AnalysisEntity.inconclusive = true`; UI exibe aviso "Inconclusivo — confiança abaixo do limiar"
- **Score de confiança não é probabilidade clínica/agronômica comprovada** — Softmax não calibrada; imagens fora do domínio (não-folhas, outras culturas) podem receber confiança alta

### Baseline

- **Threshold 0.70** — Valor inicial definido em `config.yaml` → `model_metadata.json` → `LeafClassifier.defaultThreshold`
- **É configuração inicial** — Não é calibração independente de campo; não há ECE, Brier score, temperature scaling no baseline
- **Produtor não ajusta no fluxo normal** — Slider 50–95% removido do Histórico (decisão arquitetural #16 em `TECHNICAL_DECISIONS.md`); threshold vem do bundle de modelo

---

## 10. Pipeline de Machine Learning

### Diagrama ASCII

```
Dataset bruto (TLA TV3, 696 imgs, 16 classes)
   ↓
import_tla.py  (importa, auditoria: 0 inválidos, 0 duplicatas exatas)
   ↓
prepare_dataset.py  (group-aware: classe + IMG_ID + dHash ≤ 4; seed 42)
   ↓
manifest / groups  (489 treino / 104 validação / 103 teste; min 8 grupos/classe)
   ↓
train.py  (MobileNetV3Small ImageNet, include_preprocessing=True)
   ├─ Etapa 1: Backbone congelado, só cabeça (GlobalAvgPool + Dropout 0.25 + Dense 16 softmax)
   │   LR 1e-3, Adam, class_weights se razão ≥ 1.5, EarlyStopping(patience=6), ReduceLROnPlateau(factor=0.5, patience=3)
   ├─ Etapa 2: Fine-tuning últimas 30 camadas, BatchNorm NÃO treinável (training=False)
   │   LR 1e-5, Adam, mesmos callbacks
   └─ Seleção por validation loss (menor entre frozen_best e finetune_best)
   ↓
model.keras  (pesos escolhidos)
   ↓
evaluate.py  (conjunto de teste reservado, 103 imagens)
   ↓
métricas  (accuracy, macro-F1, top-3, coverage, accepted_accuracy, classification_report, confusion_matrix)
   ↓
export_tflite.py  (float32, TFLITE_BUILTINS, include_preprocessing mantido)
   ├─ Paridade Keras↔TFLite em 20 imgs validação: max_abs_error ≤ 0.0001, top-1 agreement 100%
   ├─ Gera leafcare.tflite, classes.json, model_metadata.json, diseases.json
   └─ Copia 4 assets para android-app/app/src/main/assets/
   ↓
validate_bundle.py  (valida hashes, ordem, shapes, dtype, catálogo)
   ↓
Android assets  (prontos para build)
```

### Detalhes do treinamento (baseline integrado)

- **Seed**: 42 (reprodutibilidade total: `tf.keras.utils.set_random_seed`, `enable_op_determinism`, threads=2)
- **Split group-aware**: 489 / 104 / 103; grupos nunca divididos entre splits
- **Augmentation apenas no treino**: `RandomFlip`, `RandomRotation(0.08)`, `RandomZoom(0.10)`, `RandomContrast(0.10)` — validação e teste **sem** augmentation
- **Transfer learning**: MobileNetV3Small ImageNet (`include_preprocessing=True`, `pooling="avg"`)
- **Backbone congelado** na etapa 1 (só cabeça treinável)
- **Fine-tuning últimas 30 camadas** na etapa 2; `BatchNormalization` mantido em `training=False` (não atualiza estatísticas)
- **Otimizador**: `tf.keras.optimizers.Adam(learning_rate)` — **NÃO AdamW**
- **LR**: 1e-3 (frozen) / 1e-5 (finetune)
- **EarlyStopping** monitora `val_loss`, `patience=6`, `restore_best_weights=True`
- **ReduceLROnPlateau** monitora `val_loss`, `factor=0.5`, `patience=3`, `min_lr=1e-7`
- **Class weights** quando razão maior/menor classe ≥ 1.5 (config `class_weight_ratio_threshold`); pesos inversamente proporcionais à frequência de **treino**
- **Seleção por validation loss** — Melhor fase (frozen vs finetune) escolhida pelo menor `val_loss`; **teste reservado** apenas para avaliação final

---

## 11. Baseline vs ensemble

### Arquiteturalmente

| Aspecto | **Baseline integrado (atual)** | **Ensemble experimental (NÃO no app)** |
|---|---|---|
| Modelo | MobileNetV3Small único | 3 modelos: MobileNetV3Small (baseline) + MobileNetV3Small/RMSprop + MobileNetV3Large |
| Interpreters | 1 | 3 |
| Inferências por imagem | 1 | 3 (média de probabilidades) |
| Calibração | Nenhuma (softmax bruta) | Temperature scaling `T = 0.8421423931819505` |
| Threshold | 0.70 (inicial, não calibrado) | 0.74 (escolhido em validação para ≥90% accepted accuracy) |
| Tamanho total | ~3.8 MB | ~19.5 MB (3 × TFLite float32) |
| Métricas teste | Top-1 77.67%, Macro-F1 0.7157, Top-3 97.09% | Top-1 80.58%, Macro-F1 0.7659, Top-3 99.03% |
| Status no Android | ✅ Integrado; unit tests, assets, build aprovados; validação física pendente | ❌ Não integrado — pendente validação móvel |

### Por que o ensemble NÃO está no app atual

- **Custo computacional**: 3× inferência → latência ~3× (CPU), aquecimento, bateria
- **RAM**: 3 modelos carregados simultaneamente ou carregamento sequencial com overhead
- **APK**: +15.7 MB (float32); ultrapassa limites confortáveis para distribuição
- **Latência**: Não medida em device real (entry-level vs mid-range)
- **Validação física pendente**: Paridade Keras/TFLite aprovada, mas **inferência real em Android não executada**
- **Estabilidade**: Risco de OOM em dispositivos com pouca memória; `Interpreter` pool adicionaria complexidade

> **Nunca diga que o Android atual possui 80.58%**. O app roda o baseline (77.67%). O ensemble é recomendação para próxima versão **após** validação em device real.

---

## 12. Offline e privacidade

Fatos verificados no código:

- **Manifest sem `INTERNET`** — `AndroidManifest.xml` não declara a permissão; `usesCleartextTraffic="false"`
- **Câmera** — Permissão `CAMERA` apenas; `android.hardware.camera.any required="false"`
- **Galeria** — Seletor de documentos do sistema (`ACTION_OPEN_DOCUMENT` / `ActivityResultContracts.OpenDocument`); sem acesso amplo a armazenamento (`READ_EXTERNAL_STORAGE` não declarada)
- **Room local** — Banco SQLite em `databases/leafcare.db`; sem sincronização
- **Fotos privadas** — `filesDir/photos/` (acesso só pelo app; `allowBackup="false"` exclui do backup do Google)
- **Backup automático desativado** — `android:allowBackup="false"` no `<application>`
- **Sem login, analytics, telemetria, Firebase, Supabase** — Nenhuma dependência de rede no `build.gradle.kts`; `LeafCareApplication` não inicializa clientes HTTP
- **Desinstalar/limpar dados remove todo o histórico** — Comportamento padrão do Android para armazenamento privado

> **Não diga que algo foi testado fisicamente se não foi**. Validação manual de câmera/galeria/persistência em dispositivo físico **pendente** (ver `docs/MANUAL_TESTS.md`).

---

## 13. Validações arquiteturais

Somente validações **realmente executadas/documentadas**:

| Validação | Comando | Status |
|---|---|---|
| Testes Python (28) | `python -m pytest -q` | **PASS** (14.07 s) |
| Validação bundle ML | `python validate_bundle.py --require-model` | **PASS** (`model_bundle_valid`, 16 classes, hashes e ordem consistentes) |
| Android unit tests | `./gradlew testDebugUnitTest` | **PASS** (8 testes: 4 política/pré-processamento + 4 interface Robolectric) |
| Verificação assets ML | `./gradlew verifyModelAssets` | **PASS** |
| Build debug | `./gradlew assembleDebug` | **PASS** (APK gerado) |

**Total**: 36 testes aprovados (28 Python + 8 Android unit).

### Explícito: NÃO executados

- `connectedDebugAndroidTest` — Testes instrumentados em emulador/dispositivo
- Teste em dispositivo físico ou emulador Android
- Câmera real, permissões runtime, galeria do sistema, persistência entre processos, desempenho no celular
- Validação agronômica de campo (Sul do Brasil)

> Robolectric **não substitui** testes instrumentados. O APK de testes instrumentados desta versão não foi executado.

---

## 14. Decisões e trade-offs

Resumo arquitetural. Detalhes completos (20 decisões com justificativa, alternativas, trade-offs, localização no código) em:

📖 **[docs/TECHNICAL_DECISIONS.md](TECHNICAL_DECISIONS.md)**

Principais decisões registradas lá:

1. Kotlin nativo + Jetpack Compose
2. CameraX para captura
3. Room + SQLite + Flow
4. Ausência de backend / `INTERNET`
5. MobileNetV3Small + Transfer Learning (duas etapas)
6. Two-stage training: freeze → fine-tune
7. Class weights (razão ≥ 1.5)
8. Entrada 224×224 RGB float32 0–255 (NHWC, Rescaling embarcado)
9. TFLite float32 (sem quantização; quantização dinâmica reprovada paridade)
10. Top-3 + threshold inconclusivo (0.70 baseline)
11. Macro-F1 como métrica principal
12. Group-aware split (seed 42, min 8 grupos/classe)
13. Hashes e contratos imutáveis (model_sha256, classes_sha256, ordem)
14. Ensemble experimental (3 modelos) — **não integrado**
15. Mutex no Repository para serializar inferência
16. Threshold definido pelo modelo (não ajustável no fluxo normal)
17. Reutilização de Interpreter validado
18. Strings e cores centralizadas (resources/design tokens)
19. CI: Python + Android unit tests (sem device, sem treino)
20. Limitações declaradas explicitamente (não escondidas)

---

## 15. Limitações arquiteturais atuais

- **Classificador closed-set** — 16 classes fixas; não detecta "não é folha de fumo" nem doenças fora do conjunto
- **Sem OOD robusto** — Imagens fora de domínio (solo, céu, outras culturas, fotos borradas) podem receber confiança alta
- **Sem quality gate** — Não há detecção de blur, baixa luz, folha distante, fora de enquadramento, múltiplas folhas
- **Sem backend/sync** — Histórico só no dispositivo; desinstalação apaga tudo; exportação manual futura (fase 5 roadmap)
- **Sem propriedade/talhão/planta/sessão** — Agrupamento por dHash/nome é proxy frágil; split group-aware não garante independência de campo
- **Ensemble pendente** — Não validado em Android real; latência, RAM, APK size, bateria, aquecimento desconhecidos
- **Sem device profiling** — Latência, memória, CPU, GPU/NPU não medidos em entry-level/mid-range
- **Conteúdo agronômico ainda sem revisão completa** — `diseases.json` tem `reviewed: false` em todas as 16 classes; nomes, descrições, sintomas, orientações precisam de validação por especialista
- **Dataset pequeno** — 696 imagens; classes raras (anthracnose, TSWV, black_shank, genetic_abnormality) com 1–2 amostras no teste
- **Threshold baseline não calibrado** — 0.70 é valor inicial; ensemble usa 0.74 calibrado apenas em validação (não em campo)
- **Validação física pendente** — Câmera real, galeria, persistência entre reinicializações, desempenho térmico não executados

---

*Documento gerado a partir do código real (branch `docs/overhaul`, commit `1eb9139`). Não contém componentes inventados, métricas não validadas ou afirmações de "produção pronta" sem evidência.*
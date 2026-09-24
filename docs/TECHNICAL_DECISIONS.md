# Decisões Técnicas — LeafCare

Este documento registra as principais decisões de arquitetura, ML e engenharia, com justificativa, alternativas consideradas e trade-offs. Não é material de marketing; serve para rastreabilidade e revisão por pares.

---

## 1. Plataforma Android: Kotlin nativo + Jetpack Compose

**Decisão:** Desenvolver o app em Kotlin com Jetpack Compose (UI declarativa), sem XML/Views legados.

**Motivo:**
- Kotlin é a linguagem oficial Android; Compose reduz boilerplate e melhora manutenibilidade.
- Compose integra-se nativamente a coroutines/Flow para assincronismo (câmera, inferência, Room).
- Evita fragmentação entre ViewBinding, DataBinding e XML.

**Alternativas:**
- Java + Views/XML: mais verboso, menos suporte a estado reativo.
- Flutter/React Native: adicionaria camada extra, JNI/bridge, e dificultaria integração direta com LiteRT/Room/CameraX.

**Trade-off:** Curva de aprendizado inicial maior; ecossistema Compose ainda evolui (APIs experimentais).

**Onde aparece no código:**
- `android-app/app/src/main/java/br/com/leafcare/ui/*.kt`
- `android-app/app/build.gradle.kts` (Compose BOM, Material3, Navigation Compose)

---

## 2. CameraX para captura

**Decisão:** Usar CameraX (camera-camera2, camera-view, camera-lifecycle) em vez de Camera2 API direta ou intent de câmera.

**Motivo:**
- Abstração de ciclo de vida, rotação, preview, captura de alta resolução.
- Suporte a `ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY`.
- Integração nativa com `PreviewView` no Compose via `AndroidView`.

**Alternativas:**
- Intent `ACTION_IMAGE_CAPTURE`: menos controle, depende de app de câmera terceirizado.
- Camera2 direta: boilerplate significativo para casos de uso padrão.

**Trade-off:** Dependência de biblioteca Google; versão fixada (1.4.1) para estabilidade.

**Onde aparece no código:**
- `CameraScreen.kt` (PreviewView, ImageCapture, ProcessCameraProvider)

---

## 3. Room + SQLite para persistência local

**Decisão:** Room como camada ORM sobre SQLite, com DAOs e Flow reativo.

**Motivo:**
- Compile-time verification de queries.
- `Flow` integra-se a ViewModel/Compose sem LiveData.
- Migrações explícitas (`exportSchema = true`), sem destruição automática.

**Alternativas:**
- SharedPreferences/DataStore: inadequado para dados relacionais e blobs (fotos).
- Realm/ObjectBox: dependência extra, menos padrão no Android moderno.

**Trade-off:** Boilerplate de Entity/DAO/Database; schemas versionados em `schemas/`.

**Onde aparece no código:**
- `AppDatabase.kt`, `AnalysisEntity`, `AnalysisDao`
- `AnalysisRepository.kt` (Mutex serializa escrita/exclusão)

---

## 4. Ausência de backend / INTERNET permission

**Decisão:** App 100% offline. Manifesto não declara `INTERNET`. Sem Firebase, Supabase, analytics, telemetria, login, sincronização.

**Motivo:**
- Requisito de uso em campo sem conectividade.
- Privacidade: dados do produtor não saem do dispositivo.
- Simplicidade operacional: zero infraestrutura, zero custo de servidor.

**Alternativas:**
- Backend opcional para backup/sync: adicionaria complexidade, dependência de rede, LGPD.

**Trade-off:** Sem backup automático; desinstalação apaga histórico. Exportação manual futura (fase 5 do roadmap).

**Onde aparece no código:**
- `AndroidManifest.xml` (sem `<uses-permission android:name="android.permission.INTERNET"/>`)
- `LeafCareApplication.kt` (nenhum cliente HTTP)

---

## 5. Arquitetura do modelo: MobileNetV3Small + Transfer Learning

**Decisão:** MobileNetV3Small (ImageNet) com cabeça nova (pooling global + dropout 0.25 + Dense 16 softmax). Fine-tuning das últimas 30 camadas, BatchNorm congelado.

**Motivo:**
- Arquitetura desenhada para mobile: ~0.95M parâmetros, ~3.8 MB TFLite float32.
- `include_preprocessing=True` incorpora Rescaling no grafo, eliminando normalização divergente no app.
- Transfer learning essencial com 696 imagens; treino do zero inviável.

**Alternativas:**
- MobileNetV3Large: 3x parâmetros, 12 MB TFLite, ganho marginal no benchmark.
- EfficientNetV2B0/NASNetMobile: maiores, mais lentos, pior macro-F1 na validação.
- Treino do zero: overfitting garantido.

**Trade-off:** Capacidade limitada para classes muito raras (anthracnose, TSWV: 1 img teste cada).

**Onde aparece no código:**
- `machine-learning/leafcare/training.py` (build, compile_model, two-stage fit)
- `machine-learning/config.yaml` (fine_tune_last_layers: 30, dropout: 0.25)
- `docs/ARCHITECTURE.md` seção Treinamento

---

## 6. Two-stage training: freeze → fine-tune

**Decisão:** Etapa 1: backbone congelado, só cabeça (12 épocas benchmark, 25 baseline). Etapa 2: últimas N camadas liberadas, BatchNorm em `training=False`, LR 1e-5.

**Motivo:**
- Congelar backbone preserva features ImageNet; cabeça adapta ao domínio.
- Fine-tuning com LR baixa especializa sem destruir representações pré-treinadas.
- BatchNorm congelado evita shift de estatísticas com batch pequeno (16).

**Alternativas:**
- Treino end-to-end com LR única: instável, destrói features genéricas.
- Congelar tudo: subutiliza capacidade do backbone.

**Trade-off:** Dois checkpoints, EarlyStopping/ReduceLROnPlateau por fase; mais complexo que treino único.

**Onde aparece no código:**
- `training.py` linhas 176-190 (frozen → finetune logic)
- `benchmark.py` linhas 76-80

---

## 7. Class weights (pesos de classe inversamente proporcionais à frequência de treino)

**Decisão:** Aplicar `class_weight` no `fit()` quando razão maior/menor ≥ 1.5 (config `class_weight_ratio_threshold`). Uma configuração sem pesos (`m3s_no_weights`) foi incluída como controle.

**Motivo:**
- Dataset desbalanceado: wildfire 89 vs anthracnose 8 (treino).
- Macro-F1 é métrica principal; pesos melhoram equilíbrio entre classes.
- Config sem pesos atingiu maior accuracy bruta (86.54%) mas macro-F1 inferior (0.7671 vs 0.7785 melhor).

**Alternativas:**
- Oversampling/undersampling: altera distribuição efetiva, pode introduzir viés.
- Focal loss: não testado neste ciclo; adicionaria hiperparâmetro.

**Trade-off:** Pesos aumentam loss de classes raras; podem reduzir accuracy global.

**Onde aparece no código:**
- `training.py` linhas 155-167 (cálculo class_weight)
- `benchmark.py` linha 67

---

## 8. Entrada 224×224 RGB float32 0–255 (NHWC)

**Decisão:** Contrato de entrada fixo: center crop → resize bilinear half-pixel integer → float32 [1,224,224,3] faixa 0–255. **Não** dividir por 255 nem normalizar para −1…1 no app. A camada Rescaling da MobileNetV3 está incorporada no modelo (`include_preprocessing=True`).

**Motivo:**
- Mesmo tensor em Python (Pillow) e Kotlin (Bitmap) → paridade numérica.
- Evita dupla normalização (app + modelo) ou canais invertidos.
- Teste golden (SHA-256 do RGB final) garante equivalência bit-a-bit.

**Alternativas:**
- Normalizar no app (0–1 ou −1–1): exige remover `include_preprocessing` do modelo, quebrando portabilidade.
- 256×256 ou 320×320: maior custo computacional; lesões pequenas ainda perdem detalhe no center crop.

**Trade-off:** Center crop pode cortar sintomas periféricos; ajuda orienta centralizar área afetada. Segmentação de folha não implementada.

**Onde aparece no código:**
- `ml/PixelPreprocessor.kt` (bilinear integer arithmetic)
- `machine-learning/leafcare/preprocessing.py` (resize_rgb idêntico)
- `docs/INFERENCE_CONTRACT.md` itens 4–6, 13–16

---

## 9. TFLite / LiteRT 1.4.0 float32 (sem quantização)

**Decisão:** Exportar modelo float32 com operadores nativos (`TFLITE_BUILTINS`). Quantização dinâmica rejeitada: top-1 agreement caiu para 92.23%/87.38%/95.15%.

**Motivo:**
- Paridade Keras/TFLite: erro absoluto máx < 0.00001, top-1 agreement 100% em 20 imagens validação.
- Quantização dinâmica troca classes previstas em quantidade relevante.
- Quantização inteira representativa ou QAT ficam para experimento futuro validado.

**Alternativas:**
- Quantização dinâmica (post-training): menor arquivo, mas paridade reprovada.
- Quantização full integer: requer representative dataset e validação rigorosa.

**Trade-off:** Modelo ~3.8 MB (vs ~1-2 MB quantizado). Latência aceitável em CPU; GPU/NPU não habilitado.

**Onde aparece no código:**
- `export_tflite.py` função `convert_model` (linha 12-19)
- `benchmark.py` linha 34-39 (conversão float32 + dynamic)
- `docs/BENCHMARK_MODELOS.md` seção Exportação

---

## 10. Top-3 + confidence policy (inconclusivo se max < threshold)

**Decisão:** Mostrar top-3 classes com scores originais (sem renormalizar). Resultado inconclusivo se `max(prob) < threshold`. Threshold inicial 0.70 (não calibrado), definido em `model_metadata.json`.

**Motivo:**
- Produtor vê alternativas; não força diagnóstico único.
- Inconclusivo evita falso positivo de alta confiança em classe errada.
- Threshold no metadado (não hardcoded) permite atualização via novo bundle de assets.

**Alternativas:**
- Top-1 apenas: perde informação de incerteza.
- Renormalizar top-3: mascara confiança real do modelo.
- Threshold ajustável pelo usuário no fluxo normal: removido na fase 5 deste hardening (ver abaixo).

**Trade-off:** Threshold 0.70 não calibrado; cobertura ~76% no teste. Ensemble usa 0.74 calibrado.

**Onde aparece no código:**
- `ml/PredictionPolicy.kt` (top3, inconclusive)
- `machine-learning/leafcare/inference.py` (rank)
- `AnalysisRepository.kt` linha 60 (lê threshold do SharedPreferences / metadata)

---

## 11. Macro-F1 como métrica principal (não accuracy)

**Decisão:** Seleção de modelos/ensemble usa macro-F1 de validação como critério primário.

**Motivo:**
- Dataset desbalanceado: accuracy mascara desempenho ruim em classes raras.
- Macro-F1 dá peso igual a cada classe (média não ponderada).
- Top-3 accuracy secundário (interface mostra 3 hipóteses).

**Alternativas:**
- Accuracy ponderada: favorece classes maiores.
- Per-class F1 com pesos: mais complexo de comunicar.

**Trade-off:** Macro-F1 mais volátil com poucas amostras por classe (ex: anthracnose 1 img teste → F1 0 ou 1).

**Onde aparece no código:**
- `benchmark.py` linha 56-59 (score function)
- `finalize_benchmark.py` linha 11-13 (metrics), linha 61 (ranking key)

---

## 12. Group-aware split (seed 42, mínimo 8 grupos/classe)

**Decisão:** Divisão estratificada por grupos (classe + identificador IMG + dHash ≤ 4). Grupos nunca divididos entre treino/val/teste.

**Motivo:**
- Evita vazamento: versões da mesma foto (rotação, crop, compressão) em subconjuntos diferentes.
- Sem IDs de planta/propriedade, grupo por nome/hash/dHash é melhor proxy disponível.
- Seed fixa garante reprodutibilidade.

**Alternativas:**
- Split aleatório por arquivo: alto risco de vazamento com augmentações/derivações.
- Split por propriedade/planta: ideal, mas metadados não existem no dataset atual.

**Trade-off:** Mínimo 8 grupos/classe é operacional, não garantia estatística. Classes raras têm 1 grupo no teste.

**Onde aparece no código:**
- `machine-learning/leafcare/dataset.py` (build_groups, stratified_group_split, load_manifest leak check)
- `docs/DATASET.md`

---

## 13. Hashes e contratos imutáveis (model_sha256, classes_sha256, classes order)

**Decisão:** `model_metadata.json` contém SHA-256 do `.tflite`, SHA-256 de `"\n".join(classes)` (sem newline final), e array `classes` na ordem exata de saída. App valida tudo na inicialização do classifier.

**Motivo:**
- Detecta corrupção, mismatch de versão, reordenação acidental de classes.
- `verifyModelAssets` task bloqueia release sem assets consistentes.
- Paridade Python/Android verificada antes de copiar assets.

**Alternativas:**
- Versionamento semântico no nome do arquivo: não garante integridade de conteúdo.
- Sem validação: risco silencioso de inferência errada.

**Trade-off:** Qualquer mudança no modelo exige regenerar os 4 assets juntos; não editar `classes.json` manualmente.

**Onde aparece no código:**
- `LeafClassifier.kt` linhas 17-38 (validação completa)
- `export_tflite.py` linhas 58-74 (gera metadata + copia 4 arquivos)
- `build.gradle.kts` task `verifyModelAssets` (linhas 72-86)

---

## 14. Ensemble experimental (3 modelos) — ainda NÃO integrado no Android

**Decisão:** Ensemble por média de probabilidades: MobileNetV3Small (baseline) + MobileNetV3Small/RMSprop + MobileNetV3Large. Temperatura T=0.8421423931819505, threshold 0.74. Exportados 3 TFLites float32 (~19.5 MB total). App mantém baseline único até validação móvel.

**Motivo:**
- Ensemble melhora macro-F1 teste 0.7157 → 0.7659 (+0.0502), top-1 77.67% → 80.58%.
- Modelos erram imagens diferentes; média reduz variância.
- Temperatura otimizada em validação (NLL); threshold escolhido para ≥90% accepted accuracy.

**Alternativas:**
- Integrar ensemble já: risco de regressão de latência/memória/APK sem medição em device.
- Destilação para MobileNetV3Small única: próxima etapa se ensemble validar.

**Trade-off:** 3x inferência, 3x memória, APK ~19.5 MB vs 3.8 MB. Não testado em Android real.

**Onde aparece no código:**
- `finalize_benchmark.py` linhas 58-71 (seleção, calibração, exportação)
- `docs/ENSEMBLE_ANDROID_INTEGRATION.md` (contrato de integração)
- `docs/RELATORIO_COMPARATIVO_MODELOS_LEAFCARE.md` seções 8, 10, 11

---

## 15. Mutex no Repository para serializar inferência

**Decisão:** `AnalysisRepository` usa `Mutex` para garantir uma análise por vez (evita concorrência no Interpreter).

**Motivo:**
- `Interpreter` não é thread-safe para `run()` concorrente.
- Câmera e galeria podem disparar análises simultâneas.
- Mutex também serializa exclusão (apaga foto + row atomicamente).

**Alternativas:**
- Pool de Interpreters: complexidade extra; ganho marginal (uso esporádico pelo produtor).
- `synchronized` / `ReentrantLock`: Mutex coroutines é mais idiomático.

**Trade-off:** Latência adicional se usuário disparar múltiplas análises rápidas (cenário improvável).

**Onde aparece no código:**
- `AnalysisRepository.kt` linha 21 (`private val mutex = Mutex()`), linha 36 (`mutex.withLock`)

---

## 16. Threshold definido pelo modelo (model_metadata.json) — não ajustável pelo produtor no fluxo normal

**Decisão:** Removido controle de threshold (50%–95%) da tela Histórico. Threshold vem de `model_metadata.json` (`confidence_threshold`). Cada análise persiste o threshold usado.

**Motivo:**
- Baseline atual não possui confidence calibration de campo; 0.70 é valor inicial arbitrário.
- Permitir ajuste arbitrário pelo produtor cria falsa sensação de controle e resultados inconsistentes.
- Ensemble futuro trará threshold calibrado (0.74) validado em validação.

**Alternativas:**
- Manter slider para experimentação: mantido apenas se documentado explicitamente como "desenvolvimento".
- Hardcode no Kotlin: impede atualização via novo bundle de assets.

**Trade-off:** Produtor não pode "forçar" resultado conclusivo baixando threshold. Isso é intencional.

**Onde aparece no código:**
- `HistoryScreen.kt` linhas 138-149 (AlertDialog removido/modificado)
- `AnalysisRepository.kt` linha 30 (`threshold()` lê de SharedPreferences com fallback para `classifier.defaultThreshold`)
- `LeafClassifier.kt` linha 18 (`defaultThreshold` lê de metadata)

---

## 17. Reutilização de Interpreter validado (não recriar por análise)

**Decisão:** `LeafClassifier` inicializa e valida modelo/metadata/classes/hashes/shapes/dtype uma única vez por instância/processo. `Interpreter` reutilizado; `Mutex` no Repository garante acesso exclusivo.

**Motivo:**
- Criação de Interpreter + leitura de assets + validações é custo fixo repetido a cada análise.
- Validações (hashes, shapes, dtype) são idempotentes; falham rápido se assets corrompidos.
- Repository já serializa chamadas via Mutex → Interpreter não chamado concorrentemente.

**Alternativas:**
- Manter recriação por análise: mais simples, mas desperdiça CPU/latência.
- Singleton global: acoplamento forte; dificulta testes.

**Trade-off:** `LeafClassifier` passa a ter estado (Interpreter, classes, metadata cache). Requer cuidado em testes unitários (mock ou instância fresca).

**Onde aparece no código:**
- `LeafClassifier.kt` (refatorado: construtor carrega/valida; classify usa instância cached)
- `AnalysisRepository.kt` (Mutex inalterado)

---

## 18. Strings e cores centralizadas (resources / design tokens)

**Decisão:** Textos estáticos de UI movidos para `res/values/strings.xml`. Cores repetidas consolidadas em `LeafColors` object (design tokens reais), não cada `8.dp`/`14.sp`.

**Motivo:**
- Facilita localização futura (PT-BR já, mas estrutura pronta).
- Cores semânticas (Green, Text, Muted, Border, Pale) = tokens de design; espaçamentos/tamanhos ficam inline (pragmático).

**Alternativas:**
- Tudo em `strings.xml`: verboso para interpolações complexas.
- Tudo em `colors.xml` + `dimens.xml`: overhead para valores usados 1-2 vezes.

**Trade-off:** Diff maior na fase 7; comportamento visual idêntico.

**Onde aparece no código:**
- `LeafCareTheme.kt` (LeafColors object)
- `strings.xml` (novo / expandido)
- Composables referenciam `stringResource(R.string.xxx)`

---

## 19. CI: Python tests + Android unit tests (sem device, sem treino)

**Decisão:** GitHub Actions workflow roda `pytest -q`, `validate_bundle.py --require-model`, `testDebugUnitTest`, `verifyModelAssets`, `assembleDebug`. Sem device farm, sem treino, sem download de dataset.

**Motivo:**
- Valida regressão de contrato, paridade, build a cada PR.
- Rápido (~5-10 min), determinístico, sem segredos.
- Testes instrumentados (`connectedDebugAndroidTest`) exigem device/emulator → executados localmente.

**Alternativas:**
- Firebase Test Lab / Gradle Managed Devices: custo, configuração, flakiness.
- Treino na CI: horas, GPU, dataset grande, não determinístico.

**Trade-off:** Não captura bugs de câmera/galeria/permissão/Room persistência real → cobertos por `MANUAL_TESTS.md`.

**Onde aparece no código:**
- `.github/workflows/ci.yml` (criado na fase 10)

---

## 20. Limitações declaradas explicitamente (não escondidas)

**Decisão:** Toda documentação (README, VALIDATION, DATASET, RELATORIO, ARCHITECTURE) declara limitações conhecidas: dataset pequeno, classes raras, sem teste externo, threshold não calibrado, ensemble não validado em device, sem detecção OOD.

**Motivo:**
- Honestidade acadêmica e técnica.
- Evita overclaiming; reviewers/audience sabem exatamente o que foi validado.
- Base para roadmap realista (fases 3–6).

**Onde aparece no código:**
- `README.md` seção Known limitations
- `docs/DATASET.md` seção Limitações
- `docs/VALIDATION.md` notas finais
- `docs/RELATORIO_COMPARATIVO_MODELOS_LEAFCARE.md` seções 12, 14

---

## Erratas (código atual vs. texto antigo acima)

- **#4 (ausência de backend): SUPERSEDE — o MVP atual tem Supabase** (Auth, Postgres, Storage) com offline-first; ver decisões 21–30.
- **#10/#16 (threshold via SharedPreferences): SUPERSEDE — resíduo removido na Fase 1**; threshold vem sempre de `classifier.defaultThreshold` (`model_metadata.json`).

---

## 21. Supabase como backend (Auth + Postgres + Storage)

**Decisão:** supabase-kt 2.1.0 (gotrue, postgrest, storage) + Ktor OkHttp 2.3.7; sem Firebase, sem backend próprio, sem Realtime no MVP.

**Motivo:** Postgres + Auth + Storage privado + RLS em um serviço gerenciado; SDK Kotlin multiplataforma; realtime desnecessário (sync sob demanda basta).

**Trade-off:** Dependência de provedor externo; publishable key embutida no APK (aceitável: RLS protege tudo; service_role nunca no app).

**Onde aparece no código:** `SupabaseClientHolder.kt` (instala Auth/Postgrest/Storage), `app/build.gradle.kts`, `local.properties` → `BuildConfig` (nunca commitado; build falha com placeholder).

---

## 22. Offline-first com Room como fonte da UI

**Decisão:** Room continua fonte única da interface; Supabase nunca é observado diretamente. Análise nasce local (`PENDING_UPLOAD`), aparece na hora, sincroniza depois.

**Motivo:** Campo sem internet não pode travar; UI idêntica online/offline; falha de rede nunca perde dado local.

**Trade-off:** Estados de sync e lógica de merge moram no app.

**Onde aparece no código:** `AnalysisDao.observeAll()`, `LeafCareViewModel.analyses`, `AnalysisSyncRunner.kt`.

---

## 23. WorkManager para a fila de sync

**Decisão:** `SyncAnalysesWorker` único (`sync-analyses`, `APPEND`): só com `CONNECTED`, backoff exponencial 30s, teto de 5 tentativas, sobrevive a restart; sem auth = nada executa; aborta a passada se a sessão mudar.

**Motivo:** Constraints, persistência e retry prontos; sem pipeline paralela.

**Trade-off:** Sem tempo real; sync acontece sob demanda/boot.

**Onde aparece no código:** `SyncAnalysesWorker.kt`, `LeafCareApplication.scheduleSync()` (+ flush no `onCreate`, `Configuration.Provider` com initializer padrão removido).

---

## 24. UUID compartilhado local/remoto + upsert idempotente

**Decisão:** Mesmo UUID gerado no `analyze()` como PK local e remota; `upsert(..., onConflict = "id")`; retry nunca duplica.

**Motivo:** Idempotência sem servidor de reconciliação; mesma conta em dois aparelhos converge naturalmente.

**Trade-off:** UUIDs precisam ser únicos (v4; colisão impraticável).

**Onde aparece no código:** `AnalysisRepository.analyze()`, `toRemoteJson()`, `PostgrestAnalysisSyncApi.upsertAnalysis()`.

---

## 25. Tombstones em vez de hard delete distribuído

**Decisão:** Exclusão local marca `PENDING_DELETE` + `deletedAt` (some da UI); worker remove foto remota (404 = ok), grava `deleted_at` remoto e só então apaga local + foto. Restore nunca importa tombstone nem o aplica sobre pendente local.

**Motivo:** Exclusão offline precisa sobreviver e propagar sem ressuscitar dados.

**Trade-off:** Linha "morta" ocupa espaço até confirmar; delete físico remoto da linha não é exigido (soft delete é o estado coerente).

**Onde aparece no código:** `AnalysisRepository.delete()`, `AnalysisSyncRunner` (push + restore), testes de tombstone.

---

## 26. Estado de foto separado (`photoSyncStatus` / `REMOTE_ONLY`)

**Decisão:** `PENDING_UPLOAD`/`SYNCED`/`ERROR`/`REMOTE_ONLY` em coluna própria; análise nunca é marcada completa pela foto e vice-versa; importados entram `SYNCED`/`REMOTE_ONLY`; download escreve via tmp + rename.

**Motivo:** Upload e download têm ciclos de vida independentes; `REMOTE_ONLY` diz "existe no servidor, sem cache" sem confundir com pendência de envio.

**Trade-off:** Mais um estado para explicar; download de linha `ERROR` de envio não é tentado (correto: nada remoto para baixar).

**Onde aparece no código:** `SyncState.kt`, migration `MIGRATION_2_3`, `downloadOnce()`.

---

## 27. Restore por merge simples e seguro

**Decisão:** Ausente → insere; mesmo UUID local (pendente/synced/tombstone) → nunca sobrescreve; tombstone remoto → nunca importado, aplicado só sobre linha `SYNCED`; linhas malformadas puladas; sem resolução avançada de conflitos.

**Motivo:** Previsibilidade total; nenhum caminho perde dado local nem ressuscita exclusão.

**Trade-off:** `photo_path` divergente entre aparelhos não é reconciliado nesta unidade (download deriva o path deterministicamente).

**Onde aparece no código:** `AnalysisSyncRunner.restoreOnce()`, `JsonObject.toAnalysisEntity()`.

---

## 28. Wipe controlado na troca de conta (isolamento)

**Decisão:** Room sem coluna de dono: `ensureAccountIsolation()` apaga linhas + fotos só na troca de conta (ou órfãos sem proveniência no primeiro login pós-upgrade); mesmo login e logout preservam tudo.

**Motivo:** Sem isso, worker subiria dados de A como B e o histórico de A ficaria visível para B (bug crítico de privacidade achado na Fase 7).

**Alternativas:** Coluna `user_id` + queries filtradas (200+ linhas, churn em DAO/VM/UI; singleton de repositório complica troca de usuário).

**Trade-off:** Pendentes nunca sincronizados de A se perdem na troca (documentado; remoto restaura o resto).

**Onde aparece no código:** `LeafCareApplication.ensureAccountIsolation()`, `shouldWipeForAccountSwitch()`, `AuthViewModel.onAuthenticated()`, recheck no worker.

---

## 29. Bucket privado + RLS (sem afrouxar)

**Decisão:** `analysis-photos` privado com policies por pasta `{user_id}/`; tabelas com `TO authenticated` + `(select auth.uid())`; triggers `SECURITY DEFINER` com revokes; paths sempre derivados da sessão; sem signed URL no banco; sem `service_role` no app.

**Motivo:** Segurança por padrão em todas as camadas; convenção de path elimina classe inteira de bugs de acesso.

**Trade-off:** Nenhum acesso anônimo/legado possível; debug exige usuário real.

**Onde aparece no código:** `supabase/migrations/*storage*`, `*security_hardening*`, `remotePhotoPath()`, `PostgrestAnalysisSyncApi`.

---

## 30. Sem Realtime e sem ensemble no MVP

**Decisão:** Sem `realtime-kt`; sem ensemble no app (segue MobileNetV3Small único).

**Motivo:** Sync sob demanda cobre o MVP; realtime adiciona conexão permanente/bateria por ganho nulo. Ensemble: +15 MB e latência sem medição em device.

**Trade-off:** Atraso de sincronização até o próximo gatilho; teto de acurácia do baseline.

**Onde aparece no código:** `app/build.gradle.kts` (dependências ausentes de propósito).
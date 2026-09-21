# Guia de Apresentação — LeafCare (PT-BR)

Este guia prepara o autor para explicar o projeto em defesa acadêmica e reunião com a empresa. Foque no fluxo técnico, decisões justificadas e limitações honestas.

---

## 1. Fluxo completo (30 segundos)

> **Foto → Decode → EXIF → Center Crop → Resize 224×224 → Float32 0–255 → LiteRT → Softmax → Top-3 → Threshold → Resultado → Room**

**Passo a passo:**

1. **Foto**: CameraX capture ou seletor de documentos (galeria). Arquivo salvo em cache/privado (≤30 MB, ≤16 MP).
2. **Decode**: `BitmapFactory` com `inPreferredColorSpace=SRGB`. `ExifInterface` corrige orientação (1-8). Transparência composta sobre branco.
3. **Center Crop**: Quadrado central `min(w,h)`. Offsets inteiros `(w-lado)/2`, `(h-lado)/2`.
4. **Resize 224×224**: Bilinear **half-pixel, aritmética inteira** (contrato idêntico Python/Kotlin). Coordenadas `(2x+1)*lado - 224` clampadas. Arredondamento half-up. **Não** usar `Bitmap.createScaledBitmap` nem `Pillow.resize` padrão.
5. **Float32 NHWC [1,224,224,3] faixa 0–255**: **Não dividir por 255**. A camada `Rescaling(1/127.5, offset=-1)` da MobileNetV3 está **incorporada no modelo** (`include_preprocessing=True`).
6. **LiteRT 1.4.0**: `Interpreter.run(input, output)`. 2 threads. Input/output `float32`. Validação de shape/dtype/hash na inicialização.
7. **Softmax**: Saída do modelo já é probabilidade (camada final `activation="softmax"`).
8. **Top-3**: Ordena por confiança decrescente; empate → menor índice. **Não renormaliza** o top-3.
9. **Threshold**: `max(prob) < threshold` → **Resultado inconclusivo**. Threshold vem de `model_metadata.json` (baseline 0.70, ensemble 0.74). Persistido por análise.
10. **Resultado**: Tela mostra top-3, descrições, aviso "Triagem visual. Confirme com profissional.".
11. **Room**: `AnalysisEntity` gravada automaticamente (photoName relativo, timestamp, classId, displayName, scientificName, confidence, top3Json, inconclusive, threshold, inferenceMs, modelSha256). Foto em `filesDir/photos/`.

---

## 2. Perguntas e respostas curtas

### Por que Kotlin?
> Linguagem oficial Android; null-safety, coroutines, interop Java. Compose reduz boilerplate e integra Flow/State nativamente.

### Por que Jetpack Compose?
> UI declarativa, state-driven. Elimina `findViewById`, `ViewBinding`, XML. Preview instantâneo no Android Studio. Material3 nativo.

### Por que funciona offline?
> Manifesto **sem** `INTERNET`. Modelo TFLite nos assets. Room/SQLite local. CameraX e seletor de documentos não exigem rede. Zero backend, Firebase, analytics, telemetria.

### O que é transfer learning?
> Usar pesos pré-treinados (ImageNet) como ponto de partida. Congelamos o backbone (detectores de borda/textura/formas genéricos) e treinamos só a cabeça de classificação (16 classes). Depois liberamos últimas 30 camadas com LR baixa (1e-5) para especializar no domínio folhas de fumo.

### O que é fine-tuning?
> Segunda etapa do treino: descongelar últimas N camadas (30 no baseline) e continuar treinamento com LR 100x menor. `BatchNormalization` mantido em modo inferência (não atualiza estatísticas) para não quebrar features ImageNet com batch pequeno.

### O que é macro-F1?
> Média não ponderada do F1 por classe. Dá peso igual a `wildfire` (89 imgs) e `anthracnose` (8 imgs). **Métrica principal** do benchmark porque accuracy esconde desempenho ruim em classes raras.

### Por que accuracy não basta?
> Baseline: 77.67% accuracy, mas `anthracnose` e `TSWV` 0/1 no teste. Macro-F1 0.7157 expõe o desequilíbrio. Ensemble: accuracy 80.58%, macro-F1 0.7659.

### O que significa confiança (score softmax)?
> Valor da classe mais provável segundo o modelo. **Não é probabilidade comprovada de doença**. Softmax não calibrada ≠ frequência real de acerto. Ex: 95% confiança ≠ acerta 95% das vezes.

### Por que confiança não é acurácia?
> Acurácia = proporção de acertos em um conjunto **conhecido** (teste). Confiança = valor para **uma imagem específica**. Calibração (temperature scaling + threshold em validação) aproxima confiança de frequência real, mas não garante.

### O que é treino / validação / teste?
> - **Treino (489)**: ajusta pesos. Augmentation só aqui.
> - **Validação (104)**: escolhe arquitetura, ensemble, temperatura, threshold. **Nunca** usado para treinar.
> - **Teste (103)**: aberto **uma vez** após decisão final. Métricas reportadas (77.67% / 80.58%).

### Como evitaram vazamento (data leakage)?
> Agrupamento por classe + identificador IMG + dHash (distância ≤ 4). **Grupos nunca divididos** entre treino/val/teste. Verificação em `load_manifest`: mesmo `group_id` ou `pixel_sha256` em splits diferentes → erro. Seed 42 fixa.

### Por que não testar infinitos modelos?
> Cada configuração consome validação. Com 104 imagens validação, 50 estratégias já satura risco de overfitting à validação. Próximo ganho confiável virá de **mais dados independentes** (fase 3 roadmap), não mais arquiteturas nos mesmos 696 arquivos.

### Por que o ensemble não está integrado no Android?
> Ensemble = 3 modelos (19.5 MB), 3 inferências, temperatura + threshold calibrados. App mantém baseline (3.8 MB, 1 inferência) **já validado no fluxo completo**. Trocar sem medir latência, memória, aquecimento, APK size, estabilidade em device real criaria risco de regressão. Próximo passo: branch isolada, medição em entry-level e mid-range, então decidir.

### O que ainda falta para uso real?
> 1. Teste em device físico (câmera, permissão, galeria, persistência entre processos).
> 2. Revisão agronômica de nomes, sintomas, descrições, orientações (todas marcadas `reviewed: false`).
> 3. Validação externa: dataset independente de propriedades no Sul do Brasil.
> 4. Classes raras: anthracnose, TSWV, black shank, genetic_abnormality precisam mais amostras.
> 5. Calibração de confiança: ECE, Brier score, reliability diagram, threshold em subset dedicado.
> 6. Rejeição de fotos ruins: blur, low-light, folha distante, "não é folha de fumo".
> 7. Workflow de campo: propriedade/talhão, notas, exportação para técnico.
> 8. Release assinado, GitHub Releases, licenças de dataset/fontes/pesos confirmadas.

---

## 3. Métricas — distinção crítica

### Modelo Android atual (baseline integrado)
| Métrica | Valor |
|---|---|
| Top-1 accuracy | **77.67%** (80/103) |
| Macro-F1 | **0.7157** |
| Top-3 accuracy | **97.09%** (100/103) |
| Accepted-prediction accuracy (thr 0.70) | **89.87%** |
| Cobertura (thr 0.70) | **76.70%** |

### Ensemble experimental (NÃO no Android)
| Métrica | Valor |
|---|---|
| Top-1 accuracy | **80.58%** (83/103) |
| Macro-F1 | **0.7659** |
| Top-3 accuracy | **99.03%** (102/103) |
| Accepted-prediction accuracy (thr 0.74) | **92.31%** |
| Cobertura (thr 0.74) | **75.73%** |

> **NUNCA diga que o Android atual possui 80.58%.** O app hoje roda o baseline 77.67%. O ensemble é recomendação para próxima versão após validação móvel.

---

## 4. Pontos-chave para defender

1. **Reprodutibilidade**: Pipeline Python completo (import → audit → split → train → eval → export → validate) com seeds fixas, hashes, manifestos, paridade Keras/TFLite verificada.
2. **Contrato Python↔Android**: Mesmo pré-processamento (bilinear integer), mesma entrada (0–255), mesma ordem de classes, hashes verificados em ambos os lados. Teste golden (SHA-256 do RGB final) passa.
3. **Arquitetura limpa**: MVVM pragmático. ViewModel não expõe Repository/Catalog diretamente. Repository serializa inferência (Mutex). Classifier valida assets uma vez e reutiliza Interpreter.
4. **Privacidade/Offline**: Zero permissão INTERNET. Dados não saem do dispositivo. Backup desativado.
5. **Benchmark honesto**: 14 configs + baseline anterior = 15 candidatos. 50 estratégias (15 single + 35 ensembles). Seleção **só validação**. Teste aberto uma vez. Quantização dinâmica rejeitada por paridade.
6. **Limitações documentadas**: Não escondidas. Dataset pequeno, classes raras, sem teste externo, threshold não calibrado (baseline), ensemble não no device.
7. **Roadmap realista**: Fases 1-6 com critérios de saída objetivos. Próximo passo técnico claro: device test + agronomic review + ensemble branch.

---

## 5. Demonstração sugerida (2-3 min)

1. Abrir app em modo avião → histórico vazio, saudação "Olá, Produtor".
2. Ajuda → mostrar 4 exemplos (correta, desfocada, distante, pouca luz).
3. Câmera → enquadrar folha (guia visual) → capturar.
4. Resultado → top-3, confiança, descrição, aviso profissional, inferência ms, threshold.
5. Histórico → pesquisa, filtro "Inconclusivas", agrupamento por data.
6. Fechar app, reabrir → histórico persiste.
7. (Opcional) `predict.py` com mesma imagem → comparar top-3 (pequenas diferenças de JPEG esperadas).

---

## 6. O que NÃO prometer

- "Diagnóstico preciso" → **triagem visual**.
- "Funciona para qualquer foto" → **folha de fumo, bem enquadrada, iluminada**.
- "80% de acurácia no celular" → **77.67% no teste controlado; ensemble 80.58% ainda não no app**.
- "Pronto para produção" → **protótipo acadêmico; roadmap fases 3-6 para campo real**.
- "Modelo calibrado" → **threshold 0.70 inicial; ensemble 0.74 calibrado em validação apenas**.

---

## 7. Referências rápidas no repositório

| Assunto | Arquivo |
|---|---|
| Arquitetura & decisões | `docs/ARCHITECTURE.md` |
| Dataset & split | `docs/DATASET.md` |
| Contrato inferência | `docs/INFERENCE_CONTRACT.md` |
| Validação V3 | `docs/VALIDATION.md` |
| Benchmark 14 modelos | `docs/BENCHMARK_MODELOS.md` |
| Relatório comparativo | `docs/RELATORIO_COMPARATIVO_MODELOS_LEAFCARE.md` |
| Integração ensemble | `docs/ENSEMBLE_ANDROID_INTEGRATION.md` |
| Testes manuais device | `docs/MANUAL_TESTS.md` |
| Decisões técnicas | `docs/TECHNICAL_DECISIONS.md` |
| Roadmap (EN) | `ROADMAP.md` |
| Roadmap (PT-BR) | `docs/ROADMAP_PT_BR.md` |
| Third-party / licenças | `docs/THIRD_PARTY.md` |

---

## 8. Checklist final antes de apresentar

- [ ] App roda no device de apresentação (modo avião testado).
- [ ] `connectedDebugAndroidTest` passa no emulador/device.
- [ ] Todos itens `MANUAL_TESTS.md` executados e anotados.
- [ ] Métricas baseline vs ensemble claras na cabeça (não confundir).
- [ ] Limitações decoradas (não precisa ler, mas saber citar).
- [ ] Próximos passos técnicos claros (device test → agronomic review → ensemble branch).
- [ ] Repositório limpo: sem APK, sem `.venv`, sem build/, sem secrets, sem caminhos absolutos.
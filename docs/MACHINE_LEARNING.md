# Machine Learning — LeafCare

## Modelo integrado

O bundle atual é **MobileNetV4 Small destilado** (`mobilenetv4_conv_small.e2400_r224_in1k`), uma rede com 2.513.520 parâmetros e TFLite float32 de 10.167.768 bytes (9,70 MiB). O professor DINOv2-B não vai no APK. A classificação permanece local e offline após autenticação.

O checkpoint da época 15 foi escolhido pela validação no experimento já concluído; esta integração não retreina, muda o split ou recalibra pelo teste. Receita completa, histórico, logits e hashes: [`experiment_config.json`](../machine-learning/benchmark_artifacts/distillation/mobilenetv4_small_distilled/experiment_config.json). Comparação dos dez alunos: [Benchmark de destilação](benchmarks/BENCHMARK_DESTILACAO.md).

O ensemble anterior continua reproduzível no tag `v1.1.2`, com relatórios em [`model-reports/ensemble/`](model-reports/ensemble/) e resultados em `machine-learning/benchmark_artifacts/ensemble/`. Seus resultados são históricos; a integração atual tem relatórios próprios em [`model-reports/mobilenetv4-distilled/`](model-reports/mobilenetv4-distilled/).

## Contrato de inferência

- Schema 2, arquitetura `MobileNetV4SmallDistilled`.
- Entrada float32 NHWC `[1,224,224,3]`, RGB sRGB na faixa 0–255.
- Decodificação mantém orientação EXIF, conversão sRGB, composição de alpha sobre branco e limite de 16 megapixels.
- Resize do lado menor para 256, lado maior calculado por divisão inteira; bicúbica antialias equivalente ao Pillow 10.4, com acumuladores de coeficientes de 22 bits e clipping uint8 entre os eixos.
- Recorte central 224×224, com arredondamento ties-to-even; contrato `resize_shorter_256_bicubic_center_crop_224_v1`.
- Normalização ImageNet (mean 0,485/0,456/0,406; std 0,229/0,224/0,225), softmax e temperatura **1,8882043704** embarcados no grafo.
- Saída float32 `[1,16]`; Top-3 ordenado por pontuação, desempate pelo índice da classe.
- Limiar **0,5066758394**, definido na validação. Confiança abaixo dele produz resultado inconclusivo. Confiança não é acurácia nem certeza agronômica.

Classes e sua ordem continuam em `artifacts/classes.json`; catálogo em `artifacts/diseases.json`. Android e CLI conferem hashes. Hash TFLite: `422ab461585a83bfabe6896d7ac7b32c726a8b285ff4ade76cb0cec54ba6da99`.

`DistilledPreprocessor.kt` e `leafcare/preprocessing.py` implementam a entrada atual. `PixelPreprocessor.kt` e o resize bilinear Python permanecem para fixtures e receitas históricas. Não normalizar duas vezes e não aplicar o preprocessing antigo ao aluno.

## Dataset, treino e destilação

696 imagens TV3 do [TLA](https://doi.org/10.3389/fpls.2024.1333236), 16 classes, seed 42 e separação por grupos: treino 489, validação 104, teste 103. Os dados brutos completos não são redistribuídos. Manifesto, mapa de classes, auditoria e proveniência permanecem nos caminhos de `machine-learning/data/`, `tla_class_map.yaml` e `docs/legal/`.

O aluno parte de pesos ImageNet e probe logística, usa AdamW, batch 8, três épocas de aquecimento e quinze de ajuste; BatchNorm congelada. A perda combina 50% entropia cruzada ponderada e 50% KL com o professor DINOv2-B, temperatura de destilação 4 (distinta da temperatura de calibração). Os logits do professor são usados somente no treino. A época é selecionada por Macro-F1/Top-1/Top-3 na validação; o teste não escolhe checkpoint nem limiar.

## Métricas registradas

| Partição | Imagens | Top-1 | Macro-F1 | Top-3 |
| --- | ---: | ---: | ---: | ---: |
| Validação | 104 | 86,54% | 0,7896 | 96,15% |
| Teste | 103 | 85,44% | 0,8083 | 97,09% |

No teste, 90/103 previsões foram aceitas (87,38% de cobertura), com 81 acertos (90%). O ensemble anterior tinha Top-1 81,55%, Macro-F1 0,7371, Top-3 99,03% e 92,68% de acerto nas 82 previsões aceitas. O aluno melhora Top-1 e Macro-F1, mas tem Top-3 e acerto entre aceitas menores. São resultados internos no teste já conhecido; não constituem validação de campo ou teste independente de novas escolhas.

O replay da integração nas 104 imagens de validação manteve todas as classes previstas e erro máximo de probabilidade de 0,0000051 contra os logits gravados, abaixo de 0,0001. O replay das 103 imagens de teste manteve as métricas publicadas. [Paridade](model-reports/mobilenetv4-distilled/integration_parity.json) e relatórios por classe estão na pasta do modelo atual.

Tempo do benchmark em desktop i5-13400, duas threads: inferência TFLite mediana 2,11 ms/P95 2,20 ms; pipeline Python mediana 3,63 ms/P95 3,91 ms. Isso não mede Android. A meta de análise completa até três segundos no Galaxy A06 segue pendente de aparelho conectado.

## Executar e validar

Com Python 3.12, dentro de `machine-learning/`:

```bash
pip install -r requirements.txt
python predict.py ../samples/reference_frog_eye.jpg
python validate_bundle.py --require-model
python -m pytest -q
```

A CLI usa TFLite e seleciona o preprocessing pelo metadata. Não existe `model.keras` correspondente ao aluno; o backend Keras é rejeitado para evitar usar um arquivo antigo local.

Para reproduzir o treino e a conversão, siga [`experiments/distillation/README.md`](../machine-learning/experiments/distillation/README.md), usando seus ambientes separados. Para promover novamente o resultado já selecionado, com dataset e caches originais disponíveis:

```bash
python deploy_distilled.py --install --pixel-parity-dir /tmp/leafcare-distilled-parity
```

A promoção verifica hashes do checkpoint, TFLite, manifesto e imagens; executa replay de validação/teste antes de atualizar `artifacts/` e `assets/`. Ela não seleciona outro modelo nem altera os experimentos históricos. O checkpoint também é distribuído na release do aluno para permitir futuras reexportações.

No Android, com JDK 17 e SDK configurados:

```bash
bash gradlew testDebugUnitTest verifyModelAssets lintDebug assembleDebug
```

Fixtures sintéticas compartilhadas verificam retrato, paisagem, upscale, downsample, alpha e arredondamento de crop. O replay Kotlin das 104 imagens decodificadas pode ser habilitado com `LEAFCARE_PARITY_DIR=/tmp/leafcare-distilled-parity` após gerar as fixtures locais. Não distribua dados brutos no Git ou APK.

As receitas Keras/ensemble são históricas. `train_ensemble.py`, `export_tflite.py` e `legacy/train.py` e `legacy/evaluate.py` rejeitam o bundle destilado atual; reproduza o ensemble em checkout `v1.1.2`. Mantenha ambientes/checkpoints em `.cache` ignorados. Novos experimentos devem continuar isolados do bundle distribuído até promoção explícita.

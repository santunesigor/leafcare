# Benchmark isolado — modelos leves e destilação

Rodada de 2026-10-08 na branch `experiment/mobile-models-distillation`, criada após pull da main em `4106004`. Dez treinos efetivamente executados; nenhum candidato substituiu o ensemble do aplicativo.

Melhor configuração pela validação: **fastvit_t8_supervised**, Macro-F1 0.8133. A escolha não usa o teste. A referência integrada tem Macro-F1 de validação 0,8179 e TFLite de 17,22 MiB; são números históricos, não um retreinamento desta rodada.

## Protocolo e isolamento

- Dataset existente: 696 imagens, 16 classes, split 489/104/103. SHA256 das imagens auditado; manifesto, grupos, ordem das classes e split preservados.
- Diretórios exclusivos: `machine-learning/experiments/distillation/`, `benchmark_artifacts/distillation/` e cache ignorado `experiments/distillation/.cache/`. Helpers de métricas/manifesto existentes são importados somente para leitura; receitas e saídas antigas não são executadas nem sobrescritas.
- Proteção por SHA256: 215 arquivos de assets, artefatos atuais, manifesto/config e resultados históricos idênticos antes/depois. Evidências em `isolation_before.json`, `isolation_after.json` e `isolation_after_export.json`.
- Checkpoints ImageNet fixados por revisão e SHA256. Cabeça de 16 classes inicializada com probe logístico C=1 (com/sem pesos), selecionado pela validação; cada par usa exatamente o mesmo estado inicial, buffers, preprocessing e augmentations.
- Seed 42; float32; batch 8; AdamW; warmup da cabeça 3 épocas a 1e-3; ajuste até 15 épocas a 1e-4 cabeça/1e-5 encoder; weight decay 1e-4; BatchNorm com estatísticas congeladas; patience 5 no ajuste. Época zero (probe) também pode vencer.
- Augmentations: flips horizontal/vertical p=0,5; rotação ±12° bilinear com fundo branco; brilho/contraste 0,1 e saturação 0,05. Seed por época/imagem; a mesma foto aumentada serve para professor/aluno, seguida do preprocessing oficial de cada checkpoint.
- Seleção por Macro-F1 de validação; desempates Top-1, Top-3 e época anterior. Temperatura minimiza NLL de validação; limiar maximiza cobertura com pelo menos 90% de acerto na validação, ou fallback explicitado em `validation_metrics.json`.
- Checkpoint, configuração, temperatura e limiar persistidos antes de inferir o teste. O teste já foi conhecido em rodadas anteriores: comparação histórica interna, não avaliação independente.

## Professor e destilação

Professor: DINOv2-B com ajuste parcial existente, escolhido pela maior Macro-F1 de validação entre os DINOv2 registrados (0,8069). Nenhuma seleção pelo Top-1 de teste. O professor fica congelado, fora do APK; somente imagens de treino fornecem alvos de destilação.

`loss = 0,5 × CE balanceada + 0,5 × T² × KL(professor || aluno)`, com `T=4` e KL média por batch. Os logits crus do professor são usados, sem aplicar sua temperatura de calibração ao treinamento. A temperatura de destilação e a calibração do aluno são coisas distintas.

Replay do professor nas 104 imagens de validação: concordância Top-1 100%, erro máximo de logits 0. Alvos cacheados com shape [18, 489, 16], identidade de professor/manifesto/receita/caminhos e SHA256; sem alvos de validação/teste usados na perda.

O aluno tem o mesmo número de parâmetros com ou sem destilação. A redução em relação ao professor vem da arquitetura pequena; o experimento verifica quanto do desempenho ela consegue aprender. FastViT já usa um checkpoint destilado em ImageNet: o controle significa ausência de **destilação adicional para LeafCare**.

## Resultados

Tabela ordenada somente pela validação. Top-1/Top-3 em porcentagem; Macro-F1 entre 0 e 1. S = supervisionado; D = destilado.

| Modelo | Receita | Parâmetros | Época | Val: Top-1 / F1 / Top-3 | Teste: Top-1 / F1 / Top-3 | Teste: cobertura / acerto aceito |
|---|---|---:|---:|---|---|---|
| fastvit_t8 | S | 3,269,536 | 0 | 91.35% / 0.8133 / 98.08% | 76.70% / 0.6605 / 95.15% | 99.03% / 77.45% |
| fastvit_t8 | D | 3,269,536 | 0 | 91.35% / 0.8133 / 98.08% | 76.70% / 0.6605 / 95.15% | 99.03% / 77.45% |
| mobilenetv4_small | D | 2,513,520 | 15 | 86.54% / 0.7896 / 96.15% | 85.44% / 0.8083 / 97.09% | 87.38% / 90.00% |
| mobilenetv3_small | S | 1,534,256 | 9 | 87.50% / 0.7834 / 98.08% | 81.55% / 0.7227 / 99.03% | 85.44% / 88.64% |
| mobilenetv3_small | D | 1,534,256 | 11 | 85.58% / 0.7750 / 97.12% | 82.52% / 0.7237 / 100.00% | 80.58% / 92.77% |
| efficientvit_m3 | S | 6,584,218 | 14 | 83.65% / 0.7633 / 98.08% | 80.58% / 0.7183 / 96.12% | 51.46% / 92.45% |
| efficientvit_m3 | D | 6,584,218 | 8 | 84.62% / 0.7553 / 97.12% | 81.55% / 0.6699 / 98.06% | 79.61% / 89.02% |
| mobilenetv4_small | S | 2,513,520 | 3 | 85.58% / 0.7550 / 94.23% | 82.52% / 0.7154 / 96.12% | 76.70% / 92.41% |
| efficientvit_m2 | S | 3,965,706 | 8 | 80.77% / 0.7246 / 97.12% | 82.52% / 0.7892 / 95.15% | 66.99% / 91.30% |
| efficientvit_m2 | D | 3,965,706 | 11 | 78.85% / 0.7171 / 97.12% | 79.61% / 0.6754 / 95.15% | 67.96% / 91.43% |

## Efeito pareado da destilação

D menos S na mesma arquitetura. Delta Top-1 em pontos percentuais; a conclusão se baseia primeiro na validação.

| Modelo | Δ Macro-F1 validação | Δ Top-1 validação | Δ Macro-F1 teste | Δ Top-1 teste |
|---|---:|---:|---:|---:|
| mobilenetv3_small | -0.0084 | -1.92 | +0.0009 | +0.97 |
| mobilenetv4_small | +0.0345 | +0.96 | +0.0929 | +2.91 |
| fastvit_t8 | +0.0000 | +0.00 | +0.0000 | +0.00 |
| efficientvit_m2 | -0.0076 | -1.92 | -0.1138 | -2.91 |
| efficientvit_m3 | -0.0079 | +0.96 | -0.0484 | +0.97 |

Nesta receita fixa, a destilação melhorou a Macro-F1 de validação em 1/5 arquiteturas: mobilenetv4_small. Isso não demonstra um ganho geral da técnica nem substitui múltiplas seeds/avaliação de campo.

Nenhuma configuração desta rodada superou a Macro-F1 de validação histórica do ensemble atual (0,8179). O bundle atual permanece como referência; não há proposta de substituição automática com base no teste conhecido.

Checkpoint inicial venceu em: `fastvit_t8_supervised`, `fastvit_t8_distilled`. Os treinos foram executados e seus históricos registrados, mas os pesos treinados não foram selecionados. Uma receita D com época zero selecionada não contém destilação adicional no checkpoint final.

## Exportações experimentais e custo

TFLite float32 NHWC RGB 0–255, saída de 16 probabilidades calibradas. Normalização/softmax/temperatura no grafo; resize/crop oficiais fora. Isso não comprova compatibilidade com o preprocessing Android integrado.

FastViT/EfficientViT são reparametrizados após restaurar os pesos selecionados. A fusão e a conversão são conferidas nas 104 imagens de validação. Conversão exige Top-1 idêntico e erro máximo de probabilidades ≤0,0001, incluindo replay contra logits do treino. Exportações falhas ficam registradas e não são consideradas aprovadas.

| Configuração | Exportação | TFLite MiB | CPU inferência mediana / P95 ms | Pipeline Python mediana / P95 ms |
|---|---|---:|---|---|
| fastvit_t8_supervised | Aprovada | 12.45 | 14.41 / 14.90 | 17.79 / 18.82 |
| fastvit_t8_distilled | Aprovada | 12.45 | 14.58 / 15.69 | 18.26 / 19.37 |
| mobilenetv4_small_distilled | Aprovada | 9.70 | 2.11 / 2.20 | 3.63 / 3.91 |
| mobilenetv3_small_supervised | Aprovada | 6.00 | 0.93 / 0.98 | 2.10 / 2.39 |
| mobilenetv3_small_distilled | Aprovada | 6.00 | 0.95 / 1.01 | 2.15 / 2.33 |
| efficientvit_m3_supervised | Aprovada | 25.80 | 3.79 / 4.06 | 5.46 / 5.74 |
| efficientvit_m3_distilled | Aprovada | 25.80 | 3.78 / 4.04 | 5.48 / 5.85 |
| mobilenetv4_small_supervised | Aprovada | 9.70 | 2.05 / 2.19 | 3.48 / 3.95 |
| efficientvit_m2_supervised | Aprovada | 15.69 | 2.88 / 3.13 | 4.50 / 4.96 |
| efficientvit_m2_distilled | Aprovada | 15.69 | 2.95 / 3.18 | 4.62 / 5.36 |

Tempos de treino/perfil PyTorch: GTX 1650 4 GiB. TFLite: Intel Core i5-13400 CPU, duas threads, cinco aquecimentos e 30 medições, após concluir os treinos. Pipeline Python inclui decode, preprocessing, inferência e Top-3; exclui Room/UI. Hardware, versões e hashes estão nos artefatos. Resultados desktop não substituem medição Android.

**Galaxy A06: medição de até três segundos permanece pendente.** Nenhum candidato foi instalado no app nesta rodada. O APK debug entregue contém somente o ensemble atual.

## Limitações e reprodução

- Seed única, treino pequeno, validação com 104 imagens já usada em buscas anteriores; diferenças pequenas não demonstram superioridade estatística. Algumas classes têm suporte de uma ou duas imagens no teste.
- Destilação pode transmitir erros do professor; não garante manter sua acurácia. Macro-F1, métricas por classe, calibração/cobertura e custo devem ser considerados juntos.
- MobileNetV3 timm e receita PyTorch desta rodada diferem do MobileNetV3 Keras integrado; a comparação causal é o par S/D desta rodada, não a diferença contra o histórico.
- Nenhuma validação externa de campo ou avaliação de imagens fora das 16 classes. Pontuações não representam certeza agronômica.

Comandos e ambientes: [README do experimento](../../machine-learning/experiments/distillation/README.md). Artefatos: [distillation](../../machine-learning/benchmark_artifacts/distillation/), incluindo protocolo, environment, probes, históricos, logits, confusões, métricas por classe, previsões, calibração, hashes e paridade. Pesos e TFLites ficam somente no cache local ignorado; os resultados antigos permanecem intactos.

## Validações da entrega

- Pytest dos experimentos de destilação, vision e DINOv2: 38 testes passaram, sem skips. Inclui replay das métricas/épocas, inicializações pareadas e matrizes de paridade/hashes das dez exportações.
- OpenSpec: validação atual e arquivada com `--strict --no-interactive`, sem falhas.
- Android: `assembleDebug` e `verifyModelAssets testDebugUnitTest` concluídos. `testDebugUnitTest` estava UP-TO-DATE; não é uma nova execução dos testes unitários.
- Hash do TFLite dentro do APK idêntico ao baseline protegido; artefato e hash do APK em `android_validation.json`. ADB sem aparelhos conectados.
- Checkpoints e TFLites experimentais ignorados pelo Git; nenhum asset do Android substituído.

## Fontes primárias

- [Destilação de conhecimento — Hinton, Vinyals e Dean](https://arxiv.org/abs/1503.02531).
- [DINOv2 — implementação Meta](https://github.com/facebookresearch/dinov2).
- [MobileNetV4 Small — checkpoint timm](https://huggingface.co/timm/mobilenetv4_conv_small.e2400_r224_in1k).
- [FastViT — implementação Apple](https://github.com/apple-aiml-research/ml-fastvit).
- [EfficientViT M — Microsoft/Cream](https://github.com/microsoft/Cream/tree/main/EfficientViT). Esta família é a MSRA, distinta de outras arquiteturas chamadas EfficientViT.
- [LiteRT Torch — conversão](https://github.com/google-ai-edge/litert-torch).

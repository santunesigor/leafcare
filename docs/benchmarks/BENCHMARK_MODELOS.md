# Benchmark de Modelos — LeafCare

## Objetivo

Centralizar resultados conhecidos de classificação de alterações em folhas de fumo e distinguir o modelo integrado de resultados experimentais. DINOv2 é somente benchmark; não altera o Android.

## Dataset e split

São 696 imagens de 16 classes da seção TV3 bruta do TLA. A importação excluiu TV6, imagens derivadas de TTDD e material sintético. Fontes e licenças estão em docs/legal/referencias_manifest.csv; o registro de importação, em machine-learning/data/import_report.json.

O manifesto machine-learning/data/prepared/manifest.json fixa treino/validação/teste em 489/104/103 imagens e seed 42. O split existente une grupos de origem de machine-learning/data/groups.csv e grupos próximos por dHash, depois faz uma alocação determinística orientada a grupos que aproxima 70/15/15. Nenhum grupo ou hash de pixels cruza subconjuntos.

DINOv2 reutilizou esse manifesto sem novo split. Classificador, temperatura e threshold foram escolhidos usando treino e validação e gravados antes da extração/inferência no teste. O teste foi usado uma vez para o resultado final e não orientou ajustes posteriores. Como já era conhecido por benchmarks históricos, a comparação não é uma avaliação externa independente.

## Metodologia

O pipeline integrado TensorFlow/Keras treinou MobileNetV3Small com backbone congelado por até 25 épocas e depois fine-tuning das últimas 30 camadas por até 20 épocas; Adam, early stopping por perda de validação, flips, rotação, zoom, contraste e pesos de classe quando necessários. A etapa registrada como selecionada foi fine-tuning. Configuração e evidências: machine-learning/config.yaml, machine-learning/artifacts/config_used.json e machine-learning/artifacts/training_metadata.json.

DINOv2 ViT-S/14 oficial usou backbone congelado e features da concatenação do token CLS normalizado com a média dos tokens de patch normalizados. Features foram extraídas em batches de 8. Um LogisticRegression multinomial (LBFGS, C=1, máximo 1000 iterações) foi ajustado em treino. Duas opções pré-definidas (sem pesos e class_weight=balanced) foram comparadas na validação por Macro-F1; Top-1 e Top-3 desempataram. O probe balanceado foi selecionado. Não houve fine-tuning parcial ou ViT-B/14: a GTX 1050 Ti disponível tem 4 GB de VRAM, e o probe linear responde ao objetivo com menos busca.

Preprocessamento DINOv2: decodificação RGB com orientação EXIF; resize bicúbico antialias mantendo proporção até borda menor de 256 px; crop central 224×224; conversão float32 [0,1]; normalização ImageNet (média 0,485/0,456/0,406, desvio 0,229/0,224/0,225). Sem augmentation estocástico. A transformação de avaliação segue o repositório oficial e difere do preprocessing MobileNet do app.

O checkpoint ficou no cache local do PyTorch, não no Git. A execução registra URL oficial, revisão, SHA-256 e tamanho. O model card oficial declara Apache-2.0.

## DINOv2

DINOv2 é um modelo visual fundacional auto-supervisionado da Meta, baseado em Vision Transformer. Seu backbone ViT-S/14 foi pré-treinado sem rótulos de classe e produz representações reutilizáveis em tarefas de visão; aqui, somente um classificador linear foi aprendido para as 16 classes do LeafCare.

## Métricas

Macro-F1 é a métrica principal devido ao desbalanceamento e às classes com suporte mínimo. Top-1 mede a primeira hipótese; Top-3 verifica se a classe correta aparece entre as três.

Coverage é a fração acima do threshold; accepted accuracy é a acurácia dessas previsões. Temperatura e threshold foram escolhidos somente na validação. A temperatura minimizou NLL; o threshold maximizou coverage visando pelo menos 90% de acurácia aceita.

## Modelos já avaliados

“Não medido” indica ausência de métrica ou artefato verificável; nenhum valor foi estimado.

| Modelo | Val. Top-1 / Macro-F1 / Top-3 | Teste Top-1 | Teste Macro-F1 | Teste Top-3 | Coverage | Accepted accuracy | Parâmetros / tamanho | Exportação/mobile |
|---|---|---:|---:|---:|---:|---:|---|---|
| MobileNetV2 | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido |
| MobileNetV3Small integrado | Top-1 86/104 (82,69%); Macro-F1/Top-3 não medidos | 80/103 (77,67%) | 0,7157 | 100/103 (97,09%) | 79/103 (76,70%) | 71/79 (89,87%) | parâmetros não registrados; TFLite 3.768.560 bytes | Integrado no app |
| Melhor modelo móvel isolado do benchmark histórico | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido | Não identificado nos artefatos deste checkout |
| MobileNetV3Small RMSprop | Não medido | Não medido isoladamente | Não medido | Não medido | Não medido | Não medido | Não medido | Variante citada na composição do ensemble |
| MobileNetV3Large base | Não medido | Não medido isoladamente | Não medido | Não medido | Não medido | Não medido | Não medido | Variante citada na composição do ensemble |
| EfficientNetB0 | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido |
| EfficientNetV2B0 | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido |
| NASNetMobile | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido | Não medido |
| Ensemble experimental (MobileNetV3Small anterior + RMSprop + MobileNetV3Large base) | Não medido | 83/103 (80,58%) | 0,7659 | 102/103 (99,03%) | 75,73% | 92,31% | Não medido | Experimental; não integrado |
| DINOv2 ViT-S/14 linear probe balanceado | 92/104 (88,46%) / 0,7676 / 103/104 (99,04%) | 87/103 (84,47%) | 0,7497 | 100/103 (97,09%) | 93/103 (90,29%) | 84/93 (90,32%) | 22.068.880 com probe; checkpoint 88.283.115 bytes | Exportação não testada |

MobileNet integrado: machine-learning/artifacts/metrics.json e machine-learning/artifacts/leafcare.tflite. Os números do ensemble são os valores históricos fornecidos para esta tarefa; os artefatos originais não estão neste checkout. Não foi encontrado machine-learning/benchmark_artifacts/ anterior nem relatório com resultados individuais das outras arquiteturas.

O ensemble histórico manteve T=0,8421423932 e threshold 0,74. O baseline integrado usa threshold 0,70 sem temperature scaling. Esses valores e métricas históricas foram preservados.

## Resultados na validação

| Probe | Top-1 | Macro-F1 | Top-3 | Observação |
|---|---:|---:|---:|---|
| Linear sem pesos por classe | 92/104 (88,46%) | 0,7676 | 102/104 (98,08%) | C=1, LBFGS; 94 iterações |
| Linear balanceado (selecionado) | 92/104 (88,46%) | 0,7676 | 103/104 (99,04%) | C=1, LBFGS; 95 iterações |

Temperature scaling escolheu T=1,218814: NLL caiu de 0,4034 para 0,3920, enquanto ECE em 15 bins subiu de 0,0782 para 0,0908. O efeito de calibração foi misto. Threshold congelado: 0,573822; 94/104 aceitações e 90,43% de acurácia aceita.

## Resultado final no teste

Com probe, T=1,218814 e threshold 0,573822 congelados: 87/103 Top-1 (84,47%), Macro-F1 0,7497 e 100/103 Top-3 (97,09%). Foram aceitas 93/103 (coverage 90,29%); 84/93 estavam corretas (90,32%). Nenhuma seleção ou ajuste usou o teste. machine-learning/benchmark_artifacts/dinov2/test_metrics.json registra test_used_for_selection=false.

## Comparação de custo

| Modelo | Parâmetros | Tamanho | Treino/extração | Inferência PC | Mobile/export |
|---|---:|---:|---|---|---|
| MobileNetV3Small integrado | Não registrado | TFLite 3.768.560 bytes | Tempo não registrado; 25 épocas congeladas e até 20 de fine-tuning | Mediana histórica 1,25 ms em 20 chamadas TFLite de validação; hardware não registrado | TFLite integrado |
| Ensemble experimental | Não medido | Não medido | Não medido | Não medido | Não integrado |
| DINOv2 ViT-S/14 + probe | 22.068.880 (12.304 treináveis) | checkpoint 88.283.115 bytes; probe 95.143 bytes | 8,72 s para features treino+validação; 0,21 s probe selecionado (duas opções: 0,54 s) | Mediana 15,12 ms, p95 15,46 ms, forward batch 1 na GTX 1050 Ti; pico PyTorch batch 8: 136.301.056 bytes alocados e 174.063.616 reservados | ONNX/TFLite/LiteRT não avaliados; sem medição Android |

O checkpoint DINOv2 equivale a aproximadamente 23,4 vezes o tamanho do TFLite integrado. A leitura de VRAM usa um forward de custo com batch sintético de zeros e mede só o alocador PyTorch; não inclui contexto CUDA nem RAM do host. O tempo de forward exclui decode, preprocessing e cópia à GPU. Os tempos PC usam caminhos diferentes e não formam comparação controlada. Este tempo não representa latência em aparelho Android. A exportação e validação de operadores Transformer no runtime Android escolhido continuam pendentes.

Detalhes do perfil: machine-learning/benchmark_artifacts/dinov2/cost_profile.json.

## Análise por classe

[per_class_metrics.csv](../../machine-learning/benchmark_artifacts/dinov2/per_class_metrics.csv) traz precision, recall, F1 e suporte para validação e teste. No teste, Anthracnose, Genetic Abnormality e TSWV têm suporte 1; Black Shank, suporte 2. São métricas estatisticamente instáveis. Seus F1 do DINOv2 foram 0,000, 1,000, 0,000 e 0,800, respectivamente. Matrizes: [teste](../../machine-learning/benchmark_artifacts/dinov2/confusion_matrix.csv) e [validação](../../machine-learning/benchmark_artifacts/dinov2/confusion_matrix_validation.csv).

| Classe | Precision | Recall | F1 | Suporte |
|---|---:|---:|---:|---:|
| anthracnose | 0,000 | 0,000 | 0,000 | 1* |
| black_shank | 0,667 | 1,000 | 0,800 | 2* |
| brown_spot | 0,667 | 0,857 | 0,750 | 7 |
| cmv | 1,000 | 1,000 | 1,000 | 8 |
| frog_eye | 0,875 | 0,875 | 0,875 | 8 |
| genetic_abnormality | 1,000 | 1,000 | 1,000 | 1* |
| healthy | 0,778 | 1,000 | 0,875 | 7 |
| nematodes | 0,714 | 0,833 | 0,769 | 6 |
| potato_tuber_moth | 1,000 | 1,000 | 1,000 | 7 |
| pvy | 0,750 | 0,600 | 0,667 | 5 |
| sunscald | 0,909 | 1,000 | 0,952 | 10 |
| target_spot | 0,833 | 0,625 | 0,714 | 8 |
| tmv | 1,000 | 0,700 | 0,824 | 10 |
| tswv | 0,000 | 0,000 | 0,000 | 1* |
| weather_fleck | 0,889 | 0,889 | 0,889 | 9 |
| wildfire | 0,917 | 0,846 | 0,880 | 13 |

*Suporte 1 ou 2: estimativa instável.*

## Limitações

- Dataset de 696 imagens e teste de 103; sem validação externa ou dados de campo independentes.
- Quatro classes no teste têm suporte de 1 ou 2.
- O teste já era conhecido por benchmarks históricos.
- Não houve fine-tuning parcial, ViT-B/14, exportação mobile, benchmark Android ou estimativa de RAM em aparelho.
- Temperatura melhorou NLL na validação, mas aumentou ECE.
- Só duas opções de pesos de classe foram comparadas; ainda há risco de seleção experimental e sobreajuste à validação.
- Grupos e dHash não comprovam independência por planta, propriedade ou condição de campo.

## Conclusão

DINOv2 supera MobileNetV3Small integrado em Top-1 por 6,80 pontos percentuais (87 contra 80 acertos) e em Macro-F1 por 0,0340; Top-3 empata em 100/103. Contra o ensemble, tem quatro acertos Top-1 a mais, mas Macro-F1 é 0,0162 menor e Top-3 tem dois acertos a menos. Macro-F1, métrica principal, continua favorecendo o ensemble histórico.

DINOv2 teve coverage maior que o ensemble (90,29% contra 75,73%) e accepted accuracy menor (90,32% contra 92,31%). O checkpoint tem 88,3 MB, e exportação/inferência Android não foram testadas.

**Classificação: C — competitivo, mas sem vantagem suficiente.** O Top-1 é forte e supera o modelo integrado, mas não supera o melhor ensemble na métrica principal e tem custo de implantação substancialmente maior. **Não devemos trocar o modelo integrado no app agora.**

## Decisão atual

- **MODELO INTEGRADO NO APP:** MobileNetV3Small, TFLite float32, threshold 0,70.
- **MELHOR RESULTADO EXPERIMENTAL:** ensemble MobileNetV3Small + MobileNetV3Small RMSprop + MobileNetV3Large base; maiores Macro-F1 e Top-3 históricos conhecidos.
- **MELHOR DINOv2:** ViT-S/14 congelado com probe linear balanceado; Top-1 84,47%, Macro-F1 0,7497, Top-3 97,09%.
- **MODELO RECOMENDADO PARA PRÓXIMA ETAPA:** manter MobileNetV3Small no Android. DINOv2 fica como candidato de pesquisa até existir validação externa e benchmark móvel.

## Referências e artefatos

- [Artigo sugerido pelo professor](https://medium.com/digital-mind/visualizing-dinov2-contrastive-learning-and-classification-examples-9e6d8f87acf6).
- [Paper DINOv2](https://arxiv.org/abs/2304.07193).
- [Repositório oficial DINOv2](https://github.com/facebookresearch/dinov2/tree/7764ea0f912e53c92e82eb78a2a1631e92725fc8), revisão 7764ea0f912e53c92e82eb78a2a1631e92725fc8.
- [Transformações oficiais de classificação](https://github.com/facebookresearch/dinov2/blob/7764ea0f912e53c92e82eb78a2a1631e92725fc8/dinov2/data/transforms.py).
- [Model card oficial](https://github.com/facebookresearch/dinov2/blob/main/MODEL_CARD.md).
- Artefatos da execução: machine-learning/benchmark_artifacts/dinov2/.
- Artefatos do modelo integrado: machine-learning/artifacts/.
- Pipeline integrado preservado em docs/MACHINE_LEARNING.md.

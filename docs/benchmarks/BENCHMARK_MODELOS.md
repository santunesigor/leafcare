# Benchmark de Modelos — LeafCare

## Escopo

Este documento reúne o benchmark móvel histórico e o experimento DINOv2. O modelo integrado ao app continua sendo MobileNetV3Small; resultados experimentais não alteram o Android.

Os modelos foram avaliados no mesmo split: 489 imagens de treino, 104 de validação e 103 de teste, em 16 classes. O conjunto de teste já era conhecido por resultados anteriores, então esta comparação não substitui uma avaliação externa. Macro-F1 é a métrica principal por causa do desbalanceamento entre classes.

## Comparação

Os candidatos móveis históricos estão ordenados por Macro-F1 de validação; ensemble e DINOv2 aparecem junto deles para comparação. Os candidatos individuais antigos têm métricas publicadas apenas para validação; `—` significa que não há resultado de teste registrado para aquela linha.

| Modelo/configuração | Validação: Top-1 / Macro-F1 / Top-3 | Teste: Top-1 / Macro-F1 / Top-3 | Situação |
|---|---|---|---|
| Ensemble: MobileNetV3Small anterior + MobileNetV3Small RMSprop + MobileNetV3Large base | 87,50% / 0,8179 / 100,00% | 80,58% / 0,7659 / 99,03% | Experimental |
| MobileNetV3Small, dropout 0,15 | 82,69% / 0,7785 / 99,04% | — | Candidato individual |
| MobileNetV3Small integrado (modelo anterior) | 82,69% / 0,7750 / 100,00% | 77,67% / 0,7157 / 97,09% | Integrado no app |
| MobileNetV3Small, RMSprop | 82,69% / 0,7677 / 99,04% | — | Candidato individual |
| DINOv2 ViT-S/14, probe linear balanceado | 88,46% / 0,7676 / 99,04% | 84,47% / 0,7497 / 97,09% | Experimental; sem exportação mobile |
| MobileNetV3Small, sem pesos de classe | 86,54% / 0,7671 / 97,12% | — | Candidato individual |
| MobileNetV3Small, Adam | 81,73% / 0,7652 / 98,08% | — | Candidato individual |
| MobileNetV3Large, base | 85,58% / 0,7592 / 99,04% | — | Candidato individual |
| MobileNetV3Small, base | 79,81% / 0,7492 / 100,00% | — | Candidato individual |
| MobileNetV2, base | 82,69% / 0,7462 / 98,08% | — | Candidato individual |
| EfficientNetB0, augmentation forte | 82,69% / 0,7253 / 98,08% | — | Candidato individual |
| MobileNetV3Small, augmentation forte | 75,96% / 0,7228 / 98,08% | — | Candidato individual |
| MobileNetV3Large, augmentation forte | 77,88% / 0,7189 / 98,08% | — | Candidato individual |
| EfficientNetB0, base | 79,81% / 0,7130 / 97,12% | — | Candidato individual |
| EfficientNetV2B0, base | 76,92% / 0,6806 / 94,23% | — | Candidato individual |
| NASNetMobile, base | 68,27% / 0,6515 / 92,31% | — | Candidato individual |
| EfficientNetV2B0, augmentation forte | 71,15% / 0,6451 / 92,31% | — | Candidato individual |

O benchmark móvel treinou 14 configurações e incluiu o modelo anterior como candidato. Também avaliou combinações de dois ou três dos seis candidatos mais bem classificados; somadas aos 15 candidatos individuais, foram 50 estratégias. O ensemble acima foi selecionado na validação por Macro-F1. As configurações variaram em arquitetura, otimizador, pesos de classe e augmentation; portanto, os resultados comparam configurações completas, não isolam o efeito da arquitetura.

## Resultados com rejeição por confiança

| Modelo | Temperatura | Limiar | Cobertura no teste | Acurácia das previsões aceitas |
|---|---:|---:|---:|---:|
| MobileNetV3Small integrado | Sem calibração | 0,70 | 79/103 (76,70%) | 71/79 (89,87%) |
| Ensemble móvel | 0,8421 | 0,74 | 78/103 (75,73%) | 72/78 (92,31%) |
| DINOv2 ViT-S/14 | 1,2188 | 0,5738 | 93/103 (90,29%) | 84/93 (90,32%) |

## Método e custo

O pipeline MobileNetV3Small integrado usou backbone pré-treinado, uma etapa congelada e fine-tuning. No ensemble histórico, os três modelos float32 ocupam cerca de 19,5 MB somados; sua conversão teve concordância Top-1 total com Keras nas 103 imagens de teste. As latências de computador registradas foram 1,10 ms, 1,29 ms e 2,45 ms por modelo, mas não representam medições em Android.

DINOv2 usa ViT-S/14 da Meta congelado, com um classificador logístico linear treinado para as 16 classes. A entrada passa pelo preprocessing oficial DINOv2, diferente do usado pela MobileNet. O checkpoint tem 88.283.115 bytes e o probe, 95.143 bytes. O forward mediano medido na GTX 1050 Ti foi 15,12 ms, sem incluir decode e preprocessing; exportação e inferência Android não foram medidas.

Os limiares e a temperatura de cada linha foram definidos sem usar o teste. O experimento DINOv2 manteve o teste fechado até congelar o classificador e a calibração. As métricas de teste são uma comparação histórica no mesmo split já conhecido, não evidência de generalização em campo.

## Leitura dos resultados

- No teste, DINOv2 tem Top-1 maior que o MobileNetV3Small integrado (84,47% contra 77,67%), mas ambos têm Top-3 de 97,09%.
- O ensemble tem o maior Macro-F1 de teste (0,7659) e Top-3 (99,03%). Seu Macro-F1 supera DINOv2 em 0,0162; DINOv2 teve quatro acertos Top-1 a mais.
- O melhor candidato individual por Macro-F1 na validação é MobileNetV3Small com dropout 0,15 (0,7785). Não há métrica de teste publicada para essa configuração.
- MobileNetV3Small permanece como modelo atual do app. Ensemble e DINOv2 seguem experimentais; o primeiro ainda precisa de avaliação no Android, e o segundo precisa de exportação e medição móvel antes de uma decisão de implantação.

## Limitações e fontes

- O dataset contém 696 imagens; quatro classes têm apenas uma ou duas imagens no teste. Não há validação externa com dados de campo.
- A busca entre configurações usou a mesma validação pequena, com seeds diferentes; diferenças não podem ser atribuídas apenas à arquitetura.
- Os resultados históricos dos 14 candidatos vêm do relatório móvel arquivado no commit `24389c3`. Os artefatos brutos desse benchmark foram removidos do checkout; por isso as métricas individuais de teste não estão disponíveis.
- Os artefatos atuais do DINOv2 estão em [`machine-learning/benchmark_artifacts/dinov2/`](../../machine-learning/benchmark_artifacts/dinov2/), e os do modelo integrado em [`machine-learning/artifacts/`](../../machine-learning/artifacts/).
- Referências do DINOv2: [paper](https://arxiv.org/abs/2304.07193), [repositório oficial](https://github.com/facebookresearch/dinov2/tree/7764ea0f912e53c92e82eb78a2a1631e92725fc8) e [model card](https://github.com/facebookresearch/dinov2/blob/main/MODEL_CARD.md).

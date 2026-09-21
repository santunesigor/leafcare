# Benchmark de modelos — 11/09/2026

## Resultado recomendado

Foram treinadas 14 configurações móveis com o mesmo manifesto de dados: 489 imagens de treino, 104 de validação e 103 de teste, em 16 classes. O modelo anterior foi incluído como um 15º candidato já existente. A seleção utilizou apenas validação; o teste foi aberto uma vez depois da escolha.

O melhor candidato para integração é um ensemble por média de probabilidades:

1. MobileNetV3Small anterior (AdamW e pesos de classe);
2. MobileNetV3Small com RMSprop;
3. MobileNetV3Large base.

Depois da média, aplica-se temperature scaling com `T = 0,8421423932`. A previsão fica inconclusiva quando a maior probabilidade calibrada é menor que `0,74`.

| Métrica | Modelo anterior | Ensemble final | Diferença |
|---|---:|---:|---:|
| Acurácia top 1 no teste | 77,67% | **80,58%** (83/103) | +2,91 p.p. |
| Macro-F1 no teste | 0,7157 | **0,7659** | +0,0502 |
| Acurácia top 3 no teste | 97,09% | **99,03%** (102/103) | +1,94 p.p. |
| Cobertura no limiar | 76,70% | 75,73% (78/103) | -0,97 p.p. |
| Acurácia entre resultados aceitos | 89,87% | **92,31%** (72/78) | +2,44 p.p. |

Na validação, o ensemble obteve 87,50% de acurácia, macro-F1 0,8179 e top 3 de 100%. O limiar foi escolhido na validação para manter pelo menos 90% de acerto nas previsões aceitas.

## Candidatos isolados na validação

| Ordem | Configuração | Acurácia | Macro-F1 | Top 3 |
|---:|---|---:|---:|---:|
| 1 | MobileNetV3Small, dropout 0,15 | 82,69% | **0,7785** | 99,04% |
| 2 | MobileNetV3Small, RMSprop | 82,69% | 0,7677 | 99,04% |
| 3 | MobileNetV3Small, sem pesos de classe | **86,54%** | 0,7671 | 97,12% |
| 4 | MobileNetV3Small, Adam | 81,73% | 0,7652 | 98,08% |
| 5 | MobileNetV3Large, base | 85,58% | 0,7592 | 99,04% |
| 6 | MobileNetV3Small, base | 79,81% | 0,7492 | **100,00%** |
| 7 | MobileNetV2, base | 82,69% | 0,7462 | 98,08% |
| 8 | EfficientNetB0, augmentation forte | 82,69% | 0,7253 | 98,08% |
| 9 | MobileNetV3Small, augmentation forte | 75,96% | 0,7228 | 98,08% |
| 10 | MobileNetV3Large, augmentation forte | 77,88% | 0,7189 | 98,08% |
| 11 | EfficientNetB0, base | 79,81% | 0,7130 | 97,12% |
| 12 | EfficientNetV2B0, base | 76,92% | 0,6806 | 94,23% |
| 13 | NASNetMobile, base | 68,27% | 0,6515 | 92,31% |
| 14 | EfficientNetV2B0, augmentation forte | 71,15% | 0,6451 | 92,31% |

Também foram avaliadas todas as combinações de dois e três modelos entre os seis melhores, totalizando 50 estratégias de inferência quando somados os candidatos isolados. O critério principal foi macro-F1, para reduzir a preferência indevida pelas classes maiores.

## Exportação para celular

Os três TFLites float32 têm 3,77 MB, 3,77 MB e 11,96 MB (aproximadamente 19,5 MB no total). Em todas as 103 imagens de teste, a classe top 1 foi idêntica à versão Keras e o maior erro absoluto ficou abaixo de `0,000009`.

Também foram produzidas versões com quantização dinâmica, mas elas **não devem ser usadas**: a concordância top 1 com Keras caiu para 92,23%, 87,38% e 95,15%. É necessário testar quantização inteira representativa ou QAT em outro experimento antes de reduzir o pacote.

Os tempos medidos no computador (medianas de 1,10 ms, 1,29 ms e 2,45 ms) não representam o desempenho em um celular. Latência, memória e aquecimento precisam ser medidos em aparelhos Android reais.

## Limitações

- O conjunto é pequeno e algumas classes possuem somente uma imagem no teste. TSWV teve 0/1 acerto, portanto resultados por classe são instáveis.
- Os candidatos usam seeds diferentes para explorar variabilidade; isso também confunde parcialmente o efeito da arquitetura com o efeito da inicialização e da ordem dos lotes.
- O teste não participou deste benchmark, mas as métricas do modelo anterior já haviam sido observadas antes. Uma validação externa ainda é necessária.
- Não há garantia de independência por propriedade, planta ou sessão fotográfica quando esses metadados não existem.
- Este é um classificador de conjunto fechado: ele não identifica com segurança fotos que não contenham folha nem doenças ausentes das 16 classes.
- Mais variações sobre apenas 696 imagens aumentariam o risco de escolher por acaso uma configuração favorável à validação. O próximo ganho confiável deve vir de mais dados independentes e validação cruzada por propriedade/origem.

Arquivos completos: `machine-learning/benchmark_artifacts/deployment/deployment_manifest.json`, `ranking_validation.json`, `test_predictions.json` e os relatórios individuais de cada candidato.

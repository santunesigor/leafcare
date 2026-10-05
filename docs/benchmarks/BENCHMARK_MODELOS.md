# Benchmark de Modelos — LeafCare

## Escopo

Este documento reúne o benchmark móvel histórico, DINOv2 ViT-S/14 e as rodadas de backbones congelados e ajuste supervisionado executadas em **2026-10-05**. As rodadas isoladas preservaram o modelo Android. Posteriormente, a pedido do usuário, o ensemble de dois MobileNetV3Small e um MobileNetV3Large foi retreinado e integrado como padrão. Dataset, classes e split permanecem os mesmos; os resultados desse novo bundle são registrados separadamente.

O dataset disponibilizado foi verificado contra o manifesto existente: 696 imagens, 16 classes, hashes SHA256 consistentes e split fixo de 489 imagens de treino, 104 de validação e 103 de teste, sem grupos ou hashes de pixels compartilhados entre partições. Isso não comprova independência entre plantas/propriedades: os grupos de origem não têm essa identificação.

Macro-F1 é a métrica principal por causa do desbalanceamento. A tabela é ordenada exclusivamente pela Macro-F1 de validação. O teste já foi conhecido em experimentos anteriores; seus resultados são uma comparação interna, não validação externa de campo.

## Critérios de uso no Android e dados de campo

- **Espera desejada: até 3 segundos no Android**, do início da análise, após a captura/seleção da foto, até o resultado disponível. A medição deve incluir leitura/decodificação, preprocessing, inferência e gravação local; registrar também a apresentação do resultado na interface. A sincronização das fotos continua em segundo plano.
- Registrar mediana e P95 desse fluxo no aparelho de referência **Samsung Galaxy A06**, escolhido pelo usuário. As latências de encoder em GPU/CPU nas tabelas não comprovam o cumprimento da meta de 3 segundos no celular.
- O app já apresenta um indicador circular animado durante a análise. A animação acompanha o trabalho real, e o resultado deve aparecer assim que estiver pronto; a meta não impõe atraso mínimo nem timeout de classificação.
- Fotos novas de campo serão obtidas ao longo do uso e passarão por triagem posterior do bucket privado existente `analysis-photos`. Somente fotos com rótulos revisados poderão compor uma futura rodada; a previsão do app não serve como diagnóstico confirmado para treinamento.
- Na futura triagem, registrar a origem e agrupar fotos da mesma planta/propriedade/sessão quando essa informação estiver disponível. Separar uma avaliação de campo independente antes de usar as demais fotos no treinamento.
- A rodada de fine-tuning foi executada e os dois finalistas pela validação seguiram para exportação experimental. A medição Android continua pendente de aparelho. O dataset/split permanece fixo. A integração do ensemble foi solicitada depois dessas rodadas e está descrita abaixo.

## Ensemble integrado — novo treinamento

A pedido do usuário, a composição histórica foi retreinada a partir de ImageNet:
dois Small (Adam/RMSprop, seeds 42/54) e um Large (AdamW, seed 44). A receita
completa e comandos estão em [Machine Learning](../MACHINE_LEARNING.md). O
preprocessing é o contrato Python/Kotlin do app, center crop/bilinear inteiro,
RGB 0–255. Dataset, manifesto, hashes e split 489/104/103 foram preservados.

O ensemble agora é o padrão do APK 1.1.0: um único `leafcare.tflite` contém as
três redes, média das probabilidades e temperatura. Não há votação por rótulo
nem seleção de rede por imagem. O limiar 0,631628 vem do bundle e
se aplica às probabilidades já calibradas. Isso mantém inferência offline e
preserva o hash/limiar das análises anteriores no histórico.

**Resultados novos:** validação 87,50% / 0,8179 / 99,04%; teste 81,55% / 0,7371 / 99,03%. São 84/103 acertos,
contra 80/103 do baseline anterior. O ensemble histórico acertou 83/103 e teve
Macro-F1 0,7659; o novo acertou uma imagem a mais, mas teve Macro-F1 0,7371.
Esse contraste não foi usado para escolher membros ou procurar outra receita.
O conjunto é pequeno e já conhecido; não comprova generalização em campo.

O TFLite de 17,22 MiB passou na paridade em todas as
104 imagens de validação, Top-1 idêntico e erro máximo 6.795e-06.
Inferência em CPU desktop: mediana/P95 4,42/4,79 ms;
pipeline Python 9,68/10,46 ms,
sem Room/UI. O teste de três segundos no A06 permanece pendente; nenhum aparelho
foi encontrado por ADB. Resultados e versões em
[`benchmark_artifacts/ensemble/`](../../machine-learning/benchmark_artifacts/ensemble/).

## Comparação

`—` indica dado não registrado ou experimento não executado. Parâmetros incluem o classificador para as 16 classes; os novos modelos descartam a cabeça original e, no MobileCLIP2, o encoder textual. Fine-tuning e probe congelado são estratégias distintas: as linhas comparam configurações completas, não isolam o efeito da arquitetura.

| Modelo/configuração | Método | Entrada RGB | Parâmetros | Validação: Top-1 / Macro-F1 / Top-3 | Teste: Top-1 / Macro-F1 / Top-3 | Situação |
|---|---|---|---:|---|---|---|
| Ensemble MobileNetV3 integrado (novo treino) | Média de 3 redes + temperatura | 224×224 | 4.908.432 | 87,50% / 0,8179 / 99,04% | 81,55% / 0,7371 / 99,03% | No app, APK 1.1.0 |
| Ensemble: 2 MobileNetV3Small + MobileNetV3Large | Ensemble de 3 modelos | 224×224 | 4.908.432 | 87,50% / 0,8179 / 100,00% | 80,58% / 0,7659 / 99,03% | Experimental histórico |
| DINOv2 ViT-B/14, ajuste parcial | Fine-tuning parcial | 224×224 | 85.749.520 | 92,31% / 0,8069 / 98,08% | 83,50% / 0,7201 / 100,00% | Experimental ajustado |
| TinyViT-11M, ajuste do classificador | Ajuste do classificador | 224×224 | 10.555.156 | 87,50% / 0,7786 / 98,08% | 80,58% / 0,7144 / 96,12% | Experimental ajustado |
| MobileNetV3Small, dropout 0,15 | Fine-tuning | 224×224 | 948.352 | 82,69% / 0,7785 / 99,04% | — | Histórico |
| MobileNetV3Small anterior | Fine-tuning | 224×224 | 948.352 | 82,69% / 0,7750 / 100,00% | 77,67% / 0,7157 / 97,09% | Integrado anteriormente |
| MobileNetV3Small, RMSprop | Fine-tuning | 224×224 | 948.352 | 82,69% / 0,7677 / 99,04% | — | Histórico |
| DINOv2 ViT-S/14 | Probe balanceado | 224×224 | 22.068.880 | 88,46% / 0,7676 / 99,04% | 84,47% / 0,7497 / 97,09% | Experimental histórico |
| MobileNetV3Small, sem pesos de classe | Fine-tuning | 224×224 | 948.352 | 86,54% / 0,7671 / 97,12% | — | Histórico |
| MobileNetV3Small, Adam | Fine-tuning | 224×224 | 948.352 | 81,73% / 0,7652 / 98,08% | — | Histórico |
| DINOv2 ViT-B/14 | Probe sem pesos | 224×224 | 85.749.520 | 87,50% / 0,7624 / 98,08% | 87,38% / 0,7551 / 97,09% | Experimental novo |
| MobileNetV3Large, base | Fine-tuning | 224×224 | 3.011.728 | 85,58% / 0,7592 / 99,04% | — | Histórico |
| TinyViT-11M | Probe sem pesos | 224×224 | 10.555.156 | 85,58% / 0,7566 / 98,08% | 83,50% / 0,7492 / 97,09% | Experimental novo |
| MobileNetV3Small, base | Fine-tuning | 224×224 | 948.352 | 79,81% / 0,7492 / 100,00% | — | Histórico |
| MobileNetV2, base | Fine-tuning | 224×224 | 2.278.480 | 82,69% / 0,7462 / 98,08% | — | Histórico |
| MobileNetV4 Conv Medium, fine-tuning | Fine-tuning | 224×224 | 8.455.008 | 83,65% / 0,7430 / 98,08% | 75,73% / 0,6220 / 93,20% | Experimental ajustado |
| MobileCLIP2-S0 image encoder, fine-tuning | Fine-tuning | 256×256 | 11.415.184 | 82,69% / 0,7376 / 95,19% | 79,61% / 0,6872 / 97,09% | Experimental ajustado |
| EfficientNetB0, augmentation forte | Fine-tuning | 224×224 | 4.070.067 | 82,69% / 0,7253 / 98,08% | — | Histórico |
| MobileNetV3Small, augmentation forte | Fine-tuning | 224×224 | 948.352 | 75,96% / 0,7228 / 98,08% | — | Histórico |
| MobileNetV3Large, augmentation forte | Fine-tuning | 224×224 | 3.011.728 | 77,88% / 0,7189 / 98,08% | — | Histórico |
| EfficientNetB0, base | Fine-tuning | 224×224 | 4.070.067 | 79,81% / 0,7130 / 97,12% | — | Histórico |
| TinyViT-5M, fine-tuning | Fine-tuning | 224×224 | 5.076.900 | 87,50% / 0,7113 / 96,15% | 82,52% / 0,7970 / 97,09% | Experimental ajustado |
| MobileNetV4 Conv Medium | Probe balanceado | 224×224 | 8.455.008 | 78,85% / 0,7020 / 95,19% | 76,70% / 0,6752 / 93,20% | Experimental novo |
| TinyViT-5M | Probe sem pesos | 224×224 | 5.076.900 | 85,58% / 0,6912 / 96,15% | 83,50% / 0,8040 / 99,03% | Experimental novo |
| EfficientNetV2B0, base | Fine-tuning | 224×224 | 5.939.808 | 76,92% / 0,6806 / 94,23% | — | Histórico |
| NASNetMobile, base | Fine-tuning | 224×224 | 4.286.628 | 68,27% / 0,6515 / 92,31% | — | Histórico |
| EfficientNetV2B0, augmentation forte | Fine-tuning | 224×224 | 5.939.808 | 71,15% / 0,6451 / 92,31% | — | Histórico |
| MobileCLIP2-S0 image encoder | Probe balanceado | 256×256 | 11.365.712 | 73,08% / 0,6343 / 95,19% | 78,64% / 0,7920 / 96,12% | Experimental novo |
| DINOv3 ViT-S/16 | Pendente | — | — | — | — | Pesos restritos |

O benchmark móvel histórico treinou 14 configurações e incluiu o modelo anterior como candidato. Também avaliou combinações de dois ou três dos seis candidatos mais bem classificados, totalizando 50 estratégias. O ensemble foi selecionado pela validação. Os candidatos individuais históricos possuem apenas métricas de validação publicadas.

## Resultados com rejeição por confiança

As linhas abaixo possuem avaliação de teste registrada. Para os novos modelos, temperatura e limiar foram escolhidos na validação, visando o maior número de previsões aceitas com pelo menos 90% de acerto nessa partição. Esse objetivo não garante a mesma precisão no teste. Os demais candidatos históricos não têm essa avaliação disponível.

| Modelo | Temperatura | Limiar | Cobertura no teste | Acurácia das previsões aceitas |
|---|---:|---:|---:|---:|
| Ensemble MobileNetV3 integrado (novo treino) | 0,8351 | 0,6316 | 82/103 (79,61%) | 76/82 (92,68%) |
| Ensemble: 2 MobileNetV3Small + MobileNetV3Large | 0,8421 | 0,7400 | 78/103 (75,73%) | 72/78 (92,31%) |
| MobileNetV3Small anterior | Sem calibração | 0,7000 | 79/103 (76,70%) | 71/79 (89,87%) |
| DINOv2 ViT-S/14 | 1,2188 | 0,5738 | 93/103 (90,29%) | 84/93 (90,32%) |
| DINOv2 ViT-B/14 | 1,4049 | 0,4580 | 93/103 (90,29%) | 85/93 (91,40%) |
| TinyViT-11M | 0,6781 | 0,5076 | 99/103 (96,12%) | 85/99 (85,86%) |
| MobileNetV4 Conv Medium | 1,0545 | 0,6903 | 69/103 (66,99%) | 63/69 (91,30%) |
| TinyViT-5M | 0,8153 | 0,5482 | 90/103 (87,38%) | 77/90 (85,56%) |
| MobileCLIP2-S0 image encoder | 0,1798 | 0,7018 | 59/103 (57,28%) | 50/59 (84,75%) |
| DINOv2 ViT-B/14, ajuste parcial | 1,8385 | 0,4675 | 97/103 (94,17%) | 85/97 (87,63%) |
| TinyViT-11M, ajuste do classificador | 0,6328 | 0,4923 | 95/103 (92,23%) | 82/95 (86,32%) |
| MobileNetV4 Conv Medium, fine-tuning | 1,4463 | 0,6086 | 80/103 (77,67%) | 69/80 (86,25%) |
| MobileCLIP2-S0 image encoder, fine-tuning | 0,7102 | 0,5381 | 83/103 (80,58%) | 74/83 (89,16%) |
| TinyViT-5M, fine-tuning | 1,0914 | 0,5494 | 91/103 (88,35%) | 79/91 (86,81%) |

## Tamanho e custo registrado

Os atributos também incluem os candidatos antigos. Tamanhos são dos artefatos indicados, em MiB (1.048.576 bytes); arquivos Keras podem incluir estado do otimizador e não equivalem a um bundle mobile. Nos novos modelos, o tamanho soma encoder visual Safetensors e probe NumPy, sem encoder textual ou cabeça original. Esses pesos permanecem no cache local; só o probe e os resultados estão versionados. Os checkpoints ajustados também ficam no cache local. A exportação dos finalistas é registrada separadamente abaixo; nenhum candidato novo foi medido no Android.

As latências dos probes congelados medem somente o encoder na GTX 1650, float32, batch 1, cinco aquecimentos e 30 amostras com sincronização CUDA. Excluem decode, preprocessing, transferência e probe. Nos ajustes, os tempos incluem encoder e classificador, sem preprocessing ou calibração. A memória é o pico alocado pelo PyTorch no mesmo perfil, incluindo o modelo residente; não é a RAM total do processo nem uma estimativa Android.

As latências históricas de TFLite foram obtidas em CPU de computador com hardware não registrado; DINOv2-S foi medido na GTX 1050 Ti. **Não compare diretamente essas latências com as da GTX 1650.** Não se atribuiu ao bundle atual a medição antiga de outro hash nem se somaram tempos individuais como se fossem uma medição do ensemble.

| Modelo/configuração | Artefato registrado | Tamanho (MiB) | Mediana / P95 (ms) | Dispositivo da medição | Pico CUDA batch 1 (MiB) | Android (ms) |
|---|---|---:|---:|---|---:|---:|
| Ensemble MobileNetV3 integrado (novo treino) | TFLite float32 único, média/calibração embutidas | 17,22 | 4,42 / 4,79 | Intel Core i5-13400, CPU, 2 threads | — | — |
| Ensemble: 2 MobileNetV3Small + MobileNetV3Large | 3 TFLite float32 | 18,60 | — | — | — | — |
| DINOv2 ViT-B/14, ajuste parcial | Checkpoint ajustado Safetensors | 327,13 | 37,11 / 37,17 | GTX 1650 | 409,82 | — |
| TinyViT-11M, ajuste do classificador | Checkpoint ajustado Safetensors | 40,35 | 5,61 / 6,61 | GTX 1650 | 120,57 | — |
| MobileNetV3Small, dropout 0,15 | Keras histórico | 9,66 | — | — | — | — |
| MobileNetV3Small anterior | TFLite float32 | 3,59 | — | — | — | — |
| MobileNetV3Small, RMSprop | TFLite float32 | 3,60 | 1,29 / — | CPU histórica; hardware não registrado | — | — |
| DINOv2 ViT-S/14 | Checkpoint PyTorch + probe | 84,28 | 15,12 / 15,46 | GTX 1050 Ti histórica | — | — |
| MobileNetV3Small, sem pesos de classe | Keras histórico | 8,54 | — | — | — | — |
| MobileNetV3Small, Adam | Keras histórico | 8,54 | — | — | — | — |
| DINOv2 ViT-B/14 | Encoder Safetensors + probe | 327,21 | 36,07 / 38,66 | GTX 1650 | 344,75 | — |
| MobileNetV3Large, base | TFLite float32 | 11,40 | 2,45 / — | CPU histórica; hardware não registrado | — | — |
| TinyViT-11M | Encoder Safetensors + probe | 40,37 | 5,44 / 5,49 | GTX 1650 | 70,46 | — |
| MobileNetV3Small, base | Keras histórico | 8,54 | — | — | — | — |
| MobileNetV2, base | Keras histórico | 22,07 | — | — | — | — |
| MobileNetV4 Conv Medium, fine-tuning | Checkpoint ajustado Safetensors | 32,56 | 2,77 / 2,78 | GTX 1650 | 90,03 | — |
| MobileCLIP2-S0 image encoder, fine-tuning | Checkpoint ajustado Safetensors | 43,84 | 8,10 / 8,34 | GTX 1650 | 122,25 | — |
| EfficientNetB0, augmentation forte | Keras histórico | 36,66 | — | — | — | — |
| MobileNetV3Small, augmentation forte | Keras histórico | 9,66 | — | — | — | — |
| MobileNetV3Large, augmentation forte | Keras histórico | 31,06 | — | — | — | — |
| EfficientNetB0, base | Keras histórico | 31,99 | — | — | — | — |
| TinyViT-5M, fine-tuning | Checkpoint ajustado Safetensors | 19,44 | 4,17 / 5,29 | GTX 1650 | 73,93 | — |
| MobileNetV4 Conv Medium | Encoder Safetensors + probe | 32,63 | 3,49 / 3,63 | GTX 1650 | 40,36 | — |
| TinyViT-5M | Encoder Safetensors + probe | 19,45 | 4,10 / 5,08 | GTX 1650 | 45,35 | — |
| EfficientNetV2B0, base | Keras histórico | 36,28 | — | — | — | — |
| NASNetMobile, base | Keras histórico | 24,33 | — | — | — | — |
| EfficientNetV2B0, augmentation forte | Keras histórico | 41,02 | — | — | — | — |
| MobileCLIP2-S0 image encoder | Encoder Safetensors + probe | 43,46 | 5,68 / 6,63 | GTX 1650 | 61,26 | — |
| DINOv3 ViT-S/16 | — | — | — | — | — | — |

## Exportações experimentais para Android

Os dois finalistas foram convertidos em TFLite float32 usando LiteRT Torch 0.8.0 e executados no LiteRT 2.1.2 em CPU desktop, duas threads. Normalização, temperatura e softmax estão no grafo; entrada NHWC RGB 0–255 e saída nas 16 classes. Resize/crop bicúbicos oficiais continuam fora do grafo. O contrato atual do app usa outro preprocessing e metadados; os arquivos experimentais não foram copiados para os assets.

A paridade foi conferida em todas as 104 imagens de validação: concordância Top-1 de 100% tanto com PyTorch CPU quanto com os logits do treino, com erro absoluto máximo abaixo de 0,0001. Não houve nova seleção com base no teste.

| Finalista | TFLite (MiB) | Inferência CPU: mediana / P95 (ms) | Pipeline Python: mediana / P95 (ms) | Maior erro absoluto vs. PyTorch | Galaxy A06: fluxo completo |
|---|---:|---:|---:|---:|---|
| DINOv2-B, ajuste parcial | 327,27 | 224,99 / 266,35 | 226,35 / 261,99 | 8.34e-06 | Pendente |
| TinyViT-11M, cabeça ajustada | 47,89 | 23,29 / 23,77 | 26,27 / 27,16 | 7.03e-06 | Pendente |

Cinco aquecimentos e 30 amostras; CPU Intel Core i5-13400. Pipeline Python inclui leitura, decode, preprocessing, inferência e Top-3; exclui Room/UI. A meta de até três segundos **não foi verificada no Galaxy A06**: `adb devices -l` não encontrou aparelho conectado. Tamanho dos pesos e tempo desktop não comprovam viabilidade no celular. Os TFLites ficam em `.cache/mobile/`, ignorado pelo Git; hashes, contratos, paridade e tempos estão em `mobile_export.json` de cada finalista.

As novas colunas de exportação no CSV também recuperam tamanhos e tempos TFLite históricos quando conhecidos. Campos sem registro permanecem vazios; não se inventaram medições para os candidatos antigos.

## Protocolo dos probes congelados

- Candidatos executados: MobileNetV4 Conv Medium, TinyViT-5M, TinyViT-11M, MobileCLIP2-S0 visual e DINOv2 ViT-B/14. DINOv3 ViT-S/16 foi tentado, mas os pesos oficiais exigem acesso aprovado e retornaram HTTP 401. Não foi treinado nem recebeu métricas.
- Backbones congelados, seed 42, float32, batch 8, sem augmentation ou padronização adicional das características. Dois probes predefinidos por modelo: regressão logística `C=1`, com/sem pesos balanceados, LBFGS até 1000 iterações; ambos convergiram em todos os candidatos executados.
- Seleção exclusivamente na validação por Macro-F1, desempate Top-1, Top-3 e probe sem pesos. Temperatura ajustada por NLL; limiar também escolhido apenas na validação. Configuração persistida antes de decodificar o teste para inferência; a leitura inicial dos arquivos de teste foi somente auditoria de hashes.
- MobileCLIP2-S0 usa seu preprocessing oficial: RGB 0–1, média 0/desvio 1, 256×256. A reparametrização foi conferida contra o encoder original em entrada sintética. Uma execução preliminar usou a normalização padrão do OpenCLIP por engano; foi corrigida conforme a implementação oficial e substituída. Seus números preliminares não entram nas tabelas.
- DINOv2-B usa timm, concatenação de CLS e média dos patches e adaptação das posições para 224×224. O DINOv2-S histórico usa a implementação Meta. Essa diferença de implementação deve ser considerada na comparação.
- MobileNetV4 usa pesos ImageNet-1k treinados pelo timm; TinyViT usa pesos destilados ImageNet-22k e ajustados no ImageNet-1k; MobileCLIP2 usa DFNDR-2B; DINOv2 usa LVD-142M. Revisões dos checkpoints, hashes, processamento, dimensões e versões estão nos artefatos.

Execução e reprodução: [`experiments/vision/README.md`](../../machine-learning/experiments/vision/README.md). A comparação completa em formato tabular está em [`comparison.csv`](../../machine-learning/benchmark_artifacts/vision/comparison.csv), gerada por `python -m experiments.vision.report`. Campos vazios significam ausência de medição.

## Ajuste supervisionado e finalistas

Os cinco candidatos públicos partiram de seus probes registrados. Receita predefinida: seed 42, batch 8, AdamW, pesos de classe somente do treino, três épocas de ajuste da cabeça e até 15 de fine-tuning, com parada após cinco épocas sem melhora de validação. Taxas: 0,001 na cabeça inicial; 0,0001 na cabeça e 0,00001 no backbone durante o ajuste; weight decay 0,0001. Estatísticas de BatchNorm congeladas; flips horizontal/vertical, rotação de até 12 graus e pequenas variações de brilho, contraste e saturação. No DINOv2-B foram liberados somente os dois últimos blocos e a normalização final; nos demais, todo o encoder.

A melhor época foi escolhida pela Macro-F1 de validação, com desempate Top-1/Top-3 e preferência pela época anterior. O checkpoint inicial também era elegível. Temperatura e limiar foram definidos na validação antes da inferência de teste. O teste não foi usado para escolher épocas ou os dois finalistas da rodada: **DINOv2-B ajustado (Macro-F1 0,8069) e TinyViT-11M com cabeça ajustada (0,7786)**. No TinyViT-11M, a época vencedora foi a primeira do ajuste do classificador; liberar o backbone não trouxe melhora de validação.

Nenhum dos cinco ajustes melhorou a Macro-F1 de teste de seu probe congelado. O DINOv2-B ajustado teve Top-1 de 83,50% e Macro-F1 de 0,7201, apesar de melhorar a validação e atingir Top-3 de 100%. Isso reforça a necessidade das fotos futuras de campo para avaliar generalização; a rodada de ajustes dos novos backbones permaneceu experimental. A integração do ensemble foi uma etapa posterior solicitada pelo usuário.

Durante a verificação da exportação, identificou-se um cache de atenção TinyViT que sobrevivia à restauração do checkpoint selecionado. O runner agora invalida esse cache, e os dois ajustes TinyViT foram repetidos com a mesma receita. Somente os resultados corrigidos entram nas tabelas.

## Leitura dos resultados

- DINOv2 ViT-B/14 congelado teve a maior Top-1 de teste registrada: **90/103 (87,38%)**, contra 87/103 do DINOv2-S, 84/103 do ensemble integrado e 80/103 do MobileNetV3Small anterior. Sua Macro-F1 foi 0,7551; não superou o ensemble histórico nessa métrica.
- TinyViT-5M teve a maior Macro-F1 de teste registrada (**0,8040**) e Top-3 de 99,03%, com aproximadamente 5,08 milhões de parâmetros. Entretanto, sua Macro-F1 de validação foi 0,6912 e suas previsões aceitas tiveram apenas 85,56% de acerto no teste. Não é um vencedor confirmado.
- MobileCLIP2-S0 teve Macro-F1 de teste 0,7920, mas Top-1 de 78,64% e cobertura de 57,28%, com 84,75% de acerto entre as previsões aceitas. Isso limita a interpretação do ganho em Macro-F1.
- Nesta configuração congelada, MobileNetV4 não melhorou a Macro-F1 do MobileNetV3Small anterior. Os resultados com ajuste supervisionado aparecem em linhas separadas. TinyViT-11M também não superou a versão 5M em Macro-F1 de teste.
- A maior Macro-F1 de validação é compartilhada pelo ensemble histórico e pelo novo integrado (0,8179). Após o ajuste, DINOv2-B chegou a Top-3 de 100% no teste (103/103); isso não significa 100% de acerto na primeira hipótese. Os novos backbones permaneceram experimentais; o ensemble foi integrado na rodada adicional descrita neste documento.

## Validações e limitações

- Na integração do ensemble, 37 testes Python do pipeline e 29 dos experimentos passaram. A integração TensorFlow foi executada no ambiente do ensemble. O bundle real passou na validação; o Keras combinado recarregado concordou com o TFLite na imagem de referência. As conversões preservaram Top-1 nas 104 imagens de validação e passaram no limite de erro de probabilidades.
- A rodada verificou hashes, divisão por grupos e ordem das classes. As métricas de validação/teste dos probes e dos cinco ajustes foram recalculadas a partir dos logits versionados; previsões individuais, confusões e calibração estão disponíveis para revisão.
- Há linhas separadas para probes congelados e ajustes supervisionados; os candidatos móveis históricos também receberam fine-tuning. Diferenças de pré-treinamento, resolução, processamento e estratégia impedem atribuir ganhos somente à arquitetura.
- Quatro classes têm apenas uma ou duas imagens no teste. Trocas de poucos acertos podem alterar bastante a Macro-F1; a diferença entre validação e teste dos TinyViTs/MobileCLIP2 reforça essa instabilidade.
- Não há validação externa de campo, testes com imagens fora das 16 classes nem medição Android dos experimentos novos. Softmax e calibração não representam certeza agronômica.
- A validação é pequena e já recebeu várias buscas históricas. Novas comparações não transformam o teste conhecido em uma avaliação independente.

## Artefatos e fontes

- Resultados novos: [`benchmark_artifacts/vision/`](../../machine-learning/benchmark_artifacts/vision/) e [`vision_finetune/`](../../machine-learning/benchmark_artifacts/vision_finetune/); por candidato: configuração, hashes, probes, logits, previsões, métricas por classe e matrizes de confusão. Pesos pré-treinados e dataset permanecem locais, ignorados pelo Git.
- Resultados históricos: commit `24389c3`. Seus atributos foram recuperados em [`historical_summary.json`](../../machine-learning/benchmark_artifacts/vision/historical_summary.json), com o hash completo de origem. Isso não representa retreinamento ou novas medições; as métricas individuais de teste não estão disponíveis.
- Modelo integrado: [`artifacts/`](../../machine-learning/artifacts/). DINOv2-S histórico: [`benchmark_artifacts/dinov2/`](../../machine-learning/benchmark_artifacts/dinov2/).
- Fontes primárias: [MobileNetV4](https://arxiv.org/abs/2404.10518), [checkpoint MobileNetV4/timm](https://huggingface.co/timm/mobilenetv4_conv_medium.e500_r224_in1k), [TinyViT](https://github.com/microsoft/Cream/tree/main/TinyViT), [MobileCLIP2](https://github.com/apple-aiml-research/ml-mobileclip), [DINOv2](https://github.com/facebookresearch/dinov2), [DINOv3 e acesso aos pesos](https://github.com/facebookresearch/dinov3).

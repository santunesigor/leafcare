# Benchmark de Modelos — LeafCare

## Escopo

Este documento reúne o benchmark móvel histórico, DINOv2 ViT-S/14 e a rodada de backbones congelados executada em **2026-10-05**. O modelo integrado continua sendo MobileNetV3Small; nenhum asset Android, threshold do app, imagem, classe ou split foi alterado. A escolha para implantação será feita posteriormente.

O dataset disponibilizado foi verificado contra o manifesto existente: 696 imagens, 16 classes, hashes SHA256 consistentes e split fixo de 489 imagens de treino, 104 de validação e 103 de teste, sem grupos ou hashes de pixels compartilhados entre partições. Isso não comprova independência entre plantas/propriedades: os grupos de origem não têm essa identificação.

Macro-F1 é a métrica principal por causa do desbalanceamento. A tabela é ordenada exclusivamente pela Macro-F1 de validação. O teste já foi conhecido em experimentos anteriores; seus resultados são uma comparação interna, não validação externa de campo.

## Critérios para a próxima rodada

- **Espera desejada: até 3 segundos no Android**, do início da análise, após a captura/seleção da foto, até o resultado disponível. A medição deve incluir leitura/decodificação, preprocessing, inferência e gravação local; registrar também a apresentação do resultado na interface. A sincronização das fotos continua em segundo plano.
- Registrar mediana e P95 desse fluxo no aparelho de referência, que ainda não foi definido. As latências de encoder em GPU/CPU nas tabelas não comprovam o cumprimento da meta de 3 segundos no celular.
- O app já apresenta um indicador circular animado durante a análise. A animação acompanha o trabalho real, e o resultado deve aparecer assim que estiver pronto; a meta não impõe atraso mínimo nem timeout de classificação.
- Fotos novas de campo serão obtidas ao longo do uso e passarão por triagem posterior do bucket privado existente `analysis-photos`. Somente fotos com rótulos revisados poderão compor uma futura rodada; a previsão do app não serve como diagnóstico confirmado para treinamento.
- Na futura triagem, registrar a origem e agrupar fotos da mesma planta/propriedade/sessão quando essa informação estiver disponível. Separar uma avaliação de campo independente antes de usar as demais fotos no treinamento.
- A próxima comparação investigará fine-tuning dos candidatos, seguido de exportação experimental e medição Android dos finalistas. O dataset/split atual e o modelo integrado permanecem como referências; ainda não há escolha de substituição.

## Comparação

`—` indica dado não registrado ou experimento não executado. Parâmetros incluem o classificador para as 16 classes; os novos modelos descartam a cabeça original e, no MobileCLIP2, o encoder textual. Fine-tuning e probe congelado são estratégias distintas: as linhas comparam configurações completas, não isolam o efeito da arquitetura.

| Modelo/configuração | Método | Entrada RGB | Parâmetros | Validação: Top-1 / Macro-F1 / Top-3 | Teste: Top-1 / Macro-F1 / Top-3 | Situação |
|---|---|---|---:|---|---|---|
| Ensemble: 2 MobileNetV3Small + MobileNetV3Large | Ensemble de 3 modelos | 224×224 | 4.908.432 | 87,50% / 0,8179 / 100,00% | 80,58% / 0,7659 / 99,03% | Experimental histórico |
| MobileNetV3Small, dropout 0,15 | Fine-tuning | 224×224 | 948.352 | 82,69% / 0,7785 / 99,04% | — | Histórico |
| MobileNetV3Small integrado | Fine-tuning | 224×224 | 948.352 | 82,69% / 0,7750 / 100,00% | 77,67% / 0,7157 / 97,09% | No app |
| MobileNetV3Small, RMSprop | Fine-tuning | 224×224 | 948.352 | 82,69% / 0,7677 / 99,04% | — | Histórico |
| DINOv2 ViT-S/14 | Probe balanceado | 224×224 | 22.068.880 | 88,46% / 0,7676 / 99,04% | 84,47% / 0,7497 / 97,09% | Experimental histórico |
| MobileNetV3Small, sem pesos de classe | Fine-tuning | 224×224 | 948.352 | 86,54% / 0,7671 / 97,12% | — | Histórico |
| MobileNetV3Small, Adam | Fine-tuning | 224×224 | 948.352 | 81,73% / 0,7652 / 98,08% | — | Histórico |
| DINOv2 ViT-B/14 | Probe sem pesos | 224×224 | 85.749.520 | 87,50% / 0,7624 / 98,08% | 87,38% / 0,7551 / 97,09% | Experimental novo |
| MobileNetV3Large, base | Fine-tuning | 224×224 | 3.011.728 | 85,58% / 0,7592 / 99,04% | — | Histórico |
| TinyViT-11M | Probe sem pesos | 224×224 | 10.555.156 | 85,58% / 0,7566 / 98,08% | 83,50% / 0,7492 / 97,09% | Experimental novo |
| MobileNetV3Small, base | Fine-tuning | 224×224 | 948.352 | 79,81% / 0,7492 / 100,00% | — | Histórico |
| MobileNetV2, base | Fine-tuning | 224×224 | 2.278.480 | 82,69% / 0,7462 / 98,08% | — | Histórico |
| EfficientNetB0, augmentation forte | Fine-tuning | 224×224 | 4.070.067 | 82,69% / 0,7253 / 98,08% | — | Histórico |
| MobileNetV3Small, augmentation forte | Fine-tuning | 224×224 | 948.352 | 75,96% / 0,7228 / 98,08% | — | Histórico |
| MobileNetV3Large, augmentation forte | Fine-tuning | 224×224 | 3.011.728 | 77,88% / 0,7189 / 98,08% | — | Histórico |
| EfficientNetB0, base | Fine-tuning | 224×224 | 4.070.067 | 79,81% / 0,7130 / 97,12% | — | Histórico |
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
| Ensemble: 2 MobileNetV3Small + MobileNetV3Large | 0,8421 | 0,7400 | 78/103 (75,73%) | 72/78 (92,31%) |
| MobileNetV3Small integrado | Sem calibração | 0,7000 | 79/103 (76,70%) | 71/79 (89,87%) |
| DINOv2 ViT-S/14 | 1,2188 | 0,5738 | 93/103 (90,29%) | 84/93 (90,32%) |
| DINOv2 ViT-B/14 | 1,4049 | 0,4580 | 93/103 (90,29%) | 85/93 (91,40%) |
| TinyViT-11M | 0,6781 | 0,5076 | 99/103 (96,12%) | 85/99 (85,86%) |
| MobileNetV4 Conv Medium | 1,0545 | 0,6903 | 69/103 (66,99%) | 63/69 (91,30%) |
| TinyViT-5M | 0,8153 | 0,5482 | 90/103 (87,38%) | 77/90 (85,56%) |
| MobileCLIP2-S0 image encoder | 0,1798 | 0,7018 | 59/103 (57,28%) | 50/59 (84,75%) |

## Tamanho e custo registrado

Os atributos também incluem os candidatos antigos. Tamanhos são dos artefatos indicados, em MiB (1.048.576 bytes); arquivos Keras podem incluir estado do otimizador e não equivalem a um bundle mobile. Nos novos modelos, o tamanho soma encoder visual Safetensors e probe NumPy, sem encoder textual ou cabeça original. Esses pesos permanecem no cache local; só o probe e os resultados estão versionados. **Nenhum modelo novo foi exportado ou medido no Android.**

As latências novas medem somente o encoder na GTX 1650, float32, batch 1, cinco aquecimentos e 30 amostras com sincronização CUDA. Excluem decode, preprocessing, transferência e probe. A memória é o pico alocado pelo PyTorch no mesmo perfil, incluindo o modelo residente; não é a RAM total do processo nem uma estimativa Android.

As latências históricas de TFLite foram obtidas em CPU de computador com hardware não registrado; DINOv2-S foi medido na GTX 1050 Ti. **Não compare diretamente essas latências com as da GTX 1650.** Não se atribuiu ao bundle atual a medição antiga de outro hash nem se somaram tempos individuais como se fossem uma medição do ensemble.

| Modelo/configuração | Artefato registrado | Tamanho (MiB) | Mediana / P95 (ms) | Dispositivo da medição | Pico CUDA batch 1 (MiB) | Android (ms) |
|---|---|---:|---:|---|---:|---:|
| Ensemble: 2 MobileNetV3Small + MobileNetV3Large | 3 TFLite float32 | 18,60 | — | — | — | — |
| MobileNetV3Small, dropout 0,15 | Keras histórico | 9,66 | — | — | — | — |
| MobileNetV3Small integrado | TFLite float32 | 3,59 | — | — | — | — |
| MobileNetV3Small, RMSprop | TFLite float32 | 3,60 | 1,29 / — | CPU histórica; hardware não registrado | — | — |
| DINOv2 ViT-S/14 | Checkpoint PyTorch + probe | 84,28 | 15,12 / 15,46 | GTX 1050 Ti histórica | — | — |
| MobileNetV3Small, sem pesos de classe | Keras histórico | 8,54 | — | — | — | — |
| MobileNetV3Small, Adam | Keras histórico | 8,54 | — | — | — | — |
| DINOv2 ViT-B/14 | Encoder Safetensors + probe | 327,21 | 36,07 / 38,66 | GTX 1650 | 344,75 | — |
| MobileNetV3Large, base | TFLite float32 | 11,40 | 2,45 / — | CPU histórica; hardware não registrado | — | — |
| TinyViT-11M | Encoder Safetensors + probe | 40,37 | 5,44 / 5,49 | GTX 1650 | 70,46 | — |
| MobileNetV3Small, base | Keras histórico | 8,54 | — | — | — | — |
| MobileNetV2, base | Keras histórico | 22,07 | — | — | — | — |
| EfficientNetB0, augmentation forte | Keras histórico | 36,66 | — | — | — | — |
| MobileNetV3Small, augmentation forte | Keras histórico | 9,66 | — | — | — | — |
| MobileNetV3Large, augmentation forte | Keras histórico | 31,06 | — | — | — | — |
| EfficientNetB0, base | Keras histórico | 31,99 | — | — | — | — |
| MobileNetV4 Conv Medium | Encoder Safetensors + probe | 32,63 | 3,49 / 3,63 | GTX 1650 | 40,36 | — |
| TinyViT-5M | Encoder Safetensors + probe | 19,45 | 4,10 / 5,08 | GTX 1650 | 45,35 | — |
| EfficientNetV2B0, base | Keras histórico | 36,28 | — | — | — | — |
| NASNetMobile, base | Keras histórico | 24,33 | — | — | — | — |
| EfficientNetV2B0, augmentation forte | Keras histórico | 41,02 | — | — | — | — |
| MobileCLIP2-S0 image encoder | Encoder Safetensors + probe | 43,46 | 5,68 / 6,63 | GTX 1650 | 61,26 | — |
| DINOv3 ViT-S/16 | — | — | — | — | — | — |

## Protocolo dos novos experimentos

- Candidatos executados: MobileNetV4 Conv Medium, TinyViT-5M, TinyViT-11M, MobileCLIP2-S0 visual e DINOv2 ViT-B/14. DINOv3 ViT-S/16 foi tentado, mas os pesos oficiais exigem acesso aprovado e retornaram HTTP 401. Não foi treinado nem recebeu métricas.
- Backbones congelados, seed 42, float32, batch 8, sem augmentation ou padronização adicional das características. Dois probes predefinidos por modelo: regressão logística `C=1`, com/sem pesos balanceados, LBFGS até 1000 iterações; ambos convergiram em todos os candidatos executados.
- Seleção exclusivamente na validação por Macro-F1, desempate Top-1, Top-3 e probe sem pesos. Temperatura ajustada por NLL; limiar também escolhido apenas na validação. Configuração persistida antes de decodificar o teste para inferência; a leitura inicial dos arquivos de teste foi somente auditoria de hashes.
- MobileCLIP2-S0 usa seu preprocessing oficial: RGB 0–1, média 0/desvio 1, 256×256. A reparametrização foi conferida contra o encoder original em entrada sintética. Uma execução preliminar usou a normalização padrão do OpenCLIP por engano; foi corrigida conforme a implementação oficial e substituída. Seus números preliminares não entram nas tabelas.
- DINOv2-B usa timm, concatenação de CLS e média dos patches e adaptação das posições para 224×224. O DINOv2-S histórico usa a implementação Meta. Essa diferença de implementação deve ser considerada na comparação.
- MobileNetV4 usa pesos ImageNet-1k treinados pelo timm; TinyViT usa pesos destilados ImageNet-22k e ajustados no ImageNet-1k; MobileCLIP2 usa DFNDR-2B; DINOv2 usa LVD-142M. Revisões dos checkpoints, hashes, processamento, dimensões e versões estão nos artefatos.

Execução e reprodução: [`experiments/vision/README.md`](../../machine-learning/experiments/vision/README.md). A comparação completa em formato tabular está em [`comparison.csv`](../../machine-learning/benchmark_artifacts/vision/comparison.csv), gerada por `python -m experiments.vision.report`. Campos vazios significam ausência de medição.

## Leitura dos resultados

- DINOv2 ViT-B/14 teve a maior Top-1 de teste registrada: **90/103 (87,38%)**, contra 87/103 do DINOv2-S e 80/103 do modelo integrado. Sua Macro-F1 foi 0,7551; não superou o ensemble histórico nessa métrica.
- TinyViT-5M teve a maior Macro-F1 de teste registrada (**0,8040**) e Top-3 de 99,03%, com aproximadamente 5,08 milhões de parâmetros. Entretanto, sua Macro-F1 de validação foi 0,6912 e suas previsões aceitas tiveram apenas 85,56% de acerto no teste. Não é um vencedor confirmado.
- MobileCLIP2-S0 teve Macro-F1 de teste 0,7920, mas Top-1 de 78,64% e cobertura de 57,28%, com 84,75% de acerto entre as previsões aceitas. Isso limita a interpretação do ganho em Macro-F1.
- Nesta configuração congelada, MobileNetV4 não melhorou a Macro-F1 do modelo atual. Isso não avalia seu potencial com fine-tuning. TinyViT-11M também não superou a versão 5M em Macro-F1 de teste.
- A melhor Macro-F1 de validação continua sendo a do ensemble histórico (0,8179). A maior Top-3 de teste, 99,03%, é compartilhada por ensemble e TinyViT-5M. Nenhum modelo foi escolhido para substituir o app.

## Validações e limitações

- A rodada verificou hashes, divisão por grupos e ordem das classes. As métricas de validação/teste dos cinco modelos foram recalculadas a partir dos logits versionados; previsões individuais, confusões e calibração estão disponíveis para revisão.
- Os backbones novos não receberam fine-tuning; os candidatos móveis históricos receberam. Diferenças de pré-treinamento, resolução, processamento e estratégia impedem atribuir ganhos somente à arquitetura.
- Quatro classes têm apenas uma ou duas imagens no teste. Trocas de poucos acertos podem alterar bastante a Macro-F1; a diferença entre validação e teste dos TinyViTs/MobileCLIP2 reforça essa instabilidade.
- Não há validação externa de campo, testes com imagens fora das 16 classes nem medição Android dos experimentos novos. Softmax e calibração não representam certeza agronômica.
- A validação é pequena e já recebeu várias buscas históricas. Novas comparações não transformam o teste conhecido em uma avaliação independente.

## Artefatos e fontes

- Resultados novos: [`benchmark_artifacts/vision/`](../../machine-learning/benchmark_artifacts/vision/); por candidato: configuração, hashes, probes, logits, previsões, métricas por classe e matrizes de confusão. Pesos pré-treinados e dataset permanecem locais, ignorados pelo Git.
- Resultados históricos: commit `24389c3`. Seus atributos foram recuperados em [`historical_summary.json`](../../machine-learning/benchmark_artifacts/vision/historical_summary.json), com o hash completo de origem. Isso não representa retreinamento ou novas medições; as métricas individuais de teste não estão disponíveis.
- Modelo integrado: [`artifacts/`](../../machine-learning/artifacts/). DINOv2-S histórico: [`benchmark_artifacts/dinov2/`](../../machine-learning/benchmark_artifacts/dinov2/).
- Fontes primárias: [MobileNetV4](https://arxiv.org/abs/2404.10518), [checkpoint MobileNetV4/timm](https://huggingface.co/timm/mobilenetv4_conv_medium.e500_r224_in1k), [TinyViT](https://github.com/microsoft/Cream/tree/main/TinyViT), [MobileCLIP2](https://github.com/apple-aiml-research/ml-mobileclip), [DINOv2](https://github.com/facebookresearch/dinov2), [DINOv3 e acesso aos pesos](https://github.com/facebookresearch/dinov3).

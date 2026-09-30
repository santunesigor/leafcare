# Model Card — LeafCare Baseline (integrado no Android)

## Modelo

- **Arquitetura:** MobileNetV3Small (ImageNet), cabeça nova (GlobalPooling + Dropout 0,25 + Dense 16 softmax)
- **Técnica:** transfer learning em duas etapas (backbone congelado → fine-tuning das últimas 30 camadas, BatchNorm congelado)
- **Exportação:** TFLite float32 (~3,8 MB), `include_preprocessing=True` (Rescaling embutido)
- **Integração:** LiteRT 1.4.0, API `Interpreter`, 2 threads, buffers reutilizados, validação de bundle na inicialização

## Dados

- **Dataset:** 696 imagens (seção TV3 do TLA), 16 classes
- **Split group-aware (seed 42):** 489 treino / 104 validação / 103 teste (grupos nunca divididos)
- **Assets no app:** `leafcare.tflite`, `classes.json`, `model_metadata.json`, `diseases.json` (hashes verificados em build e runtime)

## Métricas (teste reservado, 103 imagens)

| Métrica | Valor |
|---|---:|
| Top-1 accuracy | **77,67%** (80/103) |
| Macro-F1 | **0,7157** |
| Top-3 accuracy | **97,09%** (100/103) |
| Cobertura (threshold 0,70) | 76,70% |
| Acurácia entre aceitos | 89,87% |

## Threshold

- **0,70** (`confidence_threshold` em `model_metadata.json`); abaixo disso, resultado **inconclusivo**
- Valor inicial, **não calibrado** em campo; softmax não calibrada

## Uso pretendido

- **Triagem visual em campo** como apoio ao produtor; resultado é hipótese, não diagnóstico
- Funciona offline após instalado; nenhuma imagem sai do aparelho para classificar

## Limitações

- Dataset pequeno; classes raras (anthracnose, TSWV, black_shank, genetic_abnormality) com 1–2 amostras no teste — instáveis
- Conjunto fechado (16 classes): não detecta "não é folha", outras culturas ou doenças fora do conjunto
- Sem detecção de blur, baixa luz, distância, enquadramento ou OOD — fora do domínio pode receber confiança alta
- Sem validação independente em campo; conteúdo agronômico (`reviewed: false`) sem revisão completa
- Ensemble experimental (80,58% top-1) **não** está no app — pendente de validação móvel

> **Nunca afirme 80,58% para o app.** O Android roda este baseline (77,67%).

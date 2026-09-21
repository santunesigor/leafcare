# Integração do ensemble no Android

O app entregue continua com o modelo único anterior para não alterar uma funcionalidade já validada sem teste em aparelho. Para ativar o novo ensemble, use somente os três arquivos `*_float32.tflite` de `machine-learning/benchmark_artifacts/deployment`. As variantes `*_dynamic.tflite` foram rejeitadas na comparação de paridade.

## Contrato

- Entrada compartilhada: `float32[1,224,224,3]`, RGB, faixa 0–255.
- Faça o recorte central e redimensionamento apenas uma vez.
- A ordem das 16 classes é a do `deployment_manifest.json`.
- Cada modelo retorna softmax de 16 posições.
- Execute os três intérpretes, some as probabilidades posição a posição e divida por 3.
- Calibre cada média `p` com `q = p^(1/T) / soma(p^(1/T))`, usando `T = 0,8421423931819505`.
- Ordene `q` para exibir top 3.
- Se `max(q) < 0,74`, mostre “Resultado inconclusivo”.

Pseudocódigo matemático de referência (a implementação executável está em `benchmark_predict.py`):

```kotlin
val mean = FloatArray(16) { i -> outputs.sumOf { it[i].toDouble() }.toFloat() / 3f }
val powered = DoubleArray(16) { i -> mean[i].coerceAtLeast(1e-12f).toDouble().pow(1.0 / 0.8421423931819505) }
val sum = powered.sum()
val calibrated = FloatArray(16) { i -> (powered[i] / sum).toFloat() }
```

Carregue os intérpretes uma vez no repositório e reutilize-os; não recrie modelos a cada fotografia. Use thread de background, limite o paralelismo para não disputar CPU/memória e feche todos os intérpretes no ciclo de vida apropriado. Registre separadamente o tempo de pré-processamento e o tempo total dos três modelos.

Antes de publicar:

1. copie os três float32 para `app/src/main/assets`;
2. atualize o manifesto e a verificação de hashes;
3. crie um teste Kotlin com uma imagem fixture e compare as 16 probabilidades com `test_predictions.json`;
4. teste câmera, galeria, rotação e persistência em ao menos um aparelho ARM64;
5. meça latência, pico de memória, tamanho do APK e consumo de bateria;
6. mantenha a opção de modelo único caso o aparelho não tenha recursos suficientes.

O arquivo `benchmark_predict.py` é a referência canônica de comportamento para comparar a implementação Kotlin.

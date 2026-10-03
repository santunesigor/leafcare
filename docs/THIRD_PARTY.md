# Terceiros, referências e atribuições

O código original do LeafCare é distribuído sob MIT. Bibliotecas, fontes, pesos, artigos, datasets e imagens de terceiros mantêm suas próprias licenças.

## Dependências principais

| Dependência | Licença/fonte principal |
|---|---|
| AndroidX | Apache 2.0 |
| Kotlin | Apache 2.0 |
| TensorFlow / LiteRT | Apache 2.0 |
| Keras | Apache 2.0 |
| Coil | Apache 2.0 |
| Gradle | Apache 2.0 |
| NumPy | BSD |
| scikit-learn | BSD |
| Pillow | HPND |
| PyYAML | MIT |
| pytest | MIT |

Consulte os pacotes upstream para versões completas e licenças transitivas.

## Fonte Inter

A interface utiliza a fonte Inter. O texto da OFL distribuído com o projeto está em:

```text
docs/legal/Inter-OFL.txt
```

## Imagem de referência em `samples/`

`samples/reference_frog_eye.jpg` é uma imagem de referência derivada de figura publicada em:

Hong Lin, Zhenping Qiang, Rita Tse, Su-Kit Tang e Giovanni Pau (2024), **A few-shot learning method for tobacco abnormality identification**, Frontiers in Plant Science.

DOI: `10.3389/fpls.2024.1333236`

A figura é usada sob CC BY 4.0. Alteração realizada: recorte da figura original.

Essa imagem serve para verificação técnica e não constitui teste independente de desempenho do classificador.

## Referências visuais e dataset inicial

Imagens de referência utilizadas durante o desenvolvimento foram obtidas/adaptadas de materiais publicados sob CC BY 4.0, incluindo:

### TLA / Frontiers in Plant Science, 2024

- Hong Lin
- Zhenping Qiang
- Rita Tse
- Su-Kit Tang
- Giovanni Pau
- *A few-shot learning method for tobacco abnormality identification*
- DOI `10.3389/fpls.2024.1333236`

Atribuição sugerida:

> Imagem adaptada de Lin et al. (2024), “A few-shot learning method for tobacco abnormality identification”, Frontiers in Plant Science, DOI 10.3389/fpls.2024.1333236, CC BY 4.0. Alteração: recorte da figura original.

### Agronomy, 2025

- Yanze Zou
- Zhenping Qiang
- Shuang Zhang
- Hong Lin
- *Semantic Segmentation of Small Target Diseases on Tobacco Leaves*
- Agronomy 15(8), 1825
- DOI `10.3390/agronomy15081825`

Atribuição sugerida:

> Imagem adaptada de Zou et al. (2025), “Semantic Segmentation of Small Target Diseases on Tobacco Leaves”, Agronomy 15(8), 1825, DOI 10.3390/agronomy15081825, CC BY 4.0. Alteração: recorte da figura original.

## Manifesto de referências

Metadados de referências visuais permanecem em:

```text
docs/legal/referencias_manifest.csv
```

## Dataset completo

O repositório não pretende relicenciar datasets completos de terceiros sob MIT.

Antes de redistribuir qualquer dataset ou fotografia original, confirme explicitamente os termos aplicáveis à fonte correspondente.

## Uso acadêmico e limites

As imagens e resultados do projeto não constituem validação agronômica. A adequação regional do modelo — especialmente para condições brasileiras — precisa ser comprovada com dados independentes e revisão especializada.

## Referências técnicas

- TensorFlow MobileNetV3Small: `https://www.tensorflow.org/api_docs/python/tf/keras/applications/MobileNetV3Small`
- Keras transfer learning: `https://keras.io/guides/transfer_learning/`
- LiteRT Android: `https://ai.google.dev/edge/litert/android`
- CameraX: `https://developer.android.com/media/camera/camerax/take-photo`
- Room: `https://developer.android.com/training/data-storage/room`
- CC BY 4.0: `https://creativecommons.org/licenses/by/4.0/`

# Créditos e fontes

Código original deste pacote: LeafCare, licença MIT. Projeto acadêmico do curso de
Engenharia da Computação da UNISATC, com contexto de uso discutido com a BE1.
Este crédito não implica endosso técnico nem validação agronômica das instituições.

## Dependências

| Dependência | Fonte/licença principal |
|---|---|
| TensorFlow, LiteRT, AndroidX, Gradle | Projetos upstream; Apache 2.0 |
| Keras | Apache 2.0 |
| Kotlin | Apache 2.0 |
| NumPy, scikit-learn | BSD |
| Pillow | HPND |
| Matplotlib | PSF/BSD compatível, conforme distribuição |
| PyYAML, pytest | MIT |
| Coil | Apache 2.0 |

Consulte os metadados distribuídos por cada dependência para o texto integral e
licenças transitivas. Gradle Wrapper é um componente oficial do Gradle (Apache 2.0).

## Referências técnicas consultadas

- MobileNetV3Small e pré-processamento integrado:
  https://www.tensorflow.org/api_docs/python/tf/keras/applications/MobileNetV3Small
- Transfer learning e BatchNorm durante fine-tuning:
  https://keras.io/guides/transfer_learning/
- Exportação Keras 3:
  https://keras.io/api/models/model_saving_apis/export/
- Runtime LiteRT/Interpreter para Android:
  https://ai.google.dev/edge/litert/android
- Compatibilidade AGP 8.7 e Gradle 8.9:
  https://developer.android.com/build/releases/agp-8-7-0-release-notes
- CameraX ImageCapture:
  https://developer.android.com/media/camera/camerax/take-photo
- Persistência com Room:
  https://developer.android.com/training/data-storage/room

## Referência visual e dados

A fotografia em `samples/reference_frog_eye.jpg` é um recorte de Lin H., Qiang Z.,
Tse R., Tang S.-K. e Pau G. (2024), “A few-shot learning method for tobacco
abnormality identification”, Frontiers in Plant Science, DOI 10.3389/fpls.2024.1333236,
figura 4, conforme atribuição do pacote de referência, sob CC BY 4.0.
Alteração realizada no pacote anterior: recorte da figura, sem rótulos.

https://creativecommons.org/licenses/by/4.0/

A interface usa ícones exportados do Figma fornecido pelo usuário e as quatro
fotografias de exemplo enviadas por ele. A fonte Inter é distribuída sob OFL;
o texto da licença está em Inter-OFL.txt. As demais referências ilustrativas
estão descritas em referencias_manifest.csv e referencias_LICENCA_E_ATRIBUICAO.md.

A MIT do código não substitui as licenças destas imagens. Não há redistribuição do
dataset completo neste repositório. Os textos do catálogo são preliminares e devem
ser revisados por um profissional antes do uso externo.

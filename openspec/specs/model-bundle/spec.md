# model-bundle Specification

## Purpose

Registrar o contrato do bundle de inferência integrado no Android e a consistência exigida entre modelo, classes, metadados e pré-processamento. A linha de base corresponde ao ensemble distribuído, sem alterar treinamento ou artefatos.

## Requirements

### Requirement: Bundle local consistente

O classificador SHALL conferir status de treinamento, hashes do modelo e classes, ordem das classes e dimensões/tipos dos tensores antes de disponibilizar a inferência.

#### Scenario: Modelo incompatível com os metadados

- **WHEN** o arquivo do modelo diverge do hash dos metadados
- **THEN** a classificação fica indisponível com um erro de disponibilidade
- **AND** o aplicativo não apresenta a execução desse bundle como uma previsão válida

### Requirement: Contrato de entrada e ensemble

O modelo integrado SHALL aceitar RGB float32 com shape `[1,224,224,3]`, usar recorte central/redimensionamento definidos no contrato e produzir probabilidades para as 16 classes. O bundle atual reúne dois MobileNetV3Small e um MobileNetV3Large com média e calibração por temperatura embarcadas.

#### Scenario: Carregar o bundle distribuído

- **WHEN** o bundle atual é validado
- **THEN** as classes e metadados correspondem ao modelo e à saída de 16 classes
- **AND** os metadados identificam três membros, média de probabilidades e calibração embarcada

### Requirement: Paridade entre pré-processamento e inferência

O pipeline SHALL permitir verificar o pré-processamento compartilhado e a inferência do modelo distribuído contra fixtures registradas, preservando o contrato entre Python e Android.

#### Scenario: Verificar a imagem de referência do ensemble

- **WHEN** o teste Python executa o TFLite integrado na imagem de referência
- **THEN** as probabilidades coincidem com a fixture dentro da tolerância declarada no teste
- **AND** as cópias TFLite em artifacts e assets coincidem com o hash registrado

## References

- [LeafClassifier](../../../android-app/app/src/main/java/br/com/leafcare/ml/LeafClassifier.kt)
- [EnsembleBundleTest](../../../android-app/app/src/test/java/br/com/leafcare/EnsembleBundleTest.kt)
- [Testes do ensemble](../../../machine-learning/tests/test_ensemble.py)
- [Validador do bundle](../../../machine-learning/validate_bundle.py)
- [Contrato e métricas](../../../docs/MACHINE_LEARNING.md)

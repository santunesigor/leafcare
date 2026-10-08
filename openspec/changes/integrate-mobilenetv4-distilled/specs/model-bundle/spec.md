# Spec Delta

## MODIFIED Requirements

### Requirement: Bundle local consistente

O classificador SHALL conferir status de treinamento, schema 2, arquitetura MobileNetV4SmallDistilled, hashes do modelo e classes, ordem das classes e dimensões/tipos dos tensores antes de disponibilizar a inferência. Contratos incompatíveis SHALL deixar a classificação indisponível com erro legível.

#### Scenario: Modelo incompatível com os metadados

- **WHEN** o arquivo do modelo diverge do hash dos metadados ou o preprocessing não corresponde ao schema suportado
- **THEN** a classificação fica indisponível com erro de disponibilidade
- **AND** o aplicativo não apresenta esse bundle como uma previsão válida

### Requirement: Contrato de entrada e ensemble

O modelo integrado SHALL ser uma única rede MobileNetV4 Small destilada, aceitar RGB float32 `[1,224,224,3]` na faixa 0–255 e produzir probabilidades para as 16 classes existentes. O preprocessing SHALL redimensionar o lado menor para 256 por bicúbica antialias equivalente ao Pillow e recortar 224 no centro com arredondamento ties-to-even. Normalização ImageNet, softmax e temperatura de calibração SHALL estar no grafo. O limiar SHALL vir do bundle, sem reutilizar o limiar do ensemble anterior.

#### Scenario: Carregar o bundle distribuído

- **WHEN** o bundle atual é validado
- **THEN** classes e metadados correspondem ao modelo e à saída de 16 classes
- **AND** os metadados identificam a rede única destilada, seu preprocessing e calibração embarcada

#### Scenario: Preservar análises anteriores

- **WHEN** o app atualizado consulta uma análise realizada com o ensemble anterior
- **THEN** seu hash de modelo e limiar registrados permanecem associados à análise
- **AND** novas análises usam o hash e limiar do aluno

### Requirement: Paridade entre pré-processamento e inferência

O pipeline SHALL verificar pixels RGB compartilhados entre Python e Kotlin contra fixtures registradas e probabilidades TFLite contra logits de validação do aluno, com Top-1 idêntico e erro máximo de probabilidades de 0,0001.

#### Scenario: Verificar a imagem de referência do aluno

- **WHEN** o teste Python executa o TFLite integrado na imagem de referência com preprocessing do aluno
- **THEN** as probabilidades coincidem com a fixture dentro da tolerância declarada
- **AND** as cópias TFLite em artifacts e assets coincidem com o hash registrado

#### Scenario: Verificar a imagem de referência do ensemble

- **WHEN** os resultados históricos do ensemble são recalculados a partir das probabilidades registradas
- **THEN** as métricas coincidem com os relatórios históricos
- **AND** esses registros permanecem separados das fixtures do aluno atual

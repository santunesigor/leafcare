# offline-analysis Specification

## Purpose

Descrever o contrato existente de triagem local de folhas no LeafCare, incluindo disponibilidade offline após autenticação, persistência do histórico e apresentação de resultados inconclusivos. Esta linha de base não declara nova validação de campo ou de desempenho.

## Requirements

### Requirement: Classificação disponível offline após autenticação

O aplicativo SHALL permitir captura/importação, classificação no dispositivo e consulta do histórico local após um acesso autenticado, sem depender de uma resposta de classificação do backend.

#### Scenario: Analisar uma foto sem conexão

- **WHEN** o usuário já autenticado escolhe uma foto válida com o dispositivo sem conexão
- **THEN** o aplicativo executa o bundle local e salva a análise no banco local
- **AND** a falta de rede não invalida a análise ou o histórico local

### Requirement: Resultado com Top-3 e limiar do bundle

O aplicativo SHALL apresentar até três hipóteses e usar o limiar definido nos metadados do modelo para sinalizar um resultado inconclusivo.

#### Scenario: Confiança abaixo do limiar

- **WHEN** a maior pontuação é inferior ao limiar do bundle
- **THEN** a análise é marcada como inconclusiva
- **AND** o histórico preserva o limiar aplicado nessa análise

#### Scenario: Confiança no limite

- **WHEN** a maior pontuação é igual ao limiar válido do bundle
- **THEN** a política não marca o resultado como inconclusivo apenas por essa comparação

### Requirement: Histórico persistente no dispositivo

O aplicativo SHALL conservar as análises locais entre aberturas do banco e oferecer consulta/exclusão por identificador estável.

#### Scenario: Reabrir o banco

- **WHEN** uma análise foi gravada e o banco local é fechado e reaberto
- **THEN** a análise continua consultável pelo mesmo identificador até sua exclusão

## References

- [AnalysisRepository](../../../android-app/app/src/main/java/br/com/leafcare/data/AnalysisRepository.kt)
- [PredictionPolicy](../../../android-app/app/src/main/java/br/com/leafcare/ml/Prediction.kt)
- [PredictionPolicyTest](../../../android-app/app/src/test/java/br/com/leafcare/PredictionPolicyTest.kt)
- [HistoryPersistenceTest](../../../android-app/app/src/androidTest/java/br/com/leafcare/HistoryPersistenceTest.kt)

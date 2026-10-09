# Spec Delta

## ADDED Requirements

### Requirement: Pausa administrativa de envios

A sincronização SHALL consultar a política remota antes de enviar análises/fotos ou exclusões. Pausa SHALL conservar dados locais, login e leitura remota. Liberação SHALL permitir só resultados criados após o corte do servidor, mantendo anteriores locais e permitindo processar tombstones pendentes.

#### Scenario: Usuário pausado

- **WHEN** o servidor informa envios pausados
- **THEN** nenhum upload ou exclusão remota é feito e o app continua localmente

#### Scenario: Liberação sem acumulado

- **WHEN** os envios são liberados
- **THEN** resultados anteriores ao corte não são enviados, inclusive fotos pendentes de análises antigas
- **AND** novas fotos só são enviadas se a análise for conclusiva

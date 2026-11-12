# Spec Delta

## MODIFIED Requirements

### Requirement: Fotos privadas com estado independente

A sincronização SHALL manter estado independente da foto e caminho por conta/UUID. Somente análises conclusivas com confiança válida maior ou igual ao limiar registrado SHALL enviar fotos. As demais fotos SHALL permanecer locais, conservando a sincronização da análise e classe prevista. Antes de upload confirmado, photo_path SHALL ser nulo e o restore SHALL representar ausência de foto remota.

#### Scenario: Enviar análise e falhar no envio da foto

- **WHEN** a análise conclusiva foi confirmada remotamente e o upload da foto falha
- **THEN** o histórico conserva a análise
- **AND** o estado da foto permite uma nova tentativa com o mesmo caminho

#### Scenario: Resultado inconclusivo

- **WHEN** a análise é inconclusiva ou sua confiança fica abaixo do limiar registrado
- **THEN** nenhum byte da foto é enviado ao bucket, inclusive em retry de versões anteriores
- **AND** o histórico e a foto local permanecem disponíveis no aparelho original

#### Scenario: Confiança no limiar

- **WHEN** uma análise conclusiva tem confiança igual ao seu limiar
- **THEN** a foto pode ser enviada e associada ao UUID e classe já sincronizados

#### Scenario: Restaurar análise sem foto remota

- **WHEN** uma análise remota não tem caminho de foto confirmado
- **THEN** a análise é restaurada sem tentar baixar um arquivo inexistente
- **AND** uma foto associada posteriormente pode ser baixada sem sobrescrever o resultado local

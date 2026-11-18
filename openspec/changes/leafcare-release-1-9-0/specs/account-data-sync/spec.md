## ADDED Requirements

### Requirement: Prioridade à restauração do histórico

O app SHALL restaurar as análises remotas e baixar suas fotos elegíveis após a autenticação sem depender da fila de envios locais.

#### Scenario: Restaurar histórico após o login

- **WHEN** uma conta autenticada com aceite vigente tem fotos remotas e uma fila de envio local
- **THEN** a restauração é tentada antes dos envios e a interface recebe as fotos assim que estiverem disponíveis

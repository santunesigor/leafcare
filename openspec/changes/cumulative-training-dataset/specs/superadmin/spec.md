## ADDED Requirements

### Requirement: Revisão das cópias de treino

O painel SHALL mostrar quantas cópias de treino estão prontas, aguardando revisão e fora do treino. Também SHALL mostrar quantas cópias v1.4 pendentes vencem em até 14 dias. Fotos com cópia de treino SHALL exibir o selo "Treino" e, quando a cópia tem prazo, os dias restantes. A consulta SHALL permitir limitar às cópias de treino, e a fila SHALL priorizar as cópias pendentes que vencem antes, deixando as permanentes depois delas.

#### Scenario: Revisar cópias
- **WHEN** a pessoa revisora toca em "Revisar cópias" no Resumo
- **THEN** a fila abre só com cópias de treino pendentes, primeiro as que vencem antes

#### Scenario: Cópia permanente
- **WHEN** a cópia foi enviada depois do aceite da v1.5
- **THEN** o selo mostra "Treino" sem contagem de dias

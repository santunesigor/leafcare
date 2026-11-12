## ADDED Requirements

### Requirement: Revisão das cópias de treino

O painel SHALL mostrar quantas cópias de treino estão prontas, aguardando revisão (e quantas vencem em até 14 dias) e fora do treino. Fotos com cópia de treino SHALL exibir quantos dias faltam para a exclusão. A consulta SHALL permitir limitar às cópias de treino, e a fila SHALL priorizar as cópias pendentes que vencem antes.

#### Scenario: Revisar cópias
- **WHEN** a pessoa revisora toca em "Revisar cópias" no Resumo
- **THEN** a fila abre só com cópias de treino pendentes, a que vence primeiro no topo

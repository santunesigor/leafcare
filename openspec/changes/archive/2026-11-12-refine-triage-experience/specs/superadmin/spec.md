# Spec Delta

## ADDED Requirements

### Requirement: Painel de triagens

A aba Triagem SHALL abrir um painel com fotos recebidas, pendentes, revisadas e progresso calculado dos totais remotos. SHALL oferecer entrada para pendências e seleções por situação/classe, sem inventar contagens indisponíveis. Carregamento e falha SHALL ser indicados e o retorno da avaliação SHALL atualizar os totais.

#### Scenario: Abrir painel

- **WHEN** o superadmin toca a aba Triagem
- **THEN** vê o painel e escolhe a seleção que deseja avaliar
- **AND** pode acompanhar quantas fotos já foram revisadas

#### Scenario: Falha de conexão

- **WHEN** os totais não podem ser carregados
- **THEN** o app indica falha e oferece nova tentativa sem apresentar zeros como resultado confirmado

## MODIFIED Requirements

### Requirement: Fila contínua de triagem

A triagem SHALL mostrar uma foto por vez, permitir navegar por botões explícitos e avançar após salvar com sucesso, respeitando os filtros ativos. Passar uma foto SHALL preservar sua revisão remota. Gestos verticais SHALL NOT trocar a foto.

#### Scenario: Salvar e continuar

- **WHEN** uma revisão é salva com sucesso
- **THEN** a próxima foto da seleção aparece na mesma tela
- **AND** a conclusão da fila é indicada quando não existem outras fotos na seleção

#### Scenario: Passar sem revisar

- **WHEN** o superadmin usa os botões de anterior ou pular
- **THEN** muda a foto sem enviar uma revisão

#### Scenario: Inspecionar foto

- **WHEN** o superadmin desliza verticalmente sobre a foto
- **THEN** a foto atual continua selecionada

#### Scenario: Falha ao salvar

- **WHEN** o salvamento falha
- **THEN** a foto e a avaliação permanecem disponíveis para tentar novamente
- **AND** conflito de versão exige recarregar o registro antes de salvar

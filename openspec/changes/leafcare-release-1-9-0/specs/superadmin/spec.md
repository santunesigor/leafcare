## ADDED Requirements

### Requirement: Edição de avaliação pela galeria

A galeria administrativa SHALL permitir editar uma avaliação salva, preenchendo decisão, classe aplicável e observação com os valores vigentes.

#### Scenario: Editar uma avaliação existente

- **WHEN** o superadmin salva a edição a partir dos detalhes da foto
- **THEN** a galeria reflete a revisão atualizada sem alterar a previsão original nem avançar a fila

### Requirement: Zoom direto na foto

Os visores administrativos SHALL aceitar gestos de ampliação na imagem e oferecer um controle de restauração sobre a foto.

#### Scenario: Avaliar com foto ampliada

- **WHEN** o superadmin amplia a foto durante a avaliação
- **THEN** os gestos não avançam a fila e as ações de revisão continuam acessíveis sem rolagem vertical na tela principal

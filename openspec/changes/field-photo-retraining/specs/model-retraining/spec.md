## ADDED Requirements

### Requirement: Ciclo de retreino com fotos de campo

O pipeline SHALL montar cada ciclo a partir do dataset TLA, com o split histórico, e de uma exportação de fotos prontas ainda válida. Fotos de campo iguais ou quase iguais a imagens do TLA SHALL ser descartadas. Um ciclo SHALL recusar exportação vencida.

#### Scenario: Exportação válida
- **WHEN** a exportação tem menos de 30 dias e o TLA confere com o manifesto
- **THEN** o ciclo registra TLA, fotos de campo, contagens por split e avisos

#### Scenario: Exportação vencida
- **WHEN** a exportação passou de `delete_after`
- **THEN** montar o ciclo, treinar, converter e promover falham pedindo uma nova exportação

### Requirement: Separação por pessoa e teste de campo

Fotos de campo SHALL ser separadas por grupo de pessoa, mantendo cada grupo e suas quase-duplicatas em um único split. Com pelo menos cinco grupos, uma parte SHALL formar o `field_test`; abaixo disso, todas SHALL ir para o treino, com aviso. Fotos de campo SHALL nunca entrar no teste TLA histórico.

#### Scenario: Poucas pessoas
- **WHEN** a exportação tem menos de cinco grupos
- **THEN** todas as fotos de campo vão para o treino e o ciclo avisa que não há teste de campo

### Requirement: Receita definida e seleção sem teste

O treino SHALL usar a receita do modelo do app (MobileNetV4 Small, probe inicial, cabeça + encoder, AdamW, destilação DINOv2-B com T=4 e peso 0,5). A época, a temperatura e o limiar SHALL ser escolhidos só pela validação; o teste TLA e o `field_test` SHALL ser avaliados depois disso.

#### Scenario: Sem professor
- **WHEN** o treino roda sem checkpoint do professor
- **THEN** o resultado é registrado como supervisionado e não pode ser promovido

### Requirement: Promoção explícita com replay

Um candidato SHALL ser convertido para o contrato TFLite do app com paridade (mesmo Top-1, erro ≤ 1e-4) e reproduzido com o pré-processamento do app antes de qualquer instalação. A instalação SHALL exigir comando explícito e SHALL recusar execuções de fumaça, treinos sem professor e candidatos não recomendados sem confirmação adicional.

#### Scenario: Conferência sem instalar
- **WHEN** a promoção roda sem `--install`
- **THEN** nada no app muda e o replay confere probabilidades e métricas

#### Scenario: Recomendação
- **WHEN** o Macro-F1 no teste TLA cai mais de 0,02, o Top-1 de campo fica abaixo do modelo atual ou falta teste de campo
- **THEN** o candidato não é recomendado e a instalação exige `--allow-unrecommended`

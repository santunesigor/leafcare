## ADDED Requirements

### Requirement: Conjunto de treino acumulado a partir do TLA

Cada ciclo SHALL usar o dataset TLA com o split histórico, conferido pelo manifesto, mais todas as fotos de campo no estado `ready` de `training_pool()`, com o rótulo revisado. O conjunto de campo SHALL crescer entre ciclos sem limite de tempo, exceto as cópias que saem do estado `ready`. Fotos de campo iguais ou quase iguais a imagens do TLA SHALL ser descartadas.

#### Scenario: Novo ciclo depois de mais revisões
- **WHEN** novas cópias foram confirmadas ou corrigidas desde o ciclo anterior
- **THEN** o novo ciclo contém as fotos do ciclo anterior que continuam prontas mais as novas

#### Scenario: TLA ausente ou diferente
- **WHEN** as imagens do TLA não existem ou não conferem com o manifesto
- **THEN** o ciclo falha antes de treinar e explica como importar o TLA

### Requirement: Espelho local sincronizado e pseudonimizado

A sincronização SHALL baixar só as cópias prontas, recodificadas sem EXIF e identificadas por pseudônimos HMAC com um segredo local que não é versionado. O espelho SHALL NOT guardar e-mail nem identificadores de conta ou de análise. Cópias que saem do estado `ready` SHALL ser apagadas do espelho na sincronização seguinte, e rótulos revisados de novo SHALL ser atualizados.

#### Scenario: Revogação
- **WHEN** uma pessoa revoga a autorização ou exclui a análise
- **THEN** a próxima sincronização apaga a foto do espelho local

#### Scenario: Correção de rótulo
- **WHEN** uma revisão muda a classe de uma cópia pronta
- **THEN** a sincronização atualiza o rótulo sem baixar a imagem de novo

### Requirement: Separação por pessoa estável entre ciclos

Fotos de campo SHALL ser separadas por pessoa, com o mesmo split em todos os ciclos, e quase-duplicatas SHALL ficar no mesmo split. Com pelo menos cinco pessoas, uma parte SHALL formar o `field_test`; abaixo disso, todas SHALL ir para o treino, com aviso. Fotos de campo SHALL NOT entrar no teste TLA histórico.

#### Scenario: Pessoa com fotos novas
- **WHEN** uma pessoa que já estava no `field_test` envia mais fotos
- **THEN** as fotos novas também vão para o `field_test`

### Requirement: Ciclo completo com um comando

`retrain.py run` SHALL sincronizar, montar o conjunto, treinar ou reutilizar o professor DINOv2-B, destilar o MobileNetV4 Small com a receita do modelo do app, converter para TFLite com paridade e reproduzir o resultado com o pré-processamento do app. Época, temperatura e limiar SHALL ser escolhidos só pela validação. O teste TLA e o `field_test` SHALL ser avaliados depois disso.

#### Scenario: Retomada
- **WHEN** um ciclo é executado de novo com o mesmo nome
- **THEN** as etapas já concluídas são reaproveitadas e as demais continuam

### Requirement: Promoção explícita com recomendação

O ciclo SHALL comparar o candidato com o modelo em uso e SHALL recomendá-lo só quando o Macro-F1 no teste TLA não cair mais de 0,02, o Top-1 no `field_test` for pelo menos igual ao do modelo em uso e houver `field_test`. O app SHALL mudar somente com `retrain.py promote`, e um candidato não recomendado SHALL exigir `--allow-unrecommended`. Execuções de fumaça SHALL NOT ser promovidas.

#### Scenario: Ciclo sem promoção
- **WHEN** o ciclo termina
- **THEN** os assets do app continuam iguais até o comando de promoção

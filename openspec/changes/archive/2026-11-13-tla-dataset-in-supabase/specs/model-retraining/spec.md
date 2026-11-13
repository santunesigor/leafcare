## MODIFIED Requirements

### Requirement: Conjunto de treino acumulado a partir do TLA

Cada ciclo SHALL usar o dataset TLA com o split histórico, conferido pelo manifesto, mais todas as fotos de campo no estado `ready` de `training_pool()`, com o rótulo revisado. O conjunto de campo SHALL crescer entre ciclos sem limite de tempo, exceto as cópias que saem do estado `ready`. Fotos de campo iguais ou quase iguais a imagens do TLA SHALL ser descartadas.

#### Scenario: Novo ciclo depois de mais revisões
- **WHEN** novas cópias foram confirmadas ou corrigidas desde o ciclo anterior
- **THEN** o novo ciclo contém as fotos do ciclo anterior que continuam prontas mais as novas

#### Scenario: TLA ausente ou diferente
- **WHEN** as imagens do TLA não existem ou não conferem com o manifesto e o ciclo não pode baixá-las
- **THEN** o ciclo falha antes de treinar e explica como sincronizar o TLA

## ADDED Requirements

### Requirement: TLA guardado no Supabase

As imagens do TLA SHALL ficar no bucket privado `tla-dataset`, uma pasta por doença, sem acesso para usuários do app. A sincronização SHALL baixar toda imagem ausente ou alterada e gravá-la só se o sha256 conferir com o manifesto. O envio ao bucket SHALL NOT substituir objetos existentes.

#### Scenario: Computador novo
- **WHEN** `data/raw/` está vazio e a sincronização roda com a chave de serviço
- **THEN** as 696 imagens são baixadas e conferidas com o manifesto

#### Scenario: Usuário do app
- **WHEN** uma conta autenticada tenta ler ou gravar em `tla-dataset`
- **THEN** a operação é negada

### Requirement: Cópia local organizada por doença

A cópia local das fotos do app SHALL guardar cada foto na pasta da classe revisada. Uma nova revisão que mude a classe SHALL mover a foto, sem baixá-la de novo.

#### Scenario: Correção de classe
- **WHEN** a revisão de uma foto pronta passa de `frog_eye` para `tmv`
- **THEN** a próxima sincronização move a foto para `data/field/tmv/`

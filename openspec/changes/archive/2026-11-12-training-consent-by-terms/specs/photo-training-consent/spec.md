## REMOVED Requirements

### Requirement: Coleta opcional e separada do histórico
**Reason**: O uso das fotos para treinamento passa a ser condição de uso, aceita junto com os documentos v1.4.
**Migration**: Contas existentes aceitam os documentos v1.4 na tela de aceite; o servidor registra o consentimento junto com o recibo.

### Requirement: Consentimento versionado e opcional
**Reason**: O consentimento deixa de ser uma escolha separada no Perfil.
**Migration**: Substituído por "Consentimento de treinamento como condição de uso".

### Requirement: Autorização individual de foto
**Reason**: A autorização por foto no modal da câmera foi removida.
**Migration**: Substituído por "Autorização automática das análises".

## ADDED Requirements

### Requirement: Consentimento de treinamento como condição de uso

O uso do app SHALL exigir o aceite dos Termos de Uso e da Política de Privacidade atuais, que incluem o uso das fotos analisadas para treinamento. O aceite SHALL registrar no servidor, em uma única operação e com timestamp do servidor, o recibo dos documentos e o consentimento de treinamento vigente. O cadastro e a tela de aceite SHALL destacar essa condição fora do texto integral dos documentos.

#### Scenario: Cadastro
- **WHEN** a pessoa cria a conta com os documentos atuais
- **THEN** o servidor registra o recibo e o consentimento de treinamento vigente

#### Scenario: Conta sem aceite atual
- **WHEN** uma conta autenticada não tem recibo dos documentos atuais ou consentimento vigente
- **THEN** o app apresenta a tela de aceite e não libera câmera, galeria, análise nem histórico
- **AND** a pessoa pode aceitar ou sair da conta

### Requirement: Autorização automática das análises

Cada análise conclusiva feita por uma conta com consentimento vigente SHALL ser autorizada para treinamento, sem confirmação adicional por foto. Fotos analisadas antes do aceite vigente e fotos de resultados inconclusivos SHALL permanecer fora da coleta. O servidor SHALL aceitar uma cópia de treinamento somente para análise ativa e conclusiva de conta liberada para envio (sem pausa, sem exclusão pendente e após o corte).

#### Scenario: Nova análise
- **WHEN** a pessoa captura ou escolhe uma foto com consentimento vigente
- **THEN** a análise começa sem modal de confirmação
- **AND** a foto é autorizada para treinamento e enviada após a confirmação remota

#### Scenario: Resultado inconclusivo
- **WHEN** a análise termina como inconclusiva
- **THEN** nenhuma autorização de treinamento é criada e a foto não é copiada

#### Scenario: Fotos anteriores
- **WHEN** uma foto foi analisada antes do aceite vigente
- **THEN** ela não é copiada nem marcada como elegível automaticamente

## MODIFIED Requirements

### Requirement: Reaceite de versões materiais por contas existentes

O app SHALL verificar no servidor se a conta autenticada possui recibo das versões atuais dos Termos e da Política e o consentimento de treinamento vigente. Quando faltar algum deles, SHALL apresentar os documentos no próprio app e exigir confirmação explícita antes de liberar o uso. O recibo SHALL ser criado no servidor somente após essa ação, com timestamp do servidor, e SHALL ser imutável. Falha de rede SHALL NOT criar recibo; nesse caso, o app SHALL continuar utilizável somente quando o aparelho guardar um consentimento vigente da conta.

#### Scenario: Conta existente sem recibo atual
- **WHEN** uma conta autenticada não tem recibo das versões atuais e há conexão
- **THEN** os documentos atuais são apresentados dentro do app
- **AND** o recibo e o consentimento só são criados após confirmação explícita

#### Scenario: Reaceite indisponível sem rede
- **WHEN** a verificação falha por indisponibilidade de rede e o aparelho guarda consentimento vigente da conta
- **THEN** classificação local e histórico continuam disponíveis
- **AND** o app verifica novamente ao retornar ao primeiro plano

#### Scenario: Sem rede sem aceite no aparelho
- **WHEN** a verificação falha por indisponibilidade de rede e o aparelho não guarda consentimento vigente
- **THEN** o app mantém a tela de aceite e informa que o aceite precisa de conexão

### Requirement: Revogação e exclusão de cópias

Uma revogação SHALL bloquear novos envios imediatamente no aparelho e SHALL ser sincronizada antes de qualquer upload de treinamento pendente, mesmo sem aceite dos documentos atuais. O servidor SHALL aceitar revogações com qualquer versão de consentimento. Após confirmação remota, o sistema SHALL excluir de fato as cópias de treinamento armazenadas e preservar as fotos de histórico e análises existentes. Uma revogação pendente SHALL ser enviada antes de um novo aceite e SHALL ser tentada ao sair da conta. Excluir uma análise SHALL excluir sua cópia de treinamento, inclusive com envios pausados; excluir a conta SHALL remover as cópias antes da conta. Depois de revogar, o app SHALL exigir um novo aceite para voltar a ser usado.

#### Scenario: Revogação offline
- **WHEN** a pessoa revoga o consentimento sem conexão
- **THEN** o estado local fica negado, nenhum upload de treinamento ocorre e o app volta para a tela de aceite
- **AND** a exclusão remota permanece pendente até a conexão retornar

#### Scenario: Sincronização da revogação
- **WHEN** a conexão retorna com uma revogação pendente
- **THEN** o servidor registra a revogação e bloqueia novos uploads antes de excluir cópias de treinamento
- **AND** o fluxo não altera o histórico comum

#### Scenario: Novo aceite após revogar
- **WHEN** a pessoa aceita os documentos atuais depois de revogar
- **THEN** o servidor registra o consentimento novamente e o app volta a funcionar

# Spec Delta

## Purpose

Permitir que cada pessoa escolha, de forma informada e revogável, se fotos específicas podem ser copiadas para uma área privada de treinamento, sem confundir esse uso com o histórico de análises.

## ADDED Requirements

### Requirement: Coleta desativada até publicar informações legais

O app, o servidor e o pipeline SHALL manter gravação e exportação de fotos de treinamento desativadas até que o prazo de retenção e os links oficiais de Termos e Privacidade sejam fornecidos. A análise local e o histórico SHALL permanecer disponíveis.

#### Scenario: Informações ainda não publicadas
- **WHEN** prazo de retenção ou links oficiais ainda não foram definidos
- **THEN** nenhuma opção de consentimento permite aceitar o uso de fotos
- **AND** nenhum byte de foto é enviado ou exportado para treinamento
- **AND** a tela informa por que a contribuição está indisponível

### Requirement: Consentimento versionado e opcional

O sistema SHALL registrar por conta a decisão explícita, a versão dos termos e a data da autorização. A decisão inicial e qualquer versão de termos ainda não aceita SHALL permanecer negadas, sem impedir a análise normal.

#### Scenario: Primeira utilização
- **WHEN** uma conta ainda não tem decisão registrada
- **THEN** o app mostra o consentimento como não autorizado
- **AND** câmera, galeria, classificação e histórico continuam disponíveis

#### Scenario: Consentimento registrado offline
- **WHEN** a pessoa aceita uma versão dos termos sem conexão
- **THEN** a decisão e a versão ficam registradas localmente
- **AND** nenhuma imagem de treinamento é enviada antes da confirmação remota

#### Scenario: Nova versão dos termos
- **WHEN** a versão apresentada difere da versão aceita
- **THEN** o sistema suspende novas autorizações de fotos até uma nova escolha explícita

### Requirement: Autorização individual de foto

Cada foto elegível SHALL ter uma escolha afirmativa própria vinculada à conta e à versão dos termos aceita. O envio de foto para histórico SHALL NOT constituir autorização para treinamento, e fotos anteriores à autorização SHALL permanecer inelegíveis.

#### Scenario: Foto autorizada
- **WHEN** a autorização vigente está ativa e a pessoa marca a opção de treinamento para uma foto
- **THEN** o sistema registra a decisão para aquele identificador de análise
- **AND** mantém separadas a cópia de histórico e a cópia de treinamento

#### Scenario: Foto sem autorização
- **WHEN** a opção individual está desmarcada ou a autorização global não está ativa
- **THEN** nenhum byte da foto é enviado para treinamento
- **AND** o fluxo comum de análise e histórico continua

#### Scenario: Fotos históricas
- **WHEN** uma foto foi enviada ao histórico antes do consentimento
- **THEN** ela não é copiada nem marcada como elegível para treinamento automaticamente

### Requirement: Confirmação remota antes do envio

O sistema SHALL enviar bytes de treinamento somente após o servidor confirmar a versão ativa do consentimento e a autorização da foto. O servidor SHALL negar acesso a imagens de treinamento a contas diferentes e a clientes sem autorização vigente.

#### Scenario: Consentimento aguardando sincronização
- **WHEN** a autorização local ainda não foi confirmada pelo servidor
- **THEN** o envio da foto permanece pendente no aparelho
- **AND** nenhuma operação de retry envia os bytes

#### Scenario: Acesso por outra conta
- **WHEN** uma conta tenta ler ou gravar a cópia de treinamento pertencente a outra conta
- **THEN** o servidor nega a operação

### Requirement: Revogação e exclusão de cópias

Uma revogação SHALL bloquear novos envios imediatamente no aparelho e SHALL ser sincronizada antes de qualquer upload de treinamento pendente. Após confirmação remota, o sistema SHALL excluir as cópias de treinamento armazenadas e preservar as fotos de histórico e análises existentes.

#### Scenario: Revogação offline
- **WHEN** a pessoa revoga o consentimento sem conexão
- **THEN** o estado local fica negado e nenhum upload de treinamento ocorre
- **AND** a exclusão remota permanece pendente até a conexão retornar

#### Scenario: Sincronização da revogação
- **WHEN** a conexão retorna com uma revogação pendente
- **THEN** o servidor registra a revogação e bloqueia novos uploads antes de excluir cópias de treinamento
- **AND** o fluxo não altera o histórico comum

### Requirement: Elegibilidade para o conjunto de treinamento

O pipeline SHALL incluir somente imagens com consentimento vigente, autorização individual confirmada, qualidade aprovada e classe validada por revisão humana. Identificadores e metadados desnecessários SHALL ser removidos antes da exportação.

#### Scenario: Foto aprovada
- **WHEN** consentimento e escolha individual estão vigentes, a qualidade passa e há classe confirmada ou corrigida por pessoa revisora
- **THEN** a imagem pode ser exportada sem identificadores de conta

#### Scenario: Falta de revisão ou qualidade
- **WHEN** falta autorização, revisão humana válida ou aprovação de qualidade
- **THEN** a imagem é excluída da exportação
- **AND** a classe prevista automaticamente não é tratada como rótulo confirmado

### Requirement: Informação transparente e retenção

O app SHALL disponibilizar os termos e o aviso de privacidade antes da autorização, explicando finalidade, dados usados, armazenamento, ciclo de retenção, revogação e limites de remoção após treinamento. A cópia de treinamento SHALL permanecer elegível somente enquanto a autorização estiver ativa.

#### Scenario: Consultar informações
- **WHEN** a pessoa abre as informações sobre uso de fotos
- **THEN** consegue consultar os termos e o aviso de privacidade sem aceitar o consentimento

#### Scenario: Modelo já treinado
- **WHEN** uma cópia de treinamento é excluída após a revogação
- **THEN** a informação apresentada explica que pesos de modelos já treinados não são automaticamente revertidos

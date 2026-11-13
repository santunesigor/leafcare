## MODIFIED Requirements

### Requirement: Elegibilidade para o conjunto de treinamento

O conjunto de treinamento SHALL incluir somente cópias de análises ativas e conclusivas, feitas depois do primeiro aceite dos documentos v1.4 ou v1.5 da conta, com consentimento e autorização 1.2 vigentes, conta fora de exclusão, cópia dentro do prazo aplicável, qualidade aprovada e classe confirmada ou corrigida por revisão humana. O rótulo de treino SHALL ser a classe revisada. Identificadores de conta e de análise e metadados EXIF SHALL ser removidos antes de a foto sair do Supabase.

#### Scenario: Foto aprovada
- **WHEN** todas as condições estão cumpridas e há classe confirmada ou corrigida
- **THEN** a cópia fica pronta e pode entrar no conjunto com a classe revisada, sem identificadores

#### Scenario: Falta de revisão ou qualidade
- **WHEN** falta revisão humana válida ou aprovação de qualidade
- **THEN** a imagem fica fora do conjunto
- **AND** a classe prevista automaticamente não é tratada como rótulo confirmado

#### Scenario: Foto anterior ao aceite
- **WHEN** a análise foi feita antes do primeiro aceite dos documentos v1.4 ou v1.5 da conta
- **THEN** a cópia não entra no conjunto

## REMOVED Requirements

### Requirement: Informação transparente e retenção

**Reason**: a retenção de 90 dias para todas as cópias e a exportação local de 30 dias deram lugar ao conjunto acumulado dos Termos v1.5.

**Migration**: substituída por "Informação transparente e retenção do conjunto de treinamento", que mantém os cenários de informação, contato, histórico, backups e modelo já treinado.

## ADDED Requirements

### Requirement: Informação transparente e retenção do conjunto de treinamento

O app SHALL disponibilizar Termos e Política explicando finalidade, dados usados, armazenamento, retenção, revogação e limites de remoção após o treinamento. O histórico remoto SHALL ser mantido enquanto a conta estiver ativa, salvo exclusão solicitada.

#### Scenario: Consultar informações
- **WHEN** a pessoa abre as informações sobre uso de fotos
- **THEN** consegue consultar os termos e o aviso de privacidade sem aceitar o consentimento

#### Scenario: Contato de privacidade no app
- **WHEN** a pessoa consulta as informações de privacidade
- **THEN** encontra o endereço de contato publicado no app
- **AND** pode copiá-lo sem abrir navegador ou site externo

#### Scenario: Retenção do histórico
- **WHEN** uma conta está ativa e não solicitou exclusão de uma análise
- **THEN** o histórico e a foto permanecem disponíveis para a finalidade de histórico
- **AND** a pessoa pode excluir o item ou encerrar a conta

#### Scenario: Exclusão e backups
- **WHEN** os dados ativos são excluídos a pedido da pessoa
- **THEN** os sistemas ativos processam a exclusão em até 30 dias
- **AND** exportações manuais do banco guardadas pela equipe no Google Drive podem persistir por até 30 dias
- **AND** a Política informa que exportações SQL não incluem arquivos do Storage

#### Scenario: Modelo já treinado
- **WHEN** uma cópia de treinamento é excluída após a revogação
- **THEN** a informação apresentada explica que pesos de modelos já treinados não são automaticamente revertidos

### Requirement: Retenção das cópias de treinamento

Cópias de fotos analisadas depois do aceite da v1.5 SHALL ser mantidas no conjunto de treinamento por tempo indeterminado, até a revogação, a exclusão da análise ou a exclusão da conta. Cópias de fotos analisadas sob a v1.4 SHALL expirar em até 90 dias do envio. A cópia local do conjunto SHALL acompanhar o servidor a cada sincronização.

#### Scenario: Cópia permanente
- **WHEN** uma cópia de foto analisada depois do aceite da v1.5 passa de 90 dias
- **THEN** ela continua no conjunto de treinamento
- **AND** uma revogação, exclusão da análise ou exclusão da conta a remove

#### Scenario: Expiração de cópia v1.4
- **WHEN** uma cópia de foto analisada sob a v1.4 alcança 90 dias desde o upload
- **THEN** a rotina automática remove o objeto do Storage

#### Scenario: Cópia local
- **WHEN** uma foto sai do conjunto no servidor
- **THEN** a sincronização seguinte a apaga da cópia local

# photo-training-consent Specification

## Purpose

Permitir que cada pessoa escolha, de forma informada e revogável, se fotos específicas podem ser copiadas para uma área privada de treinamento, sem confundir esse uso com o histórico de análises.

## Requirements

### Requirement: Coleta opcional e separada do histórico

O app SHALL exigir aceite explícito dos documentos atuais, consentimento geral opcional e autorização individual antes de coletar cada foto. A opção SHALL ficar desmarcada por padrão e SHALL NOT surgir do cadastro ou do envio ao histórico. A falta de CNPJ, endereço institucional ou substituto do contato SHALL NOT bloquear a coleta. A recusa SHALL manter análise local e histórico disponíveis.

#### Scenario: Primeira utilização
- **WHEN** a conta ainda não tem consentimento de treinamento
- **THEN** a opção aparece desmarcada e nenhuma foto é enviada para treinamento
- **AND** câmera, galeria, análise local e histórico continuam disponíveis

#### Scenario: Informações ainda não publicadas
- **WHEN** o projeto não possui CNPJ, endereço institucional ou pessoa substituta do contato
- **THEN** a pessoa ainda pode decidir pelo consentimento opcional após ler os documentos atuais
- **AND** nenhum dado é enviado sem as escolhas geral e individual

#### Scenario: Aceite explícito e autorização por foto
- **WHEN** a pessoa aceitou os documentos atuais, ativou a opção geral e autorizou uma foto específica
- **THEN** o app pode sincronizar aquela foto para o bucket privado após confirmação do servidor
- **AND** fotos sem as duas escolhas continuam fora da coleta

### Requirement: Consentimento versionado e opcional

O sistema SHALL registrar por conta a decisão explícita, a versão dos termos de treinamento e a data da autorização, gerada pelo servidor. A decisão inicial e qualquer versão de termos ainda não aceita SHALL permanecer negadas, sem impedir a análise normal. Uma concessão SHALL exigir aceite das versões atuais dos Termos e da Política.

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

### Requirement: Reaceite de versões materiais por contas existentes

O app SHALL verificar no servidor se a conta autenticada possui recibo das versões atuais dos Termos e da Política. Quando faltar recibo, SHALL apresentar os documentos no próprio app e exigir confirmação explícita antes de liberar funções conectadas. O recibo SHALL ser criado no servidor somente após essa ação, com timestamp do servidor, e SHALL ser imutável. Falha de rede SHALL NOT criar recibo nem impedir classificação local offline.

#### Scenario: Conta existente sem recibo atual
- **WHEN** uma conta autenticada não tem recibo das versões atuais e há conexão
- **THEN** os documentos atuais são apresentados dentro do app
- **AND** o recibo só é criado após confirmação explícita

#### Scenario: Reaceite indisponível sem rede
- **WHEN** a verificação ou o registro de aceite falha por indisponibilidade de rede
- **THEN** nenhum recibo é criado pelo app
- **AND** classificação local continua disponível
- **AND** o app verifica novamente ao retornar ao primeiro plano

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

O app SHALL disponibilizar Termos e Política antes da autorização, explicando finalidade, dados usados, armazenamento, retenção, revogação e limites de remoção após treinamento. O histórico remoto SHALL ser mantido enquanto a conta estiver ativa, salvo exclusão solicitada; cópias de treinamento SHALL expirar em até 90 dias e exportações locais em até 30 dias.

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

#### Scenario: Expiração de cópia de treinamento
- **WHEN** uma cópia de treinamento alcança 90 dias desde o upload
- **THEN** a rotina automática remove o objeto do Storage
- **AND** uma revogação remove a cópia antes desse prazo

#### Scenario: Expiração de exportação
- **WHEN** uma exportação local alcança 30 dias desde sua criação
- **THEN** o processo de manutenção remove a pasta privada da exportação

#### Scenario: Exclusão e backups
- **WHEN** os dados ativos são excluídos a pedido da pessoa
- **THEN** os sistemas ativos processam a exclusão em até 30 dias
- **AND** exportações manuais do banco guardadas pela equipe no Google Drive podem persistir por até 30 dias
- **AND** a Política informa que exportações SQL não incluem arquivos do Storage

#### Scenario: Modelo já treinado
- **WHEN** uma cópia de treinamento é excluída após a revogação
- **THEN** a informação apresentada explica que pesos de modelos já treinados não são automaticamente revertidos

### Requirement: Identificação do projeto e contato

A Política SHALL identificar o projeto acadêmico, a equipe LeafCare de cinco integrantes, Ígor da Silva Antunes como representante e igorsantunes@outlook.com como contato. SHALL informar que não há CNPJ nem endereço institucional. Essas informações SHALL NOT bloquear o consentimento de treinamento.

#### Scenario: Entender quem responde pelo projeto
- **WHEN** a pessoa consulta a Política de Privacidade
- **THEN** encontra a equipe, o representante, o contato e a informação de que não há CNPJ ou endereço institucional

### Requirement: Backups manuais no Google Drive

A Política SHALL informar que a equipe mantém backups manuais do banco no Google Drive, com acesso restrito e retenção máxima de 30 dias. SHALL informar que exportações SQL não incluem arquivos do Storage. A falta de backup automático do plano Supabase SHALL NOT bloquear o consentimento de treinamento.

#### Scenario: Entender o prazo e o conteúdo dos backups
- **WHEN** a pessoa consulta a Política de Privacidade
- **THEN** encontra o prazo máximo de 30 dias e o limite das exportações SQL quanto aos arquivos do Storage

### Requirement: Termos e privacidade acessíveis no cadastro

A tela de cadastro SHALL mostrar uma única declaração com links inline verdes, do mesmo tamanho do restante do texto, para os Termos de Uso e a Política de Privacidade. Cada documento SHALL abrir dentro do app e permitir retornar ao cadastro. A declaração geral SHALL NOT autorizar treinamento de fotos.

#### Scenario: Consultar documentos antes do cadastro
- **WHEN** a pessoa escolhe Termos de Uso ou Política de Privacidade no cadastro
- **THEN** o documento correspondente abre dentro do aplicativo
- **AND** a ação de voltar retorna ao formulário de cadastro
- **AND** câmera, confirmação de e-mail e recuperação continuam usando seus fluxos de retorno ao aplicativo

#### Scenario: Criar conta após consultar a declaração legal
- **WHEN** a pessoa visualiza o cadastro
- **THEN** há apenas uma frase informativa com links inline para os dois documentos
- **AND** cada link abre o documento correspondente em uma tela interna
- **AND** nenhuma autorização global ou individual para treinamento é criada

#### Scenario: Aceite geral registrado no cadastro
- **WHEN** a pessoa cria conta a partir do cadastro do LeafCare
- **THEN** o app envia as versões exibidas dos Termos de Uso e da Política de Privacidade
- **AND** o servidor registra essas versões com seu próprio timestamp
- **AND** o recibo não autoriza treinamento de fotos

### Requirement: Público adulto

Os Termos de Uso SHALL informar que o LeafCare é destinado a pessoas com 18 anos ou mais.

#### Scenario: Consultar idade mínima
- **WHEN** a pessoa abre os Termos de Uso pelo cadastro
- **THEN** encontra a informação de que menores de 18 anos não devem criar conta nem usar o serviço

#### Scenario: Termos gerais não autorizam treinamento
- **WHEN** a pessoa lê ou aceita os termos gerais para criar uma conta
- **THEN** nenhuma autorização global ou individual para treinamento de fotos é criada

# photo-training-consent Specification

## Purpose

Permitir que cada pessoa escolha, de forma informada e revogável, se fotos específicas podem ser copiadas para uma área privada de treinamento, sem confundir esse uso com o histórico de análises.

## Requirements

### Requirement: Reaceite de versões materiais por contas existentes

O app SHALL verificar no servidor o recibo das versões atuais dos Termos e da Política e o consentimento de treinamento vigente. Se faltar algum, SHALL apresentar os documentos no app e exigir confirmação explícita antes de liberar o uso. O recibo SHALL ser criado só no servidor, após essa ação, com timestamp do servidor, e SHALL ser imutável. Sem rede, nenhum recibo SHALL ser criado, e o app SHALL funcionar só se o aparelho guardar consentimento vigente da conta.

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

Uma revogação SHALL bloquear novos envios imediatamente no aparelho e SHALL ser sincronizada antes de qualquer upload de treinamento pendente, mesmo sem aceite dos documentos atuais, antes de um novo aceite e, como tentativa, ao sair da conta. O servidor SHALL aceitar revogações com qualquer versão de consentimento. Após confirmação remota, as cópias de treinamento SHALL ser excluídas de fato, preservando histórico e análises. Depois de revogar, o app SHALL exigir um novo aceite.

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

### Requirement: Exclusão de cópias com a análise ou a conta

Excluir uma análise SHALL excluir sua cópia de treinamento, inclusive com envios pausados. Excluir a conta SHALL remover as cópias de treinamento antes da conta.

#### Scenario: Análise excluída
- **WHEN** a pessoa exclui uma análise com cópia de treinamento
- **THEN** a cópia é removida do Storage quando a exclusão sincroniza, mesmo com envios pausados

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

# Spec Delta

## MODIFIED Requirements

### Requirement: Administração autenticada

O backend SHALL conferir o papel atual e a autorização da operação administrativa. O APK SHALL mostrar o acesso circular à Administração no cabeçalho do histórico somente após confirmação online para admin, superadmin ou proprietário, mantendo credenciais elevadas fora do cliente.

#### Scenario: Conta comum ou permissão revogada

- **WHEN** uma conta sem papel administrativo atual chama a API
- **THEN** a operação é negada e o app limpa o estado administrativo

#### Scenario: Acesso inferior restrito

- **WHEN** um papel administrativo é confirmado online
- **THEN** o histórico oferece acesso à administração junto ao Perfil
- **AND** contas comuns não veem esse acesso e o Perfil não repete o atalho

### Requirement: Gestão de contas

Superadmins e proprietário SHALL listar/buscar contas, convidar e gerenciar contas inferiores com confirmação, incluindo recuperação, permissões, envios e exclusão retomável. Admins SHALL acessar somente fotos, revisões e suas atividades. Autoexclusão, mutação de pares/superiores e mudança do proprietário pelo app SHALL ser negadas.

#### Scenario: Exclusão parcialmente concluída

- **WHEN** a limpeza de Storage ou Auth falha
- **THEN** a conta permanece marcada para exclusão e a operação pode ser retomada

#### Scenario: Proteção da hierarquia

- **WHEN** um superadmin tenta rebaixar, excluir, pausar ou recuperar a conta do proprietário ou de outro superadmin
- **THEN** o servidor recusa antes de executar efeitos externos

### Requirement: Visão geral e auditoria

O resumo SHALL priorizar pendências e resultados de revisão com atalhos para triagem e consulta; totais de contas/envios SHALL aparecer para gestores. Classes e modelos SHALL permitir consultar fotos, exibindo modelos abreviados sem expansão do identificador. Listas SHALL usar lotes de 20 e a triagem SHALL carregar novos lotes automaticamente.

#### Scenario: Consultar pendências

- **WHEN** o administrador abre a triagem
- **THEN** vê primeiro fotos pendentes e pode filtrar a fila
- **AND** novos lotes são carregados sem repetir fotos salvas nem saltar fotos devido à redução das pendências

#### Scenario: Dados indisponíveis

- **WHEN** um total não é fornecido pelo servidor ou a consulta falha
- **THEN** o app indica indisponibilidade sem apresentar zero como contagem confirmada

## ADDED Requirements

### Requirement: Galeria de consulta

Atalhos de avaliações, classes e modelos SHALL abrir uma galeria privada de duas colunas com situação, previsão, filtros e navegação entre lotes. Tocar uma foto SHALL abrir detalhe de consulta com ampliação, previsão original e revisão humana; consultar SHALL preservar as revisões e a seleção ao fechar o detalhe.

#### Scenario: Consultar confirmadas por classe

- **WHEN** o administrador escolhe confirmadas ou uma classe
- **THEN** vê fotos em duas colunas com o filtro correspondente
- **AND** abrir e fechar o detalhe não envia revisão nem perde a página

#### Scenario: Falha de imagem

- **WHEN** uma miniatura não pode ser carregada
- **THEN** o cartão oferece nova tentativa sem bloquear outras fotos

### Requirement: Proprietário protegido

O sistema SHALL manter um único proprietário imutável pelo aplicativo. Apenas ele SHALL conceder/remover superadmins; superadmins SHALL conceder/remover admins em contas inferiores. A migração SHALL identificar o proprietário pelo bootstrap registrado ou único superadmin existente e recusar seleção ambígua.

#### Scenario: Delegação segura

- **WHEN** o proprietário concede superadmin a outra pessoa
- **THEN** ela pode gerir admins e usuários sem alterar a conta do proprietário ou seus pares

#### Scenario: Migração ambígua

- **WHEN** existem vários superadmins sem bootstrap que identifique o proprietário
- **THEN** a migração falha atomicamente e exige seleção explícita pelo responsável do banco

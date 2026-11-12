# superadmin Specification

## Purpose

Permitir administração restrita de contas e triagem humana de fotos, preservando a previsão original e registrando ações.

## Requirements

### Requirement: Administração autenticada

O backend SHALL conferir o papel atual e a autorização da operação administrativa. O APK SHALL mostrar o acesso circular à Administração no cabeçalho do histórico somente após confirmação online para admin, superadmin ou proprietário, mantendo credenciais elevadas fora do cliente.

#### Scenario: Conta comum ou permissão revogada

- **WHEN** uma conta sem papel administrativo atual chama a API
- **THEN** a operação é negada e o app limpa o estado administrativo

#### Scenario: Acesso inferior restrito

- **WHEN** um papel administrativo é confirmado online
- **THEN** o histórico oferece acesso à administração junto ao Perfil
- **AND** contas comuns não veem esse acesso e o Perfil não repete o atalho

#### Scenario: Acesso superior restrito

- **WHEN** o papel administrativo é confirmado online
- **THEN** o histórico oferece Administração ao lado do Perfil
- **AND** a câmera permanece disponível separadamente e contas comuns não veem o atalho

#### Scenario: Falha temporária após confirmação

- **WHEN** uma consulta de permissão falha por rede após confirmação na mesma sessão
- **THEN** o atalho permanece visível e as operações continuam exigindo autorização atual no servidor

### Requirement: Gestão de contas

Superadmins e proprietário SHALL listar/buscar contas, convidar e gerenciar contas inferiores com confirmação, incluindo recuperação, permissões, envios e exclusão retomável. Admins SHALL acessar somente fotos, revisões e suas atividades. Autoexclusão, mutação de pares/superiores e mudança do proprietário pelo app SHALL ser negadas.

#### Scenario: Exclusão parcialmente concluída

- **WHEN** a limpeza de Storage ou Auth falha
- **THEN** a conta permanece marcada para exclusão e a operação pode ser retomada

#### Scenario: Proteção da hierarquia

- **WHEN** um superadmin tenta rebaixar, excluir, pausar ou recuperar a conta do proprietário ou de outro superadmin
- **THEN** o servidor recusa antes de executar efeitos externos

### Requirement: Revisão separada

A triagem SHALL exibir foto, classe/confiança/Top3/modelo originais e revisão humana separada. Confirmar, corrigir, marcar duvidosa ou rejeitar SHALL registrar responsável, data, observação e histórico sem alterar a inferência original.

#### Scenario: Correção ou rejeição

- **WHEN** o superadmin salva uma revisão válida
- **THEN** o registro humano é atualizado e a foto rejeitada permanece privada
- **AND** uma versão desatualizada exige recarregar antes de salvar

### Requirement: Visão geral e auditoria

O resumo SHALL priorizar pendências e resultados de revisão com atalhos para triagem e consulta; totais de contas/envios SHALL aparecer para gestores. Classes e modelos SHALL permitir consultar fotos, exibindo modelos abreviados sem expansão do identificador. Listas SHALL usar lotes de 20 e a triagem SHALL carregar novos lotes automaticamente.

#### Scenario: Consultar pendências

- **WHEN** o administrador abre a triagem
- **THEN** vê primeiro fotos pendentes e pode filtrar a fila
- **AND** novos lotes são carregados sem repetir fotos salvas nem saltar fotos devido à redução das pendências

#### Scenario: Dados indisponíveis

- **WHEN** um total não é fornecido pelo servidor ou a consulta falha
- **THEN** o app indica indisponibilidade sem apresentar zero como contagem confirmada

### Requirement: Fila contínua de triagem

A triagem SHALL mostrar uma foto por vez, permitir navegar por botões explícitos e avançar após salvar com sucesso, respeitando os filtros ativos. Passar uma foto SHALL preservar sua revisão remota. Gestos verticais SHALL NOT trocar a foto.

#### Scenario: Salvar e continuar

- **WHEN** uma revisão é salva com sucesso
- **THEN** a próxima foto da seleção aparece na mesma tela
- **AND** a conclusão da fila é indicada quando não existem outras fotos na seleção

#### Scenario: Passar sem revisar

- **WHEN** o superadmin usa os botões de anterior ou pular
- **THEN** muda a foto sem enviar uma revisão

#### Scenario: Inspecionar foto

- **WHEN** o superadmin desliza verticalmente sobre a foto
- **THEN** a foto atual continua selecionada

#### Scenario: Falha ao salvar

- **WHEN** o salvamento falha
- **THEN** a foto e a avaliação permanecem disponíveis para tentar novamente
- **AND** conflito de versão exige recarregar o registro antes de salvar

### Requirement: Ações de revisão acessíveis

Confirmar, Corrigir, Duvidosa e Rejeitar SHALL aparecer na avaliação com dimensões equivalentes, junto à previsão e sem menu intermediário. A navegação SHALL permanecer acessível. Observação SHALL ser opcional para todas as decisões no app e no servidor. Correção SHALL permitir escolher a classe; os campos SHALL ser roláveis e as ações respeitar barras do sistema e teclado.

#### Scenario: Tela pequena e teclado aberto

- **WHEN** o superadmin preenche uma avaliação em uma tela pequena ou com teclado aberto
- **THEN** pode rolar os campos e acessar cancelar e salvar no rodapé
- **AND** os dados originais, Top3, modelo, zoom e histórico continuam acessíveis

#### Scenario: Rejeição ou dúvida sem observação

- **WHEN** o superadmin escolhe Rejeitar ou Duvidosa e salva sem observação
- **THEN** a revisão é registrada, preservando previsão original, versão e auditoria
- **AND** a fila avança somente após sucesso

### Requirement: Painel de triagens

A aba Triagem SHALL abrir um painel com fotos recebidas, pendentes, revisadas e progresso calculado dos totais remotos. SHALL oferecer entrada para pendências e seleções por situação/classe, sem inventar contagens indisponíveis. Carregamento e falha SHALL ser indicados e o retorno da avaliação SHALL atualizar os totais.

#### Scenario: Abrir painel

- **WHEN** o superadmin toca a aba Triagem
- **THEN** vê o painel e escolhe a seleção que deseja avaliar
- **AND** pode acompanhar quantas fotos já foram revisadas

#### Scenario: Falha de conexão

- **WHEN** os totais não podem ser carregados
- **THEN** o app indica falha e oferece nova tentativa sem apresentar zeros como resultado confirmado

### Requirement: Compartilhar convite de acesso

Superadmins SHALL poder gerar um convite de acesso vinculado ao e-mail informado e compartilhá-lo pelo seletor nativo do Android, além do envio direto por e-mail. O link SHALL ser gerado exclusivamente no servidor após autorização atual, sem persistência ou registro do link na auditoria.

#### Scenario: Escolher aplicativo para o convite

- **WHEN** um superadmin informa o e-mail e escolhe compartilhar convite
- **THEN** o servidor gera o link de acesso e o Android apresenta os aplicativos compatíveis instalados
- **AND** o usuário escolhe o destino e confirma o envio no aplicativo escolhido

#### Scenario: Falha ao gerar convite

- **WHEN** a geração falha ou o acesso é negado
- **THEN** o app informa o erro e não abre o compartilhamento com um link inválido

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

### Requirement: Revisão das cópias de treino

O painel SHALL mostrar quantas cópias de treino estão prontas, aguardando revisão e fora do treino. Também SHALL mostrar quantas cópias v1.4 pendentes vencem em até 14 dias. Fotos com cópia de treino SHALL exibir o selo "Treino" e, quando a cópia tem prazo, os dias restantes. A consulta SHALL permitir limitar às cópias de treino, e a fila SHALL priorizar as cópias pendentes que vencem antes, deixando as permanentes depois delas.

#### Scenario: Revisar cópias
- **WHEN** a pessoa revisora toca em "Revisar cópias" no Resumo
- **THEN** a fila abre só com cópias de treino pendentes, primeiro as que vencem antes

#### Scenario: Cópia permanente
- **WHEN** a cópia foi enviada depois do aceite da v1.5
- **THEN** o selo mostra "Treino" sem contagem de dias

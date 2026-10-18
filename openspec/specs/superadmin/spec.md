# superadmin Specification

## Purpose

Permitir administração restrita de contas e triagem humana de fotos, preservando a previsão original e registrando ações.

## Requirements

### Requirement: Administração autenticada

O backend SHALL conferir papel superadmin atual em toda operação administrativa. O APK SHALL mostrar acesso circular à Administração ao lado da câmera no histórico somente após confirmação online e manter credenciais elevadas fora do cliente.

#### Scenario: Conta comum ou permissão revogada

- **WHEN** uma conta sem papel atual de superadmin chama a API
- **THEN** a operação é negada e o app limpa o estado administrativo

#### Scenario: Acesso inferior restrito

- **WHEN** a permissão de superadmin é confirmada online
- **THEN** o histórico oferece acesso circular à administração ao lado da câmera
- **AND** contas comuns não veem esse acesso e o Perfil não repete o atalho

### Requirement: Gestão de contas

Superadmins SHALL listar/buscar contas, convidar, reenviar convite, enviar recuperação, administrar permissões e excluir conta/dados remotos com confirmação e retomada. Autoexclusão e remoção do último superadmin SHALL ser negadas.

#### Scenario: Exclusão parcialmente concluída

- **WHEN** a limpeza de Storage ou Auth falha
- **THEN** a conta permanece marcada para exclusão e a operação pode ser retomada

### Requirement: Revisão separada

A triagem SHALL exibir foto, classe/confiança/Top3/modelo originais e revisão humana separada. Confirmar, corrigir, marcar duvidosa ou rejeitar SHALL registrar responsável, data, observação e histórico sem alterar a inferência original.

#### Scenario: Correção ou rejeição

- **WHEN** o superadmin salva uma revisão válida
- **THEN** o registro humano é atualizado e a foto rejeitada permanece privada
- **AND** uma versão desatualizada exige recarregar antes de salvar

### Requirement: Visão geral e auditoria

O painel SHALL mostrar contagens de usuários/envios/fotos/revisões e distribuição por classe/modelo, e oferecer filtros e histórico de ações. Listas administrativas SHALL usar lotes de 20; a triagem SHALL carregar novos lotes sem exigir navegação manual por páginas.

#### Scenario: Consultar pendências

- **WHEN** o superadmin abre a triagem
- **THEN** vê primeiro fotos pendentes e pode filtrar a fila
- **AND** novos lotes são carregados durante a revisão sem repetir fotos salvas na sessão nem saltar fotos devido à redução das pendências

### Requirement: Fila contínua de triagem

A triagem SHALL mostrar uma foto por vez, permitir deslizar verticalmente ou usar um botão para passar sem salvar e avançar após salvar com sucesso, respeitando os filtros ativos. Passar uma foto SHALL preservar sua revisão remota.

#### Scenario: Salvar e continuar

- **WHEN** uma revisão é salva com sucesso
- **THEN** a próxima foto da seleção aparece na mesma tela
- **AND** a conclusão da fila é indicada quando não existem outras fotos na seleção

#### Scenario: Passar sem revisar

- **WHEN** o superadmin desliza ou usa o botão de próxima foto
- **THEN** muda a foto sem enviar uma revisão

#### Scenario: Falha ao salvar

- **WHEN** o salvamento falha
- **THEN** a foto e a avaliação permanecem disponíveis para tentar novamente
- **AND** conflito de versão exige recarregar o registro antes de salvar

### Requirement: Ações de revisão acessíveis

Confirmar e avançar SHALL estar disponíveis na fila. Corrigir, marcar duvidosa ou rejeitar SHALL oferecer avaliação com conteúdo rolável e ações fora da rolagem, respeitando barras do sistema e teclado. Motivo SHALL ser obrigatório para dúvida e rejeição.

#### Scenario: Tela pequena e teclado aberto

- **WHEN** o superadmin preenche uma avaliação em uma tela pequena ou com teclado aberto
- **THEN** pode rolar os campos e acessar cancelar e salvar no rodapé
- **AND** os dados originais, Top3, modelo, zoom e histórico continuam acessíveis

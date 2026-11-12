# Spec Delta

## MODIFIED Requirements

### Requirement: Administração autenticada

O backend SHALL conferir papel superadmin atual em toda operação administrativa. O APK SHALL mostrar Administração no cabeçalho do histórico ao lado do Perfil após confirmação online e manter credenciais elevadas fora do cliente. Falhas transitórias SHALL repetir a consulta e preservar a visibilidade já confirmada na sessão; negação ou troca de conta SHALL limpar o estado administrativo.

#### Scenario: Conta comum ou permissão revogada

- **WHEN** uma conta sem papel atual de superadmin chama a API
- **THEN** a operação é negada e o app limpa o estado administrativo

#### Scenario: Acesso superior restrito

- **WHEN** a permissão de superadmin é confirmada online
- **THEN** o histórico oferece Administração ao lado do Perfil
- **AND** a câmera permanece disponível separadamente e contas comuns não veem o atalho

#### Scenario: Acesso inferior restrito

- **WHEN** o usuário abre o histórico
- **THEN** a área inferior oferece a câmera sem um segundo botão administrativo
- **AND** o acesso administrativo autorizado fica no cabeçalho

#### Scenario: Falha temporária após confirmação

- **WHEN** uma consulta de permissão falha por rede após confirmação na mesma sessão
- **THEN** o atalho permanece visível e as operações continuam exigindo autorização atual no servidor

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

## ADDED Requirements

### Requirement: Compartilhar convite de acesso

Superadmins SHALL poder gerar um convite de acesso vinculado ao e-mail informado e compartilhá-lo pelo seletor nativo do Android, além do envio direto por e-mail. O link SHALL ser gerado exclusivamente no servidor após autorização atual, sem persistência ou registro do link na auditoria.

#### Scenario: Escolher aplicativo para o convite

- **WHEN** um superadmin informa o e-mail e escolhe compartilhar convite
- **THEN** o servidor gera o link de acesso e o Android apresenta os aplicativos compatíveis instalados
- **AND** o usuário escolhe o destino e confirma o envio no aplicativo escolhido

#### Scenario: Falha ao gerar convite

- **WHEN** a geração falha ou o acesso é negado
- **THEN** o app informa o erro e não abre o compartilhamento com um link inválido

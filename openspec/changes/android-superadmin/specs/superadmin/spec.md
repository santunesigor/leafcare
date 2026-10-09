# Spec Delta

## Purpose

Permitir administração restrita de contas e triagem humana de fotos, preservando a previsão original e registrando ações.

## ADDED Requirements

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

O painel SHALL mostrar contagens de usuários/envios/fotos/revisões e distribuição por classe/modelo, e oferecer listas paginadas de 20 com filtros e histórico de ações.

#### Scenario: Consultar pendências

- **WHEN** o superadmin abre a triagem
- **THEN** vê primeiro fotos pendentes e pode filtrar/navegar por páginas

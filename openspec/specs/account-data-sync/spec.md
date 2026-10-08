# account-data-sync Specification

## Purpose

Descrever a persistência local como base da interface e o contrato de sincronização do LeafCare com Supabase, incluindo retries, exclusão com tombstones e isolamento das análises/fotos por usuário autenticado.

## Requirements

### Requirement: Falhas remotas preservam dados locais

A sincronização SHALL usar o UUID local como identificador remoto e conservar análises pendentes quando houver falha de rede ou autenticação, permitindo tentar novamente sem criar uma segunda análise para o mesmo identificador.

#### Scenario: Falha no upload

- **WHEN** o backend falha durante o envio de uma análise local
- **THEN** a análise permanece no banco com estado para retry
- **AND** uma nova tentativa reutiliza seu identificador

### Requirement: Exclusão não ressuscita por restauração

A sincronização SHALL preservar tombstones locais até confirmar a exclusão remota e SHALL impedir que um restore sobrescreva uma análise com alteração ou exclusão local pendente.

#### Scenario: Restore encontra análise excluída localmente

- **WHEN** a análise remota ainda aparece ativa e a linha local possui tombstone
- **THEN** o restore preserva a exclusão local pendente
- **AND** a análise não reaparece no histórico ativo

### Requirement: Isolamento por conta

O aplicativo SHALL preservar dados locais na reautenticação da mesma conta e limpar dados/fotos da conta anterior quando uma conta diferente é autenticada. O backend SHALL restringir análises e fotos privadas ao usuário proprietário pelas políticas existentes.

#### Scenario: Trocar de conta no aparelho

- **WHEN** um usuário diferente é autenticado em um aparelho com dados da conta anterior
- **THEN** as linhas e fotos anteriores são removidas do armazenamento local antes do acesso ao histórico dessa conta

#### Scenario: Entrar novamente na mesma conta

- **WHEN** o mesmo usuário se autentica novamente
- **THEN** o aplicativo conserva seu histórico local

### Requirement: Fotos privadas com estado independente

A sincronização SHALL acompanhar o estado das fotos separadamente do envio da análise e derivar o caminho remoto a partir da conta autenticada e do UUID da análise.

#### Scenario: Enviar análise e falhar no envio da foto

- **WHEN** a análise foi confirmada remotamente e o upload da foto falha
- **THEN** o histórico conserva a análise
- **AND** o estado da foto permite uma nova tentativa com o mesmo caminho

## References

- [AnalysisSyncRunner](../../../android-app/app/src/main/java/br/com/leafcare/data/AnalysisSyncRunner.kt)
- [LeafCareApplication](../../../android-app/app/src/main/java/br/com/leafcare/LeafCareApplication.kt)
- [AnalysisSyncRunnerTest](../../../android-app/app/src/test/java/br/com/leafcare/data/AnalysisSyncRunnerTest.kt)
- [AccountIsolationTest](../../../android-app/app/src/test/java/br/com/leafcare/data/AccountIsolationTest.kt)
- [Migrations Supabase](../../../supabase/migrations/)

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

A sincronização SHALL manter estado independente da foto e caminho por conta/UUID. Somente análises conclusivas com confiança válida maior ou igual ao limiar registrado SHALL enviar fotos. As demais fotos SHALL permanecer locais, conservando a sincronização da análise e classe prevista. Antes de upload confirmado, photo_path SHALL ser nulo e o restore SHALL representar ausência de foto remota.

#### Scenario: Enviar análise e falhar no envio da foto

- **WHEN** a análise conclusiva foi confirmada remotamente e o upload da foto falha
- **THEN** o histórico conserva a análise
- **AND** o estado da foto permite uma nova tentativa com o mesmo caminho

#### Scenario: Resultado inconclusivo

- **WHEN** a análise é inconclusiva ou sua confiança fica abaixo do limiar registrado
- **THEN** nenhum byte da foto é enviado ao bucket, inclusive em retry de versões anteriores
- **AND** o histórico e a foto local permanecem disponíveis no aparelho original

#### Scenario: Confiança no limiar

- **WHEN** uma análise conclusiva tem confiança igual ao seu limiar
- **THEN** a foto pode ser enviada e associada ao UUID e classe já sincronizados

#### Scenario: Restaurar análise sem foto remota

- **WHEN** uma análise remota não tem caminho de foto confirmado
- **THEN** a análise é restaurada sem tentar baixar um arquivo inexistente
- **AND** uma foto associada posteriormente pode ser baixada sem sobrescrever o resultado local

## References

- [AnalysisSyncRunner](../../../android-app/app/src/main/java/br/com/leafcare/data/AnalysisSyncRunner.kt)
- [LeafCareApplication](../../../android-app/app/src/main/java/br/com/leafcare/LeafCareApplication.kt)
- [AnalysisSyncRunnerTest](../../../android-app/app/src/test/java/br/com/leafcare/data/AnalysisSyncRunnerTest.kt)
- [AccountIsolationTest](../../../android-app/app/src/test/java/br/com/leafcare/data/AccountIsolationTest.kt)
- [Migrations Supabase](../../../supabase/migrations/)

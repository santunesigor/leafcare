## MODIFIED Requirements

### Requirement: Elegibilidade para o conjunto de treinamento

O pipeline SHALL incluir somente cópias com menos de 90 dias de análises ativas e conclusivas, feitas depois do aceite dos documentos v1.4 da conta, com consentimento e autorização 1.2 vigentes, conta fora de exclusão, qualidade aprovada e classe confirmada ou corrigida por revisão humana. O rótulo de treino SHALL ser a classe revisada. Identificadores de conta e de análise e metadados EXIF SHALL ser removidos antes da exportação, e fotos de uma mesma pessoa SHALL ser agrupadas sem identificador reutilizável entre exportações.

#### Scenario: Foto aprovada
- **WHEN** todas as condições estão cumpridas e há classe confirmada ou corrigida
- **THEN** a cópia fica pronta e pode ser exportada com a classe revisada, sem identificadores

#### Scenario: Falta de revisão ou qualidade
- **WHEN** falta revisão humana válida ou aprovação de qualidade
- **THEN** a imagem é excluída da exportação
- **AND** a classe prevista automaticamente não é tratada como rótulo confirmado

#### Scenario: Foto anterior ao aceite
- **WHEN** a análise foi feita antes do primeiro aceite dos documentos v1.4 da conta
- **THEN** a cópia não entra em nenhum conjunto

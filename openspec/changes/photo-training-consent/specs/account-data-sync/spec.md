# Spec Delta

## MODIFIED Requirements

### Requirement: Consentimento de treinamento independente do histórico

A sincronização SHALL manter as decisões de treinamento separadas das análises e fotos de histórico. SHALL transmitir autorizações e revogações pendentes antes de qualquer foto para treinamento, e somente após confirmação remota enviar fotos marcadas individualmente. O envio SHALL exigir recibo atual dos Termos e da Política.

#### Scenario: Foto de histórico sem autorização de treino
- **WHEN** uma análise conclusiva sincroniza sua foto normal de histórico sem autorização individual de treinamento
- **THEN** o caminho normal de backup pode ser usado conforme a política atual
- **AND** nenhum arquivo é enviado para a área de treinamento

#### Scenario: Revogação pendente com uploads na fila
- **WHEN** o usuário revogou localmente enquanto há fotos de treinamento pendentes
- **THEN** o runner sincroniza a revogação primeiro e não envia as fotos pendentes
- **AND** os dados locais permanecem disponíveis para a análise normal

#### Scenario: Recibo legal atual pendente
- **WHEN** a conta ainda não aceitou as versões atuais dos documentos legais
- **THEN** nenhuma foto de treinamento é enviada
- **AND** a classificação local continua disponível

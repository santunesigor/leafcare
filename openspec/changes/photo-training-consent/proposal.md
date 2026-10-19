# Proposal

## Why

As fotos enviadas para o histórico não têm autorização para treinamento. O aplicativo precisa registrar uma escolha opcional e explícita, mantê-la disponível offline e permitir que apenas imagens autorizadas e revisadas cheguem ao conjunto de treinamento.

## What Changes

- Adicionar termos e aviso de privacidade consultáveis no app, consentimento desmarcado por padrão e gestão/revogação no Perfil.
- Exibir no cadastro acesso aos Termos de Uso e à Política de Privacidade em telas internas, sem depender de um site; a navegação retorna ao cadastro.
- Registrar a versão dos termos, o estado e o instante do consentimento no Room e no Supabase; registrar separadamente a autorização de cada foto.
- Separar fotos de treinamento das fotos de histórico, exigir confirmação remota do consentimento antes de enviar bytes e bloquear uploads assim que houver revogação local pendente.
- Excluir remotamente as cópias de treinamento após revogação, mantendo o histórico normal da análise.
- Preparar a exportação de treinamento para aceitar apenas consentimento vigente, foto com qualidade aprovada e classe validada por revisão humana; retirar identificadores desnecessários.
- Documentar retenção, revogação, exclusão e o limite de remover imagens de modelos já treinados.

## Capabilities

### New Capabilities
- `photo-training-consent`: consentimento versionado, autorização individual de fotos, fila de exclusão e elegibilidade segura para treinamento.

### Modified Capabilities
- `account-data-sync`: sincronização offline de consentimentos e revogações, sem tratar o backup do histórico como autorização de treino.

## Impact

- Android: Room, WorkManager, fluxo de captura/galeria e Perfil em Jetpack Compose.
- Android Auth: cadastro com leitura interna dos documentos legais e retorno ao formulário.
- Supabase: nova migration, tabelas/RLS e bucket privado separado para cópias de treinamento.
- Machine Learning: ferramenta de exportação/ingestão que valida consentimento atual, qualidade e rótulo humano antes de incluir uma foto.
- Documentação: aviso de privacidade e termos versionados no app e explicação de retenção e limitações.

O consentimento será opcional e desmarcado por padrão. Fotos antigas do bucket de histórico não serão promovidas automaticamente para treinamento. O prazo de retenção será definido pelo ciclo de autorização: a cópia fica elegível enquanto o consentimento estiver ativo; a revogação bloqueia novos envios e inicia a exclusão das cópias ainda armazenadas. Nenhuma remoção de foto implica esquecimento automático por modelos já treinados.

O projeto identifica provisoriamente a equipe LeafCare, com cinco pessoas e sem CNPJ informado, como controladora. O histórico remoto é mantido durante a vida da conta; exclusões são processadas em até 30 dias; futuras cópias de treinamento expiram em até 90 dias e exportações locais em 30 dias. Backups do banco podem reter dados eliminados por até 30 dias conforme o plano Supabase. Os documentos ficam dentro do app, na versão 1.1, sem depender de site. A coleta permanece indisponível até haver canal de privacidade monitorado e limpeza automática instalada e testada.

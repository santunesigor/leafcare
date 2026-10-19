# Design

## Context

O Room guarda análises e estados independentes de sincronização; o WorkManager chama um runner que envia linhas, fotos conclusivas e tombstones. O bucket `analysis-photos` é privado e não representa autorização para treinamento. O Profile não tem uma área de privacidade, e o pipeline ML não consulta Supabase.

## Goals / Non-Goals

**Goals:**
- Manter autorização global e escolha individual disponíveis offline, com negação como padrão.
- Separar registro, objeto de Storage e fila de treinamento do backup de histórico.
- Bloquear o runner em revogação pendente e apagar cópias remotas após confirmação.
- Oferecer exportação atualizada que exige consentimento vigente, revisão humana de classe e aprovação de qualidade.

**Non-Goals:**
- Usar automaticamente imagens antigas, classes previstas pelo modelo ou fotos sem revisão.
- Retreinar, recalibrar ou substituir o modelo integrado nesta mudança.
- Remover retroativamente o conhecimento de um modelo já treinado.

## Decisions

1. **Room é a fonte local.** Adicionar registros de consentimento por conta e de opt-in por análise. A migração cria o estado sem autorização; análises e fotos existentes permanecem sem opt-in. A troca de conta limpa ou isola esses registros junto com os dados locais atuais.

2. **Armazenamento separado.** Criar tabelas e bucket privado próprios para consentimento e cópias de treinamento. RLS confere conta, consentimento ativo e opt-in individual na gravação; leitura fica restrita a `service_role`. A remoção pelo próprio usuário continua permitida mesmo depois da revogação. O APK nunca recebe `service_role`.

3. **Sincronização ordenada.** Uma aceitação local só libera bytes depois que a mesma versão de termos for confirmada no servidor. Revogação altera o estado local imediatamente, bloqueia retries e entra numa fila prioritária; ao reconectar, o servidor nega uploads primeiro e o runner remove cópias de treinamento antes de processar qualquer outro envio.

4. **Escolha por imagem.** A configuração no Perfil registra a autorização e os documentos consultáveis. Na câmera/galeria, cada nova análise recebe uma opção individual desmarcada; sem consentimento global vigente, ela fica desabilitada. A análise local e seu backup normal seguem o fluxo atual.

5. **Conteúdo versionado no app.** Os documentos abrem em telas internas, com versão 1.3 e data 19/10/2026. O cadastro envia as versões aceitas e o banco registra timestamp do servidor. O projeto acadêmico identifica a equipe LeafCare (cinco pessoas) como responsável pelo tratamento e Ígor da Silva Antunes como representante; não há CNPJ nem endereço institucional. O contato é igorsantunes@outlook.com, exibido como texto copiável no app. O histórico remoto fica enquanto a conta estiver ativa; exclusões são processadas em até 30 dias; cópias de treinamento expiram em 90 dias; exportações locais em 30 dias; backups manuais do banco no Google Drive têm acesso restrito e retenção máxima de 30 dias. A coleta é opcional e exige aceite atual, consentimento geral e autorização individual.

6. **Reaceite de contas existentes.** No início de uma sessão autenticada, o app consulta o recibo das versões atuais. Se estiver ausente, apresenta os documentos em telas internas e só libera o caminho conectado após o RPC autenticado registrar a confirmação explícita com timestamp do servidor. O RPC aceita apenas as versões publicadas e não permite alteração ou remoção de recibos. Se a rede falhar, nenhuma confirmação é presumida; o usuário pode continuar com classificação local e o app repete a consulta ao voltar ao primeiro plano.

7. **Exportação somente autorizada.** O exportador consulta fotos apenas com consentimento vigente, autorização individual, qualidade, deduplicação e revisão humana. Remove EXIF e identificadores e grava classe revisada com identificador anônimo numa área local ignorada pelo Git. O bucket continua privado e o RLS limita upload/leitura à foto própria com as autorizações atuais.

8. **Documentos legais no app.** O cadastro abre Termos de Uso e Política de Privacidade em telas Compose internas e retorna ao formulário. O fluxo não depende de um site nem abre navegador externo. A leitura ou aceite dos termos gerais permanece separada do consentimento opcional para treinamento.

## Risks / Trade-offs

- [Revogação sem rede] Cópias remotas não podem ser apagadas enquanto o aparelho está offline → bloquear novos envios imediatamente e manter a exclusão como prioridade na fila até confirmação.
- [Revogação após exportação local] Uma cópia já exportada pode existir no diretório de trabalho → o comando reconcilia a saída com a lista vigente em cada execução; arquivos e datasets derivados devem permanecer privados e ser documentados.
- [Treino já executado] Um peso treinado não identifica isoladamente a imagem que o influenciou → excluir imagem/registro disponíveis e informar que o modelo não é automaticamente revertido.
- [Qualidade heurística] Validações automáticas de imagem não substituem revisão agronômica → rótulo vem exclusivamente da revisão humana e as métricas de qualidade são registradas no relatório da exportação.
- [Credencial de exportação] A consulta do dataset requer `service_role` fora do app → ler somente de variável de ambiente, nunca imprimir, persistir ou versionar a chave.

## Migration Plan

1. Aplicar migrations aditivas: tabelas/RLS para treinamento e recibo de aceite geral, com documentos atuais v1.3 e termos de treinamento v1.1. Recibos de cadastro vêm do trigger; contas existentes recebem recibo somente via RPC após confirmação in-app. Upload exige aceite atual, consentimento geral e autorização individual. Usuários leem apenas seus próprios registros.
2. Distribuir o APK com Room v4 e fluxo de reaceite; dados locais antigos migram como não autorizados. Falhas de rede não bloqueiam a análise local.
3. Atualizar o Supabase/Edge Function antes de habilitar exportações. Sem o contrato remoto, consentimentos ficam em retry e nenhuma foto de treinamento sai do aparelho.
4. Rollback do app mantém as tabelas e bucket; não apagar consentimentos ou fotos automaticamente. Para rollback do backend, bloquear novos uploads e preservar registros de revogação até a limpeza auditada.

## Open Questions

O usuário informou que mantém MFA no Outlook e indicou Ígor da Silva Antunes como representante do projeto. Não há CNPJ, endereço institucional ou substituto do contato; esses dados não bloqueiam a coleta opt-in. A organização Supabase está no plano Free, que não oferece backup diário automático; a equipe fará exportações manuais do banco e as guardará no Google Drive com acesso restrito e retenção máxima de 30 dias. O SQL dump não inclui objetos de Storage. O projeto fica em São Paulo (`sa-east-1`); a Política informa suporte global, telemetria/logs processados fora da região e CDN global potencial do Storage, conforme configuração. A proteção contra senhas vazadas aparece no Security Advisor, mas a documentação oficial informa que esse recurso só está disponível nos planos Pro ou superiores; permanece um alerta do plano, não uma condição para a coleta. A limpeza automática foi instalada e testada. Os documentos são internos ao app; a coleta está habilitada como opt-in, nunca por padrão.

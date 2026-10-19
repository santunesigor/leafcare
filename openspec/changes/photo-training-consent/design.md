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

5. **Conteúdo versionado no app.** Os documentos abrem em telas internas, com versão e data de publicação. O cadastro envia as versões aceitas e um trigger registra o timestamp do servidor em tabela com leitura somente do próprio titular. Enquanto controlador, contato de privacidade e prazos de retenção não forem definidos, o app explica o motivo da indisponibilidade e mantém desativados o aceite global e a escolha individual para treinamento. A autorização só será habilitada quando esses dados forem publicados; o conteúdo explicará também que isso não desfaz modelos já treinados.

6. **Exportação bloqueada por padrão.** O exportador valida consentimento vigente, qualidade, duplicatas, EXIF e revisão humana, mas termina sem consultar nem baixar fotos enquanto os dados legais não forem publicados. O bucket também não concede INSERT a clientes autenticados. Após habilitação futura, a saída contém classe revisada e identificador anônimo e ficará numa área local ignorada pelo Git.

7. **Documentos legais no app.** O cadastro abre Termos de Uso e Política de Privacidade em telas Compose internas e retorna ao formulário. O fluxo não depende de um site nem abre navegador externo. A leitura ou aceite dos termos gerais permanece separada do consentimento opcional para treinamento.

## Risks / Trade-offs

- [Revogação sem rede] Cópias remotas não podem ser apagadas enquanto o aparelho está offline → bloquear novos envios imediatamente e manter a exclusão como prioridade na fila até confirmação.
- [Revogação após exportação local] Uma cópia já exportada pode existir no diretório de trabalho → o comando reconcilia a saída com a lista vigente em cada execução; arquivos e datasets derivados devem permanecer privados e ser documentados.
- [Treino já executado] Um peso treinado não identifica isoladamente a imagem que o influenciou → excluir imagem/registro disponíveis e informar que o modelo não é automaticamente revertido.
- [Qualidade heurística] Validações automáticas de imagem não substituem revisão agronômica → rótulo vem exclusivamente da revisão humana e as métricas de qualidade são registradas no relatório da exportação.
- [Credencial de exportação] A consulta do dataset requer `service_role` fora do app → ler somente de variável de ambiente, nunca imprimir, persistir ou versionar a chave.

## Migration Plan

1. Aplicar migrations aditivas: tabelas e RLS para treinamento e recibo de aceite geral. O recibo é criado pelo trigger de cadastro; usuários só leem seu próprio registro. Não criar política de inserção de fotos enquanto faltarem as definições legais; manter a exclusão própria permitida.
2. Distribuir o APK com Room v4; dados locais antigos migram como não autorizados.
3. Atualizar o Supabase/Edge Function antes de habilitar exportações. Sem o contrato remoto, consentimentos ficam em retry e nenhuma foto de treinamento sai do aparelho.
4. Rollback do app mantém as tabelas e bucket; não apagar consentimentos ou fotos automaticamente. Para rollback do backend, bloquear novos uploads e preservar registros de revogação até a limpeza auditada.

## Open Questions

O usuário confirmou que o controlador, o contato de privacidade e os prazos de retenção ainda não foram definidos. Os documentos legais são acessados dentro do aplicativo e não dependem de URLs externas. O aceite para treinamento, a autorização por foto, gravações no Storage e a exportação permanecem desativados até receber essas definições.

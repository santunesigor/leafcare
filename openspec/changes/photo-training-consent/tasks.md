# Tasks

## 1. Persistência offline e contrato remoto

- [x] 1.1 Adicionar entidades/DAO Room para consentimento global e autorização por foto com migração negada por padrão; verificar migração v3→v4 e testes para estados iniciais/revogação.
- [x] 1.2 Criar migrations Supabase com tabelas, bucket privado e RLS para consentimentos e fotos; permitir upload apenas após aceite legal atual, escolha geral e autorização individual, verificando no PostgreSQL isolamento, revogação e timestamps do servidor. Migration `20261019194328_enable_photo_training_consent_uploads` aplicada no Supabase.
- [x] 1.3 Implementar sincronização isolada por conta; consentimento geral e autorização por foto são opt-in, revogações são enviadas antes de qualquer upload pendente e falhas mantêm retry sem enviar bytes.
- [x] 1.4 Documentar contrato de retenção, isolamento, revogação offline e limitações de remoção de modelos já treinados; verificar consistência com migration e textos do app.

## 2. Interface e escolhas do usuário

- [x] 2.1 Exibir consentimento de treinamento opcional, separado dos documentos gerais e desmarcado por padrão; a ausência de CNPJ, endereço ou substituto não impede a escolha.
- [x] 2.2 Adicionar controle de consentimento no Perfil usando LeafCareTheme; explicar finalidade, revogação, retenção de 90 dias e manter a análise local disponível.
- [x] 2.3 Mostrar escolha individual no fluxo de captura/galeria somente com consentimento geral atual; nenhuma análise cria autorização sem ação específica da pessoa.
- [x] 2.4 Revisar acessibilidade e conteúdo informativo sobre consentimento opcional, retenção, revogação e modelos previamente treinados no APK debug; revisão estática e lint concluídos.
- [x] 2.5 Exibir no cadastro uma declaração única com links inline verdes, manter leitura e retorno em telas internas, informar público adulto e não misturar aceite geral com consentimento de treinamento; validar ambos os caminhos em teste Compose.

## 3. Elegibilidade e exportação do dataset

- [x] 3.1 Habilitar exportador de fotos autorizadas, ainda condicionado por consentimento atual, autorização individual, revisão humana e qualidade; verificar que revogações removem cópias da exportação.
- [x] 3.2 Validar decodificação, dimensão, arquivo vazio, duplicatas e remoção de EXIF/identificadores; verificar fixtures aprovadas e relatórios de rejeição.
- [x] 3.3 Documentar execução com credencial somente por variável de ambiente e destino privado ignorado pelo Git; verificar segredo ausente do app, logs e repositório.

## 4. Integração e entrega

- [x] 4.1 Atualizar documentação técnica da jornada e dos fluxos de dados; verificar referências ao comportamento implementado.
- [x] 4.2 Executar testes Android (289), PostgreSQL/PGlite, 6 testes Python do exportador e validações estritas dos specs/change e arquivo; todos passaram.
- [x] 4.3 Gerar APK debug 1.7.0 (versionCode 14) para teste manual.
- [x] 4.4 Versionar Termos e Política, mostrar data de publicação e registrar versões aceitas com timestamp gerado pelo servidor; validar RLS de recibos imutáveis.
- [x] 4.5 Definir retenção por categoria: histórico enquanto a conta estiver ativa, exclusão em até 30 dias, cópia de treino em até 90 dias, exportação local em até 30 dias e backups manuais do banco no Drive em até 30 dias.
- [x] 4.6 Instalar e testar a limpeza automática diária: Edge Function remove fotos vencidas no Supabase após 90 dias; timer systemd remove exportações locais após 30 dias. A limpeza não é uma condição administrativa para permitir consentimento opt-in.
- [x] 4.7 Publicar o e-mail de privacidade fornecido pela equipe nos documentos do app, com ação de cópia no design system LeafCare.
- [x] 4.8 Atualizar Termos/Política v1.3 com equipe acadêmica, representante, ausência de CNPJ/endereço institucional, contato, região/processamentos do Supabase e backup manual do banco no Drive por até 30 dias; registrar que essas condições não bloqueiam o opt-in. Revisar o alerta de senha vazada, registrando que o recurso exige plano Pro ou superior.
- [x] 4.9 Atualizar documentos para v1.3 e APK para 1.7.0 (versionCode 14); registrar as migrations de aceite legal e upload condicionado e validar o resultado.
- [x] 4.10 Implementar comunicação e novo aceite de versões materiais para contas existentes; nunca criar recibos retroativos sem ação do usuário. A verificação ocorre no login/retorno ao app, a confirmação é registrada por RPC imutável, falha de rede não inventa aceite, e a sincronização fica pausada até haver recibo atual. Migration `20261019190156_existing_user_legal_acceptance` aplicada no Supabase; testes Android, PostgreSQL/PGlite e OpenSpec passaram.
- [x] 4.11 Aplicar a migration `20261019194328_enable_photo_training_consent_uploads` no Supabase e verificar policies RLS/Storage, aceite v1.3, alertas Security Advisor, testes e APK debug final.

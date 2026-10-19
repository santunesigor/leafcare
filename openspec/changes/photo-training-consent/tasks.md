# Tasks

## 1. Persistência offline e contrato remoto

- [x] 1.1 Adicionar entidades/DAO Room para consentimento global e autorização por foto com migração negada por padrão; verificar migração v3→v4 e testes para estados iniciais/revogação.
- [x] 1.2 Criar migration Supabase com tabelas, bucket privado e políticas RLS para consentimentos e fotos; verificar SQL em testes locais e negar acesso cruzado entre contas.
- [x] 1.3 Implementar cliente isolado para decisões por conta e revogações; manter upload globalmente bloqueado enquanto faltar informação legal, verificando ordem, falha/retry e ausência de bytes.
- [x] 1.4 Documentar contrato de retenção, isolamento, revogação offline e limitações de remoção de modelos já treinados; verificar consistência com migration e textos do app.

## 2. Interface e escolhas do usuário

- [x] 2.1 Adicionar aviso de indisponibilidade até existirem controlador, contato de privacidade e prazos de retenção; verificar que nenhum aceite de treinamento pode ser registrado.
- [x] 2.2 Adicionar estado de treinamento desativado no Perfil usando LeafCareTheme; verificar que a tela explica a pendência e mantém a análise local disponível.
- [x] 2.3 Mostrar escolha individual desabilitada no fluxo de captura/galeria enquanto a coleta estiver bloqueada; verificar que nenhuma análise cria autorização local nem upload.
- [x] 2.4 Revisar acessibilidade e conteúdo informativo sobre bloqueio, revogação futura e modelos previamente treinados no APK debug; revisão estática e lint concluídos. QA visual das telas autenticadas permanece pendente até haver credenciais de teste e dispositivo disponível.
- [x] 2.5 Exibir no cadastro uma declaração única com links inline verdes, manter leitura e retorno em telas internas, informar público adulto e não misturar aceite geral com consentimento de treinamento; validar ambos os caminhos em teste Compose.

## 3. Elegibilidade e exportação do dataset

- [x] 3.1 Preparar exportador condicionado por bloqueio padrão; verificar que sem publicação legal ele encerra antes de consultar ou baixar dados.
- [x] 3.2 Validar decodificação, dimensão, arquivo vazio, duplicatas e remoção de EXIF/identificadores; verificar fixtures aprovadas e relatórios de rejeição.
- [x] 3.3 Documentar execução com credencial somente por variável de ambiente e destino privado ignorado pelo Git; verificar segredo ausente do app, logs e repositório.

## 4. Integração e entrega

- [x] 4.1 Atualizar documentação técnica da jornada e dos fluxos de dados; verificar referências ao comportamento implementado.
- [x] 4.2 Executar testes Android, testes Supabase locais, testes Python do exportador e validações OpenSpec estritas; registrar resultados reais.
- [x] 4.3 Gerar e disponibilizar APK debug para teste manual; usuário confirmou teste do APK em 19/10/2026.
- [x] 4.4 Versionar Termos e Política, mostrar data de publicação e registrar versões aceitas com timestamp gerado pelo servidor; validar RLS de recibos imutáveis.
- [x] 4.5 Definir retenção por categoria: histórico enquanto a conta estiver ativa, exclusão em até 30 dias, cópia de treino em até 90 dias, exportação em até 30 dias e backups conforme plano (máximo informado de 30 dias).
- [ ] 4.6 Instalar e testar a limpeza automática de cópias de treinamento e exportações antes de habilitar coleta.
- [ ] 4.7 Definir e publicar canal de privacidade monitorado; validar representação e endereço institucional do controlador.

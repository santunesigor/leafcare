## Context

A autenticação, os downloads e a administração usam o Supabase existente. A interface lê o histórico local do Room. O servidor já aceita atualizações de revisão com controle de versão.

## Goals / Non-Goals

Entregar os ajustes no APK debug da release 1.9.0, preservando autenticação, análise offline e auditoria. A limpeza remota ocorre na publicação.

## Decisions

- Oferecer login no estado de confirmação sem sessão e aproveitar apenas códigos ou tokens válidos para autenticar. Não consultar contas por e-mail para detectar duplicidade.
- Restaurar o histórico e suas fotos no mesmo trabalho de sincronização, antes de processar a fila de envio.
- Reusar o formulário de revisão e o visor com gestos na fila e na galeria; a edição pela galeria não avança a fila.
- Atualizar o texto explicativo sem alterar o aceite nem a política de retenção.

## Risks / Trade-offs

O retorno de confirmação sem credenciais de sessão exige login. O tempo de restauração depende da rede e do volume de fotos. O reset requer inventário e validação antes da execução.

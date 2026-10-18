# Design

## Context

Ver proposal.md. HistoryContent coloca Administração no FAB junto à câmera. AdminViewModel apaga o acesso em qualquer erro da política e pode executar consultas concorrentes no início. A RPC também exige texto em dúvida/rejeição. Convites usam inviteUserByEmail e não retornam link.

## Goals / Non-Goals

**Goals:** decisões igualmente visíveis, observação opcional e compartilhamento de um acesso real.

**Non-Goals:** publicar em produção sem autorização, alterar tabelas/dados, conceder papéis, modificar ML ou fluxo offline.

## Decisions

- Reusar os controles circulares do cabeçalho. Serializar consultas de acesso, tentar até três vezes em falhas transitórias e manter o papel apenas em memória; 401/403, papel comum e troca de conta continuam limpando o acesso.
- Organizar foto acima de um painel contínuo com previsão e grade 2×2 de decisões equivalentes. Confirmar salva diretamente; as demais abrem a avaliação já selecionada, com observação opcional e correção de classe quando aplicável. Remover o menu Outra avaliação.
- Reusar o formulário de e-mail, com Compartilhar convite e Enviar por e-mail. A nova operação invite_link usa auth.admin.generateLink(type=invite), com redirect de senha já existente, e retorna somente o action_link. O APK envia texto via ACTION_SEND/createChooser. Fluxo efêmero sem replay evita armazenar links no estado ou compartilhá-los depois de sair da tela.
- Registrar somente o evento e o identificador da conta na auditoria; nenhuma credencial ou link. Revalidar autorização antes de retornar o link. Manter geração no servidor conforme a [API oficial do Supabase](https://supabase.com/docs/reference/javascript/auth-admin-generatelink).
- Acrescentar migration forward com CREATE OR REPLACE da RPC retirando exclusivamente a exigência do motivo. Preservar validações de classe, versão, papel e tamanho da observação; não editar a migration já publicada.

## Risks / Trade-offs

- [Rede transitória no início] → tentativas limitadas e nova verificação ao retomar o app, sem bloquear inferência.
- [Janelas pequenas/fonte grande] → grade compacta e painel de edição rolável com ações fixas; capturas locais e APK para teste físico.
- [Link de acesso sensível] → resposta no-store, sem persistência/log de link; compartilhamento iniciado explicitamente pelo usuário e sem envio automático.
- [Apps compatíveis variam por aparelho] → usar seletor Android sem fixar destinos.
- [Backend antigo recusa observação vazia/nova operação] → informar a necessidade da publicação, preparando e validando tudo localmente antes de solicitar autorização.

## Migration Plan

Após autorização, aplicar somente a migration nova e publicar leafcare-admin no projeto conferido. A versão Android anterior continua compatível. Reversão do backend restaura a função e a validação anteriores; não há conversão de dados.

# Proposal

## Why

O atalho administrativo some em falhas temporárias de conexão e ocupa a área da câmera. A avaliação destaca excessivamente a confirmação, esconde as demais decisões e exige motivos desnecessários; o convite só pode ser enviado por e-mail.

## What Changes

- Mover Administração para o cabeçalho, ao lado do Perfil; repetir verificações transitórias e preservar a visibilidade já autorizada na sessão até negação explícita ou troca de conta.
- Oferecer compartilhamento de um convite real vinculado ao e-mail pelo seletor nativo do Android, mantendo envio direto por e-mail.
- Integrar foto, previsão e decisões em uma avaliação mais coesa, com Confirmar, Corrigir, Duvidosa e Rejeitar visíveis e dimensões equivalentes.
- Tornar a observação opcional para todas as avaliações, inclusive no servidor.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `superadmin`: posição e estabilidade do acesso, compartilhamento de convites e ações de avaliação sem motivo obrigatório.

## Impact

Kotlin/Compose, ViewModel administrativo, Edge Function e validação de revisão na RPC. A migration acrescentada altera somente essa regra da função, conforme o pedido explícito de remover o motivo obrigatório; não altera tabelas nem dados existentes. Publicação remota depende de autorização separada. Sem mudanças em inferência, modelo, dataset, sincronização ou disponibilidade offline.

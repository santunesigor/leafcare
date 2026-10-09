# Proposal

## Why

O usuário precisa administrar contas e revisar fotos dentro do Android, mantendo a previsão original e o funcionamento offline.

## What Changes

Área online no Perfil para superadmins: visão geral, usuários, triagem/revisão e auditoria. Gestão de convite, recuperação, permissões, exclusão retomável e pausa de envios. Pausa preserva login/dados locais; liberação permite apenas resultados novos. Fotos rejeitadas permanecem privadas.

## Capabilities

### New Capabilities

- `superadmin`: administração autenticada e revisão humana independente da inferência.

### Modified Capabilities

- `account-data-sync`: política remota de pausa e corte de envios, respeitada por banco/Storage e app.

## Impact

Migrations e Edge Function Supabase, UI/API Android, sincronização, convite, testes e documentação. Sem mudanças de modelo/dataset. Branch depende do PR #7. Publicação em produção e habilitação inicial são etapas separadas da implementação validada.

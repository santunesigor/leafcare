## Context

O treinamento usava duas escolhas: um consentimento geral (versão 1.1), dado em Perfil, e uma autorização por foto, dada no modal da câmera. O servidor só aceita cópias de treinamento com o consentimento ativo, a autorização da foto e o recibo dos documentos atuais.

## Goals / Non-Goals

**Goals:**
- Um único aceite (documentos v1.4) registra o recibo e o consentimento no servidor, com timestamp do servidor.
- O app bloqueia o uso sem aceite e continua funcionando offline para quem já aceitou no aparelho.
- A revogação sempre chega ao servidor e bloqueia o app até um novo aceite.

**Non-Goals:**
- Não altera modelo, dataset, retenção (90/30 dias) nem o fluxo de revisão humana.
- Não reenvia fotos analisadas antes do aceite v1.4.

## Decisions

- **O consentimento é gravado pelo servidor no aceite.** `accept_current_legal_documents('1.4','1.4')` e `handle_new_user` (cadastro com v1.4) fazem upsert de `training_consents (granted, '1.2')`. Assim, o recibo e o consentimento não podem divergir. Alternativa descartada: o cliente gravar o consentimento depois do aceite, o que abriria uma janela de inconsistência.
- **A autorização por foto continua existindo, mas é automática.** O app cria `training_photo_authorizations` para cada análise de conta com consentimento ativo. A tabela e a política de storage existentes continuam vinculando cada cópia à sua análise, sem nova tabela.
- **Revogação aceita com qualquer versão.** As políticas permitem `granted=false` e `authorized=false` independentemente da versão, para que um aparelho com estado antigo nunca fique impedido de revogar.
- **Gate:** o estado atual exige o recibo v1.4 e o consentimento 1.2 no servidor. Uma revogação local pendente prevalece até ser sincronizada. Em falha de rede, o app usa o consentimento local: se ele existe, o app funciona; se não existe, a tela de aceite é exibida.

## Risks / Trade-offs

- [Consentimento como condição de serviço] → Conforme a LGPD, art. 9º, §3º, o texto destaca a condição no cadastro, na tela de aceite e nos documentos. A revisão jurídica final continua com o responsável pelo projeto.
- [Contas existentes bloqueadas até aceitar] → O aceite exige conexão, e a tela explica isso; quem já aceitou continua funcionando offline.

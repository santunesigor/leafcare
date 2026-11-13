## Why

O modal "Analisar fotografia" pedia uma autorização por foto que só ficava disponível depois de ativar um consentimento separado no Perfil. Na prática, a opção parecia quebrada e travava o fluxo de captura. O proprietário decidiu que o uso das fotos para treinamento passa a ser condição para usar o app: quem aceita os documentos autoriza todas as fotos analisadas, e quem não aceita não usa o app.

## What Changes

- **BREAKING**: Termos de Uso e Política de Privacidade v1.4. O aceite inclui o consentimento de treinamento (versão 1.2), destacado como condição de uso (LGPD, art. 9º, §3º).
- **BREAKING**: sem aceite das versões atuais, o app não pode ser usado. A opção "Continuar offline sem aceitar" é removida. Offline, só funciona a conta que já aceitou neste aparelho.
- O modal de autorização por foto é removido: capturar ou escolher uma foto inicia a análise, e cada análise de conta com consentimento ativo é autorizada automaticamente.
- A revogação continua disponível em Perfil → Privacidade e fotos; depois dela, o app só volta a funcionar com um novo aceite.
- Supabase: migração `20261111120000_legal_documents_v1_4_training_by_terms` (aceite v1.4 também grava o consentimento; políticas passam a exigir v1.4/v1.2; revogações são aceitas com qualquer versão).

## Capabilities

### New Capabilities

### Modified Capabilities
- `photo-training-consent`: o consentimento deixa de ser opcional e separado, passando a fazer parte do aceite dos documentos, sem autorização individual por foto.

## Impact

- Android: `LegalAcceptanceRepository`, `AuthViewModel`, telas de aceite, cadastro, documentos legais, Privacidade e fotos, câmera e `LeafCareViewModel`.
- Supabase: funções `accept_current_legal_documents` e `handle_new_user`, e políticas de `training_consents`, `training_photo_authorizations` e `storage.objects` (bucket `training-photos`).
- Contas existentes precisam aceitar a v1.4 ao abrir o app.

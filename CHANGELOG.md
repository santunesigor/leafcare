# Changelog — LeafCare

## [Unreleased]

## [1.0.0] - 2026-09-29

### MVP

- Classificação offline de folhas de fumo com MobileNetV3Small, resultados Top-3 e limiar para resultado inconclusivo.
- Captura pela câmera e seleção pela galeria, com histórico local em Room.
- Conta Supabase Auth com confirmação por e-mail, recuperação de senha por deep link e alteração de senha autenticada no Perfil.
- Sincronização e restauração do histórico entre sessões, com fotos privadas e isolamento por usuário.
- QA manual dos fluxos Auth validado pelo usuário em dispositivo em 2026-09-29; demais evidências e limitações estão em `docs/FINAL_QA_CHECKLIST.md`.

### Limitações conhecidas

- O SMTP padrão do Supabase é destinado a desenvolvimento e testes; domínio e SMTP próprios ficam para pós-MVP/produção.
- O teste em segundo aparelho não foi executado por indisponibilidade de outro dispositivo.
- Capturas abaixo do limiar retornam “Inconclusivo”; não há aviso específico para pouca luz.
- O APK gerado por este projeto é debug e serve para demonstração/teste, não é um pacote de produção assinado.
- LeafCare oferece triagem visual, não diagnóstico agronômico definitivo.

# Changelog — LeafCare

## [Unreleased]

- APK 1.1.1 (versionCode 5): botão/gesto de voltar do Android acompanha a seta nas telas de conta; confirmação de e-mail volta ao cadastro e recuperação mantém a confirmação antes de sair.
- Filtro ativo do histórico apresentado em selo removível; confiança mínima exibida com duas casas decimais e explicação de resultado inconclusivo, preservando o limiar calibrado do ensemble.
- APK 1.1.0 (versionCode 4): ensemble de dois MobileNetV3Small e um MobileNetV3Large como classificador padrão, em um único TFLite float32 com média e calibração embutidas.
- Novo treinamento reproduzível em `train_ensemble.py`, preservando dataset/split e avaliando o TFLite final; métricas e paridade em `benchmark_artifacts/ensemble/`.
- Benchmark ampliado com backbones congelados, ajustes supervisionados e exportações experimentais. APK debug disponibilizado pela CI em branches de experimentos.
- Inferência, Top-3, resultado inconclusivo e histórico continuam locais/offline. Medição de até três segundos no Galaxy A06 pendente de aparelho conectado.

## [1.0.0] - 2026-09-29

### MVP

- Classificação offline de folhas de fumo com MobileNetV3Small, resultados Top-3 e limiar para resultado inconclusivo.
- Captura pela câmera e seleção pela galeria, com histórico local em Room.
- Conta Supabase Auth com confirmação por e-mail, recuperação de senha por deep link e alteração de senha autenticada no Perfil.
- Sincronização e restauração do histórico entre sessões, com fotos privadas e isolamento por usuário.
- QA manual dos fluxos Auth validado pelo usuário em dispositivo em 2026-09-29; demais evidências e limitações estão em `docs/TESTING.md`.

### Limitações conhecidas

- O SMTP padrão do Supabase é destinado a desenvolvimento e testes; domínio e SMTP próprios ficam para pós-MVP/produção.
- O teste em segundo aparelho não foi executado por indisponibilidade de outro dispositivo.
- Capturas abaixo do limiar retornam “Inconclusivo”; não há aviso específico para pouca luz.
- O APK gerado por este projeto é debug e serve para demonstração/teste, não é um pacote de produção assinado.
- LeafCare oferece triagem visual, não diagnóstico agronômico definitivo.

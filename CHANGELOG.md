# Changelog — LeafCare

## [Unreleased]

## [1.2.0] - 2026-10-08

- [Pré-release com APK/bundle](https://github.com/santunesigor/leafcare/releases/tag/v1.2.0) publicada a partir de `cca5514`; [PR #6](https://github.com/santunesigor/leafcare/pull/6) aberto para merge.

- MobileNetV4 Small destilado como modelo integrado: TFLite float32 de 9,70 MiB, professor DINOv2-B somente no treino; cerca de 44% menor que o ensemble anterior.
- Schema 2, preprocessing bicúbico/recorte central equivalente entre Python e Kotlin, temperatura e limiar próprios do aluno. Análises anteriores preservam hash e limiar registrados.
- Teste conhecido: Top-1 85,44%, Macro-F1 0,8083 e Top-3 97,09%; acerto entre aceitas 90% com cobertura 87,38%. Sem nova seleção pelo teste e sem validação de campo.
- APK debug 1.2.0 (versionCode 7); receita, bundle atual e relatórios de integração em pastas próprias. Ensemble anterior preservado na release v1.1.2.

- Experimentos isolados de MobileNetV3Small, MobileNetV4 Small, FastViT-T8 e EfficientViT-M2/M3, com cinco controles e cinco treinos com destilação de DINOv2-B. Dez exportações TFLite aprovadas; receitas, resultados e relatório em diretórios próprios, preservando o ensemble durante os experimentos.
- [Relatório de destilação](docs/benchmarks/BENCHMARK_DESTILACAO.md): 38 testes Python passaram; classificação em aparelho dos candidatos continua pendente.

## [1.1.2] - 2026-10-08

- Release com APK debug e bundle do ensemble (TFLite, classes e metadados), acompanhados dos hashes SHA256.
- APK 1.1.2 (versionCode 6): dias do histórico podem ser recolhidos/expandidos com contador, transição de cards e seta animada; removido o espaço excedente entre filtro e data.
- Build debug e download da CI disponibilizam `leafcare-<versão>-<commit>.apk` automaticamente.
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

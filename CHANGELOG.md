# Changelog — LeafCare

## [1.8.3] - 2026-11-11

- APK 1.8.3 (versionCode 21): revisão completa dos fluxos de conta, produtor, sincronização e admin.
- Privacidade: revogar o consentimento apaga de fato as cópias de treinamento no servidor (antes a exclusão não removia nada); excluir uma análise remove sua cópia; excluir uma conta pelo painel remove também as cópias de treinamento; fotos inconclusivas não são usadas para treinamento; uma revogação pendente é enviada antes de um novo aceite e ao sair da conta.
- Sincronização: autorizações que nunca poderiam ser enviadas não travam mais a fila em novas tentativas; revogações e exclusões vão antes da política de envio; atualizações recusadas pelo servidor ficam pendentes em vez de sumirem; a restauração não mistura contas.
- Conta: link de convite/recuperação não trava mais na tela de aceite; a troca de senha não mostra mensagens antigas; e-mail recusado pelo servidor mostra "E-mail inválido"; o histórico da conta anterior não aparece após trocar de conta.
- Câmera e resultado: resolução limitada para não estourar o limite de 16 MP; permissão e câmera escolhida sobrevivem à rotação; sem mensagens técnicas em inglês; o resultado volta ao histórico se a análise for apagada; limpeza de arquivos órfãos.
- Sair da conta avisa quando há análises ainda não enviadas, que seriam apagadas se outra conta entrar no aparelho; o diálogo de exclusão diz que a análise sai também da conta.
- Visual: cores de situação de revisão consistentes no admin, botões e campos no padrão do app, áreas de toque de 48dp, ícone de perfil, textos corrigidos.
- Migration `20261111150000_training_photo_rules` e função `leafcare-admin` publicadas em produção.

## [1.8.2] - 2026-11-11

- APK 1.8.2 (versionCode 20): a tela de aceite deixa de dizer "Documentos atualizados" para contas que nunca aceitaram os termos (contas convidadas ou criadas pelo servidor); o texto passa a servir para contas novas e antigas.

## [1.8.1] - 2026-11-11

- APK 1.8.1 (versionCode 19): a tela de aceite explica quando o servidor ainda não foi atualizado para os documentos v1.4, em vez de pedir para conferir a conexão.
- Card do uso das fotos no treinamento mais curto e legível: ícone, frase principal e três pontos.

## [1.8.0] - 2026-11-11

- APK 1.8.0 (versionCode 18): Termos de Uso e Política de Privacidade v1.4. O uso das fotos analisadas para treinamento passa a ser condição de uso, registrada no servidor junto com o aceite; sem aceite, o app não pode ser usado (offline, só para quem já aceitou no aparelho).
- O modal "Analisar fotografia" foi removido: capturar ou escolher uma foto inicia a análise, e a foto é autorizada automaticamente.
- Privacidade e fotos mostra o estado da autorização e permite revogar com confirmação; depois disso, o app pede um novo aceite.
- Migration `20261111120000_legal_documents_v1_4_training_by_terms`; revogações são aceitas com qualquer versão de consentimento.

## [1.7.3] - 2026-11-11

- APK 1.7.3 (versionCode 17): fotos do histórico aparecem assim que o download termina, sem precisar abrir a câmera (#36).
- Tema completo com as cores do app: diálogos, calendário, menus e chips não usam mais o lilás padrão do Material 3; diálogos seguem o padrão da ajuda (#37).
- Modal de filtrar fotos do admin com chips de situação, seções, erro de data visível e rodapé Limpar/Aplicar (#38).
- Ajustes visuais em Privacidade e fotos, confirmações do admin e no resultado (#39).

## [1.7.2] - 2026-11-11

- APK 1.7.2 (versionCode 16): correções de estabilidade sem alteração de modelo, dataset ou schema.
- Login não fica mais carregando indefinidamente (#18); abertura offline com token expirado não trava (#19); voltar ao app não reinicia a navegação nem perde a foto da galeria (#20).
- Recuperação de senha resiste a rotação e ao encerramento do processo (#21, #24); sair sem internet desconecta localmente (#22); erro ao aceitar os termos permanece visível (#23).
- Sincronização: worker aguarda a sessão (#25), exclusões durante upload não se perdem (#26), fila não fica bloqueada (#31) e fotos de treinamento seguem o histórico (#30).
- Privacidade: revogações apagam todas as cópias possíveis (#27), não são sobrescritas por consentimento antigo (#28) e sincronizam mesmo aguardando novo aceite (#29).
- Admin: rotação mantém o painel (#32) e datas digitadas no filtro funcionam (#33). Captura sem espaço e falta de memória mostram erro em vez de fechar o app (#34).

## [1.7.1] - 2026-10-19

- APK 1.7.1 (versionCode 15): contato de privacidade apresentado como texto padronizado no final dos Termos de Uso e da Política de Privacidade. O usuário testou os fluxos no aparelho e confirmou que estão funcionando.

## [1.7.0] - 2026-10-19

- APK 1.7.0 (versionCode 14): habilita contribuição opcional de fotos para treinamento com aceite legal atual, consentimento geral e autorização individual por foto; recusar ou revogar mantém classificação e histórico.
- Termos e Política v1.3 descrevem a equipe acadêmica, contato, retenção e backups manuais no Google Drive. Cópias de treinamento expiram em até 90 dias e exportações locais em até 30 dias.
- Migration `20261019194328_enable_photo_training_consent_uploads` aplicada; upload e leitura ficam restritos por RLS e autorização vigente.

## [Versões anteriores]

- APK 1.5.0 (versionCode 12): agenda diária de expiração para fotos de treinamento no Supabase (90 dias) e limpeza local de exportações (30 dias). Naquela versão, a coleta ainda aguardava decisões operacionais que foram concluídas na 1.7.0.
- APK 1.4.1 (versionCode 11): documentos legais v1.2 publicam o contato de privacidade no app. A coleta de treinamento ainda não estava habilitada nessa versão.
- APK 1.2.1 (versionCode 8): envia ao bucket apenas fotos conclusivas com confiança >= limiar da análise, incluindo proteção para retries antigos. Histórico inconclusivo continua sincronizado; sua foto permanece local. Restore sem foto remota não tenta download inexistente.
- Consulta de arquivo/classe/confiança documentada em `docs/SUPABASE.md`, usando o UUID já associado a cada foto.

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

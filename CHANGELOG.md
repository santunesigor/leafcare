# Changelog — LeafCare

## [1.8.0] - em preparação

Release reunindo tudo desde a 1.7.1. Durante a preparação, a versão do app fica em 1.8.0 e só o `versionCode` sobe a cada APK de teste (atual: 27).

### Termos v1.4 e treinamento
- Termos de Uso e Política de Privacidade v1.4: o uso das fotos analisadas para treinamento passa a ser condição de uso, registrada no servidor junto com o aceite; sem aceite, o app não pode ser usado (offline, só para quem já aceitou no aparelho). A tela de aceite serve para contas novas e antigas e explica quando o servidor ainda não foi atualizado.
- Card do uso das fotos no treinamento curto e legível; o modal "Analisar fotografia" foi removido: capturar ou escolher uma foto inicia a análise.
- Privacidade e fotos mostra o estado da autorização e permite revogar com confirmação; depois disso, o app pede um novo aceite.
- Revogar apaga de fato as cópias de treinamento no servidor; excluir uma análise remove sua cópia; excluir uma conta pelo painel remove também as cópias; fotos inconclusivas não são usadas para treinamento; revogações pendentes são enviadas antes de um novo aceite e ao sair da conta.
- Migrations `20261111120000_legal_documents_v1_4_training_by_terms` e `20261111150000_training_photo_rules` e função `leafcare-admin` publicadas em produção.

### Conjunto de treino acumulado e retreino (Termos v1.5)
- Termos de Uso e Política de Privacidade v1.5 (12/11/2026): as cópias de treino formam um conjunto acumulado e ficam por tempo indeterminado, até revogação, exclusão da análise ou exclusão da conta. Cópias de fotos analisadas sob a v1.4 continuam vencendo 90 dias após o envio. Contas existentes aceitam a v1.5 para continuar.
- Supabase (migration `20261112180000_legal_documents_v1_5_permanent_training`): aceite v1.5; envio com recibo 1.4 ou 1.5; validade por cópia (`expires_at` nulo para cópias permanentes); `training_pool()` sem corte de 90 dias; `training_photos_to_delete()` lista cópias vencidas, de análises excluídas ou inconclusivas, revogadas ou de contas em exclusão. A limpeza diária `cleanup-training-photos` passa a apagar essas cópias, inclusive as de revogações feitas em outro aparelho. Migration e função publicadas em produção em 12/11/2026.
- `training_pool()` (migration `20261112120000_training_pool`, aplicada em produção) define em um só lugar quais cópias estão prontas: análise ativa e conclusiva após o aceite, consentimento vigente, conta fora de exclusão e revisão confirmada ou corrigida; o rótulo é a classe revisada.
- `machine-learning/retrain.py`, ponto de entrada único: `setup`, `status`, `sync`, `run` e `promote`. O conjunto parte do TLA (split histórico) e soma todas as fotos prontas, espelhadas em `data/field/` sem identificadores nem EXIF, com pessoas fixas no mesmo split entre ciclos. Cada ciclo treina o professor DINOv2-B, destila o aluno com a receita atual, converte para TFLite com paridade, compara com o modelo em uso e só muda o app com `promote`.
- Admin: bloco "Treinamento do modelo" no Resumo, selo "Treino" (com "· N dias" só para cópias v1.4) e filtro "Só cópias de treino"; a fila mostra primeiro as cópias pendentes que vencem antes.
- Repositório enxuto: saem os experimentos, benchmarks e o pipeline Keras/ensemble (preservados na tag `v1.7.1`), o exportador de 30 dias, o timer systemd, o catálogo antigo e código Android sem uso. A CI de Python usa LiteRT em vez de TensorFlow.
- Conjunto completo no Supabase: o TLA (696 imagens, uma pasta por doença) fica no bucket privado `tla-dataset` (migration `20261113120000_tla_dataset_bucket`, aplicada e preenchida em produção). `retrain.py sync` reconstrói `data/raw/` em qualquer computador, conferindo cada imagem com o manifesto, e a cópia local das fotos do app passa a ficar numa pasta por doença.
- O replay tolera só imagens a até 1e-4 do limiar mudando de aceita para inconclusiva (achado no primeiro ciclo real); teste de login por convite deixou de ser intermitente.
- Plano único em [Retreinar o modelo](docs/RETREINAMENTO.md).

### Conta e estabilidade
- Login não fica carregando indefinidamente (#18); abertura offline com token expirado não trava (#19); voltar ao app não reinicia a navegação nem perde a foto da galeria (#20).
- Recuperação de senha resiste a rotação e ao encerramento do processo (#21, #24); sair sem internet desconecta localmente (#22); erro ao aceitar os termos permanece visível (#23); link de convite/recuperação não trava na tela de aceite.
- Troca de senha sem mensagens antigas; e-mail recusado pelo servidor mostra "E-mail inválido"; o histórico da conta anterior não aparece após trocar de conta; sair avisa quando há análises ainda não enviadas.

### Sincronização
- Worker aguarda a sessão (#25), exclusões durante upload não se perdem (#26), fila não fica bloqueada (#31) e fotos de treinamento seguem o histórico (#30).
- Revogações não são sobrescritas por consentimento antigo (#28) e sincronizam mesmo aguardando novo aceite (#29); revogações e exclusões vão antes da política de envio; atualizações recusadas ficam pendentes; a restauração não mistura contas.
- Fotos do histórico aparecem assim que o download termina (#36).
- Resultados inconclusivos voltam normalmente ao entrar de novo na conta (reinstalação ou troca de conta), mas a foto deles fica só no aparelho onde a análise foi feita, como diz a Política de Privacidade. Antes aparecia um quadro vazio que parecia resultado perdido; agora o histórico mostra um ícone e o resultado explica onde a foto ficou.

### Câmera e resultado
- O botão de luz da câmera vira uma lanterna de verdade: acende na hora e fica acesa enquanto você enquadra e fotografa, e volta acesa ao retornar ao app. Antes ele só armava o flash para o momento da foto, sem nenhum sinal visível, e parecia não funcionar. O botão mostra claramente se a lanterna está ligada.
- Resolução limitada para não estourar 16 MP; permissão e câmera escolhida sobrevivem à rotação; captura sem espaço e falta de memória mostram erro em vez de fechar o app (#34); sem mensagens técnicas em inglês; o resultado volta ao histórico se a análise for apagada; limpeza de arquivos órfãos.

### Visual
- Tema completo com as cores do app, sem o lilás padrão do Material 3 (#37); áreas de toque de 48dp, ícone de perfil, textos corrigidos.
- Modais compactos: cartão menor, título de 18sp, botões lado a lado (Cancelar à esquerda, ação principal à direita) que empilham só quando o texto não cabe; "Analisando no aparelho" como aviso compacto; ajuda da câmera no mesmo padrão; foto ampliada fecha pelo botão no canto.

### Admin
- Telas refeitas para caber sem rolagem desnecessária: cabeçalho único de 56dp (voltar, título, papel e atualizar) em todas as telas do admin e abas segmentadas em uma linha, no lugar do bloco "LEAFCARE / Administração" e dos quatro cartões de ícones.
- Resumo sem repetir a triagem: faixa de pendentes com "Avaliar" (abre a fila direto), totais em uma linha (recebidas, revisadas, usuários e pausados), barra de resultados com legenda e modelos de origem.
- Triagem com um único cartão de progresso e "Avaliar pendentes", situações e classes em listas agrupadas; Usuários e Atividades em listas agrupadas, sem títulos repetindo a aba, com "Convidar" ao lado do título da lista; paginação só aparece quando há outra página.
- Painéis inferiores ocupam só a altura do conteúdo; confirmações de conta abrem em diálogo; filtro de fotos com chips de situação e rodapé Limpar/Aplicar (#38); rotação mantém o painel (#32) e datas digitadas no filtro funcionam (#33); cores de situação consistentes.

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
- Relatório de destilação (`docs/benchmarks/BENCHMARK_DESTILACAO.md`, até a tag `v1.7.1`): 38 testes Python passaram; classificação em aparelho dos candidatos continua pendente.

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

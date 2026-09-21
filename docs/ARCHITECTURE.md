# Arquitetura e decisões

## Aplicativo

Kotlin/Jetpack Compose com MVVM e injeção manual de dependências no `Application`:

- `ui/`: telas e ViewModel. A interface observa StateFlow e o banco, sem executar
  inferência na thread principal. Navigation Compose restaura a navegação.
- `data/`: Repository, DAO e Room. O Repository copia a imagem para o armazenamento
  privado, executa a inferência e grava o registro. Um Mutex serializa análise/exclusão.
- `ml/`: decodificação, correção EXIF, contrato de pixels, Interpreter e política top 3.
- `assets/`: contrato de modelo, classes, descrições locais, referências e
  `leafcare.tflite` treinado, incluído nesta V3.

Cada registro conserva foto, data, identificador da classe, nome, nome científico,
confiança, top 3, limiar usado, tempo de inferência e hash do modelo. A foto é um
arquivo privado; o Room guarda apenas o nome relativo, sem caminho absoluto.

As análises são salvas automaticamente antes de abrir o resultado, evitando um
botão de salvar duplicado. Ao excluir, pede-se confirmação, remove-se a linha e a
foto. O app não adiciona resultados de exemplo. O banco não usa destruição automática
de versões; alterações de schema devem incluir migrações.

## Offline e privacidade

O manifesto não solicita INTERNET. Não há login, backend, telemetria, Firebase,
Supabase ou sincronização. A câmera só pede CAMERA; a galeria usa o seletor de
documentos do sistema, sem acesso geral ao armazenamento. Escolha arquivos já
disponíveis no aparelho para trabalhar offline.

O backup automático está desativado. Desinstalar/limpar dados remove o histórico.
Não há exportação/backup do histórico nesta versão. Uma interrupção abrupta do
processo durante importação pode deixar um arquivo órfão privado; isso não cria
uma análise falsa. O app não mantém uma tarefa de classificação após encerramento
forçado do processo; é necessário repetir a captura.

## Comportamento de proteção quando o modelo estiver ausente

O debug abre e permite testar câmera, galeria, ajuda, filtros e estados vazios.
Ao tentar analisar, informa que falta um modelo treinado e não salva resultados.
A tarefa `verifyModelAssets`, obrigatória para build release, bloqueia a geração
de uma versão de distribuição sem modelo e hashes consistentes. Após exportar os
quatro assets válidos, não é necessário mudar o código Kotlin para classificar.

## Treinamento

- MobileNetV3Small com pesos ImageNet, cabeça softmax nova e dropout 0,25.
- Primeiro, backbone congelado; depois, últimas 30 camadas, mantendo BatchNorm em
  modo de inferência e sem atualização de estatísticas.
- Adam com 1e−3/1e−5, early stopping, checkpoints por fase e ReduceLROnPlateau.
- Pesos inversamente proporcionais às frequências de **treino** se razão entre
  maior/menor classe ≥ 1,5.
- A melhor fase é escolhida pela perda de validação; o teste é reservado para
  avaliação final. Histórico e gráficos incluem as duas fases executadas.
- Exportação float32 com operadores nativos, priorizando equivalência funcional.
  Quantização e aceleração por GPU/NPU ficam para uma versão futura validada.

Instalação de dependências e obtenção inicial dos pesos ImageNet requerem internet
no computador. O treinamento posterior pode usar os pesos em cache. A inferência
instalada no telefone e `predict.py` com os artefatos locais não precisam de rede.

## Escolhas de compatibilidade

Android 8.0/API 26 ou superior, compile/target SDK 35, JDK 17, AGP 8.7.3, Gradle 8.9,
Kotlin 2.0.21 e LiteRT 1.4.0 com API Interpreter. As versões são fixadas para esta
base reproduzível; não representam necessariamente os releases mais novos.
Publicação na Play Store exige revisar requisitos vigentes, assinatura e políticas,
que são diferentes da publicação do código no GitHub solicitada aqui.

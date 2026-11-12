## Context

Desde a v1.4, usar o app exige aceitar que as fotos analisadas sejam copiadas para o bucket privado `training-photos`. A revisão humana no painel admin grava `photo_reviews`, e `training_pool()` decide quais cópias estão prontas. Até agora as cópias expiravam em 90 dias, e o ciclo de retreino era formado por uma exportação de 30 dias mais quatro comandos que importavam código de `experiments/`. O checkpoint do professor DINOv2-B usado na destilação do modelo atual não existe mais neste computador.

## Goals / Non-Goals

**Goals:**
- Um conjunto de treino que cresce a cada revisão, partindo do TLA.
- Um único comando para sincronizar e treinar um candidato comparável ao modelo atual.
- Revogação e exclusões continuam valendo, inclusive na cópia local.
- `machine-learning/` contém só o que o app e o retreino usam.

**Non-Goals:**
- Mudar classes, contrato TFLite, receita do aluno ou o modelo do app.
- Treinar ou promover automaticamente.
- Guardar fotos analisadas sob a v1.4 além de 90 dias.

## Decisions

- **Retenção por cópia, no banco.** `expires_at` é nulo quando a análise foi feita depois do primeiro aceite 1.5/1.5 da conta. Caso contrário, vale o envio + 90 dias. Assim, a mesma regra serve ao painel, ao pipeline e à limpeza. A Edge Function pergunta ao banco quais cópias venceram, em vez de calcular a idade pelo Storage.
- **Aceite 1.4 ou 1.5 para enviar.** As políticas de upload aceitam os dois recibos, para que um APK antigo não quebre o envio. A cópia herda o prazo do recibo que vale para ela. O aceite novo só grava 1.5.
- **Espelho local com pseudônimos estáveis.** A sincronização recodifica as imagens, sem EXIF, e identifica foto e pessoa por HMAC com um segredo local (`data/field/.secret`, modo 600, fora do Git). Como o pseudônimo da pessoa é estável, cada pessoa fica sempre no mesmo split entre ciclos e o `field_test` continua independente. Fotos que deixam o estado `ready` são apagadas do espelho na sincronização seguinte.
- **TLA como base fixa.** O split histórico (489/104/103) é mantido para comparar com todos os modelos anteriores. Fotos de campo que repetem uma imagem do TLA são descartadas.
- **Professor treinado no ciclo.** Sem o checkpoint original, o ciclo faz o fine-tune do DINOv2-B nos splits de treino e validação, com a receita registrada, e guarda o resultado em cache por hash do conjunto. `--teacher` reutiliza um checkpoint existente.
- **Promoção inalterada.** Paridade TFLite (mesmo Top-1, erro ≤ 1e-4), replay com o pré-processamento do app e recomendação automática pelos critérios atuais. A instalação só acontece com `promote`.
- **Remoção de histórico.** Evidências de experimentos encerrados continuam no histórico do Git (tags anteriores). O relatório do modelo em uso fica em `docs/model-reports/mobilenetv4-distilled/`.

## Risks / Trade-offs

- **Retenção indeterminada aumenta a responsabilidade sobre os dados.** Mitigação: bucket privado, revogação e exclusões efetivas, espelho local pseudonimizado e de acesso restrito.
- **Armazenamento do Supabase cresce sem limite de tempo.** É preciso acompanhar o uso do bucket no plano atual.
- **Professor retreinado não é idêntico ao original.** As métricas do candidato são comparadas com as do modelo atual antes de qualquer promoção.
- **`field_test` pequeno no começo.** A recomendação automática é apenas apoio; quem decide é a equipe.

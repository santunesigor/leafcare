# Retreinar o modelo com as fotos do app

Este guia explica como usar as fotos que as pessoas analisam no LeafCare para treinar uma nova versão do modelo, do banco de dados até o APK. Ele cobre o modelo que definimos para o app: **MobileNetV4 Small destilado de um professor DINOv2-B**, a mesma receita do modelo atual ([Machine Learning](MACHINE_LEARNING.md)).

Tudo roda localmente, em `machine-learning/`. Nenhuma foto vai para um serviço externo de treino.

## Visão geral do ciclo

```text
App (análise conclusiva)
  └─ cópia no bucket privado training-photos ............ apagada 90 dias após o envio
       └─ revisão humana no admin (confirmar/corrigir) ... só confirmadas/corrigidas servem
            └─ training_pool() = 'ready' (banco)
                 └─ 1. exportar → data/authorized_training/ ... apagada em 30 dias
                      └─ 2. montar o ciclo (TLA + campo) → runs/<ciclo>/dataset.json
                           └─ 3. treinar o candidato (receita definida)
                                └─ 4. converter para TFLite + paridade
                                     └─ 5. replay com o pré-processamento do app + comparação
                                          └─ 6. decisão da equipe → promover → APK
```

Cada ciclo é independente: só usa as cópias que existem naquele momento (até 90 dias de fotos). Os pesos treinados ficam; as fotos não. Isso é o que os Termos v1.4 permitem.

## O que os Termos v1.4 exigem e onde isso é garantido

| Regra (Termos/Política v1.4, seção 7) | Onde é aplicada |
| --- | --- |
| Só fotos analisadas **depois** do aceite da v1.4 | `training_pool()`: `analyses.created_at` ≥ primeiro aceite 1.4/1.4 da conta |
| Consentimento (1.2) e autorização vigentes; revogação tira a foto | `training_pool()` exige `granted`/`authorized` na versão 1.2; a revogação também apaga a cópia |
| Só análises ativas e conclusivas | `training_pool()` exclui `deleted_at` e `inconclusive`; o servidor já recusa o upload dessas |
| Revisão humana antes de entrar em um conjunto | Só o estado `ready` (revisão `confirmed`/`corrected` com classe) é exportado; o rótulo é a classe revisada, nunca a prevista |
| Validação de qualidade | Exportador: decodificação igual à do app, mínimo de 128 px, até 16 MP, sem duplicatas |
| Cópia apagada em até 90 dias | Função `cleanup-training-photos` (cron diário 03:12 UTC); `training_pool()` ignora cópias com mais de 90 dias |
| Exportação sem identificadores da conta e sem EXIF | Exportador: imagem recodificada, nomes por hash, sem e-mail/ID de conta ou análise; grupos por pessoa com chave aleatória descartada a cada exportação |
| Exportação local apagada em até 30 dias | `export_authorized_training.py --cleanup-only` (timer systemd diário); montar o ciclo e treinar recusam exportação vencida |
| Conta em exclusão sai do conjunto | `training_pool()` marca como `ineligible` quando `deletion_pending` |
| Pesos treinados não são desfeitos | Documentado nos Termos; a promoção registra a origem de cada modelo |

`training_pool()` só pode ser lida pelo pipeline com a chave de serviço e pelo painel admin. Os estados são:

- `ready`: entra no treino;
- `awaiting_review`: falta revisar;
- `excluded`: revisada como duvidosa ou rejeitada;
- `ineligible`: alguma regra acima não foi cumprida.

## Preparação (uma vez por computador)

### 1. Ambientes Python

Use Python 3.12 e [`uv`](https://docs.astral.sh/uv/). Dentro de `machine-learning/`:

```bash
# Treino (PyTorch, timm, scikit-learn). Com GPU NVIDIA, troque cpu por auto.
uv venv --python 3.12 .cache/retrain-venv
uv pip install --python .cache/retrain-venv/bin/python --torch-backend cpu -r experiments/distillation/requirements.txt

# Conversão TFLite e replay (versões fixas do LiteRT)
uv venv --python 3.12 .cache/retrain-export-venv
uv pip install --python .cache/retrain-export-venv/bin/python --torch-backend cpu -r experiments/distillation/requirements-export.txt
```

`.cache/` e `runs/` são ignorados pelo Git. A primeira execução baixa os pesos ImageNet do MobileNetV4 Small na revisão fixada.

### 2. Dataset TLA em `data/raw/`

As fotos do TLA não ficam no Git. Baixe os arquivos públicos do artigo ([doi:10.3389/fpls.2024.1333236](https://doi.org/10.3389/fpls.2024.1333236)), o ZIP do TLA e o do TTDD, e importe:

```bash
.cache/retrain-venv/bin/python import_tla.py --tla /caminho/TLA.zip --ttdd /caminho/TTDD.zip
```

O manifesto `data/prepared/manifest.json` já registra o hash de cada imagem e o split. A montagem do ciclo confere tudo e recusa arquivos diferentes.

### 3. Professor DINOv2-B

A destilação precisa do checkpoint do professor (`dinov2_vitb14.safetensors`, 343 MB, SHA256 `0423c9fe8cb99a8f741a4ffe1280e7f118b9e2744fd79a7bc329bf97ca86fae6`). **Em 12/11/2026 ele não estava neste computador nem nas releases do GitHub.** Há duas formas de obtê-lo:

1. **Recuperar a cópia original**, se alguém da equipe tiver a pasta `experiments/vision/.cache/finetune/`. Depois, publique-a como asset de uma release privada para não perder de novo.
2. **Treinar o professor de novo no TLA**, com a receita de [`experiments/vision`](../machine-learning/experiments/vision/README.md) (`experiments.vision.benchmark --models dinov2_vitb14` e depois `experiments.vision.finetune --models dinov2_vitb14`). O resultado deve ficar perto do original (Macro-F1 de validação registrado em `benchmark_artifacts/vision_finetune/dinov2_vitb14/`), mas o hash não será o mesmo. O treino registra se o professor usado é o original (`matches_recorded_teacher`).

Sem professor, `retraining.train` treina só supervisionado. O resultado serve de diagnóstico, mas **não pode ser promovido**: só a receita definida vai para o app.

### 4. Chave de serviço do Supabase

No painel do Supabase: **Project Settings → API Keys → `service_role`** (legada) ou uma chave secreta nova. Use só em variáveis de ambiente da sessão:

```bash
export SUPABASE_URL=https://nhkqfanjfcivcbndivav.supabase.co
read -rs SUPABASE_SERVICE_ROLE_KEY && export SUPABASE_SERVICE_ROLE_KEY
```

Nunca coloque a chave em arquivo versionado, no APK, em issue ou em log.

## Antes de treinar: revisar as cópias

O treino só aproveita cópias revisadas, e elas somem 90 dias após o envio. No app, na conta admin:

- **Resumo → Treinamento do modelo** mostra as cópias prontas, as que aguardam revisão (com quantas vencem em até 14 dias) e as que ficaram fora do treino. "Revisar cópias" abre a fila só com cópias de treino pendentes, a que vence primeiro no topo.
- Na fila e na galeria, o selo **Treino · N dias** indica uma cópia de treino e quanto tempo ela ainda tem. O filtro **Só cópias de treino** limita a consulta a elas.
- Confirme quando a classe prevista está certa e corrija quando não está. Duvidosa e Rejeitada ficam fora do treino.

Para ver o estado sem baixar fotos:

```bash
.cache/retrain-venv/bin/python export_authorized_training.py --status
```

### Quando vale a pena retreinar

Não há número mágico. Como referência para o ciclo trazer evidência útil:

- pelo menos **5 pessoas diferentes** com fotos prontas. Abaixo disso, todas as fotos de campo vão para o treino e não existe teste de campo, só o teste TLA;
- algumas fotos prontas por classe nas doenças mais comuns no campo. Classes sem foto de campo continuam aprendendo só com o TLA;
- fotos de condições reais variadas: luz, distância, aparelhos.

## Passo a passo de um ciclo

Comandos em `machine-learning/`. Use um nome de ciclo com data, por exemplo `2026-12-ciclo1`.

### 1. Exportar as fotos prontas

```bash
.cache/retrain-venv/bin/python export_authorized_training.py
```

Gera `data/authorized_training/` com as imagens e o `manifest.json`: classe revisada, previsão original, grupo por pessoa, hashes, `delete_after`. A pasta anterior é apagada antes, o que reconcilia revogações e exclusões. A exportação vale 30 dias: o ciclo inteiro precisa caber nesse prazo, ou basta exportar de novo.

### 2. Montar o ciclo

```bash
.cache/retrain-venv/bin/python -m retraining.dataset --run 2026-12-ciclo1
```

Gera `runs/2026-12-ciclo1/dataset.json`:

- **TLA** mantém o split histórico (treino 489, validação 104, teste 103), para comparar com todos os modelos anteriores.
- **Fotos de campo** são separadas por pessoa: 70% treino, 15% validação e 15% `field_test`. Todas as fotos de uma pessoa, e quase-duplicatas, ficam no mesmo split. A atribuição é fixa por grupo dentro de uma exportação.
- Fotos de campo iguais ou quase iguais a uma do TLA são descartadas.
- `production_model_on_field_test` registra o acerto do modelo em uso nas fotos de teste de campo, usando a previsão gravada na hora da análise. É a linha de base.

Leia `warnings`: avisa quando há poucas pessoas, quando faltam classes no campo e quando nada de campo foi aproveitado.

### 3. Treinar o candidato

```bash
.cache/retrain-venv/bin/python -m retraining.train --run 2026-12-ciclo1 --teacher /caminho/dinov2_vitb14.safetensors
```

A receita é a mesma do modelo atual, sem mudanças:

| Item | Valor |
| --- | --- |
| Aluno | `mobilenetv4_conv_small.e2400_r224_in1k` (timm, revisão fixada), pesos ImageNet |
| Cabeça inicial | probe logístico C=1 (com e sem pesos de classe), escolhido pela validação |
| Etapas | 3 épocas só da cabeça (lr 1e-3) + até 15 épocas do encoder (cabeça 1e-4, encoder 1e-5), paciência 5 |
| Otimização | AdamW, weight decay 1e-4, batch 8, BatchNorm congelada, float32, seed 42 |
| Augmentation | flips H/V, rotação ±12° com fundo branco, brilho/contraste 0,1, saturação 0,05 |
| Perda | 0,5 × entropia cruzada balanceada + 0,5 × T² × KL(professor ‖ aluno), T = 4 |
| Seleção | Macro-F1 de validação, desempate por Top-1 e Top-3, época mais cedo |
| Calibração | temperatura por NLL na validação; limiar com acerto ≥ 90% entre aceitas na validação |

Validação TLA + validação de campo escolhem a época e calibram. O teste TLA e o `field_test` só são lidos depois disso.

Tempo esperado: o treino do aluno atual levou 79 s (18 épocas) numa GTX 1650. Os alvos do professor ViT-B, um passe por imagem de treino em cada uma das 18 épocas, levam mais. Em CPU tudo é bem mais lento. Com fotos de campo, o tempo cresce na proporção das imagens de treino.

Saídas em `runs/<ciclo>/`: `checkpoint.safetensors`, `experiment_config.json`, `history.json`, métricas de validação/teste/campo, matrizes de confusão, previsões, `comparison.json` e `run_summary.json`.

### 4. Converter para TFLite

```bash
.cache/retrain-export-venv/bin/python -m retraining.export_model --run 2026-12-ciclo1
```

Gera `model_float32.tflite` no contrato do app: entrada RGB 0–255 NHWC `[1,224,224,3]`, normalização ImageNet, temperatura e softmax no grafo, saída `[1,16]`. Exige mesmo Top-1 e erro ≤ 1e-4 em todas as imagens de validação.

### 5. Conferir com o pré-processamento do app

```bash
.cache/retrain-export-venv/bin/python -m retraining.promote --run 2026-12-ciclo1
```

Sem `--install`, nada muda. O script decodifica cada imagem de validação, teste e teste de campo como o app (EXIF, sRGB, resize bicúbico 256, recorte 224), roda o TFLite e exige as mesmas probabilidades e métricas do treino.

Então leia `runs/<ciclo>/comparison.json`. A recomendação automática só é positiva quando:

- o Macro-F1 no teste TLA não cai mais de 0,02 em relação ao modelo atual;
- o Top-1 nas fotos de campo é pelo menos igual ao do modelo atual;
- existe teste de campo.

É uma recomendação. Quem decide é a equipe.

### 6. Promover para o app (decisão explícita)

Só depois da decisão da equipe:

```bash
.cache/retrain-export-venv/bin/python -m retraining.promote --run 2026-12-ciclo1 --install --name mobilenetv4-retreino-2026-12
```

Isso substitui `leafcare.tflite` e `model_metadata.json` em `artifacts/` e nos assets Android, atualiza `training_metadata.json`/`metrics.json` e grava os relatórios em `docs/model-reports/<nome>/`. Os relatórios incluem um resumo do ciclo, sem fotos nem linhas por imagem. Um candidato não recomendado só entra com `--allow-unrecommended`, depois de registrar o motivo.

Depois da promoção:

1. `cd ../android-app && bash gradlew testDebugUnitTest verifyModelAssets assembleDebug`;
2. testar no aparelho: análise de referência, tempo no Galaxy A06 (meta ≤ 3 s), resultados inconclusivos;
3. atualizar [Machine Learning](MACHINE_LEARNING.md) com as métricas novas, o CHANGELOG e a versão do app (modelo novo = versão minor);
4. publicar o checkpoint do aluno, e do professor se ainda não estiver salvo, como asset de release, junto do APK.

As análises antigas continuam com o hash e o limiar do modelo que as gerou.

## Testar o pipeline sem dados reais

Teste de fumaça com imagens sintéticas, 16 classes, TLA e campo, professor com pesos aleatórios. Ele confere o encadeamento, não a qualidade:

```bash
LEAFCARE_TRAINING_SMOKE=1 .cache/retrain-venv/bin/python -m pytest -q tests/test_retraining_smoke.py -k distilled
LEAFCARE_TRAINING_SMOKE=1 .cache/retrain-export-venv/bin/python -m pytest -q tests/test_retraining_smoke.py -k tflite
.cache/retrain-venv/bin/python -m pytest -q -m "not tensorflow"
```

Para a parte do banco: `bash supabase/tests/run-local.sh` (Postgres em Docker) inclui `training-pool.sql`, que cobre cada regra da tabela acima.

## Limitações e decisões em aberto

- **Janela de 90 dias.** Os Termos v1.4 não permitem guardar as fotos de campo além de 90 dias, nem as exportações além de 30. Cada ciclo só aprende com as fotos daquele período. Acumular um conjunto de campo permanente exige mudar os Termos e a Política, uma decisão do controlador, e pedir novo aceite.
- **Professor ausente.** Veja a seção do professor. Sem ele não há promoção.
- **Teste de campo pequeno.** Com poucas pessoas, as métricas de campo variam muito de um ciclo para outro. Trate como indício, não como prova.
- **Rótulo revisado.** A qualidade do treino depende da revisão. Use Duvidosa quando houver dúvida, porque fotos duvidosas ficam fora.
- **Contrato fixo de 16 classes.** O pipeline não adiciona nem remove classes. Isso exige outra mudança, no app, no catálogo e no banco.

## Arquivos

| Caminho | Papel |
| --- | --- |
| `supabase/migrations/20261112120000_training_pool.sql` | `training_pool()` e dados de treino no painel admin |
| `supabase/tests/training-pool.sql` | Testes de cada regra de elegibilidade e do painel |
| `machine-learning/export_authorized_training.py` | Estado do pool, exportação local e limpeza de 30 dias |
| `machine-learning/retraining/dataset.py` | Monta o ciclo (TLA + campo, split por pessoa, linha de base) |
| `machine-learning/retraining/train.py` | Treina o candidato com a receita definida |
| `machine-learning/retraining/export_model.py` | Converte para TFLite e confere paridade |
| `machine-learning/retraining/promote.py` | Replay com o pré-processamento do app e promoção |
| `machine-learning/systemd/` | Timer diário de limpeza das exportações |

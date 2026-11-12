# Retreinar o modelo com as fotos do app

Este é o plano de retreino do LeafCare. Ele explica como as fotos analisadas no app viram um conjunto de treino que cresce com o tempo e como gerar e promover uma nova versão do modelo com um único script.

O modelo continua o mesmo tipo de hoje: **MobileNetV4 Small destilado de um professor DINOv2-B** ([Machine Learning](MACHINE_LEARNING.md)). Tudo roda localmente, em `machine-learning/`. Nenhuma foto vai para um serviço externo de treino.

## Como o conjunto cresce

```text
App: análise conclusiva
  └─ cópia no bucket privado training-photos (Supabase) ...... fica até revogação/exclusão (Termos v1.5)
       └─ revisão humana no admin: confirmar ou corrigir ....... duvidosa/rejeitada fica fora
            └─ training_pool() = 'ready' ......................... o rótulo é a classe revisada
                 └─ retrain.py sync → machine-learning/data/field/  espelho local, sem IDs e sem EXIF
                      └─ retrain.py run: TLA + todas as fotos prontas
                           └─ professor → aluno → TFLite → comparação com o modelo atual
                                └─ retrain.py promote (decisão da equipe) → APK
```

- **Ponto de partida: o TLA.** As 696 imagens do dataset público, com o split histórico (treino 489, validação 104, teste 103), estão em todos os ciclos. Assim as métricas continuam comparáveis com todos os modelos anteriores.
- **Fotos de campo acumuladas.** Toda foto revisada como confirmada ou corrigida entra no conjunto e fica nele nos ciclos seguintes. Cada ciclo usa o TLA mais tudo o que já foi revisado até ali.
- **O que sai do conjunto:**
  - fotos com autorização revogada, análise excluída ou conta excluída;
  - fotos revisadas de novo como duvidosas ou rejeitadas;
  - cópias da v1.4 depois de 90 dias.

  A limpeza diária do servidor apaga essas cópias, e o próximo `sync` as remove do espelho local.

## O que os Termos v1.5 garantem e onde

| Regra | Onde é aplicada |
| --- | --- |
| Só fotos analisadas depois do aceite (v1.4 ou v1.5) | `training_pool()`: análise posterior ao primeiro aceite |
| Cópias da v1.5 sem prazo; as da v1.4 vencem em 90 dias | `training_copy_expires_at()` calcula `expires_at` por cópia (nulo = permanente) |
| Revisão humana antes de entrar no conjunto | Só o estado `ready` (`confirmed`/`corrected` com classe) é baixado |
| Revogação, exclusão da análise ou da conta removem a cópia | App (sincronização), painel (exclusão de conta) e `training_photos_to_delete()` na limpeza diária `cleanup-training-photos` (03:12 UTC), que pega até revogações feitas em outro aparelho |
| Cópia local sem identificadores e sem EXIF | `retrain.py sync`: imagem recodificada e nomes por HMAC com segredo local |
| Cópia local acompanha o servidor | Cada `sync` apaga o que saiu de `ready`; `run` recusa continuar um ciclo cujas fotos saíram |
| Pesos treinados não são desfeitos | Documentado nos Termos; cada promoção registra a origem do modelo |

## Preparação (uma vez por computador)

Comandos em `machine-learning/`, com Python 3.12 e [`uv`](https://docs.astral.sh/uv/).

### 1. Ambientes

```bash
python retrain.py setup
```

O comando cria `.cache/retrain-venv` (treino, com PyTorch e CUDA quando houver GPU) e `.cache/retrain-export-venv` (conversão TFLite), com as versões fixadas em `requirements-train.txt` e `requirements-export.txt`. O `retrain.py` escolhe sozinho o ambiente de cada etapa.

Se `.cache/retrain-venv` já existir com o PyTorch de CPU, apague a pasta e rode `setup` de novo para usar a GPU.

### 2. TLA em `data/raw/`

As imagens do TLA não ficam no Git. Baixe os arquivos públicos do artigo ([doi:10.3389/fpls.2024.1333236](https://doi.org/10.3389/fpls.2024.1333236)), o ZIP do TLA e o do TTDD, e importe uma vez:

```bash
.cache/retrain-venv/bin/python import_tla.py --tla /caminho/TLA.zip --ttdd /caminho/TTDD.zip
```

Cada ciclo confere cada imagem com o hash do manifesto `data/prepared/manifest.json` e recusa arquivos diferentes.

### 3. Chave de serviço do Supabase

No painel do Supabase: **Project Settings → API Keys → `service_role`**. Use só em variáveis da sessão do terminal:

```bash
export SUPABASE_URL=https://nhkqfanjfcivcbndivav.supabase.co
read -rs SUPABASE_SERVICE_ROLE_KEY && export SUPABASE_SERVICE_ROLE_KEY
```

Nunca coloque a chave em arquivo versionado, no APK, em issue ou em log.

### 4. Guardar o segredo do espelho

O primeiro `sync` cria `data/field/.secret`. Faça backup dele em lugar seguro, junto com a pasta `data/field/`. Se o segredo se perder, o espelho é baixado de novo com chaves novas, e as pessoas podem mudar de split, o que quebra a comparação do `field_test` entre ciclos.

## Dia a dia: revisar

O treino só usa cópias revisadas. No app, na conta admin:

- **Resumo → Treinamento do modelo** mostra as cópias prontas, as que aguardam revisão e as que ficaram fora. "Revisar cópias" abre a fila só com as cópias pendentes.
- O selo **Treino** marca uma cópia de treino. As cópias da v1.4 mostram **Treino · N dias**: revise essas antes, porque elas vencem.
- Confirme quando a classe prevista está certa e corrija quando não está. Na dúvida, marque Duvidosa: ela fica fora do treino.

Para ver o estado sem baixar nada:

```bash
python retrain.py status
```

## Rodar um ciclo

Use um nome com data, por exemplo `2026-12-retreino`.

```bash
python retrain.py run --name 2026-12-retreino
```

Etapas, todas gravadas em `runs/<ciclo>/`:

1. **Sincronizar** `data/field/` com o servidor. Use `--no-sync` para trabalhar sem rede. Mesmo assim, as cópias vencidas são apagadas.
2. **Montar o conjunto** (`dataset.json`): TLA com o split histórico mais as fotos prontas.
   - Fotos de campo são separadas por pessoa: 70% treino, 15% validação e 15% `field_test`. A divisão é fixa, então uma pessoa fica sempre no mesmo split, ciclo após ciclo.
   - Quase-duplicatas ficam juntas, e fotos que repetem o TLA são descartadas.
   - Com menos de 5 pessoas, todas as fotos de campo vão para o treino, o ciclo avisa e não há teste de campo.
3. **Professor DINOv2-B**: fine-tune nos splits de treino e validação do ciclo, guardado em `.cache/teachers/` e reaproveitado se o conjunto não mudar. `--teacher ARQUIVO` usa um checkpoint já treinado. Na primeira vez, o script baixa cerca de 350 MB de pesos pré-treinados, conferidos por hash.
4. **Aluno MobileNetV4 Small**, com a receita do modelo atual:

   | Item | Valor |
   | --- | --- |
   | Pesos iniciais | ImageNet (`mobilenetv4_conv_small.e2400_r224_in1k`, revisão fixada) |
   | Cabeça inicial | probe logístico, escolhido pela validação |
   | Etapas | 3 épocas só da cabeça + até 15 do encoder, paciência 5 |
   | Otimização | AdamW, batch 8, BatchNorm congelada, seed 42 |
   | Perda | 0,5 × entropia cruzada balanceada + 0,5 × destilação do professor (T = 4) |
   | Seleção e calibração | Macro-F1 de validação; temperatura e limiar só pela validação |

5. **TLA test e `field_test`** são avaliados só depois da seleção.
6. **TFLite**: conversão no contrato do app, com paridade (mesmo Top-1, erro ≤ 1e-4).
7. **Replay**: o candidato e o modelo atual rodam com o pré-processamento do app nas mesmas imagens. O resultado sai em `comparison.json`.

Se o comando parar no meio, rode de novo com o mesmo nome: as etapas prontas são reaproveitadas. Se alguma foto do ciclo tiver saído do conjunto nesse meio-tempo, o ciclo recusa continuar e pede um nome novo.

Tempo medido no primeiro ciclo real (12/11/2026, GTX 1650 de 4 GB): professor em cerca de 6 min, aluno em menos de 1 min, e conversão e replay em poucos minutos. Na primeira vez, o download dos pesos pré-treinados soma alguns minutos. Em CPU tudo é bem mais lento, e o tempo cresce com o número de fotos de campo.

No replay, uma imagem cuja confiança fica a até 1e-4 do limiar pode mudar de aceita para inconclusiva, ou o contrário. Isso é tolerado e registrado em `threshold_borderline_flips`. Qualquer outra diferença interrompe o ciclo.

## Decidir e promover

Leia `runs/<ciclo>/comparison.json`. A recomendação automática só é positiva quando:

- existe `field_test`;
- o Top-1 no `field_test` é pelo menos igual ao do modelo atual;
- o Macro-F1 no teste TLA não cai mais de 0,02.

Ela é só um apoio. Quem decide é a equipe. Para instalar:

```bash
python retrain.py promote --name 2026-12-retreino --model-name mobilenetv4-2026-12
```

O comando substitui o bundle em `artifacts/` e nos assets Android, atualiza os metadados e grava o relatório em `docs/model-reports/<nome>/`, sem fotos nem linhas por imagem. Um candidato não recomendado só entra com `--allow-unrecommended`, depois de registrar o motivo.

Depois da promoção:

1. `cd ../android-app && bash gradlew testDebugUnitTest verifyModelAssets assembleDebug`;
2. testar no aparelho: análise de referência, tempo no Galaxy A06 (meta ≤ 3 s) e resultados inconclusivos;
3. atualizar [Machine Learning](MACHINE_LEARNING.md), o CHANGELOG e a versão do app (modelo novo = versão minor);
4. guardar o checkpoint do aluno e do professor como asset da release, junto do APK.

As análises antigas continuam com o hash e o limiar do modelo que as gerou.

## Testar sem dados reais

O teste de fumaça usa imagens sintéticas e um professor pequeno com pesos aleatórios. Ele percorre a cadeia toda: sync falso, conjunto, professor, aluno, TFLite, replay, retomada e promoção numa pasta temporária. Ele confere o encadeamento, não a qualidade.

```bash
.cache/retrain-venv/bin/python -m pytest -q
LEAFCARE_TRAINING_SMOKE=1 .cache/retrain-venv/bin/python -m pytest -q tests/test_smoke.py
```

Para o banco: `bash supabase/tests/run-local.sh` (Postgres em Docker) cobre cada regra da tabela acima em `training-pool.sql`.

## Primeiro ciclo real (12/11/2026)

`2026-11-retreino-1`: TLA + 1 foto de campo (1 pessoa, classe `healthy`).

| Teste TLA (103) | Modelo atual | Candidato |
| --- | ---: | ---: |
| Top-1 | 85,44% | 83,50% |
| Macro-F1 | 0,8083 | 0,7922 |
| Top-3 | 97,09% | 97,09% |
| Cobertura / acerto entre aceitas | 87,4% / 90,0% | 99,0% / 84,3% |

Não recomendado: ainda não existe `field_test`, porque há menos de 5 pessoas. Não foi promovido. O ciclo provou o pipeline com dados reais: sincronização, professor, aluno, TFLite com paridade e replay. O ganho real só vai aparecer com mais fotos revisadas de mais pessoas.

## Riscos e limites

- **Armazenamento.** As cópias deixam de vencer, então o bucket `training-photos` só cresce. Acompanhe o uso no painel do Supabase e o limite do plano.
- **Professor novo a cada ciclo.** O checkpoint original do DINOv2-B se perdeu. O professor treinado pelo ciclo fica perto do original, mas não é idêntico. Por isso o candidato sempre é comparado com o modelo atual antes de qualquer promoção.
- **Teste de campo pequeno no começo.** Com poucas pessoas, as métricas de campo variam muito. Trate como indício, não como prova. Quando o conjunto passar de 5 pessoas, as fotos de campo passam a ser divididas entre treino, validação e teste, o que acontece uma única vez.
- **Rótulo revisado.** A qualidade do treino depende da revisão.
- **16 classes fixas.** O pipeline não adiciona nem remove classes. Isso exige outra mudança, no app, no catálogo e no banco.

## Arquivos

| Caminho | Papel |
| --- | --- |
| `machine-learning/retrain.py` | Ponto de entrada: `setup`, `status`, `sync`, `run`, `promote` |
| `machine-learning/training/` | Espelho, conjunto, treino, conversão/replay, promoção e métricas |
| `machine-learning/data/field/` | Espelho local das fotos de campo (fora do Git) |
| `machine-learning/runs/` | Ciclos (fora do Git) |
| `supabase/migrations/20261112120000_training_pool.sql` | `training_pool()` e dados de treino no painel |
| `supabase/migrations/20261112180000_legal_documents_v1_5_permanent_training.sql` | Aceite v1.5, retenção por cópia e `training_photos_to_delete()` |
| `supabase/functions/cleanup-training-photos/` | Limpeza diária das cópias que precisam sair |

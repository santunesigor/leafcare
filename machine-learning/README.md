# Machine Learning do LeafCare

Modelo do app: **MobileNetV4 Small destilado** de um professor DINOv2-B, TFLite float32, 16 classes, classificação no aparelho. Contrato, métricas e limitações: [Machine Learning](../docs/MACHINE_LEARNING.md). Ciclo de retreino: [Retreinar o modelo](../docs/RETREINAMENTO.md).

## Arquivos

| Caminho | Uso |
| --- | --- |
| `artifacts/` | Bundle atual do app: `leafcare.tflite`, classes, catálogo, metadados, treino e métricas (também a referência de produção no retreino). |
| `retrain.py` | Ponto de entrada único do retreino: `setup`, `status`, `sync`, `run`, `promote`. |
| `training/` | Pipeline: `sync` (espelho das fotos), `dataset` (TLA + campo), `train` (professor e aluno), `export` (TFLite, paridade e replay), `promote` (recomendação e instalação), `metrics`. |
| `leafcare/`, `predict.py`, `validate_bundle.py` | Pré-processamento e inferência com o contrato do app; previsão local e validação do bundle Android. |
| `config.yaml` | Política do ciclo: seed, proporções do split de campo, mínimo de grupos, quase-duplicatas, tamanho mínimo, critério de promoção. |
| `data/prepared/`, `data/groups.csv`, `data/import_report.json`, `import_tla.py`, `tla_class_map.yaml` | TLA: split histórico fixo (489/104/103) e proveniência. As imagens ficam no bucket privado `tla-dataset` (uma pasta por doença) e `sync` as baixa para `data/raw/`; `import_tla.py` só recria o bucket a partir dos ZIPs originais. |
| `requirements*.txt` | `requirements.txt` (CI e testes), `requirements-train.txt` (treino), `requirements-export.txt` (conversão TFLite). |
| `tests/` | Contrato, espelho, dataset, CLI e o ciclo completo de fumaça (opt-in). |

Ignorados pelo Git: `data/raw/` (TLA), `data/field/` (espelho das fotos de campo, dados sensíveis), `runs/` (ciclos) e `.cache/` (ambientes, pesos pré-treinados, professores).

## Retreino

Python 3.12 e [uv](https://docs.astral.sh/uv/). Credenciais só pelo ambiente: `SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY`.

```bash
python retrain.py setup                       # cria .cache/retrain-venv e .cache/retrain-export-venv
python retrain.py status                      # pool no servidor e espelho local; não baixa nada
python retrain.py sync                        # baixa o TLA que faltar e atualiza data/field/
python retrain.py run --name 2026-12-retreino # ciclo completo; retoma etapas concluídas
python retrain.py promote --name 2026-12-retreino --model-name mobilenetv4-2026-12
```

`run` faz: sincronização → `runs/<ciclo>/dataset.json` (TLA com o split histórico + fotos `ready` com o rótulo revisado, split determinístico por pessoa: 70/15/15, teste de campo só com 5 grupos ou mais) → professor DINOv2-B treinado no ciclo e guardado em `.cache/teachers/` (ou `--teacher ARQUIVO`) → aluno destilado → TFLite com paridade → replay com o pré-processamento do app, do candidato e do modelo atual → `comparison.json`. A recomendação exige teste de campo, Top-1 de campo ≥ modelo atual e queda de Macro-F1 no teste TLA ≤ 0,02. `promote` só instala por comando explícito; depois rode os testes Android e gere o APK.

O treino roda em `.cache/retrain-venv` (GPU quando houver; lotes de 8 cabem numa GTX 1650 de 4 GB) e a conversão em `.cache/retrain-export-venv`; `retrain.py` escolhe o ambiente sozinho.

### Espelho `data/field/`

As cópias do bucket privado `training-photos` ficam no servidor até revogação, exclusão da análise ou da conta (Termos v1.5); cópias dos Termos v1.4 vencem 90 dias após o envio. O espelho segue o servidor a cada `sync`: baixa fotos novas prontas, apaga as que saíram de `ready` (inclusive vencidas, também sem rede no início de `run`) e atualiza rótulos sem novo download.

```
data/field/            (modo 700)
  .secret              chave HMAC local (modo 600); se for apagada, o espelho é refeito e os grupos mudam
  manifest.json        por foto: rótulo revisado, grupo da pessoa, validade, hashes e tamanho
  <doença>/<chave>.jpg JPEG recodificado, sem EXIF, na pasta da doença revisada
```

Nomes e manifesto não têm IDs de conta ou análise: a chave da foto e o grupo da pessoa são HMAC-SHA256 com o segredo local, estáveis entre ciclos. A chave de serviço nunca entra no APK, em arquivos versionados ou em logs.

## Testes

```bash
pip install -r requirements.txt
python -m pytest -q
python validate_bundle.py
python predict.py ../samples/reference_frog_eye.jpg

# ciclo completo sintético (minutos, CPU), em qualquer um dos dois ambientes
LEAFCARE_TRAINING_SMOKE=1 .cache/retrain-venv/bin/python -m pytest -q tests/test_smoke.py
```

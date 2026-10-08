# Machine Learning do LeafCare

O app usa **MobileNetV3Ensemble**: dois MobileNetV3Small (`small_adam` e `small_rmsprop`) e um MobileNetV3Large (`large_adamw`), unidos em um TFLite float32. São 16 classes, entrada RGB `[1,224,224,3]`, temperatura 0,835105 e limiar calibrado 0,631628. O bundle instalado fica em `../android-app/app/src/main/assets/`.

## Estrutura

```text
machine-learning/
├── README.md                  # guia da organização
├── train_ensemble.py          # treino/exportação do modelo atual
├── export_tflite.py           # conversão compartilhada + exportador antigo
├── validate_bundle.py         # confere o bundle instalado
├── predict.py                 # inferência local para inspeção
├── import_tla.py              # importação das fontes locais
├── prepare_dataset.py        # auditoria e split por grupos
├── config.yaml               # configuração base
├── requirements.txt          # dependências principais
├── pytest.ini                # suíte padrão: tests/
├── disease_catalog.json      # fonte do catálogo explicativo
├── classes.reference.json    # contrato de referência sem modelo
├── tla_class_map.yaml        # proveniência/taxonomia
├── leafcare/                 # utilitários compartilhados de dados, treino e inferência
├── data/                     # grupos, manifesto, auditoria e dados brutos locais
├── artifacts/                # bundle atual e metadados/métricas consumidos pelo código
├── tests/                    # testes do contrato e do ensemble
├── legacy/                   # pipeline anterior de modelo único
│   ├── train.py
│   └── evaluate.py
├── experiments/              # comparação de modelos; ambientes/checkpoints locais
│   ├── dinov2/
│   ├── vision/
│   └── ensemble/             # ambiente e checkpoints ignorados pelo Git
└── benchmark_artifacts/      # registros de benchmarks, preservados nos caminhos atuais

docs/
├── model-reports/ensemble/   # gráficos e relatórios do ensemble atual
└── archive/ui/               # capturas históricas usadas na documentação
```

`docs/` é irmã de `machine-learning/`, na raiz do repositório. Nomes como `legacy`, `model-reports` e `archive` distinguem código antigo, relatórios técnicos e evidências históricas.

## O que é necessário para o modelo atual

- `artifacts/` mantém `leafcare.tflite`, `classes.json`, `diseases.json`, `model_metadata.json`, `training_metadata.json` e `metrics.json`. O `model.keras` local continua ignorado pelo Git. A cópia TFLite é usada pela CLI e pelos testes; os assets Android são a cópia distribuída no APK.
- `train_ensemble.py`, `leafcare/`, `config.yaml`, dados locais e os checkpoints de `experiments/ensemble/.cache/` permitem reproduzir/reexportar o ensemble. As classes e o catálogo já exportados são reaproveitados pelo ensemble.
- `export_tflite.py` permanece na raiz porque `convert_model` é usado pelo ensemble e pelos testes. Sua entrada de exportação de modelo único rejeita o ensemble.
- `experiments/dinov2/benchmark.py` é uma dependência real: fornece métricas e calibração ao ensemble. Não remover essa pasta inteira.
- `tests/`, `pytest.ini`, `samples/` na raiz e artefatos de benchmark verificam o contrato e recalculam resultados registrados.

Gráficos, histórico por época, snapshot de configuração, matriz de confusão, previsões detalhadas e relatório de paridade ficam em [`docs/model-reports/ensemble/`](../docs/model-reports/ensemble/). Futuras exportações do ensemble escrevem nessa pasta.

## O que não é necessário para executar o APK

| Grupo | Recomendação |
| --- | --- |
| `legacy/train.py` | Não participa do pipeline atual; conservar isolado para reproduzir o modelo único anterior. Candidato a remoção se esse suporte for abandonado. |
| `legacy/evaluate.py` | Não avalia o ensemble; conservar isolado porque há teste que verifica sua rejeição do bundle atual. Remoção exigiria ajustar o teste e a documentação. |
| Experimentos `vision/` | Não fazem parte da inferência no app; conservar para comparação e para seus testes explícitos. Podem ser separados futuramente junto com os consumidores. |
| Gráficos e relatórios em `docs/model-reports/ensemble/` | Não são carregados pelo app; conservar como evidência identificada do modelo atual. |
| `.venv/`, caches e outputs de build | Não são versionados nem distribuídos. Ambientes podem ser reinstalados; checkpoints treinados devem ser guardados para reexportar sem novo treino. |
| `benchmark_artifacts/` | Não vai no APK, mas tem leitores em testes e relatórios. Preservada integralmente conforme solicitado. |

## Comandos

Execute dentro de `machine-learning/`, usando o ambiente Python apropriado:

```bash
python -m pytest -q
python validate_bundle.py --require-model
python predict.py ../samples/reference_frog_eye.jpg
```

O treino e a exportação atuais continuam com `python train_ensemble.py --train-only` e `python train_ensemble.py --export-only`. Esses comandos alteram artefatos/assets e não foram executados na organização.

Para o legado, use `python -m legacy.train --config config.yaml` e `python -m legacy.evaluate --config config.yaml`, a partir desta pasta. O modelo único precisa de um `output_dir` separado do ensemble.

Contrato completo, métricas e instruções de reprodução: [Machine Learning](../docs/MACHINE_LEARNING.md). Inventário e decisões da limpeza: [Mapa do repositório](../docs/explain/MAPA_REPOSITORIO.md).

# Machine Learning do LeafCare

O modelo atual é **MobileNetV4 Small destilado**, TFLite float32 de 9,70 MiB com 16 classes. Professor DINOv2-B somente no treino. Contrato, métricas, comandos e limitações: [Machine Learning](../docs/MACHINE_LEARNING.md).

## Organização

| Caminho | Uso |
| --- | --- |
| `deploy_distilled.py` | Promoção hash-verificada do aluno selecionado; replay antes de atualizar bundle. |
| `artifacts/` | TFLite, classes, catálogo, metadata, treinamento e métricas atuais. |
| `leafcare/`, `predict.py`, `validate_bundle.py` | Preprocessing, CLI e validação do bundle instalado. |
| `tests/` | Suíte padrão do contrato atual e replay dos resultados históricos. |
| `experiments/distillation/` | Receita de treino/exportação; ambientes e checkpoints locais em `.cache`, ignorados. |
| `benchmark_artifacts/distillation/` | Registros dos dez experimentos originais, preservados. |
| `../docs/model-reports/mobilenetv4-distilled/` | Relatórios exclusivos da integração atual. |
| `train_ensemble.py`, `experiments/ensemble/`, `benchmark_artifacts/ensemble/` | Receita e evidências históricas do ensemble; reproduzir no checkout `v1.1.2`. |
| `../docs/model-reports/ensemble/` | Relatórios históricos do ensemble, preservados. |
| `export_tflite.py`, `legacy/` | Pipeline Keras histórico; rejeita o modelo destilado atual. |
| `experiments/dinov2/`, `experiments/vision/` | Benchmarks históricos e utilitários de métricas usados pelos experimentos. |
| `data/`, `import_tla.py`, `prepare_dataset.py`, `tla_class_map.yaml` | Dataset, split e proveniência; nenhuma alteração nesta promoção. |

`docs/` é irmã de `machine-learning/`. Nenhum experimento ou relatório entra no APK; somente o bundle e catálogo dos assets Android.

## Comandos

Execute aqui, com Python 3.12 e `requirements.txt` instalados:

```bash
python -m pytest -q
python validate_bundle.py --require-model
python predict.py ../samples/reference_frog_eye.jpg
```

Com dados brutos e caches do experimento disponíveis:

```bash
python deploy_distilled.py --install --pixel-parity-dir /tmp/leafcare-distilled-parity
```

O backend Keras não possui modelo correspondente ao aluno. Arquivos `model.keras` locais antigos não participam da inferência atual. Novos testes permanecem isolados em `experiments/`; sua promoção ao app exige solicitação explícita.

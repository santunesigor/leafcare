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
| `export_authorized_training.py` | Estado do pool de treino e exportação das cópias prontas (revisão humana, regras v1.4) para pasta local privada, apagada em 30 dias. |
| `retraining/` | Ciclo de retreino: montar TLA + campo, treinar com a receita definida, converter para TFLite e promover. Guia: [Retreinar o modelo](../docs/RETREINAMENTO.md). |
| `runs/`, `.cache/` | Ciclos de retreino e ambientes locais; ignorados pelo Git. |

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

### Fotos do app para retreino

Desde os Termos v1.4, o uso das fotos analisadas para treinamento é condição de uso. Cada análise conclusiva feita depois do aceite gera uma cópia no bucket privado `training-photos`, separado do histórico. A cópia só entra em um conjunto de treino depois de revisão humana: confirmada ou corrigida no painel admin.

- **Elegibilidade.** A função `training_pool()` do banco decide quem entra; o exportador baixa apenas o estado `ready`.
- **Retenção.** Cópias no Supabase expiram em 90 dias (Edge Function diária). Exportações locais expiram em 30 dias: `--cleanup-only` remove e o timer de usuário em `systemd/` roda isso todo dia, com o Python do sistema.
- **Anonimização.** A exportação recodifica as imagens, sem EXIF e sem identificadores de conta ou análise. Agrupa as fotos por pessoa com uma chave aleatória descartada a cada exportação.
- **Chave de serviço.** É exclusiva do pipeline confiável: nunca no APK, em arquivos versionados ou em logs.

Backups manuais do banco no Google Drive têm acesso restrito e retenção máxima de 30 dias; a exportação SQL não inclui os arquivos de Storage.

O ciclo completo (exportar, montar o ciclo, treinar, converter, conferir e promover) está em [Retreinar o modelo](../docs/RETREINAMENTO.md).

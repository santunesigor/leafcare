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
| `export_authorized_training.py` | Exportação de fotos com consentimento vigente e revisão humana, para pasta local privada ignorada pelo Git. |

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

### Fotos autorizadas para triagem

O consentimento geral é opcional, desmarcado por padrão, e cada foto requer autorização individual antes da análise. O servidor aceita upload apenas para a própria conta, após aceite das versões atuais da Política e dos Termos, consentimento geral vigente e autorização vigente daquela foto. Recusar ou revogar mantém a análise e o histórico normais; revogações são sincronizadas antes de novos uploads. O bucket `training-photos` é privado e separado do histórico. Cópias no Supabase expiram após 90 dias; uma Edge Function percorre o bucket diariamente e remove arquivos vencidos. Exportações locais expiram após 30 dias; `--cleanup-only` executa a remoção e o timer de usuário em `systemd/` agenda essa verificação diária neste ambiente. `python export_authorized_training.py` consulta somente autorizações atuais, exige revisão humana (`confirmed`/`corrected`), valida qualidade, remove EXIF/identificadores e grava em `data/authorized_training/`, ignorada pelo Git. O exportador remove a pasta anterior antes de cada execução para reconciliar revogações. A chave de serviço é exclusiva do pipeline confiável: nunca deve ser colocada no APK, em arquivos versionados ou em logs. Backups manuais do banco mantidos pela equipe no Google Drive têm acesso restrito e retenção máxima de 30 dias; exportação SQL não inclui os arquivos de Storage.

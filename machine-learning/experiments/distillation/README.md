# Modelos leves e destilação — experimento isolado

Código, resultados e cache próprios. Não modifica treinamento integrado, dataset,
split, benchmarks anteriores ou assets Android. O professor DINOv2-B é somente
lido do checkpoint experimental existente; nenhum modelo é instalado no app.

## Reproduzir

Python 3.12, na pasta `machine-learning/`. Dataset original em `data/raw/`,
manifesto existente em `data/prepared/` e checkpoint do professor no caminho
registrado em `benchmark_artifacts/vision_finetune/dinov2_vitb14/experiment_config.json`.
As imagens não são enviadas a um serviço. Crie ambientes separados:

```bash
uv venv --python 3.12 experiments/distillation/.venv
uv pip install --python experiments/distillation/.venv/bin/python -r experiments/distillation/requirements.txt
experiments/distillation/.venv/bin/python -m experiments.distillation.benchmark --validate-only
experiments/distillation/.venv/bin/python -m pytest -q experiments/distillation
experiments/distillation/.venv/bin/python -m experiments.distillation.benchmark

uv venv --python 3.12 experiments/distillation/.cache/export-venv
uv pip install --python experiments/distillation/.cache/export-venv/bin/python --torch-backend cpu -r experiments/distillation/requirements-export.txt
CUDA_VISIBLE_DEVICES='' TF_ENABLE_ONEDNN_OPTS=0 TF_CPP_MIN_LOG_LEVEL=3 experiments/distillation/.cache/export-venv/bin/python -m experiments.distillation.export
experiments/distillation/.venv/bin/python -m experiments.distillation.report
experiments/distillation/.venv/bin/python -m pytest -q experiments/distillation
```

A execução de 2026-10-08 reutiliza **somente para leitura** os ambientes já
instalados em `experiments/vision/.venv` e `experiments/vision/.cache/export-venv`;
nenhum pacote ou saída antiga é modificado. Os comandos acima criam ambientes
próprios para uma reprodução independente.

`--models` permite continuar arquiteturas ainda não iniciadas. Execuções completas
são preservadas após conferir identidade/checkpoint. Uma execução parcial gera erro
em vez de sobrescrever resultados: preserve suas evidências antes de uma nova rodada.
O protocolo completo fica fixado antes do primeiro treino. Uma receita diferente
exige outro diretório de experimento, sem sobrescrever esta rodada.

## Receita

Cinco arquiteturas, cada uma supervisionada e destilada. Mesmo estado inicial,
probe logístico, seed e augmentations para cada par. Treino: batch 8, AdamW,
3 épocas de cabeça + até 15 de encoder, patience 5, BatchNorm congelada.
Professor DINOv2-B ajustado, selecionado pela validação histórica e verificado
por hash/replay; congelado. Perda: 0,5 CE balanceada + 0,5 T² KL(professor || aluno),
T=4. Alvos de treino cacheados por época/imagem; validação/teste fora da perda.

Selecionar checkpoint por Macro-F1 de validação, desempatar Top-1/Top-3 e época
anterior. Época zero também compete. Ajustar temperatura/limiar na validação,
persistir configuração e só depois inferir teste. O teste é conhecido de outras
rodadas; não é validação de campo.

## Saídas

- `benchmark_artifacts/distillation/`: protocolo, ambiente, hashes de isolamento,
  professor, probes, históricos, logits, métricas/calibração por classe, confusões,
  previsões e `comparison.csv`.
- `.cache/`: downloads, estado inicial, alvos do professor, checkpoints e TFLites,
  tudo ignorado pelo Git.
- [Relatório separado](../../../docs/benchmarks/BENCHMARK_DESTILACAO.md).

Todas as dez conversões são tentadas. Falhas ficam explícitas em `mobile_export.json`.
TFLite incorpora normalização, temperatura e softmax; preprocessing oficial de
resize/crop é externo e pode divergir do app. Paridade completa na validação,
Top-1 idêntico e erro máximo ≤0,0001. FastViT/EfficientViT são fundidos apenas
após restaurar o checkpoint selecionado, com paridade verificada.

Tempos em CPU/GPU desktop não comprovam o limite de três segundos no Galaxy A06.
O APK debug continua usando o ensemble de produção; não serve para testar os
alunos desta rodada.

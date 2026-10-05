# Benchmark de backbones pré-treinados

Experimento isolado: não altera `config.yaml`, o dataset, o split, o treinamento
original ou os assets Android. Os resultados e limitações estão em
[`docs/benchmarks/BENCHMARK_MODELOS.md`](../../../docs/benchmarks/BENCHMARK_MODELOS.md).

Use Python 3.12. Na pasta `machine-learning`, no Linux:

```bash
python3.12 -m venv experiments/vision/.venv
experiments/vision/.venv/bin/python -m pip install -r experiments/vision/requirements.txt
experiments/vision/.venv/bin/python -m experiments.vision.benchmark --validate-only
experiments/vision/.venv/bin/python -m experiments.vision.benchmark
experiments/vision/.venv/bin/python -m experiments.vision.report
experiments/vision/.venv/bin/python -m pytest -q tests experiments/dinov2/test_benchmark.py experiments/vision/test_vision_benchmark.py
```

No Windows, use `py -3.12` para criar o ambiente e
`experiments\vision\.venv\Scripts\python.exe` nos comandos seguintes.
O dataset bruto deve estar em `data/raw/`, compatível com os hashes do manifesto
existente. Os checkpoints são baixados uma vez para `.cache/`, ignorado pelo Git;
as imagens não são enviadas a nenhum serviço.

Os candidatos são DINOv3 ViT-S/16, MobileNetV4 Conv Medium, TinyViT-5M/11M,
MobileCLIP2-S0 (somente encoder visual) e DINOv2 ViT-B/14. Para executar um
subconjunto, use `--models tinyvit_5m dinov2_vitb14`.

Os pesos DINOv3 exigem acesso aprovado no Hugging Face. Sem acesso, o runner
registra `access_restricted` e segue com os outros candidatos. Também aceita
`--dinov3-directory /caminho/snapshot`, contendo `model.safetensors`,
`config.json` e `preprocessor_config.json`. Não coloque tokens no código, nos
argumentos ou nos artefatos.

## Protocolo

- Verifica SHA256 de todas as imagens, grupos, ordem das classes e split fixo
  489/104/103; não recria o manifesto.
- Usa float32, batch 8, seed 42, backbones congelados, sem augmentation e sem
  padronização adicional das características. Não executa fine-tuning.
- Compara apenas dois probes logísticos predefinidos: `C=1`, com e sem pesos
  balanceados, LBFGS, até 1000 iterações. Falha se o classificador não convergir.
- Escolhe por Macro-F1 de validação; desempata por Top-1, Top-3 e probe sem pesos.
- Ajusta temperatura pela NLL da validação e escolhe o limiar para maximizar
  cobertura atingindo 90% de acerto na validação. Essa meta não garante 90% no teste.
- Salva a configuração antes de decodificar as imagens de teste para inferência.
  A auditoria inicial lê seus arquivos apenas para verificar hashes. O teste
  permanece uma comparação interna histórica, já conhecida por experimentos anteriores.
- Respeita o preprocessing do checkpoint: MobileCLIP2-S0 usa RGB 0–1, média 0,
  desvio 1 e 256×256; os demais executados usam 224×224. A reparametrização
  MobileCLIP é verificada contra o encoder original em entrada sintética.
- DINOv2-B concatena CLS e média dos patches via timm, com adaptação determinística
  das posições para 224×224. O experimento DINOv2-S antigo usa a implementação Meta;
  são configurações distintas, mesmo com a mesma estratégia de características.

Cada candidato executado registra métricas, calibração, confusões, previsões,
logits e o probe NumPy em `benchmark_artifacts/vision/<modelo>/`. As revisões dos
checkpoints são fixas e seus hashes, configurações e versões ficam registrados.
`historical_summary.json` recupera medições do commit `24389c3`; não são novas execuções.
`report.py` gera `comparison.csv` combinando esses dados com os artefatos atuais e
ordenando exclusivamente por Macro-F1 de validação. Campos vazios significam
ausência de medição, não zero.

Tempo mediano/P95: somente encoder, batch 1, cinco aquecimentos e 30 medições,
CUDA sincronizada; exclui decode, preprocessing, transferências e probe. Memória
é o pico alocado pelo PyTorch em batch 1, incluindo o modelo residente, não a RAM
total do processo. Não houve exportação ou avaliação Android.

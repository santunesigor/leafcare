# Benchmark e ajuste de backbones pré-treinados

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

## Protocolo dos probes congelados

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
total do processo. Essa rodada congelada não foi exportada ou avaliada no Android.

## Ajuste supervisionado e exportação experimental

Com os cinco probes públicos já registrados, execute no ambiente de treino:

```bash
experiments/vision/.venv/bin/python -m experiments.vision.finetune
```

O runner mantém o mesmo dataset/split, inicializa a cabeça a partir dos probes,
ajusta somente com imagens de treino e seleciona épocas pela validação. A receita
predefinida está em `finetune.py`: AdamW, batch 8, seed 42, cabeça por três épocas
e até 15 épocas de ajuste, parada após cinco sem melhora. DINOv2 libera os dois
últimos blocos/norm; os demais liberam todo o encoder. Uma época de ajuste apenas
da cabeça pode vencer. Pesos ficam em `.cache/finetune/`; métricas, logits e
histórico completo em `benchmark_artifacts/vision_finetune/`.

Use um ambiente separado para a conversão [LiteRT Torch](https://github.com/google-ai-edge/litert-torch):

```bash
uv venv --python 3.12 experiments/vision/.cache/export-venv
uv pip install --python experiments/vision/.cache/export-venv/bin/python --torch-backend cpu -r experiments/vision/requirements-export.txt
CUDA_VISIBLE_DEVICES='' TF_ENABLE_ONEDNN_OPTS=0 TF_CPP_MIN_LOG_LEVEL=3 experiments/vision/.cache/export-venv/bin/python -m experiments.vision.export_candidates
experiments/vision/.venv/bin/python -m experiments.vision.report
experiments/vision/.venv/bin/python -m pytest -q tests experiments/dinov2/test_benchmark.py experiments/vision
```

Todos os ajustes predeclarados devem terminar antes de selecionar os dois
finalistas pela validação. Para repetir uma conversão, use `--models tinyvit_11m`;
somente finalistas são aceitos. TFLites ficam em `.cache/mobile/`, sem substituir
assets do app. Os contratos, hashes, versões e resultados ficam nos artefatos.
Entrada: float32 NHWC RGB 0–255; saída: probabilidades calibradas nas 16 classes.
Normalização e temperatura ficam no grafo; resize/crop/bicubic oficiais ainda
precisam ocorrer antes da inferência, conforme cada `preprocessing` registrado.
Não basta copiar o arquivo para o app para reproduzir estas métricas.

A conversão só passa com concordância Top-1 de 100% nas 104 imagens de validação
e erro absoluto máximo de probabilidades ≤0,0001 contra PyTorch e logits do
treino. Tempos LiteRT usam CPU desktop, duas threads, cinco aquecimentos/30
amostras; a medição Python de pipeline inclui decode e preprocessing, mas não
Room/UI. Essas medições não comprovam o limite de três segundos no Android.

A referência escolhida é Samsung Galaxy A06. Para medir o fluxo completo,
conecte esse aparelho com depuração USB e confirme sua presença em `adb devices
-l`. A medição em dispositivo continua pendente; o app já possui carregamento
animado durante a análise. Não houve alteração de UI ou do Supabase.

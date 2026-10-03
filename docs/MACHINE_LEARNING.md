# Machine Learning — LeafCare

Este documento reúne o dataset, o contrato do modelo atual, suas métricas e os comandos do pipeline.

Resultados comparativos e experimentais estão centralizados em [Benchmark de Modelos](benchmarks/BENCHMARK_MODELOS.md).

## Modelo integrado

Arquitetura atual:

```text
MobileNetV3Small
```

Treinamento baseado em transfer learning e fine-tuning.

Configuração principal em `machine-learning/config.yaml`:

```yaml
seed: 42
image_size: 224
batch_size: 16
epochs_frozen: 25
epochs_finetune: 20
fine_tune_last_layers: 30
learning_rate_frozen: 0.001
learning_rate_finetune: 0.00001
confidence_threshold: 0.70
```

## Classes

A ordem de classes é parte do contrato e não pode ser alterada sem reexportar todo o bundle:

```text
anthracnose
black_shank
brown_spot
cmv
frog_eye
genetic_abnormality
healthy
nematodes
potato_tuber_moth
pvy
sunscald
target_spot
tmv
tswv
weather_fleck
wildfire
```

## Dataset e split

O experimento registrado utiliza 696 imagens distribuídas em 16 classes.

Split configurado:

- treino: 70%;
- validação: 15%;
- teste: 15%;
- seed: 42;
- separação orientada por grupos para reduzir vazamento entre imagens relacionadas.

Os datasets completos não são redistribuídos neste repositório.

As imagens para o treinamento estão nesta [pasta do Google Drive](https://drive.google.com/drive/folders/1jMaqmAc-BQj3aDC6c50RuNQIAPneVd2A). Baixe a pasta `raw` e coloque-a em `machine-learning/data/raw/`.

As origens e licenças das imagens estão em `docs/legal/referencias_manifest.csv`.

## Contrato de entrada

| Campo | Valor |
|---|---|
| Shape | `[1,224,224,3]` |
| Dtype | `float32` |
| Cor | RGB |
| Faixa | `0–255` |
| Resize | center crop + bilinear |
| Normalização | embutida na MobileNetV3 |
| Saída | 16 scores float32 |

## Métricas do modelo integrado

Dados registrados em `machine-learning/artifacts/metrics.json`:

| Métrica | Valor |
|---|---:|
| Imagens de teste | 103 |
| Accuracy Top-1 | 0,7767 |
| Macro-F1 | 0,7157 |
| Accuracy Top-3 | 0,9709 |
| Threshold | 0,70 |
| Cobertura | 0,7670 |
| Accuracy nos resultados aceitos | 0,8987 |

### Atenção às classes raras

Algumas classes possuem apenas 1 ou 2 exemplos no conjunto de teste. Métricas individuais dessas classes têm alta variância e não devem ser interpretadas como estimativas estáveis de desempenho real.

## Conversão TFLite

A exportação registrada validou 20 imagens do conjunto de validação.

Resultado:

- concordância Top-1 Keras/TFLite: `1.0`;
- maior erro absoluto observado: `7.718801498413086e-06`.

Esse teste mede paridade de implementação/conversão; não mede generalização em campo.

## Seleção do modelo

Durante o desenvolvimento, foram comparadas arquiteturas e configurações na validação. O aplicativo usa o MobileNetV3Small descrito nesta documentação; as métricas apresentadas são do modelo integrado.

## Pipeline

### Instalação

```bash
cd machine-learning
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

No Windows, ative com:

```powershell
.\.venv\Scripts\Activate.ps1
```

### Testes

```bash
python -m pytest -q
```

### Preparação

```bash
python prepare_dataset.py --config config.yaml
```

Somente auditoria:

```bash
python prepare_dataset.py --config config.yaml --audit-only
```

A preparação gera manifestos e dados de auditoria em `data/prepared/`.

### Treinamento

```bash
python train.py --config config.yaml
```

### Avaliação

```bash
python evaluate.py --config config.yaml
```

### Exportação

```bash
python export_tflite.py --config config.yaml
```

O export copia os assets finais necessários para:

```text
android-app/app/src/main/assets/
```

### Validação do bundle

```bash
python validate_bundle.py --require-model
```

### Predição isolada

```bash
python predict.py imagem.jpg
```

Com backend Keras:

```bash
python predict.py imagem.jpg --backend keras
```

## Artefatos principais

```text
machine-learning/artifacts/
├── leafcare.tflite
├── model_metadata.json
├── classes.json
├── diseases.json
├── metrics.json
├── confusion_matrix.json
├── confusion_matrix.png
├── history.json
├── accuracy.png
├── loss.png
└── conversion_parity.json
```

## Reprodutibilidade

Ao alterar o modelo:

1. mantenha o seed registrado;
2. preserve o conjunto de teste para avaliação final;
3. não escolha arquitetura usando o conjunto de teste;
4. atualize `model_metadata.json`;
5. reexporte o TFLite;
6. rode `validate_bundle.py`;
7. rode `verifyModelAssets` no Android;
8. confira a equivalência de preprocessing Python ↔ Kotlin.

## Limitações científicas

- poucas imagens para algumas classes;
- possível diferença entre imagens acadêmicas e condições reais de campo;
- ausência de validação externa suficiente no Brasil;
- threshold do modelo integrado não calibrado;
- confiança softmax não representa certeza agronômica;
- conjunto fechado de 16 classes;
- necessidade de revisão de rótulos, sintomas e recomendações por especialista.

# Machine Learning — LeafCare

Este documento reúne o dataset, o contrato do modelo atual, suas métricas e os comandos do pipeline.

Resultados comparativos e experimentais estão centralizados em [Benchmark de Modelos](benchmarks/BENCHMARK_MODELOS.md).

Para a explicação da preparação dos dados, da sequência de experimentos, da escolha do ensemble e dos próximos passos, veja a [Jornada do Machine Learning](explain/JORNADA_MACHINE_LEARNING.md).

## Modelo integrado

Arquitetura atual: **ensemble de dois MobileNetV3Small e um MobileNetV3Large**,
retreinados a partir dos pesos ImageNet. Cada membro produz 16 probabilidades;
o grafo calcula a média e aplica `softmax(log(clip(média, 1e-7, 1)) / temperatura)`.
As três redes e a calibração estão no mesmo `leafcare.tflite`. O Android executa
um único Interpreter e mantém classificação offline, Top-3 e histórico local.

A receita está em `machine-learning/train_ensemble.py`, fixada antes da avaliação:

| Membro | Seed | Otimizador | LR inicial | Dropout | Camadas finais liberadas | Épocas máximas: cabeça / ajuste |
|---|---:|---|---:|---:|---:|---:|
| Small Adam | 42 | Adam | 0,001 | 0,25 | 30 | 25 / 20 |
| Small RMSprop | 54 | RMSprop, momentum 0,9 | 0,0007 | 0,25 | 30 | 12 / 12 |
| Large AdamW | 44 | AdamW, weight decay 0,00001 | 0,0007 | 0,30 | 40 | 12 / 12 |

Batch 16; LR de ajuste 0,00001; BatchNorm congelada; pesos de classe calculados
somente no treino. Augmentation padrão do pipeline existente: flips, rotação,
zoom e contraste. Cada membro escolhe o checkpoint de menor perda de validação,
com preferência pela fase congelada em empate. Temperatura minimiza NLL na
validação; limiar maximiza cobertura com pelo menos 90% de acerto nessa partição,
ou prioriza acerto/cobertura se a meta não for alcançada. O limiar é definido a
partir das saídas TFLite, no intervalo entre a última previsão rejeitada e a
primeira aceita. Tudo é congelado antes da inferência de teste.

A composição veio do ensemble vencedor da validação histórica. Esta é uma
**nova execução**, com augmentation padrão atual; suas métricas não devem ser
substituídas pelos números do benchmark antigo. Configuração, hashes, históricos,
versões e previsões estão em `benchmark_artifacts/ensemble/`.

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
| Normalização | rescaling embutido em cada MobileNetV3 |
| Saída | 16 probabilidades float32, média e temperatura embutidas |

## Métricas do modelo integrado

Dados desta nova execução, medidos no TFLite final e registrados em `machine-learning/artifacts/metrics.json`. O ensemble acertou 84/103 imagens, contra 80/103 do MobileNetV3Small anterior. Validação: Top-1 87,50%, Macro-F1 0,8179 e Top-3 99,04%.

| Métrica | Valor |
|---|---:|
| Imagens de teste | 103 |
| Accuracy Top-1 | 0,8155 |
| Macro-F1 | 0,7371 |
| Accuracy Top-3 | 0,9903 |
| Threshold | 0,631628 |
| Temperatura | 0,835105 |
| Cobertura | 0,7961 |
| Accuracy nos resultados aceitos | 0,9268 |

### Atenção às classes raras

Algumas classes possuem apenas 1 ou 2 exemplos no conjunto de teste. Métricas individuais dessas classes têm alta variância e não devem ser interpretadas como estimativas estáveis de desempenho real.

## Conversão e seleção

A exportação usa somente operadores TFLite built-in, float32. Confere todas as
104 imagens de validação: Top-1 idêntico entre Keras/TFLite e erro absoluto máximo
≤0,0001. Também compara o grafo com a fórmula NumPy de média/calibração.
Os assets só são substituídos após passar nessa verificação.

Paridade mede implementação, não generalização de campo. A composição foi fixada
pela validação histórica; nesta rodada não há busca de membros nem seleção pelo
teste. O teste de 103 imagens já era conhecido e continua sendo uma comparação
interna. A referência MobileNetV3Small anterior foi preservada em
`benchmark_artifacts/ensemble/baseline_reference.json`.

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
python train_ensemble.py --train-only
```

### Avaliação

A avaliação do novo ensemble ocorre em `train_ensemble.py --export-only`, depois de congelar a seleção e verificar a conversão. O pipeline antigo fica em `legacy/`; a avaliação de um único MobileNetV3Small pode ser chamada com `python -m legacy.evaluate`, dentro de `machine-learning/`.

### Exportação

```bash
python train_ensemble.py --export-only
```

A exportação instala o ensemble em `artifacts/` e copia o TFLite/metadados para:

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
├── training_metadata.json
└── model.keras               # local, ignorado pelo Git

docs/model-reports/ensemble/
├── config_used.json
├── history.json
├── accuracy.png
├── loss.png
├── confusion_matrix.json
├── confusion_matrix.png
├── test_predictions.json
└── conversion_parity.json
```

O [guia da pasta ML](../machine-learning/README.md) separa o pipeline atual, o legado e os experimentos. `benchmark_artifacts/` conserva todos os registros dos benchmarks nos caminhos existentes. A exportação do ensemble passa a escrever seus relatórios em `docs/model-reports/ensemble/`; a receita, os dados e o bundle não mudaram.

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
- temperatura e threshold ajustados somente na validação pequena, sem garantia de calibração em campo;
- confiança softmax não representa certeza agronômica;
- conjunto fechado de 16 classes;
- necessidade de revisão de rótulos, sintomas e recomendações por especialista.

## Teste no Android

O APK 1.1.0 usa o ensemble como padrão. A animação de carregamento existente
acompanha a análise; a sincronização continua em segundo plano. Cada registro
salva o hash do novo TFLite e o limiar do bundle; análises antigas preservam seu
hash/limiar original. Nenhum schema Room/Supabase foi alterado.

A referência é Samsung Galaxy A06, com meta de até três segundos do início da
análise até o resultado disponível, incluindo decode, preprocessing, inferência,
Room e UI. A medição no aparelho continua pendente de conexão física; os tempos
de CPU desktop não comprovam essa meta. A integração foi solicitada pelo usuário
para teste no app, sem validação externa de campo.

O TFLite integrado tem 17,22 MiB e 4,908,432 parâmetros. A maior diferença Keras/TFLite foi 6.795e-06, com Top-1 idêntico nas 104 imagens. Em CPU desktop Intel Core i5-13400, duas threads, cinco aquecimentos e 30 amostras: inferência mediana/P95 4,42/4,79 ms; pipeline Python 9,68/10,46 ms, sem Room/UI. Esses tempos não são do A06.

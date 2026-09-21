# LeafCare — Relatório comparativo dos modelos de classificação

**Data do experimento:** 11 de setembro de 2026  
**Objetivo:** comparar arquiteturas e configurações de redes neurais compatíveis com execução offline em Android para classificar alterações visuais em folhas de fumo.  
**Decisão atual:** manter temporariamente no aplicativo o modelo MobileNetV3Small anterior, já integrado e testado, e considerar como evolução o ensemble de três modelos selecionado pelo benchmark após validação em aparelhos reais.

---

## 1. Resumo executivo

O LeafCare precisa equilibrar dois objetivos que competem entre si: obter a melhor classificação possível e continuar funcionando offline em celulares. Por isso, o experimento não procurou simplesmente a maior rede. Foram comparadas seis famílias adequadas ou plausíveis para uso móvel — MobileNetV3Small, MobileNetV3Large, MobileNetV2, EfficientNetB0, EfficientNetV2B0 e NASNetMobile — além de variações de aumento de dados, dropout, otimizador, balanceamento e profundidade de fine-tuning.

Foram treinadas **14 novas configurações**. O modelo MobileNetV3Small anterior foi incluído posteriormente como o 15º candidato individual. Entre os seis melhores candidatos de validação, foram testadas todas as combinações de dois e três modelos, resultando em **50 estratégias de inferência**: 15 modelos individuais e 35 ensembles.

O melhor modelo novo isolado segundo o critério principal, macro-F1 de validação, foi o **MobileNetV3Small com dropout 0,15**, com macro-F1 de 0,7785. Entretanto, a melhor estratégia global foi o ensemble composto por:

1. MobileNetV3Small anterior;
2. MobileNetV3Small treinado com RMSprop;
3. MobileNetV3Large base.

Esse ensemble alcançou na validação:

- acurácia top 1: **87,50%**;
- macro-F1: **0,8179**;
- acurácia top 3: **100%**;
- NLL: **0,4328**.

Somente depois da escolha, o conjunto de teste foi utilizado. No teste final de 103 imagens, o ensemble alcançou **80,58% de acurácia**, **0,7659 de macro-F1** e **99,03% de top 3**, superando o modelo anterior, que obteve 77,67%, 0,7157 e 97,09%, respectivamente.

Apesar dessa melhora, o aplicativo permaneceu temporariamente com o modelo antigo porque ele já estava integrado, compilado e validado no fluxo Android. O ensemble exige três arquivos TFLite, aproximadamente 19,5 MB no total, três inferências por fotografia e uma nova implementação de média e calibração. Substituí-lo sem medir latência, memória, aquecimento e estabilidade em celulares reais criaria risco de regressão funcional. Portanto, a manutenção do modelo antigo é uma decisão de engenharia e controle de versão, não a afirmação de que ele é o modelo mais preciso.

---

## 2. Pergunta experimental e critérios de decisão

A pergunta do benchmark foi:

> Qual configuração oferece o melhor equilíbrio entre desempenho por classe, acerto geral e viabilidade de implantação offline, sem utilizar o teste para escolher o vencedor?

O critério principal foi **macro-F1 de validação**, pois ele calcula o F1 de cada classe e atribui o mesmo peso a todas. Isso é especialmente importante porque o conjunto é desbalanceado: existem 89 imagens de `wildfire`, mas apenas 8 de `anthracnose` e 9 de `tswv`.

A acurácia foi mantida como métrica secundária. Ela responde quantas imagens foram classificadas corretamente no total, mas pode esconder desempenho ruim nas classes menores. O top 3 também foi avaliado porque o LeafCare mostra três hipóteses ao produtor. A NLL, ou perda logarítmica negativa, foi utilizada para observar a qualidade das probabilidades: previsões erradas com confiança extrema são mais penalizadas.

### 2.1 Confiança não é acurácia

- **Acurácia** é uma medida coletiva: proporção de exemplos corretamente classificados em um conjunto conhecido.
- **Confiança** é o valor produzido para uma imagem específica, normalmente o maior valor da saída softmax.
- Uma previsão com 95% de confiança não significa automaticamente que o sistema acerta 95% das vezes em situações semelhantes.
- Para aproximar confiança de frequência real de acerto, é necessário avaliar calibração em dados independentes.

Por isso, o relatório não propõe simplesmente “aumentar a confiança”. Tornar a softmax numericamente mais alta pode deixar o modelo mais convincente e menos seguro. O objetivo correto é aumentar a **confiabilidade**, isto é, elevar a acurácia real e calibrar a confiança para que ela represente melhor o risco.

---

## 3. Dados utilizados e prevenção de vazamento

O treinamento efetivamente executado utilizou **696 imagens brutas da seção TV3 do TLA**, distribuídas em 16 classes. O TTDD foi auditado e forneceu a taxonomia. As imagens TV6 e as versões processadas ou sintéticas do TTDD foram excluídas para evitar que derivações da mesma imagem aparecessem em subconjuntos diferentes. O pacote público CC BY, com 43 imagens, foi mantido apenas como referência visual e não foi tratado como conjunto suficiente para treinamento.

| Subconjunto | Imagens | Proporção aproximada | Uso |
|---|---:|---:|---|
| Treino | 489 | 70% | Ajuste dos pesos |
| Validação | 104 | 15% | Escolha de configuração, ensemble, temperatura e limiar |
| Teste | 103 | 15% | Avaliação final após a escolha |

A auditoria encontrou zero arquivos inválidos, zero duplicatas exatas e zero conflitos de rótulo. A divisão foi estratificada por grupos, com seed 42. Arquivos associados à mesma origem detectada não podiam ser divididos entre treino, validação e teste.

Trecho real de `leafcare/dataset.py` que impede o mesmo grupo ou os mesmos pixels de atravessarem subconjuntos:

```python
groups, hashes = {}, {}
for row in manifest["rows"]:
    for mapping, key in [(groups, row["group_id"]),
                         (hashes, row["pixel_sha256"])]:
        if key in mapping and mapping[key] != row["split"]:
            raise ValueError("Vazamento entre os subconjuntos.")
        mapping[key] = row["split"]
```

Essa proteção é mais importante que simplesmente fazer uma divisão aleatória de arquivos. Se duas versões da mesma fotografia fossem separadas, o modelo poderia memorizar a imagem no treino e aparentar generalização no teste.

Ainda existe uma limitação: os dados não trazem, para todos os casos, identificadores confiáveis de propriedade, planta ou sessão fotográfica. O agrupamento por nome, hash e similaridade reduz o risco, mas não comprova independência biológica ou geográfica.

---

## 4. Como os modelos foram criados

### 4.1 Transfer learning e cabeça de classificação

Todas as arquiteturas utilizaram pesos ImageNet como ponto de partida. O topo original da rede foi removido, foi aplicado pooling global, dropout e uma camada final softmax de 16 saídas.

Trecho real de `benchmark.py`:

```python
app = getattr(tf.keras.applications, s["arch"])
kw = dict(
    input_shape=(size, size, 3),
    include_top=False,
    weights="imagenet",
    pooling="avg"
)
base = app(**kw)
base.trainable = False

inp = tf.keras.Input((size, size, 3), name="rgb_0_255")
x = base(inp, training=False)
x = tf.keras.layers.Dropout(s["drop"])(x)
out = tf.keras.layers.Dense(
    n, activation="softmax", name="probabilities"
)(x)
model = tf.keras.Model(inp, out)
```

O transfer learning foi escolhido porque 696 imagens são poucas para treinar uma CNN profunda do zero. Os pesos ImageNet já contêm detectores de bordas, texturas, formas e padrões de cor. O treinamento do LeafCare adapta essas representações para lesões e alterações em folhas.

### 4.2 Contrato de entrada compatível com Android

A entrada foi mantida em RGB, `float32`, 224 × 224, na faixa 0–255. Nas arquiteturas MobileNetV3 e EfficientNetV2, o pré-processamento é incorporado ao modelo. Para MobileNetV2 e NASNetMobile, foi adicionada uma camada equivalente:

```python
if s["arch"] in {"MobileNetV2", "NASNetMobile"}:
    x = tf.keras.layers.Rescaling(
        1 / 127.5, offset=-1,
        name="embedded_rescaling"
    )(x)
```

Assim, o Android não precisa conhecer regras diferentes para cada arquitetura. Ele envia o mesmo tensor 0–255 para todos os membros. Essa decisão reduz a chance de um modelo funcionar em Python e falhar no celular por dupla normalização ou canais invertidos.

### 4.3 Aumento de dados

O augmentation foi aplicado somente ao treino. A versão padrão utilizou espelhamento, rotação, zoom e contraste; a versão forte acrescentou translação e brilho.

```python
layers = [
    tf.keras.layers.RandomFlip("horizontal_and_vertical", seed=43),
    tf.keras.layers.RandomRotation(.08, fill_mode="reflect", seed=44),
    tf.keras.layers.RandomZoom(.1, fill_mode="reflect", seed=45),
    tf.keras.layers.RandomContrast(.12, seed=46),
]
if aug == "strong":
    layers += [
        tf.keras.layers.RandomTranslation(
            .08, .08, fill_mode="reflect", seed=47
        ),
        tf.keras.layers.RandomBrightness(
            .12, value_range=(0., 255.), seed=48
        ),
    ]
```

O objetivo é ensinar invariância a posição, enquadramento e iluminação. Entretanto, augmentation mais forte não foi automaticamente melhor. Nas três famílias testadas em pares, a variante forte apresentou macro-F1 inferior à base. Isso indica que, neste conjunto pequeno, algumas transformações podem ter afastado as imagens demais da distribuição real ou dificultado excessivamente o aprendizado.

### 4.4 Balanceamento por pesos de classe

Na maioria dos candidatos, erros nas classes raras receberam peso maior:

```python
counts = Counter(
    r["class_id"] for r in manifest["rows"]
    if r["split"] == "train"
)
total = sum(counts.values())
class_weight = {
    i: total / (len(counts) * counts[class_id])
    for i, class_id in enumerate(manifest["classes"])
}
```

Uma configuração sem pesos foi incluída propositalmente. Ela atingiu a maior acurácia individual de validação, 86,54%, mas seu macro-F1 foi 0,7671. O resultado mostra por que acurácia isolada não deve decidir o modelo: reduzir a compensação das classes pequenas ajudou o total de acertos, mas não produziu o melhor equilíbrio entre classes.

### 4.5 Treinamento em duas etapas

Na primeira etapa, o backbone ficou congelado e somente a nova cabeça foi treinada. Na segunda, as últimas camadas foram liberadas, com Batch Normalization congelada e taxa de aprendizado reduzida para `1e-5`.

```python
model, base = build(s, len(classes), image_size)
compile_model(model, s["lr"], s)
model.fit(train, validation_data=val, epochs=12, class_weight=used)

base.trainable = True
start = len(base.layers) - s["ft"]
for i, layer in enumerate(base.layers):
    layer.trainable = (
        i >= start and
        not isinstance(layer, tf.keras.layers.BatchNormalization)
    )

compile_model(model, 1e-5, s)
model.fit(train, validation_data=val, epochs=12, class_weight=used)
```

Congelar Batch Normalization evita alterar agressivamente estatísticas internas aprendidas com ImageNet usando lotes pequenos. O fine-tuning com taxa baixa permite especialização sem destruir rapidamente as representações pré-treinadas.

### 4.6 Controle de treinamento

Cada etapa utilizou checkpoint, early stopping, redução automática da taxa de aprendizado e encerramento em caso de valores inválidos:

```python
return [
    tf.keras.callbacks.ModelCheckpoint(
        path, monitor="val_loss",
        save_best_only=True, save_weights_only=True
    ),
    tf.keras.callbacks.EarlyStopping(
        monitor="val_loss", patience=3,
        restore_best_weights=True
    ),
    tf.keras.callbacks.ReduceLROnPlateau(
        monitor="val_loss", patience=2,
        factor=.35, min_lr=1e-7
    ),
    tf.keras.callbacks.TerminateOnNaN(),
]
```

Após as duas fases, o código compara a menor perda de validação de cada etapa e mantém os pesos da melhor. Nos 14 candidatos apresentados, a fase de fine-tuning foi escolhida.

### 4.7 Otimizadores comparados

```python
if s.get("optimizer") == "adam":
    optimizer = tf.keras.optimizers.Adam(lr)
elif s.get("optimizer") == "rmsprop":
    optimizer = tf.keras.optimizers.RMSprop(lr, momentum=.9)
else:
    optimizer = tf.keras.optimizers.AdamW(
        lr, weight_decay=1e-5
    )
```

- **AdamW:** opção padrão do benchmark, com decaimento de pesos desacoplado.
- **Adam:** referência direta e próximo do procedimento do modelo anterior.
- **RMSprop:** alternativa que teve desempenho complementar suficiente para participar do ensemble final.

### 4.8 Reprodutibilidade e seeds

O benchmark usa seed fixa derivada da seed principal e da posição do candidato:

```python
tf.keras.utils.set_random_seed(
    config["seed"] + CANDIDATES.index(candidate)
)
tf.config.experimental.enable_op_determinism()
```

Isso torna cada execução individual reproduzível e explora mais de uma inicialização. Porém, significa que diferenças entre modelos também podem conter efeito da seed. Para uma conclusão científica mais forte, cada configuração deve ser repetida com as mesmas várias seeds e comparada pela média e desvio-padrão.

---

## 5. Configurações avaliadas

| ID | Arquitetura | Dropout | Augmentation | Camadas liberadas | Otimizador | Pesos de classe |
|---|---|---:|---|---:|---|---|
| `m3s_base` | MobileNetV3Small | 0,25 | Padrão | 30 | AdamW | Sim |
| `m3s_strong` | MobileNetV3Small | 0,35 | Forte | 45 | AdamW | Sim |
| `m3l_base` | MobileNetV3Large | 0,30 | Padrão | 40 | AdamW | Sim |
| `m3l_strong` | MobileNetV3Large | 0,40 | Forte | 60 | AdamW | Sim |
| `effb0_base` | EfficientNetB0 | 0,30 | Padrão | 40 | AdamW | Sim |
| `effb0_strong` | EfficientNetB0 | 0,40 | Forte | 60 | AdamW | Sim |
| `effv2b0_base` | EfficientNetV2B0 | 0,30 | Padrão | 40 | AdamW | Sim |
| `effv2b0_strong` | EfficientNetV2B0 | 0,40 | Forte | 60 | AdamW | Sim |
| `m2_base` | MobileNetV2 | 0,30 | Padrão | 40 | AdamW | Sim |
| `nasnet_base` | NASNetMobile | 0,35 | Padrão | 50 | AdamW | Sim |
| `m3s_adam` | MobileNetV3Small | 0,25 | Padrão | 30 | Adam | Sim |
| `m3s_no_weights` | MobileNetV3Small | 0,25 | Padrão | 30 | AdamW | Não |
| `m3s_rmsprop` | MobileNetV3Small | 0,25 | Padrão | 30 | RMSprop | Sim |
| `m3s_low_dropout` | MobileNetV3Small | 0,15 | Padrão | 45 | AdamW | Sim |

O modelo anterior também é MobileNetV3Small com dropout 0,25, Adam, pesos de classe e fine-tuning das 30 últimas camadas. A diferença é que seu treinamento permitiu até 25 épocas congeladas e 20 de fine-tuning, com paciência 6; ele executou as 45 épocas. Os candidatos do benchmark utilizaram até 12 + 12 épocas e paciência 3. Assim, `m3s_adam` é semelhante, mas não é uma cópia exata do modelo anterior.

---

## 6. Comparação quantitativa dos modelos isolados

As métricas desta tabela pertencem à **validação**, não ao teste. O ranking foi ordenado por macro-F1.

| Pos. | Modelo | Acurácia | Macro-F1 | Top 3 | Confiança média | Parâmetros | Keras | Tempo de treino |
|---:|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | `m3s_low_dropout` | 82,69% | **0,7785** | 99,04% | 72,54% | 0,95 M | 10,13 MB | 3,48 min |
| — | `baseline_anterior` | 82,69% | 0,7750 | **100%** | — | 0,95 M | 8,96 MB | experimento anterior |
| 2 | `m3s_rmsprop` | 82,69% | 0,7677 | 99,04% | 89,27% | 0,95 M | 8,96 MB | 2,88 min |
| 3 | `m3s_no_weights` | **86,54%** | 0,7671 | 97,12% | 76,80% | 0,95 M | 8,96 MB | 3,96 min |
| 4 | `m3s_adam` | 81,73% | 0,7652 | 98,08% | 74,08% | 0,95 M | 8,96 MB | 4,31 min |
| 5 | `m3l_base` | 85,58% | 0,7592 | 99,04% | 79,61% | 3,01 M | 28,51 MB | 3,99 min |
| 6 | `m3s_base` | 79,81% | 0,7492 | **100%** | 74,91% | 0,95 M | 8,96 MB | 2,91 min |
| 7 | `m2_base` | 82,69% | 0,7462 | 98,08% | 82,55% | 2,28 M | 23,14 MB | 5,56 min |
| 8 | `effb0_strong` | 82,69% | 0,7253 | 98,08% | 63,30% | 4,07 M | 38,44 MB | 6,99 min |
| 9 | `m3s_strong` | 75,96% | 0,7228 | 98,08% | 68,08% | 0,95 M | 10,13 MB | 3,16 min |
| 10 | `m3l_strong` | 77,88% | 0,7189 | 98,08% | 74,04% | 3,01 M | 32,57 MB | 3,94 min |
| 11 | `effb0_base` | 79,81% | 0,7130 | 97,12% | 67,79% | 4,07 M | 33,54 MB | 6,41 min |
| 12 | `effv2b0_base` | 76,92% | 0,6806 | 94,23% | 66,83% | 5,94 M | 38,04 MB | 5,48 min |
| 13 | `nasnet_base` | 68,27% | 0,6515 | 92,31% | 54,67% | 4,29 M | 25,51 MB | 6,09 min |
| 14 | `effv2b0_strong` | 71,15% | 0,6451 | 92,31% | 59,49% | 5,94 M | 43,01 MB | 6,73 min |

O tamanho Keras não equivale ao tamanho final do TFLite, mas ajuda a comparar custo relativo. O tempo foi medido no ambiente de treinamento e não representa tempo em celular.

---

## 7. Análise detalhada de cada modelo

### 7.1 Modelo anterior — MobileNetV3Small

**Papel:** baseline real e modelo atualmente integrado ao Android.

Na validação usada pelo benchmark, obteve 82,69% de acurácia, macro-F1 0,7750, top 3 de 100% e NLL 0,5280. No teste, obteve 77,67% de acurácia, macro-F1 0,7157 e top 3 de 97,09%.

**Pontos favoráveis:** é pequeno, rápido, possui pipeline Python/TFLite comprovado, hashes verificados, pré-processamento equivalente no Kotlin e funcionamento já compilado no aplicativo. É também um componente útil do ensemble, sinal de que seus erros não são totalmente iguais aos dos outros modelos.

**Limitações:** o limiar 0,70 original não foi calibrado; o teste apresentou zero acerto para `anthracnose` e `tswv`, cada uma com apenas uma amostra. Seu macro-F1 de teste mostra desequilíbrio maior do que a acurácia sugere.

**Conclusão:** continua sendo o melhor modelo operacional no estado atual do app, mas não é a melhor estratégia experimental disponível.

### 7.2 `m3s_low_dropout` — MobileNetV3Small com dropout 0,15

Foi o melhor novo modelo isolado em macro-F1 de validação: 0,7785. O dropout menor permite que mais informação chegue à camada final, enquanto o fine-tuning de 45 camadas oferece adaptação adicional ao domínio.

**Pontos favoráveis:** melhor equilíbrio individual entre classes, somente 0,95 milhão de parâmetros e arquitetura adequada para dispositivo móvel. É a escolha mais defensável caso seja obrigatório selecionar **um único novo modelo** apenas pelos resultados de validação.

**Limitações:** não obteve a maior acurácia; sua vantagem de macro-F1 sobre o modelo anterior na validação foi pequena, aproximadamente 0,0035. Ele não foi aberto isoladamente no teste, pois isso violaria a regra de reservar o teste para a estratégia final. Também não foi um dos três membros do ensemble vencedor.

**Conclusão:** melhor candidato novo individual, mas não há evidência suficiente para afirmar que substituiria o modelo anterior no teste ou no celular.

### 7.3 `m3s_rmsprop` — MobileNetV3Small com RMSprop

Obteve 82,69% de acurácia, macro-F1 0,7677 e top 3 de 99,04%. Seu treinamento encerrou o fine-tuning após seis épocas, enquanto a maioria executou doze, indicando que a validação deixou de melhorar mais cedo.

**Pontos favoráveis:** mesmo porte do modelo anterior, boa NLL de 0,5513 e comportamento suficientemente diferente para melhorar a média do ensemble. Foi selecionado não por ser o melhor sozinho, mas porque seus erros e probabilidades complementaram os demais.

**Limitações:** confiança média de 89,27%, muito superior à acurácia de 82,69%, é um sinal de possível excesso de confiança antes da calibração. Não deve usar sua softmax bruta como probabilidade confiável.

**Conclusão:** é valioso como membro do ensemble; isoladamente não justifica trocar o modelo anterior.

### 7.4 `m3s_no_weights` — MobileNetV3Small sem pesos de classe

Obteve a maior acurácia isolada da validação, 86,54%, mas macro-F1 de 0,7671 e top 3 de 97,12%.

**Pontos favoráveis:** demonstra que o conjunto permite elevado acerto geral sem reponderação. Seria tentador escolhê-lo olhando apenas a acurácia.

**Limitações:** retirar os pesos reduz a penalização relativa dos erros em classes raras. Como o objetivo do LeafCare inclui doenças pouco representadas, esse ganho global pode sacrificar justamente casos mais difíceis e menos frequentes.

**Conclusão:** não foi escolhido porque macro-F1 e segurança entre classes são mais importantes que maximizar o total bruto de acertos.

### 7.5 `m3s_adam` — MobileNetV3Small com Adam

Obteve 81,73% de acurácia, macro-F1 0,7652 e top 3 de 98,08%.

**Pontos favoráveis:** oferece uma comparação direta do efeito do otimizador e confirma que a família MobileNetV3Small é estável entre configurações. Entrou entre os candidatos fortes para ensembles.

**Limitações:** ficou abaixo do modelo anterior e do `m3s_low_dropout` na validação. Embora use Adam como o anterior, diferenças de seed, número máximo de épocas e callbacks impedem uma comparação causal pura.

**Conclusão:** alternativa competente, mas sem ganho suficiente para implantação isolada.

### 7.6 `m3l_base` — MobileNetV3Large padrão

Obteve 85,58% de acurácia, macro-F1 0,7592, top 3 de 99,04% e a melhor NLL individual entre os novos modelos, 0,4736. Possui 3,01 milhões de parâmetros, cerca de três vezes a MobileNetV3Small.

**Pontos favoráveis:** maior capacidade representacional, alta acurácia e probabilidades úteis para combinar com modelos pequenos. Seu TFLite float32 preservou 100% da classe top 1 em relação ao Keras no teste.

**Limitações:** arquivo TFLite de 11,96 MB e maior custo computacional. Seu macro-F1 individual não foi superior ao das melhores MobileNetV3Small.

**Conclusão:** não seria a melhor troca isolada em custo-benefício, mas é importante no ensemble por aumentar diversidade arquitetural.

### 7.7 `m3s_base` — MobileNetV3Small com AdamW

Obteve 79,81% de acurácia, macro-F1 0,7492 e top 3 de 100%.

**Pontos favoráveis:** é pequeno e nunca excluiu a classe correta do top 3 da validação. Isso combina com a interface do LeafCare, que apresenta três hipóteses.

**Limitações:** o top 1 e o macro-F1 ficaram abaixo de outras variações da mesma arquitetura. O resultado mostra que AdamW não foi automaticamente superior ao Adam ou RMSprop neste conjunto.

**Conclusão:** bom modelo de referência, mas dominado por outras MobileNetV3Small.

### 7.8 `m2_base` — MobileNetV2

Obteve 82,69% de acurácia, macro-F1 0,7462 e top 3 de 98,08%, com 2,28 milhões de parâmetros.

**Pontos favoráveis:** arquitetura móvel conhecida e desempenho geral razoável. Serviu como controle para verificar se a geração V3 realmente acrescentava valor.

**Limitações:** é maior e teve macro-F1 inferior às melhores MobileNetV3Small. Também exige uma camada explícita de normalização para manter o contrato 0–255.

**Conclusão:** não oferece vantagem suficiente sobre MobileNetV3Small para este projeto.

### 7.9 `effb0_strong` — EfficientNetB0 com augmentation forte

Obteve 82,69% de acurácia, macro-F1 0,7253 e top 3 de 98,08%. Foi a melhor das duas EfficientNetB0 em macro-F1, mas teve confiança média relativamente baixa, 63,30%.

**Pontos favoráveis:** augmentation forte ajudou o macro-F1 em relação ao EfficientNetB0 base. Isso sugere que essa família se beneficiou mais de variações de iluminação e posição.

**Limitações:** 4,07 milhões de parâmetros, treinamento mais demorado e resultado inferior às MobileNet. Baixa confiança média não é necessariamente ruim, mas exige calibração antes de definir inconclusivo.

**Conclusão:** arquitetura válida para estudo, porém pouco competitiva no custo-benefício atual.

### 7.10 `m3s_strong` — MobileNetV3Small com augmentation forte

Obteve 75,96% de acurácia e macro-F1 0,7228, abaixo da variante padrão.

**Pontos favoráveis:** manteve top 3 alto, 98,08%, e testa robustez a transformações mais agressivas.

**Limitações:** aumento de dropout, augmentation e profundidade de fine-tuning foram modificados simultaneamente. Portanto, não é possível atribuir a queda a um único fator. O conjunto pequeno pode ter sofrido com exemplos transformados pouco realistas.

**Conclusão:** não deve ser escolhido; o resultado recomenda testar cada transformação isoladamente no próximo ciclo.

### 7.11 `m3l_strong` — MobileNetV3Large com augmentation forte

Obteve 77,88% de acurácia, macro-F1 0,7189 e top 3 de 98,08%.

**Pontos favoráveis:** avalia regularização mais intensa em uma rede de maior capacidade.

**Limitações:** ficou claramente abaixo do `m3l_base`, apesar de liberar mais camadas e aumentar dropout. Isso sugere regularização excessiva, transformações agressivas ou fine-tuning mais difícil para o volume de dados disponível.

**Conclusão:** a variante base é preferível.

### 7.12 `effb0_base` — EfficientNetB0 padrão

Obteve 79,81% de acurácia, macro-F1 0,7130 e top 3 de 97,12%.

**Pontos favoráveis:** arquitetura de referência eficiente e com capacidade maior que MobileNetV3Small.

**Limitações:** mais parâmetros, maior arquivo e maior tempo de treinamento sem melhora de validação. Seu resultado reforça que maior capacidade não compensa automaticamente a pouca quantidade de dados.

**Conclusão:** não selecionado por inferioridade simultânea em desempenho e custo.

### 7.13 `effv2b0_base` — EfficientNetV2B0 padrão

Obteve 76,92% de acurácia, macro-F1 0,6806 e top 3 de 94,23%, com 5,94 milhões de parâmetros.

**Pontos favoráveis:** inclui pré-processamento no modelo e representa uma família moderna de escalonamento de redes.

**Limitações:** é o candidato com maior quantidade de parâmetros e ficou abaixo das alternativas menores. O conjunto e o regime de treinamento podem não ser suficientes para aproveitar sua capacidade.

**Conclusão:** não adequado ao equilíbrio atual de dados e dispositivo.

### 7.14 `nasnet_base` — NASNetMobile

Obteve 68,27% de acurácia, macro-F1 0,6515 e top 3 de 92,31%. Sua NLL de 1,0155 foi a pior entre os candidatos isolados.

**Pontos favoráveis:** introduziu diversidade estrutural e impediu que a comparação se limitasse às famílias MobileNet/EfficientNet.

**Limitações:** menor desempenho geral, treinamento relativamente lento e 4,29 milhões de parâmetros. A confiança média baixa, 54,67%, coincide com maior incerteza e pior separação.

**Conclusão:** descartado para implantação.

### 7.15 `effv2b0_strong` — EfficientNetV2B0 com augmentation forte

Obteve 71,15% de acurácia, macro-F1 0,6451 e top 3 de 92,31%, o menor macro-F1 do benchmark.

**Pontos favoráveis:** serviu para verificar se maior regularização corrigiria o desempenho da variante base.

**Limitações:** não corrigiu. O maior dropout, maior quantidade de camadas liberadas e transformações fortes pioraram o equilíbrio por classe. É também o maior arquivo Keras, 43,01 MB.

**Conclusão:** descartado; aumentar arquitetura e augmentation sem aumentar dados não trouxe ganho.

---

## 8. Por que o ensemble foi escolhido

O ensemble calcula a média das probabilidades dos seus três membros. A lógica real de seleção em `finalize_benchmark.py` foi:

```python
for n in (2, 3):
    for combo in itertools.combinations(top, n):
        p = np.mean([x["p"] for x in combo], axis=0)
        ensembles.append({
            "members": [x["id"] for x in combo],
            **metrics(y_validation, p)
        })

candidates = sorted(
    singles + ensembles,
    key=lambda x: (
        x["macro_f1"], x["accuracy"],
        x["top3_accuracy"], -x["parameters"]
    ),
    reverse=True
)
```

A combinação venceu porque modelos diferentes erram imagens diferentes. A MobileNetV3Small anterior fornece uma base equilibrada e já comprovada; a versão RMSprop altera a dinâmica de otimização; a MobileNetV3Large oferece maior capacidade e outra representação. A média reduz decisões excessivamente dependentes de um único conjunto de pesos.

O código ainda impede que um ensemble seja escolhido por uma melhora irrelevante: ele exige ganho mínimo de 0,01 em macro-F1 sobre o melhor modelo individual.

```python
best_single = max(singles, key=lambda x: (
    x["macro_f1"], x["accuracy"]
))
best = candidates[0]
if (len(best["members"]) > 1 and
        best["macro_f1"] - best_single["macro_f1"] < .01):
    best = best_single
```

O ensemble superou o melhor indivíduo em mais de 0,039 de macro-F1 na validação, ultrapassando com folga essa exigência.

### 8.1 Resultado final comparado

| Métrica de teste | Modelo anterior | Ensemble | Diferença |
|---|---:|---:|---:|
| Acurácia top 1 | 77,67% (80/103) | **80,58% (83/103)** | +2,91 p.p. |
| Macro-F1 | 0,7157 | **0,7659** | +0,0502 |
| Acurácia top 3 | 97,09% (100/103) | **99,03% (102/103)** | +1,94 p.p. |
| Cobertura pelo limiar | 76,70% | 75,73% | -0,97 p.p. |
| Acurácia das previsões aceitas | 89,87% | **92,31%** | +2,44 p.p. |

O ensemble é a recomendação para a próxima versão quando a prioridade for desempenho. Se a exigência for escolher somente **um novo modelo**, a escolha mais justificável pela validação é `m3s_low_dropout`. Se a prioridade imediata for estabilidade operacional, o modelo anterior continua sendo a escolha segura até concluir a validação móvel.

---

## 9. Calibração e resultado inconclusivo

Após a média, foi aplicada calibração por temperatura. A temperatura foi otimizada somente na validação:

```python
def temp_scale(p, temperature):
    z = np.log(np.clip(p, 1e-7, 1)) / temperature
    z -= z.max(1, keepdims=True)
    z = np.exp(z)
    return z / z.sum(1, keepdims=True)

opt = minimize_scalar(
    lambda t: log_loss(
        y_validation, temp_scale(p_validation, t),
        labels=range(number_of_classes)
    ),
    bounds=(.35, 3), method="bounded"
)
```

A temperatura encontrada foi `0,8421423932`. Depois, o limiar foi pesquisado em passos de 0,01. O código escolheu a maior cobertura entre os limites que mantiveram pelo menos 90% de acurácia nas previsões aceitas:

```python
for threshold in np.arange(.45, .951, .01):
    accepted = confidence >= threshold
    if accepted.sum() >= 10:
        rows.append({
            "threshold": round(float(threshold), 2),
            "coverage": float(accepted.mean()),
            "accepted_accuracy": float(
                (prediction[accepted] == truth[accepted]).mean()
            )
        })

eligible = [
    row for row in rows
    if row["accepted_accuracy"] >= .90
]
selected = max(eligible, key=lambda row: row["coverage"])
```

O limiar selecionado foi **0,74**. Na validação, aceitou 82 de 104 imagens, com 90,24% de acerto nas aceitas. No teste, aceitou 78 de 103, com 92,31% de acerto. As demais devem aparecer como “Resultado inconclusivo”.

Essa política não aumenta artificialmente a acurácia global; ela explicita a troca entre cobertura e risco. Quanto maior o limiar, menos imagens recebem resposta conclusiva, mas tende a aumentar a precisão das respostas aceitas.

---

## 10. Exportação e compatibilidade com o aplicativo

Os três membros foram convertidos para TFLite float32. A exportação verifica erro numérico e concordância da classe top 1 entre Keras e TFLite:

```python
keras_probabilities = model(tensor, training=False).numpy()[0]
tflite_probabilities, elapsed = predictor.scores(tensor)

max_abs_error = float(np.max(np.abs(
    keras_probabilities - tflite_probabilities
)))
top1_agrees = (
    int(keras_probabilities.argmax()) ==
    int(tflite_probabilities.argmax())
)
```

| Membro | TFLite float32 | Erro máximo | Concordância top 1 |
|---|---:|---:|---:|
| Modelo anterior | 3,77 MB | 0,00000542 | 100% |
| MobileNetV3Small/RMSprop | 3,77 MB | 0,00000858 | 100% |
| MobileNetV3Large | 11,96 MB | 0,00000334 | 100% |

As versões com quantização dinâmica foram menores, mas foram rejeitadas: a concordância top 1 caiu para 92,23%, 87,38% e 95,15%. Reduzir tamanho não compensa trocar a classe prevista em quantidade relevante de imagens.

O algoritmo de inferência entregue em `benchmark_predict.py` é:

```python
member_scores = []
for member in manifest["members"]:
    predictor = TFLitePredictor(model_path, classes)
    scores, elapsed = predictor.scores(tensor)
    member_scores.append(scores)

averaged = np.mean(np.stack(member_scores), axis=0)
calibrated = calibrate(
    averaged, float(manifest["temperature"])
)
result = rank(calibrated, classes, threshold=0.74)
```

No Android, o tensor deve ser preparado uma vez e reutilizado nos três intérpretes. Depois, o app calcula a média posição a posição, aplica a mesma temperatura, ordena o top 3 e compara o maior valor com 0,74.

---

## 11. Por que o modelo antigo foi mantido no aplicativo

A decisão deve ser compreendida em três níveis.

### 11.1 O benchmark produziu uma recomendação, não uma implantação automaticamente segura

O ensemble foi validado em Python e convertido para TFLite, mas ainda não foi executado dentro do aplicativo em um aparelho Android físico. O modelo antigo já tinha:

- asset TFLite presente no aplicativo;
- ordem das classes e hashes verificados;
- pré-processamento Kotlin compatível;
- build Android concluído;
- testes unitários e de interface aprovados;
- fluxo de câmera, galeria, resultado e histórico preservado.

Trocar um arquivo por três não é apenas substituir pesos. Exige alterar o repositório de inferência, gerenciar três intérpretes, combinar probabilidades, aplicar calibração e atualizar os testes.

### 11.2 O ganho existe, mas precisa ser comparado ao custo móvel

O ganho de 2,91 pontos percentuais de acurácia e 0,0502 de macro-F1 é relevante para este experimento. Porém, o custo passa de aproximadamente 3,77 MB para 19,5 MB em TFLites, além de três execuções. O tempo medido no computador não pode ser usado como garantia de latência no celular.

É necessário medir:

- tempo total em aparelhos de entrada e intermediários;
- pico de memória;
- tamanho final do APK;
- aquecimento após classificações repetidas;
- estabilidade após alternar câmera e galeria;
- comportamento em ARM64 e, se suportado, outros ABIs;
- persistência correta do top 3 calibrado no Room.

### 11.3 Preservar a versão conhecida facilita rollback

O modelo antigo funciona como versão operacional estável. O ensemble pode ser implementado em uma branch ou versão seguinte, comparado por testes A/B offline e promovido somente depois da aprovação. Se houver problema de desempenho, o app pode manter um modo de modelo único como fallback.

**Conclusão:** o modelo antigo foi mantido por controle de risco, não por superioridade estatística. A recomendação é integrar o ensemble em uma versão experimental e promovê-lo depois de passar por testes móveis e validação externa.

---

## 12. Como aumentar a confiabilidade do modelo

As ações estão ordenadas aproximadamente pelo impacto esperado.

### 12.1 Coletar mais dados independentes e equilibrados

Este é o ponto mais importante. Mais arquiteturas sobre as mesmas 696 imagens aumentam o risco de escolher uma configuração adaptada à validação. É necessário coletar imagens:

- em propriedades diferentes, principalmente no Sul do Brasil;
- em celulares diferentes;
- sob sol, sombra, interior de estufa e iluminação fraca;
- em diferentes estágios da planta e severidades da doença;
- com fundo, distância, inclinação e enquadramento variados;
- das classes raras: `anthracnose`, `tswv`, `black_shank` e `genetic_abnormality`;
- de folhas saudáveis e sintomas visualmente semelhantes.

Cada imagem nova deve possuir, quando possível, identificadores de propriedade, talhão, planta, data e sessão. A separação deve ocorrer por propriedade ou planta, não apenas por arquivo.

### 12.2 Revisar rótulos com especialista

Um modelo não supera sistematicamente rótulos incorretos ou diagnósticos ambíguos. Recomenda-se dupla revisão agronômica, registro do nível de certeza do rótulo e exclusão ou tratamento separado de imagens sem confirmação.

### 12.3 Criar teste externo bloqueado

O próximo teste deve vir de uma origem que não participou de nenhuma decisão. Ele deve permanecer inacessível até que arquitetura, seed, augmentation, calibração e limiar estejam congelados. Isso mede generalização com menor otimismo.

### 12.4 Repetir cada configuração com várias seeds

Executar, por exemplo, cinco seeds iguais para cada configuração e reportar média, desvio-padrão e intervalo de confiança. Isso separa ganho arquitetural de sorte de inicialização ou ordem de lotes.

### 12.5 Usar validação cruzada agrupada

Com poucos dados, uma única validação de 104 imagens é instável. Group K-Fold por propriedade/planta/origem permitiria repetir a comparação sem misturar grupos relacionados. A seleção deve usar a média de macro-F1 dos folds.

### 12.6 Medir calibração explicitamente

Além de NLL, incluir:

- Expected Calibration Error (ECE);
- Brier Score multiclasses;
- diagrama de confiabilidade;
- acurácia seletiva versus cobertura;
- calibração por classe, quando houver amostras suficientes.

Temperature scaling deve ser ajustado em um subconjunto de calibração separado ou dentro de um protocolo aninhado de validação. Reutilizar a mesma validação para escolher modelo, temperatura e limiar pode produzir otimismo.

### 12.7 Detectar imagens fora do domínio

O classificador atual sempre distribui probabilidade entre as 16 classes. Uma foto de solo, mão, ferramenta ou cultura diferente pode receber confiança alta. O aplicativo precisa de:

- filtro de qualidade para desfoque, pouca luz e folha distante;
- classe ou detector “não é folha de fumo”;
- exemplos negativos de outras plantas e objetos;
- método de abstention para padrões distantes do treino.

### 12.8 Testar classificação em maior resolução e recortes da lesão

Lesões pequenas podem perder detalhes em 224 × 224. Devem ser comparados, com o mesmo protocolo, 256, 320 ou uma estratégia em duas etapas: localizar/recortar a área afetada e depois classificar. O custo móvel deve ser medido; maior resolução não é automaticamente melhor.

### 12.9 Fazer busca de hiperparâmetros controlada

O benchmark alterou combinações plausíveis, mas não é uma busca exaustiva. Um próximo ciclo pode testar isoladamente:

- intensidade de cada transformação;
- dropout 0,10 a 0,40;
- número de camadas liberadas;
- learning rate e weight decay;
- label smoothing;
- focal loss ou class-balanced loss;
- tamanho do batch;
- MixUp/CutMix, somente se visualmente coerentes.

O orçamento de tentativas deve ser definido antes, e a comparação deve usar validação cruzada para reduzir seleção oportunista.

### 12.10 Usar active learning

O app pode separar, com consentimento e processo adequado, imagens inconclusivas ou discordantes para revisão posterior. Um especialista rotula os casos mais informativos, e o próximo treinamento concentra esforço onde o modelo mais erra.

### 12.11 Melhorar o ensemble e reduzir seu custo

Depois de validar o ensemble completo, pode-se testar:

- média ponderada escolhida apenas na validação;
- distillation do ensemble para uma MobileNetV3Small;
- quantização inteira com conjunto representativo;
- quantization-aware training;
- seleção dinâmica: executar o modelo grande apenas quando o pequeno estiver incerto.

Distillation é especialmente promissora: o ensemble atua como professor e uma rede pequena aprende suas probabilidades. O objetivo é aproximar o ganho do conjunto mantendo uma única inferência no Android.

---

## 13. Plano recomendado para a próxima versão

### Etapa 1 — integração experimental

1. Criar uma branch específica para o ensemble.
2. Adicionar somente os três TFLites float32 aprovados.
3. Implementar média, temperatura 0,8421423932 e limiar 0,74.
4. Criar teste de paridade Kotlin/Python para todas as 16 probabilidades.
5. Manter o modelo antigo como fallback.

### Etapa 2 — validação móvel

1. Testar em pelo menos um celular de entrada e um intermediário.
2. Medir tempo de pré-processamento, inferência e total.
3. Medir memória e tamanho do APK.
4. Executar repetidamente para observar aquecimento e estabilidade.
5. Confirmar câmera, galeria, rotação e histórico.

### Etapa 3 — validação científica

1. Ampliar principalmente as classes raras.
2. Obter dados locais e metadados de origem.
3. Repetir candidatos com múltiplas seeds e Group K-Fold.
4. Congelar a decisão.
5. Avaliar uma vez em teste externo.
6. Revisar resultados com profissional agrícola.

### Etapa 4 — promoção

Promover o ensemble somente se:

- mantiver ganho de macro-F1 em dados externos;
- preservar paridade Python/Android;
- cumprir limite de latência e memória definido para o app;
- não introduzir falhas no fluxo offline;
- passar pela revisão agronômica.

Se o custo for excessivo, usar `m3s_low_dropout` como candidato novo individual ou destilar o ensemble para uma única MobileNetV3Small.

---

## 14. Conclusão final

O benchmark mostrou que **não existe uma relação simples entre tamanho da rede e qualidade**. EfficientNetV2B0 e NASNetMobile foram maiores ou mais custosas e tiveram resultados inferiores. A família MobileNetV3Small apresentou o melhor equilíbrio, enquanto a MobileNetV3Large agregou diversidade útil ao ensemble.

O melhor novo modelo isolado foi `m3s_low_dropout`, mas sua vantagem sobre o modelo anterior apareceu somente na validação e foi pequena. A melhor evidência disponível favorece o ensemble formado pelo modelo anterior, `m3s_rmsprop` e `m3l_base`. Ele melhorou acurácia, macro-F1, top 3 e acurácia das previsões aceitas no teste final.

O modelo antigo permaneceu no aplicativo porque era a versão operacional já validada. A implantação do ensemble exige alterações de código e testes de desempenho em hardware real. Essa separação entre “melhor experimento” e “versão segura para produção” é correta: evita trocar um componente funcional por outro ainda não validado no ambiente final.

Por fim, a principal forma de aumentar a confiabilidade não é forçar valores maiores de softmax nem treinar dezenas de arquiteturas adicionais sobre os mesmos arquivos. É obter dados independentes e representativos, corrigir o desequilíbrio, validar por origem, revisar rótulos, medir calibração e permitir que o aplicativo responda “inconclusivo” quando não houver evidência suficiente.

---

## 15. Arquivos que sustentam este relatório

- `machine-learning/benchmark.py`: candidatos, datasets, augmentation, modelos, otimizadores e treinamento.
- `machine-learning/finalize_benchmark.py`: seleção, ensembles, calibração, limiar, teste e exportação.
- `machine-learning/benchmark_predict.py`: inferência offline equivalente à futura implementação Android.
- `machine-learning/leafcare/dataset.py`: auditoria, agrupamento e prevenção de vazamento.
- `machine-learning/leafcare/preprocessing.py`: contrato RGB 224 × 224.
- `machine-learning/train.py`: procedimento do modelo anterior.
- `machine-learning/evaluate.py`: métricas do teste do modelo anterior.
- `machine-learning/export_tflite.py`: conversão e teste de paridade.
- `machine-learning/benchmark_artifacts/*/validation.json`: métricas individuais reais.
- `machine-learning/benchmark_artifacts/deployment/deployment_manifest.json`: decisão final, hashes, tamanhos e métricas de teste.

Todas as métricas apresentadas vieram desses artefatos. Nenhum resultado ausente foi estimado ou inventado.

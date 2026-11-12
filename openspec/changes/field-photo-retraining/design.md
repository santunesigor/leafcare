## Context

As cópias de treino vivem no bucket privado `training-photos` por até 90 dias. Exportações locais podem existir por até 30 dias, sem identificadores nem EXIF. A revisão humana acontece no painel admin, sobre as fotos do histórico (`analysis-photos`), e grava `photo_reviews`. O modelo do app foi treinado uma vez, no TLA, por um experimento com hashes fixos que não aceita outro conjunto.

## Goals / Non-Goals

**Goals:**
- Uma regra única e testada de elegibilidade no banco.
- Revisores sabendo quais fotos podem treinar e quanto tempo resta.
- Um ciclo reprodutível com a mesma receita do modelo atual, que nunca troca o modelo do app sem decisão.

**Non-Goals:**
- Guardar fotos de campo além dos prazos dos Termos.
- Mudar classes, receita ou contrato do modelo.
- Treinar automaticamente.

## Decisions

- **Elegibilidade em SQL (`training_pool()`).** Exportador e painel usam a mesma função, sem regras duplicadas em Python e Kotlin. Ela é `SECURITY DEFINER` e só concede `EXECUTE` a `service_role`; o painel a usa por dentro de `admin_command`.
- **Data do aceite.** Vale o primeiro recibo 1.4/1.4 da conta: os Termos excluem fotos analisadas antes dele. Uma revogação desautoriza as fotos (`authorized=false`) e apaga as cópias, então um novo aceite não ressuscita fotos antigas.
- **Grupos por pessoa com HMAC de chave aleatória por exportação.** Mantém as fotos de uma pessoa no mesmo split, evitando vazamento entre treino e teste, sem guardar identificador e sem ligar exportações diferentes.
- **TLA com split histórico + `field_test` separado.** As métricas continuam comparáveis com todos os modelos anteriores, e o `field_test` é a primeira avaliação em fotos reais. A linha de base do modelo em uso vem da previsão gravada na análise.
- **Receita importada do experimento de destilação.** `retraining/train.py` reutiliza `RECIPE`, a augmentation, a perda KD, o probe e as métricas do código que gerou o modelo atual; só muda a origem das imagens.
- **Promoção em dois passos.** Primeiro, replay obrigatório com o pré-processamento do app (erro ≤ 1e-4 e métricas iguais). Depois, `--install` explícito. Execução de fumaça e treino sem professor nunca são promovidos.

## Risks / Trade-offs

- Professor DINOv2-B ausente neste computador e nas releases → é preciso recuperar a cópia ou treinar de novo (documentado); sem ele não há promoção.
- Janela de 90 dias → cada ciclo vê pouco campo; acumular exige mudar os Termos.
- `field_test` pequeno → métricas variam muito; a recomendação automática é só um apoio.
- Redefinir `admin_command` inteiro → coberto pelos testes SQL existentes, que continuam passando.

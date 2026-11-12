## Why

Pelos Termos v1.4, cada cópia de treino era apagada 90 dias depois do envio, e cada exportação local valia 30 dias. Assim, um retreino só via as fotos dos últimos 90 dias e não havia como acumular um conjunto de campo entre versões do modelo. O ciclo antigo também exigia cinco comandos em dois ambientes e dependia de um checkpoint do professor DINOv2-B que se perdeu. Além disso, `machine-learning/` carregava código e evidências de experimentos encerrados: ensemble, Keras, benchmarks e candidatos descartados.

## What Changes

- **Termos e Política v1.5**: as cópias de treino ficam no conjunto por tempo indeterminado, até a revogação, a exclusão da análise ou a exclusão da conta. As cópias de fotos analisadas sob a v1.4 mantêm a promessa de 90 dias.
- **Supabase**: aceite v1.5. A retenção passa a ser definida por cópia (`expires_at` nulo para as permanentes). `training_pool()` deixa de cortar em 90 dias, e a limpeza diária apaga só as cópias v1.4 vencidas.
- **Conjunto acumulado**: o dataset de cada ciclo é o TLA, com o split histórico, mais todas as fotos de campo prontas. Ele fica espelhado localmente em `machine-learning/data/field/`, que cada sincronização atualiza: baixa as novas, apaga as que saíram e atualiza rótulos corrigidos.
- **Um script**: `machine-learning/retrain.py` cobre estado, sincronização, ciclo completo (incluindo o treino do professor DINOv2-B) e promoção explícita.
- **Limpeza**: saem os experimentos, os benchmarks, o pipeline Keras/ensemble, o exportador de 30 dias, o timer systemd e o plano anterior (`field-photo-retraining`), que esta change substitui.
- **Android**: textos v1.5, selo "Treino" sem contagem de dias para cópias permanentes e `versionCode` novo da release 1.8.0.

## Capabilities

### New Capabilities
- `model-retraining`: conjunto acumulado (TLA + campo), sincronização local, ciclo de treino e promoção explícita.

### Modified Capabilities
- `photo-training-consent`: elegibilidade e retenção das cópias de treino segundo a v1.5.
- `superadmin`: revisão das cópias de treino com prazo apenas para cópias v1.4.

## Impact

- Supabase: nova migração de documentos v1.5 e retenção por cópia, Edge Function `cleanup-training-photos` e testes SQL/Deno.
- Android: documentos legais, telas de aceite e de privacidade, painel admin e testes.
- Machine learning: `retrain.py`, pacote de treino consolidado, testes e remoção dos arquivos históricos. O modelo do app não muda: promover continua exigindo um comando explícito.
- Termos: contas existentes precisam aceitar a v1.5 para continuar usando o app.

# Contexto do LeafCare

Referência operacional para mudanças no projeto. Código, testes e migrations são a fonte de verdade; investigue divergências antes de atualizar a documentação.

## Produto e estado esperado

LeafCare é um app Android de triagem visual de alterações em folhas de fumo, não um diagnóstico agronômico definitivo. A conta exige internet no primeiro acesso. Depois da autenticação, câmera, galeria, classificação e histórico devem funcionar offline.

O app já inclui captura/importação de imagens, classificação local, resultado Top-3/inconclusivo, histórico Room, autenticação Supabase e sincronização de análises e fotos.

## Arquitetura e dados

- Inferência sempre local; o backend não classifica imagens.
- Room é a fonte observada pela UI. Salve a análise local antes de tentar sincronizar.
- Sincronização usa UUID estável, WorkManager e retry; falhas remotas não podem invalidar a análise local.
- Tombstones de exclusão não podem ressuscitar durante restore.
- Fotos ficam no armazenamento local e em bucket privado; estado de sync da foto é independente da análise.
- Dados devem permanecer isolados por conta; RLS restringe acesso remoto pelo usuário autenticado.

## Machine Learning

O bundle integrado é um ensemble de dois MobileNetV3Small e um MobileNetV3Large em um único TFLite float32, 16 classes e entrada `[1,224,224,3]` RGB 0–255. Média e calibração por temperatura (0,835105) estão no grafo; threshold 0,631628 definido na validação. Métricas do teste interno: Top-1 81,55%, Macro-F1 0,7371 e Top-3 99,03%. Treino/exportação padrão: `train_ensemble.py`. Medição de até três segundos no Galaxy A06 pendente de aparelho conectado.

Não altere modelo, treinamento, dataset, ordem das classes ou threshold sem solicitação explícita. Confiança softmax não é certeza agronômica. Detalhes e comandos estão em [MACHINE_LEARNING.md](MACHINE_LEARNING.md).

## Segurança e autenticação

- Não inclua service_role, senha de banco, JWT secret ou credenciais administrativas no app.
- Mantenha RLS ativa e o bucket de fotos privado.
- Auth usa confirmação por link e recuperação por deep link: `leafcare://auth/confirm-email` e `leafcare://auth/reset-password`.
- Não adicione login social nem pipeline de coleta de fotos para treinamento sem solicitação explícita.

## Débitos conhecidos

- Verifique o resíduo de threshold persistido em SharedPreferences antes de mexer nesse fluxo; o threshold deve seguir o bundle do modelo.
- Os fluxos Auth foram reportados aprovados manualmente em dispositivo em 2026-09-29. Validação em segundo aparelho não foi executada por indisponibilidade.
- Atualize limitações e estado de QA sem transformar resultados antigos em evidência de uma execução nova.

## Regras de trabalho

- Prefira a menor mudança suficiente; não inicie refatorações amplas para remover dívida de baixo impacto.
- Leia apenas arquivos necessários e preserve o comportamento offline-first.
- Antes de editar, confira `git status`, branch atual e histórico recente.
- Execute testes relacionados e documente limitações reais. Nunca afirme que um teste passou sem executá-lo.
- Não faça push sem autorização explícita.

## Fonte de verdade

Em caso de conflito: código, testes, migrations/schema, artefatos do modelo, documentação técnica e, por último, este contexto. Não altere ML ou schema para resolver divergência documental.

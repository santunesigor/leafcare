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

O bundle integrado é MobileNetV4 Small destilado, schema 2, TFLite float32 de 9,70 MiB, 16 classes e entrada `[1,224,224,3]` RGB 0–255. Resize bicúbico do lado menor para 256 e crop central 224; normalização ImageNet e temperatura 1,888204 embarcadas; limiar 0,506676. Teste conhecido: Top-1 85,44%, Macro-F1 0,8083, Top-3 97,09%. Promoção: `deploy_distilled.py --install`; receita em `experiments/distillation/`. Relatórios atuais: `docs/model-reports/mobilenetv4-distilled/`. Ensemble anterior: tag `v1.1.2` e relatórios históricos separados. Meta de três segundos no Galaxy A06 pendente de aparelho conectado.

Não altere modelo, treinamento, dataset, ordem das classes ou threshold sem solicitação explícita. Confiança softmax não é certeza agronômica. Detalhes e comandos estão em [MACHINE_LEARNING.md](MACHINE_LEARNING.md).

## Segurança e autenticação

- Não inclua service_role, senha de banco, JWT secret ou credenciais administrativas no app.
- Mantenha RLS ativa e o bucket de fotos privado.
- Auth usa confirmação por link e recuperação por deep link: `leafcare://auth/confirm-email` e `leafcare://auth/reset-password`.
- Não adicione login social nem pipeline de coleta de fotos para treinamento sem solicitação explícita.

## Débitos conhecidos

- O threshold usado por AnalysisRepository vem de LeafClassifier e do bundle. Análises anteriores preservam o limiar e hash registrados.
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

# Design

## Context

Split existente 489/104/103, 16 classes, GTX 1650 4 GiB. Teste já conhecido: comparação interna, sem validação de campo. Pesos DINOv2-B ajustados disponíveis localmente.

## Goals / Non-Goals

Executar dez treinos pareados e avaliar custo de exportação. Não integrar candidatos, alterar pesos atuais, coletar dados ou mudar o split.

## Decisions

- Código em `machine-learning/experiments/distillation/`, cache ignorado próprio e artefatos em `machine-learning/benchmark_artifacts/distillation/`.
- Professor DINOv2-B ajustado, melhor Macro-F1 de validação entre os DINOv2 existentes; nenhuma escolha pelo teste. Verificar SHA256 e replay da validação antes do uso.
- Cada arquitetura começa do mesmo probe logístico selecionado por validação entre C=1 balanceado/sem pesos. Controle e aluno usam mesmos pesos e augmentations determinísticas.
- Receita: seed 42, batch 8, AdamW, warmup da cabeça 3 épocas, encoder até 15 épocas, patience 5; LRs 1e-3 warmup, 1e-4 cabeça e 1e-5 encoder, weight decay 1e-4, BatchNorm congelada.
- Destilação: 0,5 CE balanceada + 0,5 T² KL(professor || aluno), T=4. Professor congelado, apenas imagens de treino; respostas cacheadas por época e imagem, com augmentations reproduzíveis compartilhadas.
- Preprocessing oficial por checkpoint sobre a mesma foto aumentada. Exportações independentes incorporam normalização/calibração, sem assumir compatibilidade com o preprocessing Android atual.
- Seleção de época e temperatura/limiar na validação, configuração persistida antes de inferência no teste. Exportar todos os alunos/controles float32, registrar falhas explicitamente e tempos CPU sem extrapolar para A06.
- Não sobrescrever resultados: execução completa não é repetida implicitamente. Hashes dos arquivos protegidos conferidos antes/depois; novos pesos/TFLites apenas no cache próprio.

## Risks / Trade-offs

Dataset pequeno, classes raras, validação já usada em buscas e seed única limitam conclusões. Reparametrização FastViT/EfficientViT ocorre apenas após carregar o checkpoint selecionado e exige paridade. Sem aparelho conectado, tempo Android fica pendente. Receitas timm não equivalem aos MobileNetV3 Keras históricos.

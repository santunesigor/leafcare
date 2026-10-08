# Design

## Context

Aluno `mobilenetv4_small_distilled`: checkpoint época 15, TFLite SHA256 422ab461585a83bfabe6896d7ac7b32c726a8b285ff4ade76cb0cec54ba6da99, 10.167.768 bytes. Temperatura 1,8882043703807203 e limiar 0,5066758394241333; entrada float32 RGB NHWC 224, saída 16 probabilidades. O preprocessing antigo não reproduz as métricas desse checkpoint.

## Goals / Non-Goals

Integrar exatamente o aluno avaliado, preservar classes/split e histórico, adaptar o repositório ao novo padrão e entregar release/PR. Não retreinar, reescolher checkpoints pelo teste, modificar UI/backend nem afirmar validação Android sem aparelho.

## Decisions

- Novo preprocessing Kotlin separável bicúbico a=-0,5, antialias, coeficientes fixos de 22 bits, clamp/round uint8 em cada eixo, equivalente ao Pillow 10.4. Resize do lado menor para 256, outro lado truncado proporcionalmente; recorte central 224 com round ties-to-even. Calcular apenas colunas/linhas necessárias ao recorte para limitar memória.
- Python usa Pillow para o mesmo resize/crop; helpers antigos permanecem para testes/receitas históricas. Fixtures sintéticas versionadas e replay local nas 104 imagens de validação verificam bytes RGB entre Kotlin/Python.
- Schema 2 distingue o novo contrato. App valida arquitetura, resize, normalização, temperatura, limiar, hashes/classes e shapes/dtypes antes de inferir; erros de inicialização disponibilizam mensagem de indisponibilidade.
- `deploy_distilled.py` promove apenas o TFLite e registros hash-verificados do aluno; reporta nova paridade/predição de referência em `docs/model-reports/mobilenetv4-distilled/`, sem sobrescrever benchmarks. Artefatos de execução atuais recebem metadados/métricas do aluno.
- CLI usa preprocessing do metadata e rejeita backend Keras para o aluno. Receitas históricas ficam explícitas como históricas e protegidas contra substituir o bundle atual por acidente.
- Testes de isolamento da rodada antiga conferem seus snapshots registrados, não o bundle atual após uma promoção autorizada.
- Versão 1.2.0/code 7; release prerelease na branch enquanto PR aguarda merge. Preservar v1.1.2 com ensemble para rollback.

## Risks / Trade-offs

Não basta copiar o TFLite sem adaptar entrada. Paridade RGB e probabilidades será verificada antes de publicar. Dataset pequeno/teste conhecido e seed única não comprovam superioridade em campo; latência de três segundos no A06 permanece pendente. Rollback por v1.1.2; análises anteriores conservam seu hash/threshold sem migração de banco.

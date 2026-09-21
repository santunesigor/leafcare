# Dados do experimento V3

Foram importadas 696 imagens brutas da seção TV3 do TLA em 16 classes. A taxonomia veio do class_map.yaml do TTDD. TV6 e versões processadas/sintéticas do TTDD não entraram no treinamento. O pacote público CC BY de 43 referências foi usado apenas para imagens ilustrativas.

A auditoria encontrou zero arquivos inválidos e zero duplicatas exatas. O agrupamento usa classe e identificador IMG do nome, com fallback para o nome-base; a comparação dHash (distância até 4) reúne imagens semelhantes. A divisão com seed 42 resultou em 489 imagens de treino, 104 de validação e 103 de teste. O mínimo configurado foi de oito grupos por classe; isso permite a divisão, mas não garante robustez estatística.

Consulte DATASET_IMPORT.json e machine-learning/data/prepared para contagens, hashes, grupos e manifestos. Os ZIPs completos não são redistribuídos.

## Limitações

Sem identificadores de planta, propriedade ou sessão fotográfica, o agrupamento não garante independência de campo. Algumas classes têm apenas uma ou duas imagens no teste. Antracnose e TSWV tiveram zero acertos em uma imagem de teste cada. Não houve teste externo, calibração de confiança ou validação agronômica.

O limiar 0,70 é uma configuração inicial, não uma probabilidade calibrada. Fotografias fora da distribuição, inclusive imagens que não sejam folhas, podem receber confiança alta.

## Reprodução e direitos

Siga no README a sequência import_tla.py, prepare_dataset.py, train.py, evaluate.py e export_tflite.py. Preserve antes os artefatos existentes para não sobrescrever o experimento.

A licença CC BY das figuras de referência não deve ser presumida para todo o TLA/TTDD. Verifique os termos dos datasets originais antes de redistribuir imagens ou explorar comercialmente o modelo. A licença MIT cobre o código original, não substitui licenças de dados, fontes e dependências.

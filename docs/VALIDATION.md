# Validação V3 — 10/09/2026

## Benchmark adicional — 11/09/2026

- Quatorze configurações novas treinadas até 24 épocas cada (12 congeladas + 12 de fine-tuning), usando exatamente o mesmo manifesto de 489/104/103 imagens.
- Seis famílias móveis e variações de augmentation, dropout, otimizador, pesos de classe e profundidade de fine-tuning.
- Cinquenta estratégias comparadas na validação: 15 modelos isolados, incluindo o anterior, e 35 ensembles de dois/três modelos.
- Ensemble escolhido sem consultar o teste: modelo anterior + MobileNetV3Small/RMSprop + MobileNetV3Large.
- Teste final: top 1 80,58% (83/103), top 3 99,03% (102/103), macro-F1 0,7659.
- Limiar 0,74 escolhido na validação; no teste, cobertura 75,73% e acurácia aceita 92,31%.
- Três exportações TFLite float32 com concordância top 1 de 100% contra Keras e erro máximo menor que 0,000009.
- Inferência real em `reference_frog_eye.jpg`: frog_eye 90,83%, resultado conclusivo.
- Vinte e oito testes Python aprovados após a adição do ensemble.

As versões TFLite com quantização dinâmica falharam no critério de paridade e foram mantidas apenas para auditoria; não devem ser integradas. O ensemble ainda não foi colocado nos assets do app nem testado em Android. Consulte `BENCHMARK_MODELOS.md` e `ENSEMBLE_ANDROID_INTEGRATION.md`.

## Machine learning concluído

- Dependências instaladas; 28 testes Python aprovados (14,07 segundos). Evidência: python_tests.xml.
- MobileNetV3Small com ImageNet: treinamento em duas etapas concluído.
- Teste independente da otimização: 103 imagens, top 1 77,67%, top 3 97,09%, macro-F1 0,7157.
- Exportação Keras/TFLite comparada em 20 imagens de validação: erro absoluto máximo 0,000007718801498413086 e concordância top 1 de 100%.
- validate_bundle.py --require-model aprovado: 16 classes, hashes e ordem de saída consistentes.
- Inferência TFLite em samples/reference_frog_eye.jpg: frog_eye 0,84048194; anthracnose 0,15917976; wildfire 0,00026704.
- Modelo nos assets Android; SHA-256: d0447e6f3cbd62318e9d9b7cbac5fbf6038df74001fe3aeb3c467e4c7cb6090b.

As métricas completas e curvas estão em machine-learning/artifacts. Os resultados têm as limitações descritas em DATASET.md; não são validação de campo.

## Validação pós-hardening — 22/09/2026

### Machine Learning
- python -m pytest -q: **PASS** — 28 passed
- python validate_bundle.py --require-model: **PASS**
- status: **model_bundle_valid**
- classes: **16**
- model_output_order_verified: **true**
- model SHA256: **d0447e6f3cbd62318e9d9b7cbac5fbf6038df74001fe3aeb3c467e4c7cb6090b**

### Android
- testDebugUnitTest: **PASS** — BUILD SUCCESSFUL
- verifyModelAssets: **PASS** — BUILD SUCCESSFUL
- assembleDebug: **PASS** — BUILD SUCCESSFUL

Não foi executado connectedDebugAndroidTest.
Não foi executado teste em dispositivo físico ou emulador Android.

## Android V3

- Compilação Kotlin/Java/recursos e assembleDebug aprovados; APK gerado localmente.
- verifyModelAssets aprovado. O modelo dentro do APK é idêntico ao artefato Python; as classes também estão incluídas.
- Oito testes Android aprovados: quatro de política/pré-processamento e quatro de interface. Junto aos 28 Python, são **36 testes aprovados**.
- Capturas das quatro telas renderizadas no Robolectric e inspecionadas visualmente: docs/screenshots. A ajuda contém exatamente as quatro fotos enviadas. Os registros e resultados nessas capturas são fixtures de teste, não métricas do modelo nem histórico inserido no app.
- Pesquisa, navegação, captura/galeria/ajuda por callbacks, fechamento da ajuda e expansão das possibilidades foram exercitados nos testes de interface.
- Manifesto mesclado sem permissão INTERNET.
- Binários de distribuição devem ser publicados em GitHub Releases, não versionados no Git.

A captura inicial com PixelCopy expirou no ambiente sem janela gráfica física. O teste foi ajustado para desenhar a hierarquia real da Activity em Canvas e aguardar a carga da foto; os oito testes passaram após essa correção. Os XMLs finais estão em docs/android-tests.

Não foi executado teste em aparelho físico ou emulador Android. Robolectric não substitui esses testes. Câmera real, permissões, galeria do sistema, persistência entre processos e desempenho no celular exigem a sequência MANUAL_TESTS.md e connectedDebugAndroidTest em aparelho ou emulador. O APK de testes instrumentados desta V3 não foi executado.

Comandos a partir de android-app:
```bash
./gradlew verifyModelAssets assembleDebug testDebugUnitTest
./gradlew connectedDebugAndroidTest
```
No Windows, substitua ./gradlew por .\\gradlew.bat.
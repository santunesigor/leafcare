# LeafCare

Aplicativo Android nativo para **triagem visual offline** de doenças e alterações em folhas de fumo. O produtor captura ou seleciona uma foto e o modelo roda direto no celular — sem internet, sem backend, sem cadastro.

> **Importante:** LeafCare é uma ferramenta de **triagem visual**, **não** um diagnóstico agronômico definitivo. Os resultados devem ser confirmados por um profissional qualificado.

---

## Visão geral

O LeafCare nasceu da necessidade de apoiar produtores em campo onde a conectividade é limitada ou inexistente. O app:

- Funciona **100% offline** — nenhuma permissão `INTERNET` no manifesto
- Executa inferência **local no dispositivo** via TensorFlow Lite / LiteRT
- Usa **MobileNetV3Small** (baseline) como modelo integrado no Android
- Armazena histórico local com **Room/SQLite** (fotos em armazenamento privado)
- Não tem Firebase, Supabase, analytics, telemetria ou sincronização na nuvem

---

## O problema

Doenças foliares no fumo causam perdas significativas. O diagnóstico precoce depende de profissionais que nem sempre estão disponíveis na propriedade no momento certo. Ferramentas baseadas em nuvem falham sem sinal de celular.

---

## A solução

Um aplicativo que o produtor leva no bolso:

1. Abre o app (modo avião funciona)
2. Fotografa a folha ou escolhe da galeria
3. O modelo classifica **offline** em milissegundos
4. Mostra as **3 classes mais prováveis** com confiança
5. Se a confiança for baixa, devolve **inconclusivo** — não força diagnóstico
6. Salva tudo no histórico local para consulta posterior

---

## Principais funcionalidades

| Funcionalidade | Descrição |
|---|---|
| **Câmera + Galeria** | CameraX para captura; seletor de documentos do sistema para importar |
| **Inferência offline** | TFLite/LiteRT 1.4.0, float32, 2 threads, sem rede |
| **Top-3 + threshold** | Mostra 3 hipóteses; abaixo do limiar → "Inconclusivo" |
| **Orientação fotográfica** | Tela de ajuda com 4 exemplos (correta, desfocada, distante, pouca luz) |
| **Histórico Room** | Busca, filtros (data, classe, inconclusivas), exclusão com confirmação |
| **Persistência real** | Fotos e registros sobrevivem a reinício do app e do celular |
| **Privacidade** | Zero permissão `INTERNET`; backup automático desativado |

---

## Como funciona

```
Foto / Galeria
      ↓
Preprocessing (decode → EXIF → center crop → resize 224×224 → float32 RGB 0–255)
      ↓
MobileNetV3Small + LiteRT
      ↓
Top-3 + threshold (0.70 baseline)
      ↓
Resultado (classes, confiança, descrições, aviso)
      ↓
Room / histórico local
```

**Detalhes do pré-processamento (contrato Python ↔ Android idêntico):**

- Decode com `BitmapFactory` + `inPreferredColorSpace=SRGB`
- Correção de orientação EXIF (1–8), transparência composta sobre branco
- Center crop quadrado (`min(width, height)`)
- Resize bilinear **half-pixel, aritmética inteira** para 224×224
- Tensor `float32 NHWC [1,224,224,3]` na faixa **0–255** (não dividir por 255 — a camada `Rescaling` da MobileNetV3 está incorporada no modelo via `include_preprocessing=True`)

---

## Arquitetura

```
Compose UI → ViewModel → Repository → Room / arquivos privados
                                 ↓
                          LiteRT Interpreter (reutilizado, validado na inicialização)
```

- **MVVM pragmático** com injeção manual no `Application`
- `Mutex` no Repository serializa inferência e exclusão (Interpreter não é thread-safe)
- `LeafClassifier` valida **hashes, shapes, dtype, ordem de classes** uma única vez na inicialização
- `verifyModelAssets` (Gradle) bloqueia build de release sem assets consistentes

---

## Tecnologias

| Camada | Tecnologia |
|---|---|
| **Android** | Kotlin, Jetpack Compose, CameraX, Navigation Compose, Material3 |
| **Persistência** | Room / SQLite (Flow reativo, migrações explícitas) |
| **Machine Learning (treino)** | Python 3.12, TensorFlow/Keras 3, MobileNetV3Small (ImageNet) |
| **Inferência móvel** | TensorFlow Lite / LiteRT 1.4.0 (API Interpreter, float32) |
| **Build** | Gradle 8.9, AGP 8.7.3, JDK 17, compile/target SDK 35 |
| **Mínimo Android** | API 26 (Android 8.0) |

---

## Machine Learning

### Baseline integrado no Android (atual)

- **Arquitetura:** MobileNetV3Small (~0,95 M parâmetros, ~3,8 MB TFLite float32)
- **Transfer learning** em duas etapas:
  1. Backbone congelado — só cabeça nova (GlobalPooling + Dropout 0,25 + Dense 16 softmax)
  2. Fine-tuning últimas 30 camadas, LR 1e-5, BatchNorm em `training=False`
- **Otimizador:** AdamW com pesos de classe inversamente proporcionais à frequência de treino (razão ≥ 1,5)
- **Early stopping** + `ReduceLROnPlateau` por fase; melhor fase escolhida por validação

### Ensemble experimental (ainda NÃO integrado no Android)

- **Composição:** Média de 3 modelos — MobileNetV3Small (baseline) + MobileNetV3Small/RMSprop + MobileNetV3Large
- **Calibração:** Temperature scaling `T = 0,8421423931819505` + threshold `0,74` (escolhidos em validação)
- **Tamanho total:** ~19,5 MB (3 × TFLite float32)
- **Por que não está no app ainda:** precisa de validação em **dispositivo Android real** para medir latência, memória, aquecimento, tamanho do APK, impacto na bateria e estabilidade antes de substituir o baseline

---

## Dataset

- **Origem:** 696 imagens brutas da seção TV3 do dataset TLA (16 classes)
- **Taxonomia:** `class_map.yaml` do TTDD
- **Split group-aware (seed 42):** 489 treino / 104 validação / 103 teste
- **Agrupamento:** classe + identificador IMG + dHash (distância ≤ 4) — grupos nunca divididos entre splits
- **Auditoria:** 0 arquivos inválidos, 0 duplicatas exatas
- **Classes raras:** anthracnose, TSWV, black_shank, genetic_abnormality têm pouquíssimas amostras (1–2 no teste)

> Os ZIPs completos não são redistribuídos. Siga o pipeline em `machine-learning/` para reproduzir.

---

## Resultados

### Comparação: Baseline integrado vs. Ensemble experimental

| Métrica | **Baseline integrado no app** | Ensemble experimental (não no app) |
|---|---:|---:|
| **Top-1 accuracy** | **77,67%** (80/103) | 80,58% (83/103) |
| **Macro-F1** | **0,7157** | 0,7659 |
| **Top-3 accuracy** | **97,09%** (100/103) | 99,03% (102/103) |
| **Cobertura (threshold)** | 76,70% (thr 0,70) | 75,73% (thr 0,74) |
| **Acurácia entre aceitos** | 89,87% | 92,31% (72/78) |
| **Tamanho do modelo** | ~3,8 MB | ~19,5 MB (3 modelos) |
| **Inferências por imagem** | 1 | 3 |
| **Status no Android** | ✅ Integrado e validado no fluxo completo | ❌ Não integrado — pendente validação móvel |

> **Nunca diga que o Android atual possui 80,58%.** O app roda o baseline (77,67%). O ensemble é recomendação para próxima versão **após** validação em device real.

### Validações executadas

- **Python:** 28 testes aprovados (`pytest -q`)
- **Bundle ML:** `validate_bundle.py --require-model` → `model_bundle_valid` (16 classes, hashes e ordem consistentes)
- **Android unit tests:** `testDebugUnitTest` → PASS (8 testes)
- **Assets ML:** `verifyModelAssets` → PASS
- **Build debug:** `assembleDebug` → PASS
- **Total:** 36 testes aprovados (28 Python + 8 Android)

> **Não executados:** `connectedDebugAndroidTest`, testes em emulador/dispositivo físico, validação manual de câmera/galeria/persistência (ver `docs/MANUAL_TESTS.md`)

---

## Funcionamento offline e privacidade

- **Manifesto sem `INTERNET`** — confirmado no merge
- **Câmera:** permissão `CAMERA` apenas
- **Galeria:** seletor de documentos do sistema (sem acesso amplo a armazenamento)
- **Dados:** ficam no dispositivo (Room + `filesDir/photos/`)
- **Backup automático:** desativado (`android:allowBackup="false"`)
- **Desinstalar/limpar dados** remove todo o histórico
- **Nenhum login, analytics, telemetria, Firebase, Supabase**

---

## Estrutura do repositório

```
leafcare/
├── android-app/          # Aplicativo Android nativo (Kotlin/Compose)
├── machine-learning/     # Pipeline: import → audit → split → train → eval → export → validate
├── docs/                 # Documentação técnica (arquitetura, dataset, validação, decisões, roadmap, licenças)
├── samples/              # Imagem de referência e fixture de pré-processamento
├── ROADMAP.md            # Próximos passos e critérios de saída
├── LICENSE               # MIT (código original)
└── README.md             # Este arquivo
```

**Não versionados (`.gitignore`):** datasets completos, ambientes virtuais, diretórios de build, APKs, caches, checkpoints Keras.

---

## Instalação rápida

> Para o guia completo (Windows/PowerShell, Python, Android SDK, device físico, testes instrumentados, checklist), veja:
> 📖 **[docs/LOCAL_TESTING_SETUP.md](docs/LOCAL_TESTING_SETUP.md)**

### Resumo (Android)

```powershell
# 1. Abra android-app no Android Studio (JDK 17, SDK 35)
# 2. Configure local.properties com sdk.dir
# 3. Sync + Run 'app' no emulador ou device
```

### Resumo (Machine Learning)

```bash
cd machine-learning
python3.12 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python -m pytest -q
python validate_bundle.py --require-model
```

---

## Testes

| Camada | Comando | Status conhecido |
|---|---|---|
| Python (ML) | `pytest -q` | 28 passed |
| Validação bundle | `python validate_bundle.py --require-model` | PASS |
| Android unit | `./gradlew testDebugUnitTest` | PASS (8 testes) |
| Assets ML | `./gradlew verifyModelAssets` | PASS |
| Build debug | `./gradlew assembleDebug` | PASS |
| Instrumentados | `./gradlew connectedDebugAndroidTest` | **Não executado** |
| Manual (device) | `docs/MANUAL_TESTS.md` | **Não executado** |

> Métricas reportadas referem-se ao experimento controlado (split 489/104/103). **Não são validação de campo.**

---

## Documentação

| Documento | Descrição |
|---|---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Arquitetura Android, offline, treinamento, contratos |
| [DATASET.md](docs/DATASET.md) | Dataset V3, split, limitações, reprodução, direitos |
| [VALIDATION.md](docs/VALIDATION.md) | Evidências de validação V3 (ML + Android) |
| [TECHNICAL_DECISIONS.md](docs/TECHNICAL_DECISIONS.md) | 20 decisões técnicas com justificativa e trade-offs |
| [BENCHMARK_MODELOS.md](docs/BENCHMARK_MODELOS.md) | Benchmark 14 configs + ensemble, exportação, limitações |
| [RELATORIO_COMPARATIVO_MODELOS_LEAFCARE.md](docs/RELATORIO_COMPARATIVO_MODELOS_LEAFCARE.md) | Relatório comparativo estendido |
| [ENSEMBLE_ANDROID_INTEGRATION.md](docs/ENSEMBLE_ANDROID_INTEGRATION.md) | Contrato para integrar ensemble no Android |
| [INFERENCE_CONTRACT.md](docs/INFERENCE_CONTRACT.md) | Contrato de entrada/saída do modelo (Python ↔ Android) |
| [MANUAL_TESTS.md](docs/MANUAL_TESTS.md) | Roteiro de testes manuais em device físico |
| [PRESENTATION_GUIDE_PT_BR.md](docs/PRESENTATION_GUIDE_PT_BR.md) | Guia de apresentação acadêmica/empresa (PT-BR) |
| [ROADMAP.md](ROADMAP.md) | Roadmap em inglês (fases 1–6) |
| [ROADMAP_PT_BR.md](docs/ROADMAP_PT_BR.md) | Roadmap em português |
| [THIRD_PARTY.md](docs/THIRD_PARTY.md) | Licenças de terceiros (datasets, fontes, pesos, imagens) |
| [LOCAL_TESTING_SETUP.md](docs/LOCAL_TESTING_SETUP.md) | Guia completo de configuração e testes locais |

---

## Limitações

**Declare-se explicitamente para evitar overclaiming:**

- Dataset pequeno (696 imagens) — algumas classes têm 1–2 amostras no teste
- Anthracnose e TSWV: 0/1 acerto no teste (instável)
- **Sem validação independente em campo** no Sul do Brasil
- **Sem validação agronômica completa** — nomes, sintomas, descrições e orientações marcados `reviewed: false`
- Threshold baseline (0,70) **não calibrado**; ensemble usa 0,74 calibrado apenas em validação
- **Softmax não calibrada** → imagens fora do domínio (não-folhas, outras culturas) podem receber confiança alta
- **Ensemble não validado em Android físico** — latência, memória, aquecimento, APK size, bateria desconhecidos
- Classificador de **conjunto fechado** — não detecta "não é folha de fumo" nem doenças fora das 16 classes
- Sem identificadores de propriedade, talhão, planta ou sessão fotográfica
- Sem detecção de blur, baixa luz, folha distante, fora de enquadramento
- App é **protótipo acadêmico** — roadmap fases 3–6 para uso real em campo

---

## Roadmap

Resumo das próximas fases (critérios de saída objetivos em cada uma):

1. **Fechar protótipo acadêmico** — device físico, testes manuais, revisão agronômica, vídeo demo
2. **Decidir estratégia de inferência** — branch ensemble, medir em entry-level e mid-range, comparar com baseline
3. **Melhorar confiabilidade do modelo** — mais dados independentes (Sul BR), classes raras, split por propriedade, calibração (ECE, Brier), validação cruzada agrupada
4. **Rejeitar fotos inadequadas** — blur, low-light, distância, OOD ("não é folha"), amostras negativas
5. **Workflow de campo completo** — propriedade/talhão, notas, exportação para técnico, retenção/exclusão de fotos
6. **Release engineering** — CI, versionamento, assinatura, GitHub Releases, licenças confirmadas

📖 Detalhes: **[ROADMAP.md](ROADMAP.md)** | **[docs/ROADMAP_PT_BR.md](docs/ROADMAP_PT_BR.md)**

---

## Licença e atribuições

- **Código original:** licença [MIT](LICENSE)
- **Datasets, imagens de referência, fontes, pesos pré-treinados (ImageNet) e dependências de terceiros:** mantêm suas **próprias licenças** — **não** são cobertos pelo MIT
- Antes de redistribuir ou usar comercialmente, revise: **[docs/THIRD_PARTY.md](docs/THIRD_PARTY.md)**

---

*LeafCare — triagem visual offline para o produtor no campo. Projeto acadêmico com engenharia honesta.*
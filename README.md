# LeafCare

Aplicativo Android nativo para **triagem visual** de doenças e alterações em folhas de fumo, com conta, histórico sincronizado e funcionamento offline.

O produtor captura ou seleciona uma foto e o modelo roda **direto no celular** (sem depender de nuvem para classificar). Com internet, análises e fotos sincronizam com o backend e o histórico pode ser restaurado em outro aparelho.

> **Importante:** LeafCare é uma ferramenta de **triagem visual**, **não** um diagnóstico agronômico definitivo. Os resultados devem ser confirmados por um profissional qualificado.

---

## Visão geral

- **Conta obrigatória** (Supabase Auth: cadastro, login, logout, recuperação por código OTP in-app, sessão persistida)
- **Offline-first** — classificação, histórico, câmera e galeria funcionam sem internet após autenticação anterior
- **Inferência local** — MobileNetV3Small via LiteRT/TFLite, float32, sem rede
- **Histórico local** — Room/SQLite (fotos em armazenamento privado)
- **Sincronização em background** — WorkManager: análises (upsert idempotente), fotos privadas, tombstones de exclusão, restore multi-device
- **Segurança** — RLS por usuário, bucket privado, sem service_role no app, isolamento de dados entre contas no aparelho

---

## O problema

Doenças foliares no fumo causam perdas significativas. O diagnóstico precoce depende de profissionais que nem sempre estão disponíveis na propriedade no momento certo. Ferramentas baseadas em nuvem falham sem sinal de celular — por isso a classificação é local e a nuvem serve para conta, backup e multi-device.

---

## A solução

1. Produtor cria conta ou entra (primeiro acesso exige internet)
2. Fotografa a folha ou escolhe da galeria
3. O modelo executa a classificação **localmente/offline**
4. Mostra as **3 classes mais prováveis** com confiança (ou "Inconclusivo" abaixo do limiar)
5. Salva no histórico local **imediatamente**
6. Com internet, sincroniza análise + foto em background (sem bloquear a UI)
7. Em outro aparelho, login restaura o histórico (texto imediato; fotos baixadas em background)

---

## Principais funcionalidades

| Funcionalidade | Descrição |
|---|---|
| **Câmera + Galeria** | CameraX para captura; seletor de documentos do sistema para importar |
| **Inferência local** | LiteRT 1.4.0, float32, 2 threads, sem rede |
| **Top-3 + threshold** | 3 hipóteses; abaixo do limiar → "Inconclusivo" |
| **Orientação fotográfica** | Tela de ajuda com 4 exemplos (correta, desfocada, distante, pouca luz) |
| **Histórico Room** | Busca, filtros, exclusão com confirmação; fonte única da UI |
| **Conta e perfil** | Cadastro/login/logout, recuperação OTP, perfil com nome e e-mail |
| **Sync offline-first** | WorkManager (só com rede, backoff, sobrevive a restart); upsert idempotente por UUID; tombstones; retry |
| **Fotos privadas** | Bucket `analysis-photos` privado em `{user_id}/{analysis_id}.jpg`; cache local |
| **Restore** | Login em aparelho novo reconstrói o histórico (texto + fotos em background) |
| **Privacidade** | RLS por usuário; backup automático desativado; isolamento entre contas no aparelho |

---

## Como funciona

```
Foto / Galeria
      ↓
Preprocessing (decode → EXIF → center crop → resize 224×224 → float32 RGB 0–255)
      ↓
MobileNetV3Small + LiteRT (local, sem rede)
      ↓
Top-3 + threshold (0.70 baseline)
      ↓
Room (estado PENDING_UPLOAD) → resultado imediato na UI
      ↓ (background, com internet)
Supabase: upsert analysis → upload foto → photo_path → tombstones → restore
```

**Detalhes do pré-processamento (contrato Python ↔ Android idêntico):** decode com `BitmapFactory` + `inPreferredColorSpace=SRGB`; correção EXIF (1–8); center crop quadrado; resize bilinear **half-pixel, aritmética inteira** 224×224; tensor `float32 NHWC [1,224,224,3]` faixa **0–255** (camada `Rescaling` incorporada no modelo).

---

## Arquitetura

```
Compose UI → ViewModel → Repository → Room (fonte da UI) → Sync Engine / WorkManager → Supabase
                                              ↓
                                    LiteRT Interpreter (classificação local, nunca na nuvem)
```

- **MVVM pragmático**, injeção manual no `Application`
- **Room é a fonte da UI** — Supabase nunca é observado diretamente
- **Sync**: `PENDING_UPLOAD` → `SYNCED` → `PENDING_DELETE`/`ERROR`; fotos com `photoSyncStatus` separado (`REMOTE_ONLY` = só no servidor)
- **Exclusões por tombstone** (`deleted_at`); deleção remota nunca ressuscita dado local
- **Restore por UUID**; pendentes locais nunca sobrescritos; tombstones remotos respeitados
- **Isolamento entre contas**: wipe local na troca de conta; worker aborta se a sessão mudar
- Detalhes em [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) e [docs/TECHNICAL_DECISIONS.md](docs/TECHNICAL_DECISIONS.md)

---

## Tecnologias

| Camada | Tecnologia |
|---|---|
| **Android** | Kotlin, Jetpack Compose, CameraX, Navigation Compose, Material3, WorkManager |
| **Persistência** | Room / SQLite v3 (migrations 1→2→3, schema exportado) |
| **Backend** | Supabase 2.1.0 (Auth/gotrue, PostgREST, Storage) + Ktor OkHttp |
| **ML (treino)** | Python 3.12, TensorFlow/Keras 3, MobileNetV3Small (ImageNet) |
| **Inferência móvel** | LiteRT 1.4.0 (float32) |
| **Build** | Gradle 8.9, JDK 17, compile/target SDK 35, minSdk 26 |

---

## Machine Learning (resumo)

Baseline integrado: **MobileNetV3Small**, transfer learning em duas etapas, 16 classes, 696 imagens (split group-aware 489/104/103, seed 42).

| Métrica (teste, 103 imgs) | Baseline no app |
|---|---:|
| **Top-1 accuracy** | **77,67%** |
| **Macro-F1** | **0,7157** |
| **Top-3 accuracy** | **97,09%** |
| **Threshold** | 0,70 |

Ensemble experimental (80,58% top-1) **não** está no app — pendente de validação móvel. Detalhes em [docs/MODEL_CARD.md](docs/MODEL_CARD.md).

---

## Dataset

- 696 imagens da seção TV3 do dataset TLA (16 classes), split group-aware seed 42
- Classes raras com 1–2 amostras no teste (anthracnose, TSWV, black_shank, genetic_abnormality)
- ZIPs completos não redistribuídos; pipeline em `machine-learning/`

---

## Supabase

- **Projeto:** `leafcare` (`nhkqfanjfcivcbndivav`, `sa-east-1`)
- **Tabelas:** `profiles` (trigger `handle_new_user` via `display_name`), `analyses` (RLS por `auth.uid()`, soft-delete `deleted_at`)
- **Storage:** bucket privado `analysis-photos` em `{user_id}/{analysis_id}.jpg`
- **App usa:** `SUPABASE_URL` + `SUPABASE_PUBLISHABLE_KEY` via `local.properties` → `BuildConfig` (nunca commitada)
- **Nunca no app:** `service_role`, senhas de banco, JWT secret
- Setup em [docs/SUPABASE_SETUP.md](docs/SUPABASE_SETUP.md)

---

## Estrutura do repositório

```
leafcare/
├── android-app/          # App Android (Kotlin/Compose, Room, WorkManager, Supabase)
├── machine-learning/     # Pipeline: import → audit → split → train → eval → export → validate
├── supabase/             # Migrations versionadas (profiles, analyses, storage, hardening)
├── docs/                 # Documentação técnica
├── samples/              # Imagem de referência e fixture de pré-processamento
├── ROADMAP.md            # Roadmap histórico (inglês)
├── LICENSE               # MIT (código original)
└── README.md             # Este arquivo
```

**Não versionados:** datasets completos, ambientes virtuais, `local.properties`, diretórios de build, APKs, caches, checkpoints.

---

## Instalação rápida

Guia completo em [docs/INSTALL.md](docs/INSTALL.md). Resumo:

```powershell
# Android (Windows/PowerShell, JDK 17, SDK 35)
cd android-app
Copy-Item local.properties.example local.properties
# edite local.properties: sdk.dir + SUPABASE_PUBLISHABLE_KEY real
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

```bash
# ML (reproduzir treino/validação)
cd machine-learning
python3.12 -m venv .venv && pip install -r requirements.txt
python -m pytest -q
```

---

## Testes

| Camada | Comando | Status conhecido |
|---|---|---|
| Python (ML) | `pytest -q` | 28 passed |
| Validação bundle | `python validate_bundle.py --require-model` | PASS |
| Android unit | `./gradlew testDebugUnitTest` | PASS (115 testes) |
| Assets ML | `./gradlew verifyModelAssets` | PASS |
| Lint | `./gradlew lintDebug` | PASS |
| Build debug | `./gradlew assembleDebug` | PASS (APK ~62 MB) |
| Instrumentados | `./gradlew connectedDebugAndroidTest` | **Não executado** |
| Manual (device) | `docs/FINAL_QA_CHECKLIST.md` | **Pendente (bateria final)** |

> Métricas reportadas referem-se ao experimento controlado. Testes físicos finais pendentes — **não executar release** antes de `docs/FINAL_QA_CHECKLIST.md`.

---

## Documentação

| Documento | Descrição |
|---|---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Arquitetura Android + nuvem, sync, contratos |
| [TECHNICAL_DECISIONS.md](docs/TECHNICAL_DECISIONS.md) | Decisões técnicas com justificativa e trade-offs |
| [MODEL_CARD.md](docs/MODEL_CARD.md) | Modelo integrado: dados, métricas, limitações |
| [INSTALL.md](docs/INSTALL.md) | Instalação reproduzível (Android + ML) |
| [SUPABASE_SETUP.md](docs/SUPABASE_SETUP.md) | Projeto, migrations, RLS, Storage, config Android |
| [FINAL_QA_CHECKLIST.md](docs/FINAL_QA_CHECKLIST.md) | Bateria final de testes físicos (A–Z) |
| [MVP_ROADMAP.md](docs/MVP_ROADMAP.md) | Fases 0–8 e estado real de cada uma |
| [CHANGELOG.md](CHANGELOG.md) | Histórico de mudanças do MVP |

---

## Limitações

- Dataset pequeno (696 imagens); classes raras instáveis
- **Sem validação independente em campo** no Sul do Brasil
- **Sem validação agronômica completa** (`reviewed: false`)
- Threshold 0,70 **não calibrado**; softmax não calibrada (OOD pode ter confiança alta)
- Ensemble não validado em Android físico
- Classificador de **conjunto fechado** (16 classes)
- Testes físicos finais pendentes — sem release até `FINAL_QA_CHECKLIST.md`

---

## Licença e atribuições

- **Código original:** licença [MIT](LICENSE)
- **Datasets, imagens, fontes, pesos ImageNet e terceiros:** licenças próprias — **não** cobertos pelo MIT ([docs/THIRD_PARTY.md](docs/THIRD_PARTY.md))

---

*LeafCare — triagem visual offline-first para o produtor no campo. Conta, sincronização e histórico multi-device sobre classificação 100% local.*

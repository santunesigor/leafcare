# Arquitetura do LeafCare

Este documento concentra os detalhes técnicos que não precisam ficar no README principal.

## Visão geral

```text
Compose UI
   ↓
ViewModel
   ↓
Repository
   ├── Room / SQLite ──► WorkManager ──► Supabase
   └── LeafClassifier ──► LiteRT / TFLite
```

O aplicativo segue uma arquitetura MVVM pragmática. A UI observa estado exposto pelos ViewModels; os ViewModels acionam repositories; e o Room funciona como fonte local principal do histórico.

## Pacotes Android

```text
br.com.leafcare
├── auth/
│   ├── AuthRepository.kt
│   ├── AuthViewModel.kt
│   └── SupabaseClientHolder.kt
├── data/
│   ├── AnalysisRepository.kt
│   ├── AnalysisSyncApi.kt
│   ├── AnalysisSyncRunner.kt
│   ├── AppDatabase.kt
│   ├── DiseaseCatalog.kt
│   ├── SyncAnalysesWorker.kt
│   └── SyncState.kt
├── ml/
│   ├── ImageDecoder.kt
│   ├── LeafClassifier.kt
│   ├── PixelPreprocessor.kt
│   └── Prediction.kt
├── ui/
│   ├── auth/
│   ├── CameraScreen.kt
│   ├── HelpSheet.kt
│   ├── HistoryScreen.kt
│   ├── LeafCareTheme.kt
│   ├── LeafCareViewModel.kt
│   └── ResultScreen.kt
├── LeafCareApplication.kt
└── MainActivity.kt
```

## Persistência local

O banco Room usa schema versão 3.

A entidade `AnalysisEntity` armazena:

- UUID da análise;
- nome da foto;
- timestamp;
- classe principal;
- nome de exibição e nome científico;
- confiança;
- Top-3 serializado;
- flag de resultado inconclusivo;
- threshold aplicado;
- tempo de inferência;
- hash do modelo;
- estado de sync da análise;
- tombstone de exclusão;
- estado de sync da foto.

### Estado da análise

```text
PENDING_UPLOAD → SYNCED
       │            │
       └── ERROR    └──► PENDING_DELETE → remoção remota
```

### Estado da foto

```text
PENDING_UPLOAD → SYNCED
       │
       └── ERROR

REMOTE_ONLY → foto presente apenas no servidor após restore
```

## Sincronização

O WorkManager processa sincronização somente quando há rede.

Características principais:

- upsert por UUID;
- retries para falhas temporárias;
- fila persistente em Room;
- tombstones para exclusão;
- upload de foto separado do upload da análise;
- restore de análises remotas;
- download posterior de fotos `REMOTE_ONLY`;
- proteção contra troca de sessão durante a execução do worker.

A UI não depende de resposta imediata do backend. Primeiro o estado local é persistido; depois a sincronização é executada em background.

## Contrato de inferência Python ↔ Android

O bundle do modelo contém:

```text
leafcare.tflite
classes.json
model_metadata.json
diseases.json
```

Antes da inferência, o Android verifica:

- `schema_version` esperado;
- status `trained`;
- hash SHA-256 do modelo;
- hash e ordem das classes;
- shape de entrada `[1,224,224,3]`;
- dtype de entrada `float32`;
- shape de saída `[1,16]`;
- dtype de saída `float32`;
- estratégia de resize registrada no metadata;
- normalização registrada no metadata.

### Pré-processamento

O contrato atual é:

1. decodificar a imagem;
2. aplicar orientação EXIF;
3. converter para sRGB/RGB;
4. fazer center crop quadrado;
5. redimensionar para `224 × 224`;
6. gerar tensor `float32` na faixa `0–255`;
7. não aplicar normalização externa, pois o preprocessing da MobileNetV3 está incorporado ao modelo.

O identificador registrado para resize é:

```text
center_crop_bilinear_integer_v1
```

O objetivo é evitar divergências de pixels entre o pipeline Python e o aplicativo Android.

## Política de predição

A saída do modelo contém 16 scores. A aplicação ordena os scores e exibe as três maiores hipóteses.

O threshold padrão vem do `model_metadata.json` e atualmente é `0.70`.

Quando a maior confiança fica abaixo do threshold, o resultado é tratado como **Inconclusivo**. Isso reduz a quantidade de previsões aceitas, mas não transforma a confiança softmax em probabilidade clínica/agronômica calibrada.

## Auth e deep links

O `MainActivity` aceita:

```text
leafcare://auth/confirm-email
leafcare://auth/reset-password
```

Esses deep links permitem retornar ao app após ações de e-mail do Supabase Auth.

## Decisões principais

### Kotlin + Compose

Foi escolhido por integração direta com Android, CameraX, Room, WorkManager e LiteRT, sem bridge de framework multiplataforma.

### Room como fonte da UI

Evita que a interface dependa da disponibilidade da rede e simplifica o comportamento offline-first.

### WorkManager

É utilizado porque a sincronização precisa sobreviver a reinicializações do processo e aguardar conectividade.

### Inferência local

Reduz dependência de rede, evita upload obrigatório da foto para classificação e mantém a resposta rápida no campo.

### Supabase

Centraliza Auth, banco PostgreSQL, PostgREST, Storage e RLS sem exigir backend próprio para o MVP.

## Pontos que exigem cuidado ao alterar o projeto

Mudanças nestes itens devem ser feitas em conjunto entre Python e Android:

- tamanho da imagem;
- ordem das classes;
- estratégia de crop/resize;
- faixa e dtype da entrada;
- normalização;
- quantidade de classes;
- threshold registrado no metadata;
- hashes do bundle.

Depois de modificar o modelo, rode:

```bash
cd machine-learning
python export_tflite.py --config config.yaml
python validate_bundle.py --require-model
```

E depois:

```bash
cd ../android-app
./gradlew verifyModelAssets
./gradlew testDebugUnitTest
```

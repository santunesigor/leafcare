# LeafCare

Aplicativo Android offline-first para triagem visual de doenças e alterações em folhas de fumo. A inferência roda no aparelho; o Supabase é usado para autenticação e sincronização do histórico.

> O LeafCare é uma ferramenta de triagem. Não substitui diagnóstico agronômico ou avaliação profissional.

## O que oferece

- Captura pela câmera ou seleção pela galeria.
- Classificação local com MobileNetV3Small, até três hipóteses e resultado inconclusivo abaixo do limiar.
- Histórico local em Room, disponível offline após autenticação.
- Sincronização em segundo plano e restauração do histórico pelo Supabase.

## Começar

Requisitos: Android Studio, JDK 17 e Android SDK 35.

```powershell
cd android-app
Copy-Item local.properties.example local.properties
```

Configure `sdk.dir` e `SUPABASE_PUBLISHABLE_KEY` em `android-app/local.properties`. Não use nem versione chaves administrativas ou arquivos de configuração locais.

```powershell
.\gradlew.bat assembleDebug
```

O APK é gerado em `android-app/app/build/outputs/apk/debug/app-debug.apk`.

## Documentação

- [Desenvolvimento e setup](docs/DEVELOPMENT.md): ambiente e comandos.
- [Contexto e regras do projeto](docs/AI_CONTEXT.md): arquitetura, contratos e limites de mudança.
- [Arquitetura](docs/ARCHITECTURE.md): persistência, inferência e sincronização.
- [Machine Learning](docs/MACHINE_LEARNING.md): modelo, métricas e pipeline.
- [Supabase](docs/SUPABASE.md): migrations, Auth, RLS e Storage.
- [Testes e QA](docs/TESTING.md): comandos, roteiro e evidências registradas.
- [Licença da fonte Inter](docs/legal/Inter-OFL.txt) e [fontes das imagens](docs/legal/referencias_manifest.csv).
- [Histórico de versões](CHANGELOG.md).

## Estrutura

```text
android-app/       aplicativo Android
machine-learning/  pipeline e validação do modelo
supabase/          migrations do backend
docs/              documentação e evidências
samples/           fixtures de validação
```

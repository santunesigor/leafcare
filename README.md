# LeafCare

LeafCare é um aplicativo Android de triagem visual de doenças e alterações em folhas de fumo. A imagem é classificada no próprio aparelho com um modelo MobileNetV4 Small; o Supabase fornece autenticação e sincronização, não faz a inferência.

O resultado apresenta até três hipóteses e pode ser marcado como inconclusivo. A pontuação é uma estimativa do modelo, não um diagnóstico agronômico. Confirme resultados importantes com um profissional qualificado.

## O que o app faz

- Captura uma foto ou importa uma imagem da galeria e classifica a folha localmente.
- Mantém análises e histórico no aparelho; após o primeiro acesso autenticado, essas funções ficam disponíveis offline.
- Sincroniza com a conta as análises e as fotos elegíveis quando há conexão. Fotos de previsões inconclusivas permanecem no aparelho.
- Oferece contribuição de fotos para treinamento como opção separada: é necessário ativar o consentimento geral e autorizar cada foto. Recusar ou revogar mantém a classificação e o histórico disponíveis.
- Abre os Termos de Uso e a Política de Privacidade dentro do app. O LeafCare não tem site; confirmações e recuperações de acesso retornam ao aplicativo por deep link.

## Baixar e instalar

Consulte a página de [releases](https://github.com/santunesigor/leafcare/releases) para baixar o APK debug mais recente. No Android, permita a instalação do arquivo baixado e abra o LeafCare. APKs debug são destinados a teste.

## Executar localmente

Requisitos: Android Studio, JDK 17, Android SDK 35 e Git. Para compilar no Linux ou macOS:

```bash
cd android-app
cp local.properties.example local.properties
```

Edite `local.properties` com o caminho do Android SDK e a chave **publicável** do Supabase (`SUPABASE_PUBLISHABLE_KEY`). Não versione esse arquivo nem use uma chave `service_role` no aplicativo. Abra a pasta `android-app` no Android Studio ou use o wrapper:

```bash
bash ./gradlew assembleDebug
```

O APK padrão fica em `android-app/app/build/outputs/apk/debug/app-debug.apk`. O build também cria um APK de distribuição com versão e commit em `android-app/app/build/outputs/apk/distribution/debug/`.

No Windows, use `gradlew.bat` no lugar de `bash ./gradlew` e crie a configuração com `Copy-Item local.properties.example local.properties` no PowerShell.

## Validar mudanças

Na pasta `android-app`:

```bash
bash ./gradlew testDebugUnitTest verifyModelAssets lintDebug assembleDebug
```

Testes do backend e as validações estritas do OpenSpec ficam na raiz do repositório:

```bash
bash supabase/tests/run-pglite.sh
openspec validate --all --strict --no-interactive
openspec validate --archived --strict --no-interactive
```

O roteiro manual de autenticação, câmera, histórico, modo offline e sincronização está em [Testes e QA](docs/TESTING.md).

## Machine Learning

O bundle Android atual contém MobileNetV4 Small destilado em TFLite, com 16 classes. O dataset completo não é distribuído neste repositório. Para comandos do pipeline, requisitos de Python, avaliação e limites das métricas, consulte [Machine Learning](docs/MACHINE_LEARNING.md) e [machine-learning/README.md](machine-learning/README.md).

O modelo só deve ser substituído após solicitação explícita e validação do contrato Android. O processo de treino e exportação está separado da inferência incluída no app.

## Documentação

- [Arquitetura Android](docs/ARCHITECTURE.md)
- [Desenvolvimento e ferramentas](docs/DEVELOPMENT.md)
- [Testes e QA](docs/TESTING.md)
- [Machine Learning](docs/MACHINE_LEARNING.md) e [benchmarks](docs/benchmarks/BENCHMARK_MODELOS.md)
- [Supabase, migrations e sincronização](docs/SUPABASE.md)
- [Administração e revisão de fotos](docs/SUPERADMIN.md)
- [Contexto de desenvolvimento e limites](docs/AI_CONTEXT.md)
- [OpenSpec](openspec/README.md)
- [Histórico de versões](CHANGELOG.md)

Relatos de experimentos e jornadas em `docs/explain/` são material histórico e educacional; instruções operacionais atuais ficam nos guias acima.

## Estrutura do repositório

```text
android-app/       aplicativo Android, testes e bundle TFLite
machine-learning/  pipeline, artefatos de pesquisa e ferramentas de exportação
supabase/          migrations, funções e testes locais do backend
docs/              arquitetura, operação, QA, proveniência e relatórios
openspec/          especificações atuais e mudanças arquivadas
.agents/skills/    workflows OpenSpec usados no projeto
```

## Licenças e proveniência

As licenças das fontes e a proveniência das imagens de referência estão em [`docs/legal/`](docs/legal/). O dataset TLA completo não está incluído. Consulte as licenças originais antes de redistribuir dados ou materiais derivados.

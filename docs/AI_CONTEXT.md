# LeafCare — AI Context

Este arquivo é a referência operacional para qualquer IA que trabalhe no repositório LeafCare.

Ele deve ser lido antes de alterações relevantes no projeto.

> Regra principal: o código e os testes são a fonte de verdade. Se este documento divergir do código, investigue antes de alterar qualquer coisa.

---

## 1. Projeto

LeafCare é um aplicativo Android para triagem visual de doenças e alterações em folhas de fumo.

Objetivos principais:

- funcionar em campo;
- realizar classificação local no celular;
- continuar funcional sem internet após autenticação;
- manter histórico local;
- sincronizar histórico e fotos quando houver conexão;
- permitir que o usuário acesse seu histórico em outro dispositivo.

LeafCare é uma ferramenta de triagem visual e NÃO deve ser apresentado como diagnóstico agronômico definitivo.

---

## 2. Estado atual do aplicativo

### Android

Stack principal:

- Kotlin;
- Jetpack Compose;
- CameraX;
- Navigation Compose;
- Room / SQLite;
- LiteRT / TensorFlow Lite;
- arquitetura MVVM pragmática.

O aplicativo já possui:

- câmera;
- seleção de imagem da galeria;
- classificação local;
- top-3 resultados;
- resultado inconclusivo por threshold;
- histórico local;
- pesquisa/filtros;
- exclusão de análises;
- armazenamento privado das fotos;
- tela de resultado;
- orientações para captura de imagem.

---

## 3. Machine Learning atual

Modelo integrado: **MobileNetV3Small**.

Técnica:

- transfer learning;
- pesos ImageNet;
- treinamento em duas etapas;
- backbone congelado inicialmente;
- fine-tuning controlado;
- exportação TFLite float32.

Métricas do baseline integrado:

- Top-1 accuracy: 77,67%
- Macro-F1: 0,7157
- Top-3 accuracy: 97,09%

Dataset atual:

- 696 imagens;
- 16 classes;
- 489 treino;
- 104 validação;
- 103 teste;
- split group-aware;
- seed 42.

Threshold padrão do baseline: `0.70`.

O valor de confiança NÃO deve ser descrito como probabilidade agronômica comprovada.

---

## 4. Ensemble

Existe um ensemble experimental com métricas superiores:

- Top-1: 80,58%
- Macro-F1: 0,7659
- Top-3: 99,03%

Ele combina:

- MobileNetV3Small baseline;
- MobileNetV3Small/RMSprop;
- MobileNetV3Large.

IMPORTANTE:

- o ensemble NÃO faz parte do MVP atual;
- NÃO implementar ensemble sem solicitação explícita;
- NÃO substituir o modelo atual automaticamente;
- NÃO alterar treinamento, dataset ou modelo apenas para tentar melhorar métricas.

O baseline atual é suficiente para o objetivo do MVP neste momento.

---

## 5. Arquitetura desejada para o MVP

O aplicativo deve ser **offline-first**.

```text
Primeiro acesso
     ↓
Internet disponível
     ↓
Cadastro / Login
     ↓
Sessão persistida
     ↓
Aplicativo liberado

Depois:

Foto / Galeria
     ↓
Classificação LOCAL
     ↓
Resultado
     ↓
Room + foto local
     ↓
Resultado disponível imediatamente
     ↓
Se houver internet
     ↓
Sincronização com backend
```

A classificação NUNCA deve depender do backend.

Após uma sessão válida já ter sido criada, perda de internet não deve impedir:

- abrir o app;
- usar câmera;
- usar galeria;
- executar o modelo;
- consultar histórico local;
- criar novas análises.

---

## 6. Backend escolhido

Backend planejado: **Supabase**.

Recursos previstos:

- Supabase Auth;
- PostgreSQL;
- Row Level Security;
- Storage privado;
- sincronização de análises;
- sincronização das fotos.

O backend não deve executar a classificação. A classificação continua no Android.

---

## 7. Autenticação

Para o MVP:

- conta é obrigatória;
- primeiro cadastro/login exige internet;
- sessão deve permanecer persistida;
- usuário autenticado anteriormente pode continuar usando o app sem conexão;
- logout remove a sessão local;
- ao entrar novamente, histórico remoto deve poder ser restaurado.

Recursos mínimos:

- cadastro;
- login;
- logout;
- recuperação de senha;
- perfil básico.

Não adicionar login social sem solicitação explícita.

---

## 8. Persistência e sincronização

Room continua sendo a fonte local usada pela UI.

Uma análise deve ser salva localmente ANTES de qualquer chamada remota.

```text
Análise concluída
     ↓
Room
     ↓
Resultado mostrado
     ↓
syncStatus = pendente
     ↓
Internet disponível?
     ├── não → permanece local
     └── sim
           ↓
        Supabase
           ↓
        syncStatus = sincronizado
```

Uma falha de internet NÃO pode fazer uma análise local falhar.

A sincronização deve ser idempotente. O mesmo UUID da análise deve ser usado local e remotamente sempre que possível. Evitar registros duplicados.

---

## 9. Fotos

A foto original da análise deve permanecer disponível localmente.

Quando houver internet:

- enviar para Storage privado;
- associar à análise;
- controlar acesso por usuário;
- retry em caso de falha.

Estrutura recomendada:

```text
analysis-photos/
  {user_id}/
    {analysis_id}.jpg
```

Uma falha no upload da imagem NÃO invalida a análise local.

---

## 10. Dados remotos previstos

### profiles

Campos aproximados:

- id;
- display_name;
- created_at;
- updated_at.

### analyses

Campos aproximados:

- id;
- user_id;
- created_at;
- class_id;
- display_name;
- scientific_name;
- confidence;
- top3;
- inconclusive;
- threshold;
- inference_ms;
- model_sha256;
- app_version;
- photo_path;
- updated_at;
- deleted_at.

A estrutura final deve ser definida por migration e validada antes da implementação Android.

---

## 11. Segurança

Toda tabela com dados de usuário deve possuir RLS.

Princípio básico:

```text
auth.uid() = user_id
```

Um usuário nunca deve acessar análises ou fotos de outro usuário.

Buckets contendo fotos devem ser privados.

NUNCA:

- colocar `service_role` key no Android;
- versionar secrets;
- expor credenciais privadas;
- desativar RLS para “fazer funcionar”;
- usar bucket público para fotos pessoais do usuário.

---

## 12. Uso futuro das fotos para Machine Learning

Não faz parte do MVP atual.

Não implementar pipeline de coleta para treinamento agora.

No futuro, caso seja implementado:

- deve existir consentimento explícito;
- predição do modelo não deve virar automaticamente ground truth;
- imagens devem passar por revisão humana/agronômica antes de treinamento.

---

## 13. Débitos técnicos conhecidos

Verificar antes de novas alterações:

- resíduo de threshold persistido em SharedPreferences;
- código/documentação devem convergir para threshold controlado pelo bundle do modelo;
- validação física completa ainda deve ser registrada;
- documentação pode conter referências antigas que precisam ser atualizadas somente quando a funcionalidade correspondente for estabilizada.

Não iniciar grandes refactors apenas para eliminar dívida técnica de baixo impacto.

---

## 14. Regras obrigatórias para agentes de IA

Antes de qualquer alteração, executar:

```bash
git status
git branch --show-current
git log --oneline --decorate -10
```

Verificar:

- branch correta;
- working tree;
- alterações anteriores;
- código relevante.

Não assumir que o repositório está limpo.

---

## 15. Git

Nunca trabalhar diretamente em `main`.

Usar branch apropriada.

Para o novo MVP cloud, a branch principal de desenvolvimento prevista é:

```text
feature/mvp-cloud
```

Se ela ainda não existir:

- verificar estado atual;
- confirmar a base apropriada;
- criar a branch sem descartar trabalho existente.

Commits devem seguir Conventional Commits.

Exemplos:

```text
fix(android): stabilize local MVP
feat(backend): add Supabase foundation
feat(auth): add account authentication
feat(sync): add offline-first analysis sync
feat(sync): add private photo synchronization
test(android): cover offline sync behavior
docs: update MVP architecture
```

Fazer commits por unidade lógica.

Sempre informar:

- arquivos alterados;
- testes executados;
- hash;
- mensagem do commit;
- git status.

Não usar sem autorização explícita:

```text
git reset --hard
git checkout .
git clean -fd
force push
```

---

## 16. Regra de escopo

Uma sessão deve resolver UMA etapa ou um problema bem definido.

Evitar:

```text
backend + auth + sync + UI + docs
```

Preferir:

```text
1 sessão
→ 1 objetivo
→ implementação
→ testes
→ diff
→ commit
→ parar
```

---

## 17. Antes de editar código

- leia apenas os arquivos necessários;
- não faça varredura completa pelo repositório sem necessidade;
- não refatore código fora do escopo;
- não altere UX sem necessidade funcional;
- não altere ML sem solicitação explícita;
- não altere métricas existentes;
- não “melhore” números documentados.

---

## 18. Validação

Antes de concluir uma etapa, executar todos os testes razoavelmente relacionados à alteração.

### Machine Learning

```bash
python -m pytest -q
python validate_bundle.py --require-model
```

Baseline conhecido:

```text
28 testes Python aprovados
model_bundle_valid
```

### Android

Dentro de `android-app` no Windows:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat verifyModelAssets
.\gradlew.bat assembleDebug
```

Quando houver device/emulador:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Nunca afirmar que um teste passou se ele não foi executado.

---

## 19. Ordem de prioridade

1. funcionamento;
2. segurança;
3. integridade dos dados;
4. comportamento offline;
5. sincronização;
6. testes;
7. UX;
8. documentação;
9. otimizações.

Não gastar ciclos polindo documentação enquanto o MVP funcional ainda estiver incompleto.

---

## 20. Definição atual do MVP

O MVP estará funcional quando:

- usuário consegue criar conta;
- usuário consegue fazer login;
- sessão permanece válida;
- aplicativo pode ser utilizado offline após login anterior;
- classificação funciona localmente;
- análise é salva no Room;
- foto é salva localmente;
- análise sincroniza quando internet volta;
- foto sincroniza;
- histórico pode ser restaurado em outro dispositivo;
- usuário só acessa seus próprios dados;
- app continua funcionando quando Supabase estiver temporariamente indisponível;
- fluxo principal foi validado em celular Android real.

---

## 21. Fonte de verdade

Em caso de conflito, considerar nesta ordem:

1. código atual;
2. testes;
3. migrations/schema;
4. artefatos do modelo;
5. documentação técnica;
6. este `AI_CONTEXT.md`.

Se encontrar divergência relevante:

- não esconda;
- não corrija silenciosamente algo de grande impacto;
- registre;
- faça a menor correção segura possível;
- atualize este arquivo quando a decisão arquitetural realmente mudar.

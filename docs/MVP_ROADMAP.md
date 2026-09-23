# LeafCare — MVP Roadmap

Este roadmap controla o desenvolvimento do MVP funcional do LeafCare.

Objetivo: transformar o protótipo Android atual em um aplicativo offline-first com conta, persistência remota e histórico sincronizado entre dispositivos.

O Machine Learning atual está congelado durante este MVP.

---

# Estado geral

Legenda:

- [ ] não iniciado
- [~] em andamento
- [x] concluído
- [!] bloqueado

---

# Fase 0 — Preparar desenvolvimento do MVP

Objetivo: estabelecer uma base segura para continuar o desenvolvimento.

- [x] Confirmar branch/base atual.
- [x] Garantir working tree limpa.
- [x] Criar/usar `feature/mvp-cloud`.
- [x] Adicionar `docs/AI_CONTEXT.md`.
- [x] Adicionar `docs/MVP_ROADMAP.md`.
- [x] Executar baseline de testes.
- [x] Registrar resultados iniciais.

Critério de saída:

```text
branch correta
+
working tree limpa
+
baseline conhecido
+
arquivos de contexto versionados
```

Baseline registrado:
- ML: 27 testes pytest aprovados, 1 skipped; validate_bundle.py requer TensorFlow (não disponível no Python 3.14 — pendência de ambiente)
- Android: testDebugUnitTest ✓, verifyModelAssets ✓, assembleDebug ✓

Commit esperado:

```text
docs: add MVP development context
```

---

# Fase 1 — Estabilizar MVP local

Objetivo: garantir que o aplicativo atual funciona corretamente antes de adicionar backend.

Escopo:

- [ ] revisar débitos técnicos que afetam o fluxo principal;
- [ ] remover comportamento obsoleto de threshold, se confirmado;
- [ ] confirmar classificação local;
- [ ] confirmar histórico Room;
- [ ] confirmar armazenamento/exclusão de fotos;
- [ ] confirmar câmera;
- [ ] confirmar galeria;
- [ ] testar resultado inconclusivo;
- [ ] executar testes Android;
- [ ] executar testes ML;
- [ ] testar em celular físico.

Não fazer:

- ensemble;
- novo treinamento;
- grandes refactors;
- backend;
- mudanças visuais não relacionadas.

Critério de saída:

```text
classificação local funcionando
+
histórico funcionando
+
app funcionando offline
+
build/testes aprovados
+
fluxo principal testado em celular
```

---

# Fase 2 — Fundação Supabase

Objetivo: criar infraestrutura remota segura antes de conectar o Android.

Escopo:

- [ ] criar/configurar projeto Supabase;
- [ ] criar migrations versionadas;
- [ ] criar `profiles`;
- [ ] criar `analyses`;
- [ ] definir IDs e timestamps;
- [ ] preparar campos de sincronização;
- [ ] criar bucket privado de fotos;
- [ ] criar RLS;
- [ ] criar policies;
- [ ] testar isolamento entre usuários;
- [ ] documentar configuração necessária no Android.

Não fazer ainda:

- sincronização Android completa;
- histórico multi-device;
- coleta para ML.

Critério de saída:

```text
schema versionado
+
RLS ativo
+
usuário A não acessa usuário B
+
bucket privado
+
migrations reproduzíveis
```

---

# Fase 3 — Autenticação e perfil

Objetivo: tornar conta obrigatória para uso do aplicativo.

Escopo:

- [ ] cadastro;
- [ ] login;
- [ ] logout;
- [ ] recuperação de senha;
- [ ] sessão persistida;
- [ ] perfil básico;
- [ ] tela de autenticação;
- [ ] direcionamento correto ao iniciar o app.

Fluxo esperado:

```text
sem sessão
→ login/cadastro obrigatório

sessão válida
→ app

sessão válida + sem internet
→ app continua disponível
```

Importante: o modelo local não pode depender da internet.

Critério de saída:

```text
primeiro login online funciona
+
sessão persiste
+
app abre offline após login anterior
+
logout funciona
```

---

# Fase 4 — Sincronização de análises

Objetivo: sincronizar o histórico local com o PostgreSQL sem tornar a rede obrigatória.

Arquitetura:

```text
UI
 ↓
Room
 ↓
Sync Engine
 ↓
Supabase
```

Escopo:

- [ ] adicionar estado de sincronização;
- [ ] manter Room como fonte local;
- [ ] salvar análise local imediatamente;
- [ ] criar fila de sincronização;
- [ ] usar WorkManager ou mecanismo adequado;
- [ ] implementar retry;
- [ ] implementar upsert idempotente;
- [ ] impedir duplicações;
- [ ] sincronizar exclusões;
- [ ] tratar erros de rede;
- [ ] testar modo offline → online.

Estados sugeridos:

```text
LOCAL_ONLY
PENDING_UPLOAD
SYNCED
PENDING_DELETE
ERROR
```

Os nomes finais podem mudar conforme a implementação.

Critério de saída:

```text
análise offline funciona
+
internet retorna
+
análise sincroniza automaticamente
+
nenhuma duplicação
```

---

# Fase 5 — Sincronização das fotos

Objetivo: salvar as fotos remotamente sem prejudicar o uso offline.

Escopo:

- [ ] criar upload para Storage privado;
- [ ] usar caminho por usuário/análise;
- [ ] retry automático;
- [ ] registrar `photo_path`;
- [ ] tratar upload incompleto;
- [ ] manter cópia local;
- [ ] proteger acesso com policies.

Estrutura preferida:

```text
analysis-photos/
  user_id/
    analysis_id.*
```

Critério de saída:

```text
foto local continua funcionando
+
foto sincroniza quando possível
+
bucket é privado
+
usuário só acessa suas fotos
```

---

# Fase 6 — Histórico entre dispositivos

Objetivo: permitir que a mesma conta recupere o histórico em outro aparelho.

Cenário obrigatório:

```text
Celular A
→ login
→ cria análise
→ sincroniza

Celular B
→ login na mesma conta
→ histórico é restaurado
→ análise aparece
→ foto fica disponível
```

Escopo:

- [ ] download de análises;
- [ ] merge com Room;
- [ ] download/cache de fotos;
- [ ] deduplicação;
- [ ] conflitos;
- [ ] exclusões;
- [ ] estado de carregamento;
- [ ] tratamento de conexão instável.

Critério de saída:

```text
conta funciona em dois dispositivos
+
histórico consistente
+
sem duplicações
```

---

# Fase 7 — QA e segurança do MVP

Objetivo: validar o sistema completo.

## Autenticação

- [ ] cadastro;
- [ ] login correto;
- [ ] senha errada;
- [ ] recuperação;
- [ ] logout;
- [ ] sessão persistida;
- [ ] início offline.

## Offline

- [ ] classificação sem internet;
- [ ] criação de análise sem internet;
- [ ] histórico local;
- [ ] várias análises offline;
- [ ] retorno da conexão;
- [ ] retry automático.

## Sincronização

- [ ] análise;
- [ ] foto;
- [ ] exclusão;
- [ ] retry;
- [ ] app fechado durante sync;
- [ ] conexão instável;
- [ ] mesmo usuário em dois aparelhos.

## Segurança

- [ ] usuário A não lê dados de B;
- [ ] usuário A não lê fotos de B;
- [ ] RLS ativa;
- [ ] bucket privado;
- [ ] nenhuma service role key no app;
- [ ] nenhum secret versionado.

## Android

- [ ] câmera;
- [ ] galeria;
- [ ] permissões;
- [ ] histórico;
- [ ] reinício;
- [ ] modo avião;
- [ ] device físico.

Critério de saída: nenhum bug crítico conhecido no fluxo principal.

---

# Fase 8 — Fechamento do MVP

Somente depois do código estabilizado.

Atualizar:

- [ ] README.md;
- [ ] ARCHITECTURE.md;
- [ ] TECHNICAL_DECISIONS.md;
- [ ] MODEL_CARD.md;
- [ ] INSTALL.md;
- [ ] CHANGELOG.md;
- [ ] documentação Supabase;
- [ ] checklist de testes.

Opcional:

- [ ] CONTRIBUTING.md.

Depois:

- [ ] rodar todos os testes;
- [ ] gerar build final;
- [ ] registrar versão;
- [ ] preparar demonstração.

---

# Fora do MVP atual

Não implementar sem nova decisão explícita:

- ensemble;
- novo modelo;
- novo dataset;
- retreinamento automático;
- coleta de fotos para Machine Learning;
- painel web agronômico;
- OOD avançado;
- lesion detection;
- propriedade/talhão completo;
- dashboard empresarial;
- atualização dinâmica do modelo.

Esses itens pertencem a versões futuras.

---

# Ordem obrigatória

```text
0. Preparar projeto
        ↓
1. Estabilizar local
        ↓
2. Supabase
        ↓
3. Auth
        ↓
4. Sync análises
        ↓
5. Sync fotos
        ↓
6. Multi-device
        ↓
7. QA + segurança
        ↓
8. Documentação/release
```

Evitar começar fase posterior antes de o critério de saída da anterior estar atendido.

---

# Regra para o OpenCode

Ao iniciar uma sessão:

1. ler `docs/AI_CONTEXT.md`;
2. ler `docs/MVP_ROADMAP.md`;
3. executar `git status`;
4. confirmar branch;
5. identificar a fase atual;
6. executar apenas a próxima unidade lógica;
7. testar;
8. revisar diff;
9. criar commit;
10. atualizar este roadmap somente se o estado real mudou;
11. parar.

Não iniciar automaticamente a próxima fase.

---

# Relatório obrigatório ao terminar uma etapa

Informar:

```text
Fase:
Objetivo executado:
Arquivos alterados:
Mudanças principais:
Testes executados:
Resultados:
Pendências:
Commit:
Git status:
Próxima ação recomendada:
```

Nunca marcar item como concluído sem evidência.

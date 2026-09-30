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
- ML: 28 testes pytest aprovados; validate_bundle.py aprovado com 16 classes, ordem de saida verificada e modelo carregado (Python 3.12).
- Android: testDebugUnitTest ✓, verifyModelAssets ✓, assembleDebug ✓

Commit esperado:

```text
docs: add MVP development context
```

---

# Fase 1 — Estabilizar MVP local

Objetivo: garantir que o aplicativo atual funciona corretamente antes de adicionar backend.

Escopo:

- [x] revisar débitos técnicos que afetam o fluxo principal;
- [x] remover comportamento obsoleto de threshold, se confirmado;
- [x] confirmar classificação local;
- [x] confirmar histórico Room;
- [x] confirmar armazenamento/exclusão de fotos;
- [x] confirmar câmera;
- [x] confirmar galeria;
- [x] testar resultado inconclusivo;
- [x] executar testes Android;
- [x] executar testes ML e validar bundle com Python 3.12.
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

- [x] criar/configurar projeto Supabase;
- [x] criar migrations versionadas;
- [x] criar `profiles`;
- [x] criar `analyses`;
- [x] definir IDs e timestamps;
- [x] preparar campos de sincronização;
- [x] criar bucket privado de fotos;
- [x] criar RLS;
- [x] criar policies;
- [x] testar isolamento entre usuários (conceitual/validado via SQL);
- [x] documentar configuração necessária no Android.

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

**Status**: Concluído. Projeto remoto `leafcare` (ref: `nhkqfanjfcivcbndivav`, region: `sa-east-1`) criado e configurado. Schema aplicado, RLS ativa, bucket `analysis-photos` privado criado, Security Advisor sem alertas. Migrations locais alinhadas com timestamps do remoto. Configuração Android preparada via `local.properties` → `BuildConfig`.

---

# Fase 3 — Autenticação e perfil

Objetivo: tornar conta obrigatória para uso do aplicativo.

Escopo:

- [x] cadastro;
- [x] login;
- [x] logout;
- [x] recuperação de senha (link seguro com retorno ao app + troca autenticada);
- [x] sessão persistida;
- [x] perfil básico;
- [x] tela de autenticação;
- [x] direcionamento correto ao iniciar o app.

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

**Status**: Concluído e validado manualmente no aparelho. Cadastro por e-mail
+ senha com confirmação por link, login, logout, sessão persistida, perfil
básico com acesso na home, launcher e UI alinhados ao V3.

> **Política do MVP:** confirmação de e-mail ATIVA via link padrão do
> Supabase (sem OTP, sem SMTP customizado); recovery por link seguro com
> deep link de retorno; troca de senha no Perfil autenticado.

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

- [x] adicionar estado de sincronização;
- [x] manter Room como fonte local;
- [x] salvar análise local imediatamente;
- [x] criar fila de sincronização;
- [x] usar WorkManager ou mecanismo adequado;
- [x] implementar retry;
- [x] implementar upsert idempotente;
- [x] impedir duplicações;
- [x] sincronizar exclusões;
- [x] tratar erros de rede;
- [x] testar modo offline → online.

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

**Status**: Fundação implementada e validada manualmente no aparelho: análise criada
offline permanece no Room; ao voltar a internet o WorkManager sincroniza; o registro
aparece em `public.analyses`; fluxo offline → online funciona. Fotos na Fase 5,
multi-device na Fase 6.

---

# Fase 5 — Sincronização das fotos

Objetivo: salvar as fotos remotamente sem prejudicar o uso offline.

Escopo:

- [x] criar upload para Storage privado;
- [x] usar caminho por usuário/análise;
- [x] retry automático;
- [x] registrar `photo_path`;
- [x] tratar upload incompleto;
- [x] manter cópia local;
- [x] proteger acesso com policies.

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

**Status**: Upload privado implementado e exercitado no QA físico registrado nas seções J e N do checklist. A cópia local permanece disponível; URLs assinadas não são armazenadas no banco.

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

- [x] download de análises;
- [x] merge com Room;
- [x] download/cache de fotos;
- [x] deduplicação;
- [ ] conflitos;
- [x] exclusões;
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

**Status**: Restore de análises e cache de fotos implementados e verificados no fluxo de reinstalação registrado nas seções O–Q do checklist. O teste com um segundo aparelho ficou N/A por indisponibilidade de dispositivo; resolução avançada de conflitos permanece pós-MVP.

---

# Fase 7 — QA e segurança do MVP

Objetivo: validar o sistema completo.

## Autenticação

- [x] cadastro;
- [x] login correto;
- [x] senha errada;
- [x] recuperação;
- [x] logout;
- [x] sessão persistida;
- [x] início offline.

## Offline

- [x] classificação sem internet;
- [x] criação de análise sem internet;
- [x] histórico local;
- [ ] várias análises offline;
- [ ] retorno da conexão;
- [ ] retry automático.

## Sincronização

- [x] análise;
- [x] foto;
- [x] exclusão;
- [x] retry;
- [ ] app fechado durante sync;
- [ ] conexão instável;
- [ ] mesmo usuário em dois aparelhos.

## Segurança

- [x] usuário A não lê dados de B;
- [x] usuário A não lê fotos de B;
- [x] RLS ativa;
- [x] bucket privado;
- [x] nenhuma service role key no app;
- [x] nenhum secret versionado.

## Android

- [x] câmera;
- [x] galeria;
- [ ] permissões;
- [x] histórico;
- [x] reinício;
- [x] modo avião;
- [ ] device físico.

**Status QA**: a revisão técnica registrada em 24/09/2026 corrigiu o isolamento Room por conta. A aprovação manual dos quatro fluxos Auth foi reportada pelo usuário em 29/09/2026. Segundo aparelho: não executado por falta de dispositivo. Permissões, conexão instável e outros cenários não têm aprovação nova registrada; ver `docs/FINAL_QA_CHECKLIST.md`. A captura inconclusiva (~30%) é permitida pelo threshold; não há aviso específico de pouca luz. SMTP próprio e distribuição assinada são pós-MVP.

Critério de saída: nenhum bug crítico conhecido no fluxo principal.

---

# Fase 8 — Fechamento do MVP

Somente depois do código estabilizado.

Atualizar:

- [x] README.md;
- [x] ARCHITECTURE.md;
- [x] TECHNICAL_DECISIONS.md;
- [x] MODEL_CARD.md;
- [x] INSTALL.md;
- [x] CHANGELOG.md;
- [x] documentação Supabase;
- [x] checklist de testes.

Opcional:

- [ ] CONTRIBUTING.md.

Depois:

- [x] rodar todos os testes;
- [x] gerar build final;
- [x] registrar versao 1.0.0 (versionCode 3);
- [x] gerar APK debug para demo/teste.

**Status Fase 8 (2026-09-29)**: os quatro fluxos Auth foram aprovados manualmente pelo usuario. Android: 199 testes, assets, lint e build aprovados. Python/ML: 28 testes; bundle valido, 16 classes, ordem de saida verificada e modelo carregado. APK debug para demo/teste, nao para producao.

**Pos-MVP**: SMTP/domino proprio, assinatura e distribuicao de producao, validacao em segundo aparelho quando disponivel e melhorias futuras de UX/ML.

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

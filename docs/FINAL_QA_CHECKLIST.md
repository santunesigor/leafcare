# LeafCare — Checklist Final de QA Físico

Bateria única, em ordem operacional para minimizar reinstalações.
APK: `android-app/app/build/outputs/apk/debug/app-debug.apk`.
Pré-requisito: `local.properties` com `SUPABASE_PUBLISHABLE_KEY` real + rebuild.

Para cada item: `[ ] passo` → **Resultado esperado** → *Evidência/observação*.

---

## A. Instalação limpa

- [OK] Desinstalar qualquer LeafCare anterior; instalar o APK via adb; abrir.
- **Resultado esperado:** app abre na tela de login, sem crash.
- *Evidência/observação:*

## B. Launcher/ícone

- [OK] Localizar o ícone na gaveta/launcher (formato do aparelho).
- **Resultado esperado:** as duas folhas verdes sobre fundo claro, centralizadas com margem, sem corte.
- *Evidência/observação:*

## C. Cadastro

- [ ] Nome + e-mail novo + senha ≥ 6 + confirmação igual → Criar conta → tela "Verifique seu e-mail".
- **Resultado esperado:** não entra direto; abrir o link de confirmação abre o LeafCare e entra no app (`leafcare://auth/confirm-email`).
- *Evidência/observação:* Quando eu tento entrar com um email que nao esta cadastrado e clico em criar conta logo após, ele continua com o aviso de que o email ou a senha estãoincorretos, deveria limpar

## D. Perfil/nome

- [OK] Home mostra "Olá, {nome}"; abrir perfil pelo botão circular.
- **Resultado esperado:** nome e e-mail corretos; botão "Sair da conta" visível.
- *Evidência/observação:*

## E. Login/logout

- [VISUAL] Sair → entra com a mesma conta.
- **Resultado esperado:** volta ao Auth no logout; login abre o app.
- *Evidência/observação:* Se eu criar uma conta e logo após sair, ao inves de ele sair e ir para a tela de boas vindas ele sai e volta para a tela de criar conta / As imagens dos cards do histórico demoram para ser carregadas de vez em quando

## F. Sessão persistida

- [VISUAL] Fechar o app por completo (remover dos recentes); reabrir.
- **Resultado esperado:** entra direto, sem pedir login.
- *Evidência/observação:* Ele da uma piscadinha na tela de login e depois entra

## G. Senha incorreta

- [OK] Sair; entrar com senha errada.
- **Resultado esperado:** "E-mail ou senha incorretos", sem crash, sem detalhe técnico.
- *Evidência/observação:*

## H. Recovery

- [ ] "Esqueci minha senha" → informar e-mail → abrir o link do e-mail → app abre "Nova senha" → definir senha → entrar.
- **Resultado esperado:** e-mail do Supabase com link; toque abre o LeafCare (`leafcare://auth/reset-password`); definir a nova senha conclui o recovery. Sem WebView, sem localhost.
- *Evidência/observação:* Nota histórica sobre OTP superada: recovery e confirmação usam links/deep links no fluxo atual. O Supabase me enviou um link, nao um código, por isso nao consigo recuperar senha / Se voce consegur fazer todo o processo in-app com o e-mail apenas mandando código, faça também para a confirmação de e-mail, ai voltamos com essa feature. / Depois de voltar da tela o aviso continua em baixo, faça o seguinte, reviso como esses avisos funcioname e os faça ficar apenas na tela onde foram criados.

## I. Análise online

- [OK] Com internet, fotografar folha.
- **Resultado esperado:** resultado imediato (top-3 ou inconclusivo) + linha no histórico.
- *Evidência/observação:*

## J. Foto online

- [OK] Aguardar sync; conferir no Supabase: linha em `public.analyses` e objeto em `analysis-photos/{user_id}/{analysis_id}.jpg`.
- **Resultado esperado:** `photo_path = "{user_id}/{analysis_id}.jpg"`; mesmo UUID local/remoto.
- *Evidência/observação:*

## K. Análise offline

- [OK] Modo avião; fotografar; conferir histórico.
- **Resultado esperado:** classifica e salva normalmente, sem erro de rede na UI.
- *Evidência/observação:*

## L. Retorno da conexão

- [OK] Sair do modo avião; aguardar ~1 min.
- **Resultado esperado:** análise pendente aparece no Supabase, sem duplicatas.
- *Evidência/observação:*

## M. Retry

- [OK] Com internet instável (ou matando o app mid-sync, se possível), repetir sync.
- **Resultado esperado:** conclui depois; nenhuma linha/foto duplicada no Supabase.
- *Evidência/observação:*

## N. Exclusão/tombstone

- [OK] Excluir análise sincronizada (com internet); conferir Supabase.
- **Resultado esperado:** some do app na hora; foto removida do Storage; linha remota coerente com `deleted_at` (soft delete — **não** exigir DELETE físico da linha).
- *Evidência/observação:*

## O. Reinstalação

- [OK] Desinstalar; reinstalar o mesmo APK; login na mesma conta.
- **Resultado esperado:** próximo item cobre o restore.
- *Evidência/observação:*

## P. Restore de histórico

- [OK] Após O, aguardar sync com internet.
- **Resultado esperado:** histórico reconstruído com todas as análises (sem duplicatas).
- *Evidência/observação:*

## Q. Restore/cache das fotos

- [OK] Após P, abrir análises restauradas.
- **Resultado esperado:** fotos aparecem após o download em background; depois, visíveis em modo avião.
- *Evidência/observação:*

## R. Offline depois do restore

- [OK] Modo avião; navegar histórico, abrir resultado, classificar nova folha.
- **Resultado esperado:** tudo local funciona.
- *Evidência/observação:*

## S. Conta B no mesmo aparelho

- [OK] Sem desinstalar: sair da conta A; criar/entrar conta B.
- **Resultado esperado:** histórico de A **não** aparece; B começa vazio.
- *Evidência/observação:*

## T. Isolamento A/B

- [OK] Na conta B, criar análise; conferir Supabase (conta B) e tentar ler como A (não deve).
- **Resultado esperado:** dados de B só existem sob `user_id` de B; nada de A visível/vazado; RLS como garantia.
- *Evidência/observação:*

## U. Mesmo usuário em dois aparelhos (se disponível)

- [NAO DISPONIVEL] Login da mesma conta em outro aparelho com internet.
- **Resultado esperado:** histórico consistente nos dois, sem duplicações.
- *Evidência/observação:*

## V. Conexão instável

- [OK] Durante sync, alternar avião on/off algumas vezes.
- **Resultado esperado:** sem crash, sem duplicata, estado final consistente.
- *Evidência/observação:*

## W. Câmera

- [DUVIDA] Capturar em luz normal e fraca; negar permissão uma vez.
- **Resultado esperado:** preview/captura ok; negação tratada sem crash.
- *Evidência/observação:* Joguei uma foto prea e ele nao me disse nada que tinha baixa luzou alguma coisa assim, só deu 30% do modelo e deu que nao conseguiu identificar, era pra fazer isso?

## X. Galeria

- [OK] Importar JPEG e PNG da galeria.
- **Resultado esperado:** ambas classificam normalmente.
- *Evidência/observação:*

## Y. Resultado inconclusivo

- [OK] Fotografar algo de baixa confiança (ex.: fundo/mão/solo).
- **Resultado esperado:** tela de "Inconclusivo", sem forçar diagnóstico.
- *Evidência/observação:*

## Z. Inspeção final no Supabase

- [ACHO QUE OK] Dashboard: RLS ativa em `profiles`/`analyses`; bucket privado; trigger criou `profiles` com `display_name`; nenhuma linha de outro `user_id` acessível pela conta de teste.
- **Resultado esperado:** tudo conforme `supabase/migrations/`.
- *Evidência/observação:*

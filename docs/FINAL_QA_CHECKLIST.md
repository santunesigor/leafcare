# LeafCare — Checklist Final de QA Físico

Bateria única, em ordem operacional para minimizar reinstalações.
APK: `android-app/app/build/outputs/apk/debug/app-debug.apk`.
Pré-requisito: `local.properties` com `SUPABASE_PUBLISHABLE_KEY` real + rebuild.

Para cada item: `[ ] passo` → **Resultado esperado** → *Evidência/observação*.

---

## A. Instalação limpa

- [ ] Desinstalar qualquer LeafCare anterior; instalar o APK via adb; abrir.
- **Resultado esperado:** app abre na tela de login, sem crash.
- *Evidência/observação:*

## B. Launcher/ícone

- [ ] Localizar o ícone na gaveta/launcher (formato do aparelho).
- **Resultado esperado:** as duas folhas verdes sobre fundo claro, centralizadas com margem, sem corte.
- *Evidência/observação:*

## C. Cadastro

- [ ] Nome + e-mail novo + senha ≥ 6 + confirmação igual → Criar conta.
- **Resultado esperado:** entra direto no app (sem confirmação web).
- *Evidência/observação:*

## D. Perfil/nome

- [ ] Home mostra "Olá, {nome}"; abrir perfil pelo botão circular.
- **Resultado esperado:** nome e e-mail corretos; botão "Sair da conta" visível.
- *Evidência/observação:*

## E. Login/logout

- [ ] Sair → entra com a mesma conta.
- **Resultado esperado:** volta ao Auth no logout; login abre o app.
- *Evidência/observação:*

## F. Sessão persistida

- [ ] Fechar o app por completo (remover dos recentes); reabrir.
- **Resultado esperado:** entra direto, sem pedir login.
- *Evidência/observação:*

## G. Senha incorreta

- [ ] Sair; entrar com senha errada.
- **Resultado esperado:** "E-mail ou senha incorretos", sem crash, sem detalhe técnico.
- *Evidência/observação:*

## H. Recovery

- [ ] "Esqueci minha senha" → enviar código → digitar código do e-mail → definir nova senha.
- **Resultado esperado:** cada etapa avança in-app; nova senha entra no app. Nada de browser.
- *Evidência/observação:*

## I. Análise online

- [ ] Com internet, fotografar folha.
- **Resultado esperado:** resultado imediato (top-3 ou inconclusivo) + linha no histórico.
- *Evidência/observação:*

## J. Foto online

- [ ] Aguardar sync; conferir no Supabase: linha em `public.analyses` e objeto em `analysis-photos/{user_id}/{analysis_id}.jpg`.
- **Resultado esperado:** `photo_path = "{user_id}/{analysis_id}.jpg"`; mesmo UUID local/remoto.
- *Evidência/observação:*

## K. Análise offline

- [ ] Modo avião; fotografar; conferir histórico.
- **Resultado esperado:** classifica e salva normalmente, sem erro de rede na UI.
- *Evidência/observação:*

## L. Retorno da conexão

- [ ] Sair do modo avião; aguardar ~1 min.
- **Resultado esperado:** análise pendente aparece no Supabase, sem duplicatas.
- *Evidência/observação:*

## M. Retry

- [ ] Com internet instável (ou matando o app mid-sync, se possível), repetir sync.
- **Resultado esperado:** conclui depois; nenhuma linha/foto duplicada no Supabase.
- *Evidência/observação:*

## N. Exclusão/tombstone

- [ ] Excluir análise sincronizada (com internet); conferir Supabase.
- **Resultado esperado:** some do app na hora; foto removida do Storage; linha remota coerente com `deleted_at` (soft delete — **não** exigir DELETE físico da linha).
- *Evidência/observação:*

## O. Reinstalação

- [ ] Desinstalar; reinstalar o mesmo APK; login na mesma conta.
- **Resultado esperado:** próximo item cobre o restore.
- *Evidência/observação:*

## P. Restore de histórico

- [ ] Após O, aguardar sync com internet.
- **Resultado esperado:** histórico reconstruído com todas as análises (sem duplicatas).
- *Evidência/observação:*

## Q. Restore/cache das fotos

- [ ] Após P, abrir análises restauradas.
- **Resultado esperado:** fotos aparecem após o download em background; depois, visíveis em modo avião.
- *Evidência/observação:*

## R. Offline depois do restore

- [ ] Modo avião; navegar histórico, abrir resultado, classificar nova folha.
- **Resultado esperado:** tudo local funciona.
- *Evidência/observação:*

## S. Conta B no mesmo aparelho

- [ ] Sem desinstalar: sair da conta A; criar/entrar conta B.
- **Resultado esperado:** histórico de A **não** aparece; B começa vazio.
- *Evidência/observação:*

## T. Isolamento A/B

- [ ] Na conta B, criar análise; conferir Supabase (conta B) e tentar ler como A (não deve).
- **Resultado esperado:** dados de B só existem sob `user_id` de B; nada de A visível/vazado; RLS como garantia.
- *Evidência/observação:*

## U. Mesmo usuário em dois aparelhos (se disponível)

- [ ] Login da mesma conta em outro aparelho com internet.
- **Resultado esperado:** histórico consistente nos dois, sem duplicações.
- *Evidência/observação:*

## V. Conexão instável

- [ ] Durante sync, alternar avião on/off algumas vezes.
- **Resultado esperado:** sem crash, sem duplicata, estado final consistente.
- *Evidência/observação:*

## W. Câmera

- [ ] Capturar em luz normal e fraca; negar permissão uma vez.
- **Resultado esperado:** preview/captura ok; negação tratada sem crash.
- *Evidência/observação:*

## X. Galeria

- [ ] Importar JPEG e PNG da galeria.
- **Resultado esperado:** ambas classificam normalmente.
- *Evidência/observação:*

## Y. Resultado inconclusivo

- [ ] Fotografar algo de baixa confiança (ex.: fundo/mão/solo).
- **Resultado esperado:** tela de "Inconclusivo", sem forçar diagnóstico.
- *Evidência/observação:*

## Z. Inspeção final no Supabase

- [ ] Dashboard: RLS ativa em `profiles`/`analyses`; bucket privado; trigger criou `profiles` com `display_name`; nenhuma linha de outro `user_id` acessível pela conta de teste.
- **Resultado esperado:** tudo conforme `supabase/migrations/`.
- *Evidência/observação:*

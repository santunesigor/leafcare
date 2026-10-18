# Validation

## Environment

Execução local em 2026-10-13, branch `feat/superadmin`, base `40e55a8`. Working tree inicialmente limpa; validações realizadas sobre as alterações locais antes do commit e push solicitados pelo usuário. Nenhuma operação em produção, envio de e-mail ou convite real foi executado.

## Results

- `bash gradlew testDebugUnitTest --tests 'br.com.leafcare.admin.*' lintDebug assembleDebug --max-workers=2`: sucesso; 44 testes administrativos (16 de estado e 28 Compose), zero falhas/erros. Cobertura de acesso superior, retry e revogação, quatro ações equivalentes com fonte ampliada, dúvida/rejeição sem observação, correção, cancelamento, navegação, teclado, formulário de convite, intent nativa, eventos efêmeros e falhas de geração.
- O diálogo inicial de convite apresentou loop de medição nos testes; foi substituído pelo painel rolável existente. Os dois testes afetados e a suíte administrativa passaram após a correção.
- `npx --yes deno test supabase/functions/leafcare-admin/handler_test.ts`: 10 aprovados. `npx --yes deno check supabase/functions/leafcare-admin/index.ts`: sucesso. APIs de Auth/Storage simuladas; link fictício usado nos testes.
- `bash supabase/tests/run-local.sh`: migrations e cenários de superadmin/RLS aprovados em PostgreSQL 18 isolado. Dúvida aceita observação vazia e rejeição aceita observação omitida, mantendo foto, previsão original, versões e auditoria. A comparação da RPC confirma remoção exclusiva da exigência do motivo; ACL e demais regras preservadas.
- `openspec validate --all --strict --no-interactive`: 10 itens aprovados. `openspec validate --archived --strict --no-interactive`: 2 aprovados. O aviso informativo sobre o delta antigo de `android-superadmin` já existente nas specs não impede validação; essa change antiga não foi alterada.
- Capturas inspecionadas em `android-app/app/build/reports/screenshots/`: acesso superior, avaliação comum, 320×480dp, fonte 1,3× em 320×640dp e formulário de convite. Todas as decisões permanecem visíveis com dimensões equivalentes nas configurações verificadas.
- Após o último ajuste do rótulo de auditoria para “Convite criado”, `bash gradlew lintDebug assembleDebug --max-workers=2`: sucesso. Lint: zero erros, 24 avisos; análise executada, relatório reaproveitado (`UP-TO-DATE`). `git diff --check`: sem problemas.
- APK da validação `leafcare-1.3.0-40e55a8.apk` gerado e copiado para `android-app/app/build/outputs/apk/distribution/debug/leafcare-1.3.0-admin-revisado.apk` para distinguir esta entrega. O hash desse arquivo indica a base anterior ao commit; o APK inclui as mudanças validadas. O build após o commit usa o novo hash no nome padrão.

## Pending QA and Deployment

QA físico de teclado, aplicativos disponíveis no seletor e abertura do convite permanece para o usuário via APK. A classificação offline não foi alterada. Publicação depende de autorização: aplicar somente `20261013120000_optional_review_note.sql` e publicar `leafcare-admin`. Até isso ocorrer, o servidor atual ainda exige motivo para dúvida/rejeição e não oferece `invite_link`.

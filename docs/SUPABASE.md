# Supabase — LeafCare

O backend do MVP usa Supabase para autenticação, PostgreSQL/PostgREST e armazenamento privado de fotos.

## Configuração Android

O app precisa somente de:

```text
SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY
```

A URL está definida no `BuildConfig`; a chave pública é lida de `android-app/local.properties` ou da variável de ambiente usada no CI.

Exemplo:

```properties
SUPABASE_PUBLISHABLE_KEY=sua_chave_publica
```

Nunca coloque no aplicativo:

- `service_role`;
- senha do banco;
- JWT secret;
- credenciais administrativas.

## Migrations

As migrations são versionadas em `supabase/migrations/`.

### `20260923121620_initial_schema.sql`

Cria:

- extensão UUID;
- função de `updated_at`;
- tabela `profiles`;
- tabela `analyses`;
- RLS;
- políticas iniciais;
- índices de consulta.

### `20260923121632_auto_profile_creation.sql`

Cria trigger em `auth.users` para gerar automaticamente o perfil da aplicação.

### `20260923121659_storage_analysis_photos.sql`

Cria/configura o bucket privado:

```text
analysis-photos
```

Formato do caminho:

```text
{user_id}/{analysis_id}.jpg
```

As políticas permitem que cada usuário manipule somente os próprios arquivos.

### `20260923121807_security_hardening.sql`

Revoga execução direta de funções internas por perfis públicos e recria políticas explicitamente para usuários autenticados.

## RLS

`profiles` e `analyses` usam Row Level Security.

O objetivo é que uma sessão autenticada acesse somente dados vinculados ao próprio `auth.uid()`.

A segurança não deve depender de filtros enviados pelo cliente Android. O banco deve aplicar a restrição mesmo quando uma requisição malformada é enviada pelo cliente.

## Auth

O aplicativo suporta:

- cadastro;
- confirmação de e-mail;
- login;
- sessão persistida;
- logout;
- recuperação de senha;
- alteração de senha autenticada.

Deep links:

```text
leafcare://auth/confirm-email
leafcare://auth/reset-password
```

Esses URLs precisam estar autorizados/configurados no projeto Supabase usado pelo app.

## Sincronização

O histórico local usa UUID como identificador estável.

Fluxo simplificado:

```text
Room
  ↓
PENDING_UPLOAD
  ↓
upsert analysis
  ↓
SYNCED
  ↓
upload foto
  ↓
photoSyncStatus = SYNCED
```

Em falhas temporárias:

```text
ERROR → retry posterior
```

Para exclusão:

```text
PENDING_DELETE + deletedAt
       ↓
remoção/soft-delete remoto
       ↓
limpeza local quando confirmado
```

## Restore

Ao autenticar em um novo aparelho, análises existentes no backend podem ser reconstruídas no Room.

Fotos remotas ainda não presentes localmente são marcadas como:

```text
REMOTE_ONLY
```

Elas podem ser baixadas posteriormente sem bloquear o carregamento inicial do histórico textual.

## Aplicando migrations em um novo projeto

Com Supabase CLI instalado:

```bash
supabase login
supabase link --project-ref SEU_PROJECT_REF
supabase db push
```

Também é possível aplicar SQL manualmente pelo Dashboard, mas o fluxo com migrations versionadas é preferível porque mantém o ambiente reproduzível.

## Checklist de segurança

Antes de usar outro projeto Supabase:

- confirme que RLS está ativa;
- teste acesso entre duas contas diferentes;
- confirme que o bucket é privado;
- confirme que uma conta não consegue acessar caminho de outra;
- confirme que nenhuma chave administrativa está no APK;
- confirme redirect URLs de Auth;
- teste recuperação de senha;
- teste restore após login;
- teste exclusão offline seguida de reconexão.

## Fotos com previsão aceita e consulta da classe

A partir do APK 1.2.1, somente fotos de análises conclusivas são enviadas para `analysis-photos`: `inconclusive=false` e confiança maior ou igual ao limiar gravado naquela análise. O modelo atual usa limiar aproximado de 0,506676; a regra respeita o limiar de cada análise histórica. Igualdade no limiar permite envio. A classe é a primeira previsão em `analyses.class_id`, junto de `confidence`, `top3` e `model_sha256`; ela não é um rótulo confirmado por especialista.

Resultados inconclusivos continuam no histórico local e sincronizam a linha da análise; a foto fica apenas no aparelho original. Sem cópia no bucket, essa foto não será recuperada após reinstalação ou em outro aparelho. O estado `LOCAL_ONLY` e `photo_path=null` representam ausência de foto remota; o caminho só é associado após upload confirmado. A fila de fotos também exclui retries inconclusivos antigos. APKs anteriores podem continuar enviando segundo a regra antiga.

O caminho continua `{user_id}/{analysis_id}.jpg`, para preservar download/exclusão de objetos existentes. No SQL Editor do seu projeto Supabase, esta consulta **somente de leitura** mostra cada arquivo, classe prevista e confiança, inclusive fotos antigas:

```sql
SELECT
    o.name AS arquivo_bucket,
    a.id AS analysis_id,
    a.user_id,
    a.class_id AS classe_prevista,
    a.display_name AS nome_classe,
    a.confidence AS confianca,
    a.threshold AS limiar,
    a.inconclusive AS inconclusivo,
    a.model_sha256,
    a.created_at AS data_analise
FROM storage.objects AS o
LEFT JOIN public.analyses AS a
    ON o.name = a.user_id::text || '/' || a.id::text || '.jpg'
WHERE o.bucket_id = 'analysis-photos'
ORDER BY o.created_at DESC;
```

O JOIN usa o UUID e usuário porque os registros antigos podem ter `photo_path` com nome local. Classe nula indica objeto sem análise correspondente. A consulta não abre o bucket nem altera políticas ou dados. Nenhum objeto previamente enviado é removido automaticamente nesta mudança.

## Administração e triagem

Configuração, publicação, bootstrap da primeira conta, pausa de envios e revisão humana: [SUPERADMIN.md](SUPERADMIN.md). As novas migrations e a função devem ser publicadas antes de distribuir o APK 1.3.0; nenhum e-mail ou credencial administrativa vai no aplicativo.

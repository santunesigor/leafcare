# Proposal

## Why

Fotos de análises inconclusivas são enviadas ao bucket sem uma previsão aceita. O usuário quer enviar somente fotos com previsão e identificar a classe de cada arquivo.

## What Changes

- Fotos só entram no upload quando a análise é conclusiva e sua confiança atinge o limiar registrado.
- Resultados inconclusivos conservam histórico e foto local; a sincronização da linha continua.
- Fotos sem upload têm estado local explícito e caminho remoto nulo; restore não tenta baixar arquivo inexistente.
- Documentar consulta somente de leitura para associar objetos do bucket à classe, confiança e modelo já gravados na análise.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `account-data-sync`: restringir envio de fotos e representar análises sem foto remota.

## Impact

Fila Room, runner, payload e restore Android; testes de sincronização, docs Supabase e APK debug. Sem mudança de modelo, limiar, schema Supabase ou políticas RLS. Objetos já enviados não são removidos automaticamente.

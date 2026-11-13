## Context

`training-photos` guarda as cópias do app em `<conta>/<análise>.jpg`. As políticas de upload, a revogação e a limpeza diária dependem desse caminho, e o rótulo pode mudar numa nova revisão. O TLA tem rótulos fixos e já vinha organizado por doença em `data/raw/`.

## Goals / Non-Goals

**Goals:**
- Todo o conjunto de treino no Supabase, recuperável em qualquer computador confiável.
- Mesma organização por doença para o TLA e para a cópia local das fotos do app.

**Non-Goals:**
- Mover as fotos do app para pastas por doença no servidor.
- Mudar o manifesto, o split ou as imagens do TLA.

## Decisions

- **Bucket separado.** `training_photos_to_delete()` apaga de `training-photos` todo objeto sem análise, o que incluiria o TLA. O bucket novo não tem políticas: só a chave de serviço o acessa.
- **Manifesto como verdade.** Cada imagem baixada é conferida com o sha256 do manifesto antes de ser gravada. O upload nunca substitui um objeto existente.
- **Nomes ASCII.** O Storage recusa caracteres fora do ASCII. Três nomes do TLA têm `副本`; no bucket esses caracteres viram `uXXXX`, e o nome local continua o do manifesto.
- **Doença só na cópia local.** No servidor, a doença das fotos do app continua no banco. O `sync` coloca cada foto na pasta da classe revisada e a move quando a revisão muda.

## Risks / Trade-offs

- **Armazenamento:** cerca de 30 MB a mais no plano do Supabase.
- **Licença:** o TLA é público e fica num bucket privado do projeto, sem redistribuição.

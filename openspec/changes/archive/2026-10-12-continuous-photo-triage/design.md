# Design

## Context

Ver proposal.md. AdminContent usa cards e ReviewDetails abre um Dialog de tela inteira. AdminViewModel busca pendentes após qualquer revisão, independentemente do filtro. A API existente aceita filtros, id e páginas de 20.

## Goals / Non-Goals

**Goals:** fila integrada, foto em destaque, rodapé acessível e navegação sem retorno à lista.

**Non-Goals:** mudanças de API, schema, ML, sincronização ou outros módulos administrativos.

## Decisions

- Usar VerticalPager Compose com chave por UUID; disponibilizar botão para navegação acessível. Somente a foto ativa é decodificada em tamanho de revisão para limitar memória.
- Substituir o diálogo por uma fila integrada. Confirmar exige um toque; outras avaliações e detalhes abrem ModalBottomSheet com expansão completa, conteúdo rolável e rodapé separado. Zoom abre visualização dedicada para evitar conflito entre gesto de zoom e troca vertical.
- Controlar índice, UUID da imagem carregada, quantidade revisada e disponibilidade de novos lotes no ViewModel. Salvar remove o item da sessão somente após sucesso remoto. Falha posterior no carregamento não permite reenviar a revisão anterior.
- Buscar lotes com filtros atuais e deduplicar UUIDs carregados/salvos. Ao buscar mais, percorrer páginas desde zero: offsets anteriores ficam inválidos quando pendências são removidas. A alternativa de incrementar sempre a página poderia saltar fotos.
- Recarregar o registro por id após conflito 409; bloquear salvamento até atualizar sua versão. Reutilizar as mesmas verificações de permissão e isolamento por conta.

## Risks / Trade-offs

- [Fila filtrada muito longa] → Percorrer páginas para deduplicar custa solicitações adicionais, preservando a API atual e a correção diante de filas que diminuem.
- [Tela pequena/teclado] → Rodapés fora da rolagem, insets de navegação/IME e testes Compose em altura reduzida.
- [Rede indisponível] → Mostrar erro e opção de tentar novamente sem marcar a foto como revisada.
- [QA físico] → Entregar APK; testes locais não substituem validação de gestos e teclado no aparelho.

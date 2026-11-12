# Design

## Context

Ver proposal.md. O dashboard remoto retorna photos/reviewed/classes/models; não fornece totais por status humano. A fila já salva com versão e carrega lotes sem saltos. A janela principal já consome safeDrawing.

## Goals / Non-Goals

**Goals:** uma entrada informativa para Triagem; avaliação sem gesto de troca; ações claras em 320dp e com teclado; preservar previsão e controles de concorrência.

**Non-Goals:** novos indicadores remotos, backend, schema, modelo e refatoração das outras áreas do app.

## Decisions

- Criar seção visual triage que consulta a operação dashboard existente. Não calcular totais de status a partir de uma página parcial. Enquanto carrega, mostrar indicador e não interpretar ausência como zero.
- Painel usa hero verde, pendências, progresso e atalhos de situação/classe. Resumo mantém visão administrativa; sua chamada abre o painel. Retorno da avaliação atualiza os totais online.
- Renderizar somente a foto ativa, removendo VerticalPager. Botões Anterior/Pular não salvam, e confirmação mantém avanço automático. Não duplicar consumo de barras do sistema.
- Rodapé com confirmação primária em largura total, avaliação alternativa e navegação secundárias; conteúdo da previsão rolável em janelas curtas. Detalhes técnicos ficam em painel secundário.
- Manter avaliação em sheet com seleção clara por ícone/texto e rodapé acessível ao teclado. Estados de dúvida/rejeição exigem motivo.
- Aplicar alvos de toque de pelo menos 48dp conforme [Android](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) e hierarquia de ação conforme [Material](https://m2.material.io/develop/android/components/buttons).

## Risks / Trade-offs

- [Dashboard pode ficar desatualizado após revisão] → consultar novamente ao retornar ao painel; falha exibe erro e permite retry.
- [Janelas curtas ou fonte grande] → campos roláveis, ações fixas, textos que podem quebrar linha e teste em 320×480dp/fonte ampliada.
- [Sem totais por situação] → atalhos sem contagem, evitando representar uma amostra como total.
- [Prévia local não representa aparelho real] → gerar screenshots Robolectric e APK; relatar QA físico pendente.

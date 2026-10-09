# Design

## Context

Não há papéis administrativos nem Edge Functions versionadas. Fotos usam user_id/analysis_id.jpg; análises já têm classe, confiança, top3 e modelo. O PR #7 ainda está aberto e é a base desta branch.

## Goals / Non-Goals

Implementar as cinco telas e gestão completa do plano aprovado; preservar inferência offline e histórico. Sem exportação de dataset, alteração de rótulo original ou retreinamento.

## Decisions

Controle por conta em tabela protegida, com papel user/superadmin, uploads_enabled, upload_cutoff_at e estado de exclusão. Verificação atual no servidor, sem confiar em user_metadata. RPC administrativo exclusivamente service_role; Edge Function valida JWT antes de invocar. Credenciais elevadas só no servidor.

Revisões por análise com versão otimista e audit log transacional. Classe corrigida validada contra catálogo exportado; duvidosa/rejeitada exigem observação. Fotos são lidas autenticadamente, sem links públicos/cache persistente no Android. Listas de 20 itens e filtros. Exclusão de conta em etapas idempotentes: pausar/marcar exclusão, remover Storage em lotes, remover Auth/dados; auditoria mínima permanece. Proibir autoexclusão e remover último superadmin.

Pausa de sync no app e RLS do backend; liberação registra corte no relógio do servidor e compara created_at da análise. Resultados anteriores ao corte tornam-se locais e não são reenviados. Tombstones não são descartados: aguardam pausa e processam após liberar. Falha ao consultar política impede uploads e mantém retry. Leitura/restauração e login continuam. O critério temporal pressupõe relógio do aparelho correto, como o histórico atual; datas futuras inválidas são recusadas pelo servidor.

Convites retornam à rota de definir senha existente, aceitando type=invite nessa rota. O usuário convidado define sua própria senha.

## Risks / Trade-offs

Excluir conta não apaga remotamente arquivos já baixados em aparelhos offline. APIs antigas são restringidas por políticas novas; novos estados locais permanecem na coluna TEXT existente. Erros parciais de Auth/Storage devem ser retomáveis e auditados. Nenhuma migration ou operação administrativa destrutiva será aplicada na produção durante testes locais.

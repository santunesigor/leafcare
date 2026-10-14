# Design

## Context

Ver proposal.md. AdminContent direciona `photos` à ReviewQueue; AdminViewModel baixa só a imagem ativa. RPC admin_command verifica apenas superadmin. A auditoria registra o bootstrap original. my_account_policy já transmite o papel atual.

## Goals / Non-Goals

**Goals:** separar leitura de avaliação, reduzir memória das miniaturas e impor hierarquia no SQL e antes de chamadas privilegiadas da Edge Function.

**Non-Goals:** alterar inferência, Room, sincronização de análises, dataset ou publicar produção nesta execução sem autorização específica.

## Decisions

- Nova seção `gallery` usa a consulta `photos` existente e lotes de 20. LazyVerticalGrid fixa duas colunas, miniaturas amostradas com concorrência limitada e cache apenas em memória. O detalhe é somente leitura e conserva página/filtros. Reutilizar a fila para consulta confundiria navegação com mutação.
- Resumo reúne pendências, totais de situações e atalhos; distribuição completa fica no painel de fotos. Modelos exibem somente hash abreviado e contagem; não são UUIDs de conta. Backend antigo indica contagens ausentes e gestão de permissões indisponível.
- Papel `owner` único, além de superadmin/admin/user. Admin revisa e vê auditoria de revisões. Superadmin administra user/admin; owner administra também superadmin. Nenhum papel altera pares/superiores ou a si mesmo por operações de conta. Owner é imutável pelo app. Validação no SQL mantém proteção mesmo com cliente antigo/adulterado.
- Migration incremental reaproveita RPC e RLS. Primeira conta promovida a owner usa auditoria bootstrap (actor=target e superadmin ativo); sem bootstrap só aceita único superadmin. Ambiguidade interrompe a transação. Bootstrap novo cria owner idempotentemente. Sem e-mail/UUID fixo.
- Edge solicita autorização para a operação e alvo antes de Auth/Storage; operações destrutivas revalidam antes da limpeza e exclusão. Permissão insuficiente de uma operação retorna erro próprio sem apagar acesso válido de admin às fotos.

## Risks / Trade-offs

- Backend antigo não protege a hierarquia → APK identifica versão da política e bloqueia gestão de permissões até publicação; proteção completa exige migration e Edge novas.
- Grade baixa imagens originais por endpoint privado → decodificação reduzida, concorrência limitada, tentativas por cartão e nenhum cache em disco.
- Sem Docker local → procurar execução PostgreSQL isolada equivalente e registrar versão/limitação real; nenhum teste em produção.

## Migration Plan

Validar migrations existentes e nova em banco isolado, incluindo bootstrap, revogação e hierarquia. Publicação separada exige conferir projeto/histórico e instalar migrations pendentes em ordem antes da Edge. Não reverter a proteção removendo owner; correções posteriores devem preservar proprietário e registros existentes.

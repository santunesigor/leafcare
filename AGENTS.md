# Instruções do LeafCare

- Leia `README.md`, `docs/AI_CONTEXT.md` e `docs/DEVELOPMENT.md` antes de mudanças relevantes.
- Mantenha a classificação no dispositivo e disponível offline após autenticação.
- Faça mudanças pequenas; não refatore fora do escopo.
- Não altere modelo, treinamento, dataset ou schema do Supabase sem solicitação explícita.
- Nunca versione secrets, tokens, `local.properties` ou arquivos `.env`.
- Confira branch e working tree antes de editar; trabalhe na branch atual e não faça push sem autorização.
- Execute validações proporcionais à mudança e relate somente resultados realmente obtidos.
- Sempre gere e disponibilize um APK debug ao concluir mudanças; o usuário testa pelo APK.
- O usuário autoriza push em branches separadas. Não faça push na `main` sem solicitação explícita.
- Preserve alterações existentes do usuário.

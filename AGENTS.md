# Instruções do LeafCare

- Leia `README.md`, `docs/AI_CONTEXT.md` e `docs/DEVELOPMENT.md` antes de mudanças relevantes.
- Mantenha a classificação no dispositivo e disponível offline após autenticação.
- Faça mudanças pequenas; não refatore fora do escopo.
- Não altere modelo, treinamento, dataset ou schema do Supabase sem solicitação explícita.
- Nunca versione secrets, tokens, `local.properties` ou arquivos `.env`.
- Confira branch e working tree antes de editar; trabalhe na branch atual e não faça push sem autorização.
- Execute validações proporcionais à mudança e relate somente resultados realmente obtidos.
- Sempre gere e disponibilize um APK debug ao concluir mudanças; o usuário testa pelo APK.
- Atualize a versão do app a cada entrega de mudanças: novas funcionalidades incrementam `1.x.0` (minor) e corrigem bugs incrementando `1.x.y` (patch), reiniciando o patch em `0` ao subir o minor. Reserve o primeiro número para mudanças maiores, somente quando explicitamente definido. Incremente também `versionCode` a cada nova versão do APK. Aplique essa regra a partir da versão atual, sem voltar versões.
- O usuário autoriza push em branches separadas. Não faça push na `main` sem solicitação explícita.
- Preserve alterações existentes do usuário.

## OpenSpec

- O projeto usa `openspec/config.yaml` e o esquema `spec-driven`, com integração Codex em `.agents/skills/openspec-*`.
- Consulte as specs relevantes em `openspec/specs/` ao mudar comportamento. Para novas funcionalidades ou mudanças de contrato, registre proposta, specs, design e tarefas em `openspec/changes/`.
- Correções rotineiras, organização de arquivos e documentação podem seguir diretamente; quando registradas como change sem alteração de contrato, use `skip_specs: true`.
- Escreva os artefatos em português e mantenha headings estruturais e palavras normativas do OpenSpec em inglês.
- Valide com `openspec validate --all --strict --no-interactive` e `openspec validate --archived --strict --no-interactive`. Arquive apenas trabalho concluído, sincronizando deltas quando existirem.
- O OpenSpec não substitui validações do código nem amplia autorização para alterações de modelo, dados, schema, Git ou produção. Guia: `openspec/README.md`.

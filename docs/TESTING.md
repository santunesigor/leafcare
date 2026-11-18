# Testes e QA

Use as verificações adequadas às partes alteradas. Os testes automatizados não substituem os fluxos que dependem de um aparelho ou de um projeto Supabase configurado.

## Android

Na pasta `android-app`:

```bash
bash ./gradlew testDebugUnitTest verifyModelAssets lintDebug assembleDebug
```

No Windows, execute os equivalentes com `gradlew.bat`. Com aparelho ou emulador conectado, rode também os testes instrumentados:

```bash
bash ./gradlew connectedDebugAndroidTest
```

## Machine Learning

Com Python 3.12 e dependências instaladas, no diretório `machine-learning/`:

```bash
python -m pytest -q
python validate_bundle.py --require-model
```

O treino e a promoção exigem dataset e caches locais; consulte [Machine Learning](MACHINE_LEARNING.md). Não promova um modelo como parte da validação de rotina.

## Backend e especificações

Na raiz do repositório:

```bash
bash supabase/tests/run-pglite.sh
npx --yes deno test supabase/functions/leafcare-admin/handler_test.ts
openspec validate --all --strict --no-interactive
openspec validate --archived --strict --no-interactive
```

Os cenários PGlite usam banco efêmero. Não use testes locais para aplicar migrations, gravar dados ou validar mudanças no projeto Supabase remoto.

## Checklist manual Android

- Cadastro, confirmação de e-mail, login, logout, recuperação de senha e retorno do deep link ao app.
- Câmera e galeria: permissões, captura, JPEG/PNG, imagem grande e orientação EXIF.
- Classificação e histórico: Top-3, resultado inconclusivo, persistência, busca, filtros e exclusão.
- Offline e sincronização: análise sem rede, retry sem duplicação, sincronização de fotos elegíveis, exclusão e restauração.
- Troca de contas no mesmo aparelho e isolamento do histórico.
- Treinamento opcional: opção desligada por padrão, recusa, autorização geral e por foto, revogação e continuidade da análise/histórico.
- Administração, quando aplicável: papel autorizado, revisão, filtros, conflitos e perda de acesso.

## Roteiro rápido da 1.9.0

Instale o APK sobre a versão anterior. Use uma conta de teste com fotos sincronizadas para o histórico e uma conta com acesso à revisão. Anote o passo e o resultado se algo falhar.

1. **Perfil:** confirme que o rodapé mostra `Versão 1.9.0 - <commit>`, com o mesmo commit do nome do APK.
2. **Cadastro (#50):** tente cadastrar um e-mail já confirmado. O app deve informar que já há uma conta ou oferecer claramente login e recuperação se o servidor não distinguir o caso.
3. **Confirmação (#51):** com um cadastro novo, confirme o e-mail no computador e depois abra o mesmo link no celular. O app deve entrar se receber uma sessão válida ou mostrar o login. Repita com outro cadastro feito e confirmado só no celular.
4. **Termos e privacidade (#52–#53):** abra os dois documentos. Confira o texto de primeiro acesso e se o endereço de privacidade aparece inteiro, inclusive o `.com`. Toque no ícone de copiar e confira o endereço colado.
5. **Histórico (#54):** entre com uma conta com fotos remotas em um aparelho onde essa conta ainda não tenha sido usada. Deixe o histórico aberto. As imagens devem aparecer à medida que baixam, sem bloquear a tela ou sair do histórico.
6. **Revisão (#55–#56):** amplie e arraste a foto na própria tela. Toque no ícone sobre ela para restaurar o zoom. Repita na galeria administrativa. Confira se a tela principal da fila cabe sem rolar, se os detalhes estão claros, se os cards **Treino** e **Pendente** estão compactos e se o ícone de lista com lápis sumiu.
7. **Salvar e editar (#57–#58):** na fila, corrija uma foto e toque em **Salvar e próxima**. O modal deve fechar uma vez e a fila avançar uma foto. Abra a foto anterior pela galeria, toque em **Editar revisão**, altere a observação, salve e reabra para conferir. A previsão original deve continuar igual.

O reset das fotos remotas (#59) será tratado na publicação e fica fora deste teste no celular.

## Limites

Testes JVM e Robolectric não medem latência, qualidade da câmera ou comportamento de provedores no aparelho. Não afirmam que SMTP, deep links ou operações administrativas remotas foram testados; valide esses fluxos manualmente quando a mudança os afetar.

# Interface V3

Referência: seção LEAFCARE V3, identificador 108:251, no arquivo Figma fTjCvKPfARfIUtm7ATvJUN. Contexto das quatro telas foi obtido pela integração Figma antes da implementação; a cota de exportações terminou depois dos ícones. Os quatro exemplos de captura foram posteriormente fornecidos pelo usuário e são usados sem alterações no conteúdo.

- Histórico: branco/verde, Inter, saudação “Olá, Produtor”, pesquisa, filtro, cartões com miniaturas e câmera circular central.
- Câmera: preview real CameraX, moldura de quatro cantos, orientação central, flash e troca de câmera, galeria e captura circular.
- Ajuda: diálogo branco arredondado, dicas, grade 2 × 2, exemplos Correta/Desfocada/Distante/Pouca luz e botão Entendi.
- Resultado: fotografia no topo, identificação, confiança e barra, aviso de triagem, referências do catálogo, descrição e cartões de sintomas/condições/orientação.

Adaptações funcionais: top 3 expansível; filtro contém ajuste de confiança; exclusão continua disponível com confirmação; salvamento automático continua ativo. Não foram inseridos registros fictícios no histórico. Áreas de toque de navegação têm 48 dp; telas roláveis comportam texto ampliado. O histórico mantém seu cabeçalho no mesmo fluxo rolável da versão original.

Inter é distribuída sob OFL (Inter-OFL.txt). Ícones PNG foram exportados diretamente do Figma. As quatro imagens de ajuda foram fornecidas pelo usuário. A imagem correta é menor que as demais e conserva sua resolução original. Imagens semelhantes são referências locais por classe, com atribuições em referencias_manifest.csv e referencias_LICENCA_E_ATRIBUICAO.md.

A inferência, o pré-processamento, o Repository e o esquema Room não foram alterados pela refatoração visual. Testes de UI usam imagens/linhas fictícias somente em src/test; não são incluídos como histórico no aplicativo.

Capturas em docs/screenshots foram renderizadas a 420 × 865 no Robolectric e inspecionadas. A ajuda foi capturada isoladamente; no app ela abre como diálogo sobre a câmera. A captura da câmera usa foto estática apenas no teste. A comparação usou o contexto exportado do Figma, sem nova exportação após o esgotamento da cota; não é uma comparação automatizada pixel a pixel.

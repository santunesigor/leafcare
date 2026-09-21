# Roadmap do LeafCare

Este plano parte da versão já implementada: aplicativo Android com CameraX, Compose e Room, classificação offline por MobileNetV3Small, histórico e interface V3. O benchmark de 14 novas configurações selecionou um ensemble experimental, que ainda não está integrado ao aplicativo.

## 1. Fechar o protótipo acadêmico — prioridade imediata

- [ ] Instalar o aplicativo em um celular Android físico e executar todo o roteiro de `docs/MANUAL_TESTS.md`, com internet desligada durante a análise.
- [ ] Testar captura, galeria, permissão negada, rotação, imagem inválida, resultado inconclusivo e persistência do histórico após fechar e reabrir o aplicativo.
- [ ] Executar `connectedDebugAndroidTest` em dispositivo ou emulador e corrigir eventuais problemas encontrados.
- [ ] Medir tempo total entre selecionar a foto e exibir o resultado no celular da apresentação.
- [ ] Pedir revisão a um profissional agrícola para os nomes, sintomas, descrições e orientações de cada categoria.
- [ ] Registrar o que cada integrante desenvolveu, preparar uma demonstração curta e confirmar com o professor os critérios de avaliação.

**Pronto quando:** o fluxo completo funcionar offline em aparelho real, sem erros críticos, e a equipe conseguir explicar dados, métricas e limites do resultado.

## 2. Decidir qual modelo instalar no app

O modelo atual do aplicativo obteve 77,67% de acurácia e macro-F1 de 0,7157 em 103 imagens de teste. O ensemble experimental chegou a 80,58% e 0,7659, mas precisa ser testado no Android.

- [ ] Implementar o ensemble em uma branch própria, carregando e reutilizando os três intérpretes TFLite float32.
- [ ] Aplicar a mesma entrada RGB 224 × 224, média das 16 probabilidades, temperatura `0,8421423931819505` e limiar `0,74` documentados no benchmark.
- [ ] Comparar em teste automatizado as 16 probabilidades Kotlin e Python para uma imagem de referência.
- [ ] Medir latência, memória, aquecimento, bateria e tamanho do APK em um celular básico e um intermediário.
- [ ] Manter a opção de modelo único para aparelhos em que três inferências forem pesadas.
- [ ] Escolher entre modelo atual, ensemble ou destilação para uma rede menor com base nesses testes.

**Pronto quando:** a opção escolhida tiver paridade numérica, execução estável e custo aceitável em aparelhos reais.

## 3. Melhorar a confiabilidade científica

- [ ] Coletar fotos independentes de propriedades e aparelhos diferentes, com prioridade para a região de uso do aplicativo.
- [ ] Aumentar as classes raras: antracnose, TSWV, black shank e anomalia genética.
- [ ] Registrar propriedade, talhão, planta, data e sessão para evitar misturar fotos relacionadas entre treino e teste.
- [ ] Revisar os rótulos com especialista agrícola e separar imagens sem diagnóstico seguro.
- [ ] Repetir os modelos finalistas com várias seeds e validação cruzada agrupada, informando média e dispersão.
- [ ] Criar um teste externo independente, reservado até congelar escolha de arquitetura e limiar.
- [ ] Medir macro-F1, desempenho por classe, calibração e relação entre cobertura e acerto dos resultados aceitos.

**Pronto quando:** houver evidência de desempenho em fotos de campo independentes, inclusive nas categorias pouco representadas.

## 4. Evitar resultados confiantes para fotografias ruins

- [ ] Detectar desfoque, pouca luz, excesso de luz e folha muito distante.
- [ ] Incluir fotos negativas de objetos, outras plantas, fundo e mãos.
- [ ] Criar uma decisão de “não é folha de fumo” ou “foto fora do domínio”.
- [ ] Pedir nova captura quando a qualidade for insuficiente.
- [ ] Investigar recorte da área afetada para sintomas pequenos.

**Pronto quando:** fotos sem uma folha adequada não retornarem uma doença com alta confiança.

## 5. Completar o fluxo de campo

- [ ] Permitir informar propriedade e talhão, além de observações opcionais.
- [ ] Deixar o produtor editar esses dados sem modificar o resultado original.
- [ ] Permitir exportar um registro para apresentar a um técnico agrícola.
- [ ] Definir exclusão e conservação local das fotografias.
- [ ] Testar legibilidade e tamanho dos botões sob luz solar com produtores reais.

## 6. Preparar uma versão distribuível

- [ ] Adicionar integração contínua para os testes Python e Android.
- [ ] Revisar licenças do dataset, imagens de referência, fontes e pesos pré-treinados antes de distribuir o aplicativo e o modelo.
- [ ] Configurar assinatura de release fora do Git e gerar APK/AAB assinado.
- [ ] Criar versões e notas de lançamento; distribuir APK pela aba *Releases* do GitHub.
- [ ] Conferir ausência de segredos, caminhos locais e artefatos temporários no repositório.

## Ordem recomendada

1. Testes offline em celular real e validação com o professor.
2. Revisão agronômica das classes e descrições.
3. Integração experimental e medição do ensemble.
4. Coleta de dados independentes e avaliação externa.
5. Rejeição de fotos inadequadas e teste com produtores.
6. Versão assinada e distribuição.

**Limite atual:** o aplicativo é um protótipo de triagem visual; os percentuais obtidos com 103 imagens de teste não demonstram desempenho definitivo em propriedades rurais.

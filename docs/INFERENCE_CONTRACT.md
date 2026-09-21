# Contrato Python ↔ Android — versão 1

1. Decodificar imagem estática JPEG/PNG/BMP/WebP em RGB de 8 bits.
2. Corrigir EXIF, inclusive espelhamentos. Preferir imagens sRGB; decodificadores
   JPEG e perfis de cor especiais podem introduzir diferenças mínimas por plataforma.
3. Compor transparência sobre branco: `(canal*a + 255*(255-a) + 127)//255`.
4. Recortar o quadrado central. Lado `min(largura, altura)`, deslocamentos inteiros
   `(largura-lado)//2`, `(altura-lado)//2`.
5. Redimensionar para 224×224 pelo bilinear inteiro definido em
   `leafcare/preprocessing.py` e `ml/PixelPreprocessor.kt`. Coordenadas half-pixel,
   borda replicada e arredondamento half-up. Não substituir por resize padrão de
   Pillow, Bitmap, Coil ou TensorFlow sem atualizar os dois lados e o teste golden.
6. Converter RGB para float32, NHWC, shape `[1,224,224,3]`, faixa **0–255**.
   **Não dividir por 255 ou normalizar para −1…1 no app.** A camada Rescaling da
   MobileNetV3Small está incorporada no modelo (`include_preprocessing=True`).
7. Saída float32 `[1,N]` softmax. `classes.json[i]` identifica a saída `i`.
   O hash das classes é SHA-256 de `"\n".join(classes)` UTF-8, sem newline final.
8. Ordenar por confiança decrescente; em empate, menor índice primeiro. Mostrar
   três valores originais; não renormalizar o top 3.
9. Resultado inconclusivo se `maior_probabilidade < limite`. Limite e probabilidades
   são comparados como float32, inclusive na igualdade. 70% é um valor inicial,
   **não calibrado**. O produtor pode ajustar de 50% a 95% na tela Histórico.
10. Tempo mostrado mede somente `Interpreter.run/invoke`, sem carga do modelo,
    leitura da imagem ou pré-processamento. A primeira execução pode demorar mais.

O contrato rejeita entrada acima de 16 milhões de pixels. O app também limita o
arquivo importado a 30 MB. Fotos enormes devem ser exportadas como cópias menores.
O recorte central pode remover sintomas periféricos; a ajuda orienta centralizar a
área afetada. Esta versão não segmenta a folha nem detecta objetos fora do domínio.

## Verificação

- O teste golden usa pixels RGBA e compara o SHA-256 do RGB final em ambas as linguagens.
- A conversão usa float32 e somente operadores TFLite nativos. A exportação compara
  Keras/TFLite em até 20 imagens **de validação**, exige erro absoluto ≤ 0,0001 e
  concordância top-1 de 100% nessa amostra; nunca usa teste para ajustar a conversão.
- O app valida hashes, ordem, shape e dtype antes da inferência.
- Teste de pixels e comparação de modelos são verificações técnicas. Não medem
  desempenho agronômico nem garantem generalização para uma nova propriedade.

## Substituição do pacote de modelo

`export_tflite.py` copia estes quatro arquivos juntos para os assets:

| Arquivo | Conteúdo |
|---|---|
| `leafcare.tflite` | Pesos treinados e grafo float32 |
| `classes.json` | Índice → identificador exato do dataset |
| `model_metadata.json` | Contrato, seed, versões e hashes |
| `diseases.json` | Conteúdo local para os identificadores |

Não edite ou reordene `classes.json` manualmente. Use o catálogo para traduzir
nomes sem alterar o identificador. Faça novo build/instalação depois de atualizar
os assets. Instalar uma atualização com o mesmo applicationId mantém o Room;
desinstalar ou limpar os dados remove histórico e fotos.

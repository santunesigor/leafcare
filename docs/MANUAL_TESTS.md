# Testes em aparelho — executar após o build

Estes cenários não devem ser marcados como aprovados sem execução. Os resultados
efetivamente obtidos nesta entrega estão em `VALIDATION.md`.

## Com e sem permissões

1. Instale o debug em Android 8.0+ e abra em modo avião.
2. Confira tema branco/verde, saudação e estado vazio, sem análises fabricadas.
3. Abra a câmera, negue permissão e confira os botões de permitir e abrir permissões.
4. Abra a galeria mesmo com câmera negada. Cancele e confirme retorno normal.
5. Autorize a câmera. Confira preview, flash somente se disponível, captura e troca
   de câmera somente se as duas existirem. Rode em retrato/paisagem e fonte ampliada.
6. Abra ajuda, confira os quatro exemplos ilustrativos e feche em “Entendi”.

## Ausência de modelo — teste negativo opcional

7. Em uma cópia de desenvolvimento sem leafcare.tflite, capture ou escolha uma
   imagem. Deve aparecer erro de modelo ausente, sem resultado aleatório. A entrega
   V3 normal já inclui o modelo treinado; não remova seus assets para uso cotidiano.

## Modelo treinado incluído na V3

8. Em modo avião, capture foto nítida, escolha foto da galeria e teste EXIF rotacionado.
9. Confira foto, tempo de inferência, top 3, descrições e aviso profissional.
10. Compare com `predict.py` usando **o mesmo arquivo original**; diferenças pequenas
    de JPEG podem existir. Use PNG sRGB para teste controlado dos pixels.
11. Aumente o limite no histórico. Confirme “Resultado inconclusivo” quando o score
    for menor que o limite; não se deve apresentar doença como identificação principal.
12. Verifique pesquisa, três filtros e agrupamento por data.
13. Feche/abra, gire o telefone e force a parada após o salvamento. O histórico deve
    permanecer. Atualize o APK sem desinstalar e confira os dados novamente.
14. Exclua uma análise e confirme que não reaparece. Cancele a confirmação em outra.
15. Tente arquivo inválido, imagem muito grande e arquivo inacessível. Deve haver
    erro compreensível, sem registro incompleto.

## Comandos (Android Studio/terminal)

PowerShell:
```powershell
cd android-app
.\gradlew.bat testDebugUnitTest assembleDebug
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat verifyModelAssets
```

Linux/macOS:
```bash
cd android-app
./gradlew testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest
./gradlew verifyModelAssets
```

`connectedDebugAndroidTest` precisa de um aparelho com depuração USB autorizada ou
de um emulador iniciado. Inclui criação do Room, gravação, fechamento/reabertura e
exclusão. O teste não contém nem depende de dados reais do usuário.

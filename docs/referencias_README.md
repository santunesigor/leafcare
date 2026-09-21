# LeafCare — conjunto público inicial de imagens

## Finalidade

Este pacote foi preparado para um projeto acadêmico de visão computacional agrícola. Ele contém **43 imagens JPEG**, organizadas em **16 categorias**, extraídas de figuras de dois artigos científicos publicados sob a licença **Creative Commons Attribution 4.0 (CC BY 4.0)**.

## Aviso técnico importante

Este material é um **conjunto inicial de referência**, não um dataset completo para colocar o aplicativo em produção. As imagens foram recortadas de figuras científicas, portanto há poucas amostras por classe e algumas podem ter compressão, iluminação ou enquadramento semelhantes.

Usos adequados:

- testar o carregamento e a organização do backend;
- validar a estrutura das classes;
- criar telas e demonstrações do LeafCare;
- executar testes preliminares e few-shot learning;
- servir como referência visual e base para solicitar os arquivos originais aos autores.

Usos inadequados:

- declarar que o modelo está validado para produtores do Sul do Brasil;
- medir a precisão final usando imagens recortadas da mesma figura no treino e no teste;
- tratar o resultado como diagnóstico agronômico definitivo;
- realizar divisão aleatória sem controlar a origem das imagens.

## Fontes incluídas

### 1. TLA — Tobacco Leaf Abnormality Dataset

- Artigo: *A few-shot learning method for tobacco abnormality identification*.
- Autores: Hong Lin, Zhenping Qiang, Rita Tse, Su-Kit Tang e Giovanni Pau.
- Publicação: Frontiers in Plant Science, 2024.
- DOI: https://doi.org/10.3389/fpls.2024.1333236
- Licença do artigo e das figuras incluídas: CC BY 4.0.
- Figuras utilizadas: 4, 5 e 11.
- Origem geográfica informada pelo estudo: China.
- Dados originais indicados pelos autores: https://drive.google.com/drive/folders/1Qn5UjATDaDpRoF1dCTdp62tlnAXJv0MF?usp=sharing

O artigo informa que o TLA possui 1.430 imagens em 16 categorias. O conjunto completo do Google Drive **não foi redistribuído neste ZIP**, pois a pasta não apresenta uma licença autônoma suficientemente clara. Foram incluídas somente as figuras cuja licença CC BY está declarada no artigo.

### 2. Agronomy 2025

- Artigo: *Semantic Segmentation of Small Target Diseases on Tobacco Leaves*.
- Autores: Yanze Zou, Zhenping Qiang, Shuang Zhang e Hong Lin.
- Publicação: Agronomy, 2025.
- DOI: https://doi.org/10.3390/agronomy15081825
- Licença: CC BY 4.0.
- Figuras utilizadas: 1, 26, 27 e 28.
- Categorias acrescentadas: frog eye, weather fleck e wildfire.
- Origem geográfica informada pelo estudo: China.

## Estrutura

```text
images/
├── 01_wildfire_pseudomonas/
├── 02_brown_spot_alternaria/
├── 03_frog_eye_cercospora/
├── 04_anthracnose/
├── 05_target_spot/
├── 06_black_shank/
├── 07_tmv/
├── 08_cmv/
├── 09_pvy/
├── 10_tswv/
├── 11_weather_fleck/
├── 12_sunscald/
├── 13_genetic_abnormality/
├── 14_phthorimaea_damage/
├── 15_nematode_damage/
└── 16_healthy/
```

O arquivo `manifest.csv` registra, para cada imagem, sua classe, figura de origem, artigo, autores, DOI e licença.

## Classes em português

| ID | Nome original | Nome explicativo em português |
|---:|---|---|
| 1 | Wildfire | Mancha bacteriana por *Pseudomonas syringae* pv. *tabaci* |
| 2 | Brown spot | Mancha de alternária |
| 3 | Frog eye | Mancha olho-de-rã por *Cercospora nicotianae* |
| 4 | Anthracnose | Antracnose do tabaco |
| 5 | Target spot | Mancha-alvo |
| 6 | Black shank | Podridão-negra-do-pé por *Phytophthora nicotianae* |
| 7 | TMV | Mosaico do tabaco |
| 8 | CMV | Mosaico do pepino em tabaco |
| 9 | PVY | Vírus Y da batata em tabaco |
| 10 | TSWV | Vira-cabeça por TSWV |
| 11 | Weather fleck | Pontuações associadas à poluição/ozônio |
| 12 | Sunscald | Escaldadura ou queimadura solar |
| 13 | Genetic abnormality | Anormalidade genética |
| 14 | Phthorimaea damage | Dano atribuído a *Phthorimaea operculella* |
| 15 | Nematode damage | Sintomas associados a nematoides |
| 16 | Healthy | Folha saudável |

## Aplicação ao Sul do Brasil

As imagens não foram coletadas no Brasil. O pacote serve para iniciar o desenvolvimento, mas a adequação regional precisa ser comprovada. Há literatura brasileira com fotografias de amarelão, mofo-branco e murcha bacteriana, porém sem licença explícita de redistribuição. Essas referências estão em `fontes_brasileiras_para_autorizacao.md`.

## Atribuição obrigatória

Ao publicar imagens ou resultados derivados, mantenha a atribuição aos artigos e à licença CC BY 4.0. Consulte `LICENCA_E_ATRIBUICAO.md`.


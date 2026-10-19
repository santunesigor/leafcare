# Contexto do LeafCare

Guia operacional para mudanças neste repositório. Código, testes, migrations e artefatos do modelo são a fonte de verdade; corrija divergências documentais quando encontrá-las.

## Produto e comportamento

- LeafCare é um app Android de triagem visual de alterações em folhas de fumo; não fornece diagnóstico agronômico definitivo.
- A inferência MobileNetV4 Small roda no aparelho. Depois da autenticação inicial, câmera, galeria, classificação e histórico local funcionam offline.
- Room é a fonte observada pela interface. Salve a análise local antes da sincronização; falhas de rede não podem descartar o resultado local.
- UUIDs estáveis, retry, tombstones e isolamento por conta preservam a sincronização sem ressuscitar exclusões.
- Fotos de resultados inconclusivos permanecem locais. Fotos elegíveis do histórico são sincronizadas para armazenamento privado; a previsão original é mantida.

## Treinamento de fotos e privacidade

- A contribuição para treinamento está habilitada como opt-in. Exige documentos atuais aceitos, consentimento geral afirmativo e autorização individual por foto; a opção é desmarcada por padrão.
- Recusar ou revogar mantém a classificação e o histórico normais. Revogações devem ser sincronizadas antes de qualquer upload pendente; estados positivos antigos nunca autorizam upload.
- Fotos de treinamento e exportações locais são separadas do histórico. A retenção máxima é de 90 dias para cópias remotas e 30 dias para exportações locais; backups manuais do banco no Google Drive têm acesso restrito e retenção máxima de 30 dias.
- O e-mail de privacidade é `igorsantunes@outlook.com`. Os Termos e a Política v1.3, publicados em 19/10/2026, abrem dentro do app; o servidor registra a versão e o horário de aceite. O aceite legal não substitui o consentimento de treinamento.
- URLs de confirmação e recuperação devem retornar ao app pelos deep links `leafcare://auth/confirm-email` e `leafcare://auth/reset-password`; não há site do produto.

## Arquitetura e dados

- Android: Kotlin, Jetpack Compose, Room, CameraX e WorkManager.
- Supabase fornece Auth, PostgREST e Storage privado; não classifica imagens.
- Mantenha RLS ativa e o isolamento por conta. Não altere schema, políticas, migrations aplicadas, dados de produção ou funções remotas sem pedido explícito.
- Não inclua credenciais, `local.properties`, `.env`, chaves de serviço ou material de assinatura em arquivos versionados.

## Modelo

O bundle atual é MobileNetV4 Small destilado, schema 2, float32 TFLite, 16 classes, entrada RGB `[1,224,224,3]`, resize bicúbico do lado menor a 256 e crop central 224. Normalização, temperatura e limiar estão registrados nos metadados do bundle. Métricas e limitações ficam em [Machine Learning](MACHINE_LEARNING.md).

Não altere pesos, pipeline de treino, dataset, ordem das classes, pré-processamento ou limiar sem pedido explícito. Resultados de teste interno não equivalem a validação de campo; não transforme medições em desktop em alegações de velocidade no aparelho.

## Validação e documentação

- Leia este arquivo, [README](../README.md) e [Desenvolvimento](DEVELOPMENT.md) antes de mudanças relevantes.
- Rode os testes e verificações proporcionais ao escopo; não relate validações que não executou.
- O proprietário testou no aparelho os fluxos do APK 1.7.1 e confirmou funcionamento. Isso não substitui testes físicos de fluxos que não foram relatados como testados.
- Atualize documentação operacional quando o comportamento mudar; preserve benchmarks, licenças, proveniência e relatos históricos que ainda servem como evidência.
- Não faça push para `main` sem autorização explícita.

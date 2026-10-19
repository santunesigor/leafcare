package br.com.leafcare.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.leafcare.auth.LEGAL_DOCUMENT_PUBLICATION_DATE
import br.com.leafcare.auth.PRIVACY_CONTACT_EMAIL
import br.com.leafcare.auth.PRIVACY_POLICY_VERSION
import br.com.leafcare.auth.TERMS_OF_USE_VERSION
import br.com.leafcare.ui.LeafColors
import br.com.leafcare.ui.PrivacyContactCard

internal enum class LegalDocument(val title: String, val sections: List<Pair<String, String>>) {
    TermsOfUse(
        title = "Termos de Uso",
        sections = listOf(
            "1. Sobre estes termos e o LeafCare" to "Estes Termos regulam o uso do aplicativo LeafCare, uma ferramenta de triagem visual de doenças e alterações em folhas de fumo. Ao criar uma conta, você declara ter pelo menos 18 anos e concorda com estes Termos. Se não concordar ou for menor de 18 anos, não crie nem utilize uma conta.",
            "2. O que o aplicativo oferece" to "O LeafCare permite capturar ou selecionar uma foto de folha, executar uma classificação por modelo de inteligência artificial no próprio aparelho e consultar o resultado e o histórico. O resultado pode apresentar uma classe provável, alternativas, nível de confiança e indicação de análise inconclusiva. Para entrar, criar a conta, confirmar o e-mail e sincronizar dados é necessária conexão. Depois do primeiro acesso autenticado, a classificação e o histórico local podem ser usados sem conexão; sem internet, sincronização, recuperação de conta e outras funções remotas ficam indisponíveis.",
            "3. Limites dos resultados" to "A classificação é automatizada, probabilística e pode errar por qualidade, iluminação, enquadramento, estágio da planta, semelhança entre sintomas ou limites do modelo. Confiança não é garantia de acerto. O LeafCare não realiza diagnóstico agronômico, não substitui inspeção de campo nem orientação de agrônomo, laboratório ou autoridade competente e não recomenda tratamento, defensivo, dose ou decisão econômica. Confirme resultados relevantes com profissional qualificado e siga rótulos e normas aplicáveis. Não tome decisão de segurança, produção ou tratamento exclusivamente com base no aplicativo.",
            "4. Conta, acesso e segurança" to "Informe nome e e-mail válidos, mantenha seus dados atualizados e proteja sua senha, aparelho e e-mail. Você é responsável pelas atividades feitas após autenticação em sua conta e deve avisar o canal de suporte quando suspeitar de uso não autorizado. Não compartilhe credenciais nem tente acessar dados de outra pessoa. A confirmação de e-mail é necessária para concluir o acesso. O LeafCare pode limitar ou suspender acesso quando necessário para proteger contas, sistemas, usuários ou cumprir a lei.",
            "5. Fotos, histórico e sincronização" to "Você mantém os direitos que possui sobre as fotos enviadas e declara ter autorização para capturá-las e utilizá-las. A imagem é processada localmente para classificação e fica na área privada do aplicativo no aparelho. O histórico de análises é salvo localmente; quando há conexão, seus dados de análise podem ser sincronizados com a sua conta. Fotos de análises conclusivas podem ser armazenadas no bucket privado do LeafCare para restaurar o histórico em outros aparelhos; fotos de resultados inconclusivos permanecem somente no aparelho, embora os dados textuais da análise possam sincronizar. Uma foto pode conter metadados incorporados, como data ou localização gravada por outro aplicativo. A exclusão no app inicia a remoção remota quando houver conexão; uma cópia offline pode permanecer no aparelho até a sincronização ou remoção local.",
            "6. Uso limitado do conteúdo enviado" to "Para fornecer classificação, histórico, sincronização e restauração, você permite ao LeafCare processar e armazenar suas fotos e dados somente na medida necessária a essas funções e conforme a Política de Privacidade. Essa permissão não transfere a propriedade da foto, não a torna pública e não autoriza publicidade, venda ou treinamento de modelos. O envio para histórico ou análise nunca equivale a autorização para treinamento.",
            "7. Treinamento de inteligência artificial" to "A contribuição de fotos para treinamento é opcional e separada do uso normal do LeafCare. Ela exige uma autorização geral explícita no Perfil e uma autorização individual para cada nova análise; nenhuma foto antiga é promovida automaticamente. É possível recusar ou revogar sem perder classificação, sincronização ou histórico. Cópias de treinamento serão removidas em até 90 dias do envio e antes disso quando a autorização for revogada. Exportações locais do conjunto serão apagadas em até 30 dias. A revogação impede novos envios e inicia a exclusão das cópias sob controle do projeto, mas não desfaz automaticamente os pesos de modelos já treinados.",
            "8. Uso aceitável" to "Use o aplicativo somente para fins lícitos. Não envie conteúdo sem direito de uso, dados pessoais de terceiros sem autorização, imagens que violem privacidade ou direitos, material ilegal ou malicioso. Não tente interferir na segurança ou disponibilidade do app, automatizar acessos abusivos, introduzir código malicioso, contornar controles, fazer engenharia reversa quando proibida pela lei, nem usar o serviço para infringir direitos de terceiros. Você responde pelo conteúdo que seleciona e pelas permissões necessárias para seu uso.",
            "9. Propriedade intelectual" to "O aplicativo, sua marca, interface, textos, organização e componentes pertencem aos respectivos titulares e são protegidos pelas leis aplicáveis. Estes Termos concedem uma licença pessoal, limitada, não exclusiva, revogável e intransferível para usar o aplicativo conforme sua finalidade. Nenhum direito sobre fotos do usuário é transferido além da permissão operacional limitada descrita nestes Termos e na Política de Privacidade.",
            "10. Serviços de terceiros" to "Autenticação, banco de dados e armazenamento remoto são providos pela infraestrutura Supabase configurada para o projeto. Android, fabricante do aparelho, operadora, loja de aplicativos e provedor de e-mail também podem tratar dados sob seus próprios termos quando necessários para o funcionamento do dispositivo, distribuição ou confirmação da conta. O LeafCare não controla serviços de terceiros; consulte as informações dos respectivos provedores. Não há site do LeafCare: documentos e retornos de autenticação disponibilizados pelo app devem abrir ou retornar ao próprio aplicativo.",
            "11. Disponibilidade e mudanças" to "O app pode receber correções, atualizações, alterações de compatibilidade ou interrupções temporárias para manutenção, segurança ou limitações técnicas. Recursos que dependem de rede ou de terceiros podem falhar sem interromper a análise local já disponível. Podemos alterar estes Termos para refletir mudanças no serviço ou na lei. Mudanças relevantes serão apresentadas no aplicativo antes de produzirem efeitos para novas utilizações; se você não concordar, deixe de usar o serviço e solicite orientação pelo canal de contato informado na Política de Privacidade.",
            "12. Responsabilidade" to "O LeafCare é fornecido como ferramenta de apoio, sem garantia de que o resultado seja completo, contínuo ou livre de erros. Na extensão permitida pela legislação, você é responsável por validar resultados e manter cópias adequadas das informações importantes. Nada nestes Termos exclui direitos ou responsabilidades que não possam ser limitados por lei, inclusive em relações de consumo. Interrupções de rede, indisponibilidade de provedores, falhas do aparelho e perda de dados locais podem afetar recursos de sincronização; por isso, o app não substitui registros técnicos ou agronômicos oficiais.",
            "13. Lei aplicável e contato" to "Aplicam-se as leis brasileiras e as normas de proteção do consumidor e de dados pessoais, sem afastar foro ou direito obrigatório previsto em lei. O projeto acadêmico identifica a equipe LeafCare, formada por cinco pessoas e representada por Ígor da Silva Antunes, como responsável. O projeto não possui CNPJ nem endereço institucional. Para dúvidas sobre privacidade, escreva para $PRIVACY_CONTACT_EMAIL."
        )
    ),
    PrivacyPolicy(
        title = "Política de Privacidade",
        sections = listOf(
            "1. Sobre esta Política" to "Esta Política descreve os dados tratados pelo LeafCare, projeto acadêmico desenvolvido pela equipe LeafCare, composta por cinco pessoas. A equipe informa Ígor da Silva Antunes como representante do projeto e contato responsável por receber solicitações de privacidade em $PRIVACY_CONTACT_EMAIL. O projeto não possui CNPJ nem endereço institucional. A contribuição de fotos para treinamento está disponível somente por escolha opcional e específica; classificação e histórico não dependem dela.",
            "2. Quem é responsável" to "Para fins desta Política, o projeto indica a equipe LeafCare como responsável pelas decisões sobre os dados, representada por Ígor da Silva Antunes. A equipe é formada por cinco pessoas e, atualmente, não possui CNPJ nem endereço institucional. O canal de privacidade é $PRIVACY_CONTACT_EMAIL. O Supabase fornece autenticação, banco de dados e armazenamento remoto como operador de infraestrutura.",
            "3. Dados tratados" to "No cadastro, são usados nome de exibição, e-mail, identificador da conta e credenciais de autenticação gerenciadas pelo Supabase Auth. Também são registrados a versão dos Termos de Uso e da Política de Privacidade aceita no cadastro e a data/hora de aceite fornecida pelo servidor. Para análises, o app trata a foto escolhida, data e hora, classe prevista, nome comum e científico, confiança, alternativas Top-3, indicador inconclusivo, limiar, tempo de inferência, versão/hash do modelo e versão do app. Um identificador aleatório liga a análise e sua foto. Não são solicitados endereço, telefone, contatos, microfone ou localização do aparelho. Uma foto selecionada pode conter metadados EXIF de data, modelo do dispositivo ou coordenadas gravadas por quem capturou o arquivo; o app de histórico não remove esses metadados antes de sincronizar a foto.",
            "4. Onde os dados ficam" to "A classificação é executada no dispositivo: a foto não é enviada a um serviço remoto para obter a previsão. O banco Room e os arquivos ficam no espaço privado do aplicativo no aparelho. O manifesto Android desativa o backup automático do app. Para restaurar o histórico em outro dispositivo, os dados de análise são sincronizados com o Postgres privado do Supabase e fotos conclusivas podem ser guardadas no bucket privado `analysis-photos`; fotos inconclusivas não são enviadas, mas seus dados de análise textual podem sincronizar. O Supabase aplica autenticação e políticas de acesso por conta. O uso de rede depende de conexão e pode enviar registros técnicos necessários à comunicação, como endereço IP, ao provedor de infraestrutura.",
            "5. Finalidades e bases de tratamento" to "Os dados de conta são usados para criar e proteger o acesso, confirmar e-mail e recuperar senha. Fotos e dados de análise são usados para executar a classificação solicitada no aparelho e manter, sincronizar, restaurar e excluir o histórico da própria conta. Esses tratamentos são necessários para fornecer as funções escolhidas e administrar a relação de uso; registros exigidos por lei podem ser tratados para cumprir obrigação legal. Dados técnicos estritamente necessários podem ser processados para segurança e operação. O app atualmente não incorpora SDK próprio de publicidade, perfil comercial, analytics ou relatório de falhas. A base legal específica de cada operação deve ser confirmada pelo controlador responsável.",
            "6. Decisões automatizadas" to "Um modelo local compara a imagem com classes aprendidas e produz probabilidades e hipóteses. A inferência não é revisão humana nem diagnóstico definitivo, e pode errar. O LeafCare não usa a classe prevista para decidir acesso, preço, crédito, emprego, tratamento médico ou outro efeito significativo sobre a pessoa. A classe pode acompanhar o histórico e uma foto sincronizada, mas permanece uma previsão até eventual revisão humana separada.",
            "7. Treinamento de modelos" to "O envio de uma foto para classificação ou histórico não autoriza seu uso em treinamento. A participação é opcional e depende de duas escolhas afirmativas: ativar a autorização geral no Perfil e marcar a autorização daquela foto antes da análise. A recusa não afeta as demais funções. Fotos antigas não recebem autorização automaticamente. As fotos autorizadas ficam em bucket privado separado do histórico e só podem ser consideradas para treinamento depois de revisão humana e validação de qualidade; na exportação são removidos identificadores da conta e metadados EXIF. Cada cópia de treinamento é apagada em até 90 dias do envio, ou antes após revogação. Exportações locais são apagadas em até 30 dias. Revogar impede novos envios e inicia a remoção das cópias ainda controladas pelo projeto; não é possível desfazer automaticamente pesos de modelos já treinados.",
            "8. Compartilhamento e destinatários" to "O LeafCare não oferece galeria pública e não vende fotos ou dados pessoais. O Supabase processa dados para autenticação, banco e armazenamento privados. O projeto está configurado na região AWS São Paulo (`sa-east-1`), onde ficam o banco, Auth e Storage. Conforme a documentação do Supabase, suporte pode ser prestado por equipe distribuída globalmente, telemetria e logs da plataforma podem ser processados na União Europeia e arquivos do Storage podem passar por CDN global, conforme configuração e localização de acesso. A empresa principal do Supabase é sediada em Singapura e sua afiliada nos Estados Unidos pode prestar suporte e operação. Isso não significa que todos os dados do LeafCare sejam enviados a todos esses locais. Android, loja de distribuição e provedores de e-mail também podem tratar dados indispensáveis às próprias funções. Informações podem ser apresentadas a autoridades diante de obrigação ou ordem legal válida. Fotos autorizadas para treinamento são acessíveis apenas ao projeto e ao pipeline restrito de revisão e exportação.",
            "9. Transferências e processamento fora do Brasil" to "O banco, Auth e Storage do projeto Supabase ficam na região AWS de São Paulo (`sa-east-1`). Isso não garante que todo processamento ocorra exclusivamente no Brasil: equipes de suporte podem acessar dados quando necessário para suporte ou incidente de segurança; logs e telemetria do provedor podem ser processados fora da região, inclusive na União Europeia; e a CDN do Storage pode atender arquivos de outras regiões conforme a configuração. O Supabase informa usar prestadores e entidades de outros países para suporte e operação. O projeto limitará o acesso ao necessário para as funções contratadas. Consulte a Política de Privacidade e a lista de subprocessadores do Supabase para informações atualizadas sobre o provedor.",
            "10. Retenção, backups e exclusão" to "Conta, perfil e recibos das versões aceitas são mantidos enquanto a conta estiver ativa; a exclusão da conta remove esses registros dos sistemas ativos em até 30 dias. Análises e fotos do histórico sincronizado permanecem enquanto a conta estiver ativa ou até a exclusão do item. Pedidos são processados nos sistemas ativos em até 30 dias; uma exclusão feita sem conexão aguarda sincronização. Cópias locais permanecem no aparelho até serem apagadas pela pessoa, pela limpeza dos dados ou pela remoção do app; o backup automático Android está desativado. Fotos autorizadas para treinamento são removidas em até 90 dias do envio, ou antes após revogação. Exportações locais do conjunto de treinamento são removidas em até 30 dias. Pesos de modelos já treinados podem permanecer até a retirada ou substituição do modelo e não são automaticamente desfeitos pela exclusão de fotos. O plano Supabase usado pelo projeto não inclui backup diário automático do banco. A equipe fará exportações manuais de backup e as manterá no Google Drive com acesso restrito por até 30 dias; essas cópias podem conservar registros excluídos até o fim desse prazo e serão removidas pela equipe. Exportações SQL do banco não incluem arquivos do Storage, que precisam de cópia separada. Dados que a lei exigir conservar ficam limitados ao prazo e à finalidade legal.",
            "11. Segurança" to "O app usa autenticação Supabase, transporte de rede sem cleartext, armazenamento remoto privado e políticas de banco que restringem registros à conta autenticada. Dados locais ficam no sandbox Android e o backup automático está desativado. Essas medidas reduzem riscos, mas nenhum sistema conectado ou aparelho é totalmente imune a perda, falha, malware, acesso físico ou incidente. Proteja o aparelho e suas credenciais e mantenha o sistema atualizado. O projeto não afirma criptografia local adicional que não esteja implementada no app.",
            "12. Seus direitos" to "Nos termos da LGPD, você pode solicitar confirmação de tratamento, acesso, correção, anonimização, bloqueio ou eliminação de dados desnecessários, excessivos ou tratados em desconformidade, portabilidade quando aplicável, informação sobre compartilhamentos, informação sobre a possibilidade de negar consentimento e consequências, e revogação de consentimento. Também pode apresentar reclamação à Autoridade Nacional de Proteção de Dados. Para falar com a equipe LeafCare sobre esses direitos, escreva para $PRIVACY_CONTACT_EMAIL.",
            "13. Crianças e adolescentes" to "O LeafCare é destinado exclusivamente a pessoas com 18 anos ou mais e não deve ser usado nem ter conta criada por menores. O cadastro atual não solicita data de nascimento, portanto o responsável deve confirmar as medidas adequadas para impedir ou tratar eventual conta de menor de acordo com a legislação aplicável.",
            "14. Alterações desta Política" to "Esta Política pode ser atualizada quando mudarem as funções, os dados tratados, provedores, regras de retenção ou requisitos legais. Alterações materiais devem ser identificadas por versão e data e apresentadas no próprio aplicativo antes de novo tratamento que exija informação ou escolha. A leitura deste aviso não é consentimento para treinamento de fotos nem substitui escolhas específicas exigidas por lei.",
            "15. Responsável e contato de privacidade" to "O projeto acadêmico identifica a equipe LeafCare, composta por cinco pessoas e representada por Ígor da Silva Antunes, como responsável pelas decisões sobre o tratamento descritas nesta Política. O projeto não possui CNPJ nem endereço institucional. Para pedidos de acesso, correção, exclusão, revogação de consentimento ou outras dúvidas de privacidade, escreva para $PRIVACY_CONTACT_EMAIL."
        )
    )
}

@Composable
internal fun LegalDocumentScreen(document: LegalDocument, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar ao cadastro", tint = LeafColors.Text)
            }
            Column(Modifier.weight(1f)) {
                Text(document.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LeafColors.Text)
                Text("DOCUMENTOS DO LEAFCARE", style = MaterialTheme.typography.labelSmall, color = LeafColors.Muted)
                Text(
                    "Versão ${if (document == LegalDocument.TermsOfUse) TERMS_OF_USE_VERSION else PRIVACY_POLICY_VERSION} · publicada em $LEGAL_DOCUMENT_PUBLICATION_DATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = LeafColors.Muted
                )
            }
        }
        HorizontalDivider(color = LeafColors.Border)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = LeafColors.Pale),
                border = BorderStroke(1.dp, LeafColors.Border),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Leia com atenção", style = MaterialTheme.typography.titleSmall, color = LeafColors.Green)
                    Text(
                        if (document == LegalDocument.TermsOfUse)
                            "Estes termos explicam o uso do app, os limites das análises e o que acontece com suas fotos."
                        else
                            "Este aviso descreve os dados tratados, onde são usados, por quanto tempo são mantidos e como entrar em contato com a equipe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LeafColors.Text
                    )
                }
            }
            document.sections.forEach { (heading, content) ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(heading, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = LeafColors.Green, fontWeight = FontWeight.SemiBold)
                    Text(content, style = MaterialTheme.typography.bodyMedium, color = LeafColors.Text)
                    if (document == LegalDocument.PrivacyPolicy && heading.startsWith("15.")) {
                        PrivacyContactCard()
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Surface(color = Color.White, shadowElevation = 4.dp) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("Voltar ao cadastro", color = LeafColors.Green, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

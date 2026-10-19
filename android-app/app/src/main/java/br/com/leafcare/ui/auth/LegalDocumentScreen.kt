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
import br.com.leafcare.auth.PRIVACY_POLICY_VERSION
import br.com.leafcare.auth.TERMS_OF_USE_VERSION
import br.com.leafcare.ui.LeafColors

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
            "7. Treinamento de inteligência artificial" to "A coleta para treinamento está desativada. Nenhuma foto do histórico é promovida automaticamente a dataset, e nenhuma autorização de treinamento é criada ao cadastrar-se. Se a funcionalidade vier a ser liberada, exigirá informações atualizadas, uma decisão geral opcional e uma autorização individual para cada foto. Será possível recusar sem perder as funções normais. A revogação bloqueará novos envios e iniciará a exclusão das cópias de treinamento conforme as regras então informadas; a exclusão de uma foto não desfaz automaticamente pesos ou resultados de modelos que já tenham sido treinados. Enquanto prazo, controlador e contato não forem definidos, o envio e a exportação para treinamento permanecem bloqueados.",
            "8. Uso aceitável" to "Use o aplicativo somente para fins lícitos. Não envie conteúdo sem direito de uso, dados pessoais de terceiros sem autorização, imagens que violem privacidade ou direitos, material ilegal ou malicioso. Não tente interferir na segurança ou disponibilidade do app, automatizar acessos abusivos, introduzir código malicioso, contornar controles, fazer engenharia reversa quando proibida pela lei, nem usar o serviço para infringir direitos de terceiros. Você responde pelo conteúdo que seleciona e pelas permissões necessárias para seu uso.",
            "9. Propriedade intelectual" to "O aplicativo, sua marca, interface, textos, organização e componentes pertencem aos respectivos titulares e são protegidos pelas leis aplicáveis. Estes Termos concedem uma licença pessoal, limitada, não exclusiva, revogável e intransferível para usar o aplicativo conforme sua finalidade. Nenhum direito sobre fotos do usuário é transferido além da permissão operacional limitada descrita nestes Termos e na Política de Privacidade.",
            "10. Serviços de terceiros" to "Autenticação, banco de dados e armazenamento remoto são providos pela infraestrutura Supabase configurada para o projeto. Android, fabricante do aparelho, operadora, loja de aplicativos e provedor de e-mail também podem tratar dados sob seus próprios termos quando necessários para o funcionamento do dispositivo, distribuição ou confirmação da conta. O LeafCare não controla serviços de terceiros; consulte as informações dos respectivos provedores. Não há site do LeafCare: documentos e retornos de autenticação disponibilizados pelo app devem abrir ou retornar ao próprio aplicativo.",
            "11. Disponibilidade e mudanças" to "O app pode receber correções, atualizações, alterações de compatibilidade ou interrupções temporárias para manutenção, segurança ou limitações técnicas. Recursos que dependem de rede ou de terceiros podem falhar sem interromper a análise local já disponível. Podemos alterar estes Termos para refletir mudanças no serviço ou na lei. Mudanças relevantes serão apresentadas no aplicativo antes de produzirem efeitos para novas utilizações; se você não concordar, deixe de usar o serviço e solicite orientação pelo canal de contato quando definido.",
            "12. Responsabilidade" to "O LeafCare é fornecido como ferramenta de apoio, sem garantia de que o resultado seja completo, contínuo ou livre de erros. Na extensão permitida pela legislação, você é responsável por validar resultados e manter cópias adequadas das informações importantes. Nada nestes Termos exclui direitos ou responsabilidades que não possam ser limitados por lei, inclusive em relações de consumo. Interrupções de rede, indisponibilidade de provedores, falhas do aparelho e perda de dados locais podem afetar recursos de sincronização; por isso, o app não substitui registros técnicos ou agronômicos oficiais.",
            "13. Lei aplicável e contato" to "Aplicam-se as leis brasileiras e as normas de proteção do consumidor e de dados pessoais, sem afastar foro ou direito obrigatório previsto em lei. A identificação legal da pessoa ou organização responsável pelo LeafCare e um canal oficial de contato ainda não foram informados; esta versão precisa ser completada antes de uma publicação legal definitiva."
        )
    ),
    PrivacyPolicy(
        title = "Política de Privacidade",
        sections = listOf(
            "1. Aviso importante sobre esta versão" to "Esta Política descreve o funcionamento observado no LeafCare. A identificação do controlador, o canal de contato para pedidos de titulares e os prazos de retenção ainda não foram definidos pelo responsável pelo projeto. Por isso, esta versão é informativa e precisa desses dados antes de ser tratada como aviso legal final. O uso de fotos para treinamento permanece integralmente desativado até a publicação das informações necessárias.",
            "2. Quem é responsável" to "O LeafCare ainda não informou o nome ou razão social, país e endereço do controlador dos dados. O Supabase é o provedor de infraestrutura de autenticação, banco de dados e armazenamento usado pelo projeto; sua atuação e localização dependem da configuração da instância. O responsável deve publicar sua identificação e um canal de privacidade acessível dentro do app para que solicitações possam ser atendidas.",
            "3. Dados tratados" to "No cadastro, são usados nome de exibição, e-mail, identificador da conta e credenciais de autenticação gerenciadas pelo Supabase Auth. Também são registrados a versão dos Termos de Uso e da Política de Privacidade aceita no cadastro e a data/hora de aceite fornecida pelo servidor. Para análises, o app trata a foto escolhida, data e hora, classe prevista, nome comum e científico, confiança, alternativas Top-3, indicador inconclusivo, limiar, tempo de inferência, versão/hash do modelo e versão do app. Um identificador aleatório liga a análise e sua foto. Não são solicitados endereço, telefone, contatos, microfone ou localização do aparelho. Uma foto selecionada pode conter metadados EXIF de data, modelo do dispositivo ou coordenadas gravadas por quem capturou o arquivo; o app de histórico não remove esses metadados antes de sincronizar a foto.",
            "4. Onde os dados ficam" to "A classificação é executada no dispositivo: a foto não é enviada a um serviço remoto para obter a previsão. O banco Room e os arquivos ficam no espaço privado do aplicativo no aparelho. O manifesto Android desativa o backup automático do app. Para restaurar o histórico em outro dispositivo, os dados de análise são sincronizados com o Postgres privado do Supabase e fotos conclusivas podem ser guardadas no bucket privado `analysis-photos`; fotos inconclusivas não são enviadas, mas seus dados de análise textual podem sincronizar. O Supabase aplica autenticação e políticas de acesso por conta. O uso de rede depende de conexão e pode enviar registros técnicos necessários à comunicação, como endereço IP, ao provedor de infraestrutura.",
            "5. Finalidades e bases de tratamento" to "Os dados de conta são usados para criar e proteger o acesso, confirmar e-mail e recuperar senha. Fotos e dados de análise são usados para executar a classificação solicitada no aparelho e manter, sincronizar, restaurar e excluir o histórico da própria conta. Esses tratamentos são necessários para fornecer as funções escolhidas e administrar a relação de uso; registros exigidos por lei podem ser tratados para cumprir obrigação legal. Dados técnicos estritamente necessários podem ser processados para segurança e operação. O app atualmente não incorpora SDK próprio de publicidade, perfil comercial, analytics ou relatório de falhas. A base legal específica de cada operação deve ser confirmada pelo controlador responsável.",
            "6. Decisões automatizadas" to "Um modelo local compara a imagem com classes aprendidas e produz probabilidades e hipóteses. A inferência não é revisão humana nem diagnóstico definitivo, e pode errar. O LeafCare não usa a classe prevista para decidir acesso, preço, crédito, emprego, tratamento médico ou outro efeito significativo sobre a pessoa. A classe pode acompanhar o histórico e uma foto sincronizada, mas permanece uma previsão até eventual revisão humana separada.",
            "7. Treinamento de modelos" to "O compartilhamento de uma foto para histórico não autoriza seu uso em treinamento. A coleta, os registros de autorização, os uploads e a exportação de treinamento estão bloqueados por padrão no app, servidor e pipeline. Fotos antigas também não são elegíveis automaticamente. Uma eventual ativação dependerá de aviso atualizado sobre controlador, finalidade, retenção, destinatários e direitos, além de aceite geral opcional e autorização por foto. A retirada impedirá novos envios e acionará remoção das cópias elegíveis após sincronização; ela não apaga automaticamente o conhecimento dos pesos de um modelo já treinado. Nenhuma foto pode ser utilizada para treinamento enquanto esses requisitos não forem publicados.",
            "8. Compartilhamento e destinatários" to "O LeafCare não oferece uma galeria pública e não vende fotos ou dados pessoais. Dados de conta e histórico são disponibilizados ao Supabase somente para autenticação, banco e armazenamento privados necessários às funções do app. O Android, a loja de distribuição e provedores de e-mail podem receber dados indispensáveis às suas próprias funções. Dados podem ser apresentados a autoridades quando houver obrigação ou ordem legal válida, ou para proteger direitos e segurança nos limites da lei. Não há integração implementada para compartilhar fotos com redes sociais, anunciantes ou parceiros de treinamento.",
            "9. Transferência internacional" to "O país ou região da instância Supabase deste projeto não está registrado nesta Política. Provedores de infraestrutura podem processar dados em países diferentes do local de uso. O responsável deve confirmar a região configurada, os operadores envolvidos e as salvaguardas contratuais e técnicas aplicáveis antes da publicação definitiva, inclusive conforme a LGPD e regras da ANPD. Não se presume que todos os dados permaneçam no Brasil.",
            "10. Retenção e exclusão" to "Os prazos ainda não foram definidos para cadastro, recibos de aceite, análises, fotos sincronizadas, cópias de segurança e registros técnicos. Localmente, histórico e arquivos permanecem no aparelho até exclusão pelo usuário, limpeza dos dados ou remoção do app; o LeafCare desativa backup automático, mas não consegue apagar cópias feitas pelo sistema operacional ou pelo usuário. Excluir uma análise inicia uma fila de exclusão local/remota; sem conexão, a solicitação remota aguarda sincronização. Não existe no app um fluxo de autoexclusão da conta; a exclusão administrativa da conta e de dados remotos é operacional, mas cópias locais em aparelhos offline não podem ser alcançadas. O controlador deve definir e divulgar prazos por categoria, tratamento de backups e como solicitar a exclusão da conta.",
            "11. Segurança" to "O app usa autenticação Supabase, transporte de rede sem cleartext, armazenamento remoto privado e políticas de banco que restringem registros à conta autenticada. Dados locais ficam no sandbox Android e o backup automático está desativado. Essas medidas reduzem riscos, mas nenhum sistema conectado ou aparelho é totalmente imune a perda, falha, malware, acesso físico ou incidente. Proteja o aparelho e suas credenciais e mantenha o sistema atualizado. O projeto não afirma criptografia local adicional que não esteja implementada no app.",
            "12. Seus direitos" to "Nos termos da LGPD, você pode solicitar confirmação de tratamento, acesso, correção, anonimização, bloqueio ou eliminação de dados desnecessários, excessivos ou tratados em desconformidade, portabilidade quando aplicável, informação sobre compartilhamentos, informação sobre a possibilidade de negar consentimento e consequências, e revogação de consentimento. Também pode apresentar reclamação à Autoridade Nacional de Proteção de Dados. Como o canal oficial do controlador ainda não foi informado, o app não consegue indicar aqui um meio válido para protocolar pedidos; esse contato deve ser publicado antes da versão final.",
            "13. Crianças e adolescentes" to "O LeafCare é destinado exclusivamente a pessoas com 18 anos ou mais e não deve ser usado nem ter conta criada por menores. O cadastro atual não solicita data de nascimento, portanto o responsável deve confirmar as medidas adequadas para impedir ou tratar eventual conta de menor de acordo com a legislação aplicável.",
            "14. Alterações desta Política" to "Esta Política pode ser atualizada quando mudarem as funções, os dados tratados, provedores, regras de retenção ou requisitos legais. Alterações materiais devem ser identificadas por versão e data e apresentadas no próprio aplicativo antes de novo tratamento que exija informação ou escolha. A leitura deste aviso não é consentimento para treinamento de fotos nem substitui escolhas específicas exigidas por lei.",
            "15. Controlador e contato de privacidade" to "O nome ou razão social do controlador, país/estado, endereço e e-mail para exercer direitos ainda não foram definidos. Esses campos são necessários para publicação final e não serão inventados pelo aplicativo. Até que sejam informados, o canal e os prazos para resposta a solicitações de privacidade permanecem pendentes."
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
                            "Este aviso descreve os dados tratados, onde são usados e quais informações ainda precisam ser definidas pelo responsável.",
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

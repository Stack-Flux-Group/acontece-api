package acontece.api.notificacao

import acontece.api.enums.StatusInscricao
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

// Monta os textos dos alertas e publica o evento. Quem chama não espera o Telegram:
// o envio real acontece no AlertaTelegramListener, depois do commit.
@Service
class TelegramNotificador(private val publicador: ApplicationEventPublisher) {

    fun eventoCriado(titulo: String, inicio: LocalDateTime, vagas: Int) =
        publicar(
            """
            Novo evento cadastrado:
            • Título: $titulo
            • Início: ${inicio.format(FORMATO_DATA)}
            • Vagas: $vagas
            """.trimIndent()
        )

    fun eventoAtualizado(titulo: String) = publicar("Evento atualizado: $titulo")

    fun inscricaoCriada(participanteNome: String, eventoTitulo: String) =
        publicar(
            """
            Nova inscrição:
            • Participante: $participanteNome
            • Evento: $eventoTitulo
            • Status: ${StatusInscricao.PENDENTE}
            """.trimIndent()
        )

    fun inscricaoStatus(participanteNome: String, eventoTitulo: String, status: StatusInscricao) =
        publicar(
            """
            Inscrição atualizada:
            • Participante: $participanteNome
            • Evento: $eventoTitulo
            • Novo status: $status
            """.trimIndent()
        )

    // Disparado só na transição para o limite: as falhas seguintes na mesma janela
    // de bloqueio não alertam de novo.
    fun loginBloqueado(email: String, ip: String, minutosBloqueio: Long) =
        publicar(
            """
            Alerta de segurança — acesso bloqueado:
            • E-mail: $email
            • IP: $ip
            • Motivo: tentativas demais de senha errada
            • Bloqueio: $minutosBloqueio min
            """.trimIndent()
        )

    private fun publicar(texto: String) = publicador.publishEvent(AlertaTelegram(texto))

    private companion object {
        val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    }
}

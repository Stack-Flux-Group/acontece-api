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

    fun contaCriada(nome: String, email: String) =
        publicar(
            """
            Nova conta de participante:
            • Nome: $nome
            • E-mail: $email
            """.trimIndent()
        )

    // A inativação é sempre pedida por um organizador (a rota PATCH /participantes/{id}/status
    // é exclusiva dele), então a mensagem já diz quem agiu.
    fun contaInativada(nome: String, email: String) =
        publicar(
            """
            Conta de participante inativada pelo organizador:
            • Nome: $nome
            • E-mail: $email
            • Efeito: login bloqueado e inscrições em eventos não encerrados canceladas
            """.trimIndent()
        )

    fun contaReativada(nome: String, email: String) =
        publicar(
            """
            Conta de participante reativada pelo organizador:
            • Nome: $nome
            • E-mail: $email
            """.trimIndent()
        )

    // A exclusão pode partir do próprio participante (DELETE com o próprio id) ou do organizador.
    fun contaExcluida(nome: String, email: String, peloProprioParticipante: Boolean) =
        publicar(
            """
            Conta de participante excluída${if (peloProprioParticipante) " pelo próprio participante" else " por um organizador"}:
            • Nome: $nome
            • E-mail: $email
            • Efeito: dados pessoais apagados; inscrições antigas ficam como "Participante excluído"
            """.trimIndent()
        )

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
    // de bloqueio não alertam de novo. Vai pelo evento imediato porque quem publica está
    // numa transação que termina em exceção (rollback) — AFTER_COMMIT nunca entregaria.
    fun loginBloqueado(email: String, ip: String, minutosBloqueio: Long) =
        publicador.publishEvent(
            AlertaTelegramImediato(
                """
                Alerta de segurança — acesso bloqueado:
                • E-mail: $email
                • IP: $ip
                • Motivo: tentativas demais de senha errada
                • Bloqueio: $minutosBloqueio min
                """.trimIndent()
            )
        )

    private fun publicar(texto: String) = publicador.publishEvent(AlertaTelegram(texto))

    private companion object {
        val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    }
}

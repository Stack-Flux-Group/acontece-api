package acontece.api.notificacao

import acontece.api.client.TelegramClient
import acontece.api.client.TelegramMensagemRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AlertaTelegramListener(
    private val client: TelegramClient,
    // Vazios = notificações desligadas; a API funciona normal sem o Telegram.
    @Value("\${telegram.bot-token:}") private val botToken: String,
    @Value("\${telegram.chat-id:}") private val chatId: String,
) {
    private val log = LoggerFactory.getLogger(AlertaTelegramListener::class.java)

    // AFTER_COMMIT: o alerta só sai se a operação gravou de verdade. @Async: a chamada
    // HTTP roda em outra thread, então um Telegram lento não segura a resposta da API.
    // fallbackExecution cobre quem publica sem transação aberta (ex.: save direto).
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun enviar(alerta: AlertaTelegram) = enviarTexto(alerta.texto)

    // Alerta de segurança: sai na hora, sem esperar commit — a transação de origem
    // (ex.: login com senha errada) termina em rollback e AFTER_COMMIT o descartaria.
    @Async
    @EventListener
    fun enviarImediato(alerta: AlertaTelegramImediato) = enviarTexto(alerta.texto)

    private fun enviarTexto(texto: String) {
        if (botToken.isBlank() || chatId.isBlank()) return
        try {
            client.enviarMensagem(TelegramMensagemRequest(chat_id = chatId, text = texto))
        } catch (ex: Exception) {
            log.warn("Não foi possível enviar alerta ao Telegram: {}", ex.message)
        }
    }
}

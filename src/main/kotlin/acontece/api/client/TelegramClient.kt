package acontece.api.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

// A API do Telegram exige o token na URL ("/bot<token>/<metodo>"). Ele entra por
// telegram.api-url, montada em application.properties a partir de TELEGRAM_BOT_TOKEN.
@FeignClient(name = "telegram", url = "\${telegram.api-url}")
interface TelegramClient {

    @PostMapping("/sendMessage")
    fun enviarMensagem(@RequestBody mensagem: TelegramMensagemRequest): TelegramResposta
}

data class TelegramMensagemRequest(
    val chat_id: String,
    val text: String,
)

data class TelegramResposta(
    val ok: Boolean,
    val description: String? = null,
)

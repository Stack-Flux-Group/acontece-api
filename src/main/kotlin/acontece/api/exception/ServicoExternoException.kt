package acontece.api.exception

// Um serviço de fora (como o ViaCEP) falhou ou não respondeu a tempo. Vira 502 Bad Gateway:
// o pedido do cliente estava certo, quem falhou foi o serviço que a API consultou.
class ServicoExternoException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

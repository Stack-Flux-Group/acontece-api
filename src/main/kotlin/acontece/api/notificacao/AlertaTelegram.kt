package acontece.api.notificacao

// Evento interno com o texto pronto do alerta. Leva String e nao a entidade porque quem
// publica pode estar numa transacao, e os campos LAZY somem depois do commit.
class AlertaTelegram(val texto: String)

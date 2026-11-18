package acontece.api.notificacao

// Evento interno com o texto pronto do alerta. Carrega String e não a entidade porque
// quem publica pode estar dentro de uma transação, e os campos LAZY (participante,
// evento) não existem mais depois do commit.
class AlertaTelegram(val texto: String)

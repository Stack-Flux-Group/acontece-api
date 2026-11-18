package acontece.api.notificacao

// Par do AlertaTelegram para avisos que não podem esperar o commit: quem publica costuma
// estar numa transação que vai falhar (ex.: login com senha errada termina em exceção e
// rollback, e AFTER_COMMIT descartaria o evento). Este usa @EventListener comum e sai na hora.
class AlertaTelegramImediato(val texto: String)

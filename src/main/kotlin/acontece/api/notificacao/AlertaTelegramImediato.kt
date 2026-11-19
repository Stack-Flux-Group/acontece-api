package acontece.api.notificacao

// Par do AlertaTelegram pros avisos urgentes: a transacao de origem costuma dar rollback (ex.: login
// errado) e AFTER_COMMIT descartaria o evento. Este usa @EventListener comum e sai na hora.
class AlertaTelegramImediato(val texto: String)

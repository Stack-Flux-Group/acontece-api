package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao
import acontece.api.modules.evento.Evento
import acontece.api.modules.usuario.Participante
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime

// Liga um Participante a um Evento. O status controla se a inscrição ocupa vaga:
// PENDENTE e CONFIRMADA ocupam, CANCELADA libera.
@Entity
class Inscricao(
    // LAZY: só busca o participante/evento no banco quando alguém realmente usar.
    @ManyToOne(fetch = FetchType.LAZY, optional = false) var participante: Participante,
    @ManyToOne(fetch = FetchType.LAZY, optional = false) var evento: Evento,

    // STRING grava o nome ("PENDENTE") em vez da posição no enum, que quebraria se a ordem mudasse.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: StatusInscricao = StatusInscricao.PENDENTE,
    @Column(nullable = false) val dataInscricao: LocalDateTime = LocalDateTime.now(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)

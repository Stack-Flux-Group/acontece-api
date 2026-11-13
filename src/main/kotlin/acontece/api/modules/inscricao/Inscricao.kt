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

@Entity
class Inscricao(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    var participante: Participante,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    var evento: Evento,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: StatusInscricao = StatusInscricao.PENDENTE,

    @Column(nullable = false)
    val dataInscricao: LocalDateTime = LocalDateTime.now()
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}

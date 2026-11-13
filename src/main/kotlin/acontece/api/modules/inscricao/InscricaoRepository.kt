package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface InscricaoRepository : JpaRepository<Inscricao, Long> {

    // Quantas inscrições do evento estão num dos status dados (usado para contar as que ocupam vaga)
    fun countByEventoIdAndStatusIn(eventoId: Long, statuses: Collection<StatusInscricao>): Long

    fun existsByParticipanteIdAndEventoIdAndStatusIn(
        participanteId: Long,
        eventoId: Long,
        statuses: Collection<StatusInscricao>,
    ): Boolean

    // Cada filtro é ignorado quando vem nulo: "IS NULL OR ..." deixa a condição sempre verdadeira.
    @Query(
        """
        SELECT i FROM Inscricao i
        WHERE (:eventoId IS NULL OR i.evento.id = :eventoId)
          AND (:participanteId IS NULL OR i.participante.id = :participanteId)
          AND (:status IS NULL OR i.status = :status)
        """
    )
    fun buscar(
        @Param("eventoId") eventoId: Long?,
        @Param("participanteId") participanteId: Long?,
        @Param("status") status: StatusInscricao?,
        pageable: Pageable,
    ): Page<Inscricao>
}

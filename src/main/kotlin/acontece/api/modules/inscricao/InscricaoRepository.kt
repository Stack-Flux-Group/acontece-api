package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

// Cada linha do resultado de contarPorEvento: o Spring preenche pelos apelidos (AS) da consulta.
interface OcupacaoDoEvento {
    val eventoId: Long
    val ocupadas: Long
}

interface InscricaoRepository : JpaRepository<Inscricao, Long> {

    // Quantas inscricoes do evento estao num dos status dados (usado pra contar as que ocupam vaga).
    fun countByEventoIdAndStatusIn(eventoId: Long, statuses: Collection<StatusInscricao>): Long

    // A mesma contagem pra varios eventos de uma vez (GROUP BY). Sem ela, cada evento da pagina
    // faria a propria consulta. Evento sem inscricao nos status nao aparece no resultado.
    @Query(
        """
        SELECT i.evento.id AS eventoId, COUNT(i) AS ocupadas
        FROM Inscricao i
        WHERE i.evento.id IN :eventoIds AND i.status IN :statuses
        GROUP BY i.evento.id
        """
    )
    fun contarPorEvento(
        @Param("eventoIds") eventoIds: Collection<Long>,
        @Param("statuses") statuses: Collection<StatusInscricao>,
    ): List<OcupacaoDoEvento>

    fun existsByParticipanteIdAndEventoIdAndStatusIn(
        participanteId: Long,
        eventoId: Long,
        statuses: Collection<StatusInscricao>,
    ): Boolean

    // Busca a inscricao travando a linha (FOR UPDATE) ate o fim da transacao, entao dois PATCH
    // juntos passam um de cada vez e a transicao sempre ve o status ja gravado. So funciona em @Transactional.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inscricao i WHERE i.id = :id")
    fun buscarParaAtualizar(@Param("id") id: Long): Inscricao?

    // Cancela as inscricoes que ocupam vaga em evento que ainda nao acabou (encerrado fica como historico).
    // Sem clearAutomatico: limpar o contexto jogaria fora as mudancas que quem chamou ainda vai fazer.
    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE Inscricao i SET i.status = :cancelada
        WHERE i.participante.id = :participanteId
          AND i.status IN :ativas
          AND i.evento.id IN (SELECT e.id FROM Evento e WHERE e.dataFim > :agora)
        """
    )
    fun cancelarEmAbertoDoParticipante(
        @Param("participanteId") participanteId: Long,
        @Param("agora") agora: LocalDateTime,
        @Param("ativas") ativas: Collection<StatusInscricao>,
        @Param("cancelada") cancelada: StatusInscricao,
    ): Int

    // Filtro nulo e ignorado ("IS NULL OR ..." deixa a condicao sempre verdadeira).
    // @EntityGraph traz participante e evento no mesmo SELECT; sem ele cada linha faria +2 consultas.
    @EntityGraph(attributePaths = ["participante", "evento"])
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

package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

// Uma linha do resultado de contarPorEvento: o Spring preenche pelos apelidos (AS) da consulta.
interface OcupacaoDoEvento {
    val eventoId: Long
    val ocupadas: Long
}

interface InscricaoRepository : JpaRepository<Inscricao, Long> {

    // Quantas inscrições do evento estão num dos status dados (usado para contar as que ocupam vaga)
    fun countByEventoIdAndStatusIn(eventoId: Long, statuses: Collection<StatusInscricao>): Long

    // A mesma contagem para vários eventos de uma vez (GROUP BY), usada nas listagens de eventos.
    // Sem ela, cada evento da página dispararia a própria consulta de contagem.
    // Evento sem nenhuma inscrição nos status dados não aparece no resultado.
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

    // Busca a inscrição travando a linha (SELECT ... FOR UPDATE) até o fim da transação. Dois PATCH
    // simultâneos na mesma inscrição passam um de cada vez, então a validação da transição sempre
    // enxerga o status já gravado pelo anterior. Só funciona dentro de uma @Transactional.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inscricao i WHERE i.id = :id")
    fun buscarParaAtualizar(@Param("id") id: Long): Inscricao?

    // Cada filtro é ignorado quando vem nulo: "IS NULL OR ..." deixa a condição sempre verdadeira.
    // @EntityGraph busca participante e evento no mesmo SELECT (JOIN). Sem ele, a resposta de cada
    // linha dispararia mais duas consultas (participante e evento são LAZY), o que numa página de
    // 20 inscrições seriam 41 idas ao banco. É seguro com paginação porque são relações *ToOne.
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

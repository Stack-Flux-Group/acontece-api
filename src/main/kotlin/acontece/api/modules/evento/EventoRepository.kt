package acontece.api.modules.evento

import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

// Query derivada: o Spring monta a consulta pelo nome do método.
// @Query: quando a consulta é complicada e quer escrever na mão.
// JpaSpecificationExecutor: monta o WHERE com os filtros que vierem (ver EventoFiltros).
interface EventoRepository : JpaRepository<Evento, Long>, JpaSpecificationExecutor<Evento> {

    // Nome vira consulta: findBy + Titulo + Containing (LIKE %valor%) + IgnoreCase
    fun findByTituloContainingIgnoreCase(titulo: String): List<Evento>

    @Query(
        """
        SELECT e FROM Evento e
        WHERE e.dataInicio >= :inicio AND e.dataInicio <= :fim
        ORDER BY e.dataInicio
        """
    )
    fun buscarPorPeriodo(
        @Param("inicio") inicio: LocalDateTime,
        @Param("fim") fim: LocalDateTime,
    ): List<Evento>

    // Trava a linha do evento no banco (SELECT ... FOR UPDATE) até a transação acabar.
    // Quem chegar junto espera a vez, então a contagem de vagas não é lida por dois
    // pedidos ao mesmo tempo. Só funciona dentro de uma transação.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Evento e WHERE e.id = :id")
    fun buscarComTrava(@Param("id") id: Long): Evento?
}

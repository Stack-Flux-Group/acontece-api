package acontece.api.modules.evento

import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

// Query derivada: serve para buscas simples. É só seguir o padrão de nome que o Spring monta a
// consulta.
// @Query: serve quando a consulta é mais complexa ou você quer controlar exatamente o que é
// executado.

// JpaSpecificationExecutor: permite montar a consulta a partir dos filtros que vierem (ver
// EventoFiltros), em vez de uma query para cada combinação.
interface EventoRepository : JpaRepository<Evento, Long>, JpaSpecificationExecutor<Evento> {

    // Query method - Spring Data JPA vai gerar a implementação automaticamente
    // findBy + Titulo + Containing (LIKE %valor%) + IgnoreCase (case-insensitive)
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

    // Busca o evento travando a linha no banco (SELECT ... FOR UPDATE) até o fim da transação.
    // Inscrições no mesmo evento e a edição das vagas esperam a sua vez, então a contagem de vagas
    // nunca é lida por dois pedidos ao mesmo tempo. Só funciona dentro de uma transação.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Evento e WHERE e.id = :id")
    fun buscarComTrava(@Param("id") id: Long): Evento?
}

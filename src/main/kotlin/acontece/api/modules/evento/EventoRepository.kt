package acontece.api.modules.evento

import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

// Query derivada: serve para buscas simples. É só seguir o padrão de nome que o Spring monta a
// consulta.
// @Query: serve quando a consulta é mais complexa ou você quer controlar exatamente o que é
// executado.

interface EventoRepository : JpaRepository<Evento, Long> {

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
}

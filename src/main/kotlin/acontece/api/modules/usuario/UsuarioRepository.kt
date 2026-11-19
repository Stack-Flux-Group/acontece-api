package acontece.api.modules.usuario

import acontece.api.enums.StatusUsuario
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UsuarioRepository : JpaRepository<Usuario, Long> {

    fun findByEmail(email: String): Usuario?

    fun existsByEmail(email: String): Boolean

    fun existsByEmailAndIdNot(email: String, id: Long): Boolean

    // So o status, sem carregar o usuario inteiro: roda a cada request com token.
    @Query("SELECT u.status FROM Usuario u WHERE u.id = :id")
    fun buscarStatus(@Param("id") id: Long): StatusUsuario?
}

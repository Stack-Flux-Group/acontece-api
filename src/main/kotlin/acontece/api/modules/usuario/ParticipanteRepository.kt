package acontece.api.modules.usuario

import acontece.api.enums.StatusUsuario
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ParticipanteRepository : JpaRepository<Participante, Long> {

    fun existsByCpf(cpf: String): Boolean

    fun existsByCpfAndIdNot(cpf: String, id: Long): Boolean

    fun findAllByStatusNot(status: StatusUsuario, pageable: Pageable): Page<Participante>

    // Trava a linha do participante ate o fim da transacao. Inscrever, inativar e excluir passam por
    // aqui, entao uma inscricao nunca nasce no meio de uma inativacao. So funciona em @Transactional.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Participante p WHERE p.id = :id")
    fun buscarComTrava(@Param("id") id: Long): Participante?
}

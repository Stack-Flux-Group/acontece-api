package acontece.api.modules.usuario

import org.springframework.data.jpa.repository.JpaRepository

interface ParticipanteRepository : JpaRepository<Participante, Long> {

    fun existsByCpf(cpf: String): Boolean

    fun existsByCpfAndIdNot(cpf: String, id: Long): Boolean
}

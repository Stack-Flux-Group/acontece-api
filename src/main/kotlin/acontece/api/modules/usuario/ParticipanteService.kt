package acontece.api.modules.usuario

import acontece.api.exception.ConflictException
import acontece.api.exception.ResourceNotFoundException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Page
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class ParticipanteService(
    private val repository: ParticipanteRepository
) {
    
    @Transactional
    fun criar(request: ParticipanteRequest): ParticipanteResponse {
        val dados = request.normalizar()

        if (repository.existsByCpf(dados.cpf)) {
            throw ConflictException("Já existe um participante com esse CPF")
        }

        if (repository.existsByEmail(dados.email)) {
            throw ConflictException("Já existe um participante com esse Email")
        }

        return repository.save(dados.toEntity()).toResponse()
    }

    @Transactional(readOnly = true)
    fun buscarPorId(id: Long): ParticipanteResponse =
        repository.findByIdOrNull(id)?.toResponse()
            ?: throw ResourceNotFoundException("Participante $id não encontrado")

    @Transactional(readOnly = true)
    fun listar(pageable: Pageable): Page<ParticipanteResponse> =
        repository.findAll(pageable).map { it.toResponse() }
    
}
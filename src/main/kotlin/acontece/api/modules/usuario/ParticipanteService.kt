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
        val dados = request.normalizado()

        if (repository.existsByCpf(dados.cpf)) {
            throw ConflictException("Já existe um participante com esse CPF")
        }

        if (repository.existsByEmail(dados.email)) {
            throw ConflictException("Já existe um participante com esse Email")
        }

        return repository.save(dados.toEntity()).toResponse()
    }

    @Transactional(readOnly = true)
    fun buscarPorId(id: Long): ParticipanteResponse = obter(id).toResponse()

    @Transactional(readOnly = true)
    fun listar(pageable: Pageable): Page<ParticipanteResponse> =
        repository.findAll(pageable).map { it.toResponse() }

    @Transactional
    fun atualizar(id: Long, request: ParticipantePatchRequest): ParticipanteResponse {
        val participante = obter(id)
        val dados = request.normalizado()

        if (dados.cpf != null && repository.existsByCpfAndIdNot(dados.cpf, id)) {
            throw ConflictException("Já existe um participante com esse CPF")
        }

        if (dados.email != null && repository.existsByEmailAndIdNot(dados.email, id)) {
            throw ConflictException("Já existe um participante com esse Email")
        }

        dados.nome?.let { participante.nome = it }
        dados.email?.let { participante.email = it }
        dados.cpf?.let { participante.cpf = it }
        dados.telefone?.let { participante.telefone = it.ifEmpty { null } }

        return participante.toResponse()
    }

    @Transactional
    fun excluir(id: Long) {
        if (!repository.existsById(id)) {
            throw ResourceNotFoundException("Participante $id não encontrado")
        }
        repository.deleteById(id)
    }


    private fun obter(id: Long): Participante =
        repository.findByIdOrNull(id)
            ?: throw ResourceNotFoundException("Participante $id não encontrado")
    
}
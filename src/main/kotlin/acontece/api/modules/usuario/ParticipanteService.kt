package acontece.api.modules.usuario

import acontece.api.exception.ConflictException
import acontece.api.exception.ResourceNotFoundException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Page
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ParticipanteService(
    private val repository: ParticipanteRepository,
    // O e-mail é único entre TODOS os usuários (participantes e organizadores), então a checagem
    // olha a tabela de usuários, não só a de participantes.
    private val usuarioRepository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    
    @Transactional
    fun criar(request: ParticipanteRequest): ParticipanteResponse {
        val dados = request.normalizado()

        if (repository.existsByCpf(dados.cpf)) {
            throw ConflictException("Já existe um participante com esse CPF")
        }

        if (usuarioRepository.existsByEmail(dados.email)) {
            throw ConflictException(EMAIL_EM_USO)
        }

        return repository.save(dados.toEntity(passwordEncoder.encode(dados.senha)!!)).toResponse()
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

        if (dados.email != null && usuarioRepository.existsByEmailAndIdNot(dados.email, id)) {
            throw ConflictException(EMAIL_EM_USO)
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

    private companion object {
        const val EMAIL_EM_USO = "Já existe um cadastro com esse e-mail"
    }
}
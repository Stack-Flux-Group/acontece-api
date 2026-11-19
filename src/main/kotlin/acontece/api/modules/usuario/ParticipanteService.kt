package acontece.api.modules.usuario

import acontece.api.enums.StatusUsuario
import acontece.api.exception.ConflictException
import acontece.api.exception.ResourceNotFoundException
import acontece.api.modules.inscricao.InscricaoService
import acontece.api.notificacao.TelegramNotificador
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Page
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ParticipanteService(
    private val repository: ParticipanteRepository,
    // E-mail e unico pra todos os usuarios, entao a checagem olha a tabela de usuarios, nao a de participantes.
    private val usuarioRepository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    // Inativar ou excluir a conta cancela as inscricoes em aberto.
    private val inscricaoService: InscricaoService,
    private val notificador: TelegramNotificador,
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

        val resposta = repository.save(dados.toEntity(passwordEncoder.encode(dados.senha)!!)).toResponse()
        notificador.contaCriada(resposta.nome, resposta.email)
        return resposta
    }

    @Transactional(readOnly = true)
    fun buscarPorId(id: Long): ParticipanteResponse = obter(id).toResponse()

    // Excluida nao aparece; inativa aparece, pro organizador poder reativar.
    @Transactional(readOnly = true)
    fun listar(pageable: Pageable): Page<ParticipanteResponse> =
        repository.findAllByStatusNot(StatusUsuario.EXCLUIDO, pageable).map { it.toResponse() }

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

    // Inativar bloqueia o login e cancela as inscricoes em aberto (reativar nao traz elas de volta).
    // Mandar o status atual de novo nao muda nada.
    @Transactional
    fun alterarStatus(id: Long, request: AlterarStatusParticipanteRequest): ParticipanteResponse {
        val novoStatus = requireNotNull(request.status) { "O status é obrigatório" }
        require(novoStatus != StatusUsuario.EXCLUIDO) {
            "Para excluir a conta, use DELETE /participantes/{id}"
        }
        val participante = obterComTrava(id)
        val statusAnterior = participante.status

        if (novoStatus == StatusUsuario.INATIVO && statusAnterior == StatusUsuario.ATIVO) {
            inscricaoService.cancelarEmAbertoDoParticipante(id)
        }
        participante.status = novoStatus

        // So avisa quando o status muda de verdade: repetir o atual nao notifica.
        when {
            statusAnterior == StatusUsuario.ATIVO && novoStatus == StatusUsuario.INATIVO ->
                notificador.contaInativada(participante.nome, participante.email)
            statusAnterior == StatusUsuario.INATIVO && novoStatus == StatusUsuario.ATIVO ->
                notificador.contaReativada(participante.nome, participante.email)
        }
        return participante.toResponse()
    }

    // Nao apaga a linha (as inscricoes apontam pra ela): apaga os dados pessoais e marca EXCLUIDO,
    // o que pra API e o mesmo que nao existir mais (404 e token recusado).
    @Transactional
    fun excluir(id: Long, peloProprioParticipante: Boolean) {
        val participante = obterComTrava(id)
        // Pega nome e e-mail antes de anonimizar: depois disso eles nao existem mais.
        val nome = participante.nome
        val email = participante.email
        inscricaoService.cancelarEmAbertoDoParticipante(id)
        participante.anonimizar()
        notificador.contaExcluida(nome, email, peloProprioParticipante)
    }

    private fun obter(id: Long): Participante =
        repository.findByIdOrNull(id)?.takeIf { it.status != StatusUsuario.EXCLUIDO }
            ?: throw naoEncontrado(id)

    private fun obterComTrava(id: Long): Participante =
        repository.buscarComTrava(id)?.takeIf { it.status != StatusUsuario.EXCLUIDO }
            ?: throw naoEncontrado(id)

    private fun naoEncontrado(id: Long) = ResourceNotFoundException("Participante $id não encontrado")

    private companion object {
        const val EMAIL_EM_USO = "Já existe um cadastro com esse e-mail"
    }
}

package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao
import acontece.api.enums.StatusUsuario
import acontece.api.exception.ConflictException
import acontece.api.exception.ResourceNotFoundException
import acontece.api.modules.evento.EventoRepository
import acontece.api.modules.usuario.ParticipanteRepository
import acontece.api.notificacao.TelegramNotificador
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InscricaoService(
    private val repository: InscricaoRepository,
    private val eventoRepository: EventoRepository,
    private val participanteRepository: ParticipanteRepository,
    private val clock: Clock,
    private val notificador: TelegramNotificador,
) {

    @Transactional
    fun criar(request: InscricaoRequest): InscricaoResponse {
        // O @Valid do controller já barra null; o requireNotNull cobre quem chama o service direto.
        val participanteId = requireNotNull(request.participanteId) { "O participante é obrigatório" }
        val eventoId = requireNotNull(request.eventoId) { "O evento é obrigatório" }

        // A trava no participante segura uma inativação/exclusão que chegue ao mesmo tempo: ou a
        // inscrição entra antes (e a inativação a cancela), ou a conta já está inativa e ela é recusada.
        val participante =
            participanteRepository.buscarComTrava(participanteId)?.takeIf { it.status != StatusUsuario.EXCLUIDO }
                ?: throw ResourceNotFoundException("Participante $participanteId não encontrado")
        if (participante.status != StatusUsuario.ATIVO) {
            throw ConflictException("A conta do participante está inativa")
        }

        // Aqui a linha do evento fica travada até o fim da transação: as checagens abaixo (duplicada
        // e vagas) rodam uma inscrição de cada vez por evento, então ninguém passa do limite.
        val evento =
            eventoRepository.buscarComTrava(eventoId)
                ?: throw ResourceNotFoundException("Evento $eventoId não encontrado")

        // "Agora" vem do Clock (horário de São Paulo), o mesmo fuso em que o evento foi cadastrado.
        val agora = LocalDateTime.now(clock)
        require(evento.dataFim.isAfter(agora)) { "O evento já foi encerrado" }

        if (
            repository.existsByParticipanteIdAndEventoIdAndStatusIn(
                participanteId,
                eventoId,
                StatusInscricao.QUE_OCUPAM_VAGA,
            )
        ) {
            throw ConflictException("Participante já possui inscrição ativa neste evento")
        }

        if (repository.countByEventoIdAndStatusIn(eventoId, StatusInscricao.QUE_OCUPAM_VAGA) >= evento.vagas) {
            throw ConflictException("Evento sem vagas disponíveis")
        }

        val resposta =
            repository
                .save(Inscricao(participante = participante, evento = evento, dataInscricao = agora))
                .toResponse()
        notificador.inscricaoCriada(resposta.participanteNome, resposta.eventoTitulo)
        return resposta
    }

    @Transactional(readOnly = true)
    fun buscarPorId(id: Long): InscricaoResponse = obter(id).toResponse()

    // O map { } roda aqui dentro, com a transação aberta, porque toResponse() lê participante e
    // evento (LAZY).
    @Transactional(readOnly = true)
    fun listar(
        eventoId: Long?,
        participanteId: Long?,
        status: StatusInscricao?,
        pageable: Pageable,
    ): Page<InscricaoResponse> =
        repository.buscar(eventoId, participanteId, status, pageable).map { it.toResponse() }

    // Confirmar ou cancelar nunca ocupa vaga nova (CANCELADA é final e reinscrever cria uma linha
    // nova), mas a transição precisa ser atômica: sem a trava, dois PATCH simultâneos leriam o mesmo
    // status e o último a gravar venceria, permitindo até CANCELADA → CONFIRMADA.
    @Transactional
    fun atualizarStatus(id: Long, request: AtualizarStatusRequest): InscricaoResponse {
        val novoStatus = requireNotNull(request.status) { "O status é obrigatório" }
        val inscricao = obterParaAtualizar(id)

        if (novoStatus !in TRANSICOES.getValue(inscricao.status)) {
            throw ConflictException("Transição de status inválida: ${inscricao.status} → $novoStatus")
        }

        inscricao.status = novoStatus // JPA grava sozinho ao fim da transação
        val resposta = inscricao.toResponse()
        notificador.inscricaoStatus(resposta.participanteNome, resposta.eventoTitulo, novoStatus)
        return resposta
    }

    // O participante desiste: a inscrição vira CANCELADA e a vaga volta para o evento.
    // Usa a mesma trava de atualizarStatus, então um cancelamento e uma confirmação do organizador
    // ao mesmo tempo passam um de cada vez.
    @Transactional
    fun cancelarPeloParticipante(id: Long, participanteId: Long): InscricaoResponse {
        val inscricao = obterParaAtualizar(id)

        // Mesma regra do GET: só o dono mexe na própria inscrição.
        if (inscricao.participante.id != participanteId) {
            throw AccessDeniedException("Acesso negado a esta inscrição")
        }
        if (inscricao.status == StatusInscricao.CANCELADA) {
            throw ConflictException("A inscrição já está cancelada")
        }
        // Depois que o evento acabou, a inscrição vira histórico (quem estava inscrito) e não muda mais.
        require(inscricao.evento.dataFim.isAfter(LocalDateTime.now(clock))) { "O evento já foi encerrado" }

        inscricao.status = StatusInscricao.CANCELADA
        val resposta = inscricao.toResponse()
        notificador.inscricaoStatus(resposta.participanteNome, resposta.eventoTitulo, StatusInscricao.CANCELADA)
        return resposta
    }

    // Chamado ao inativar ou excluir a conta: libera as vagas que o participante ainda ocupava.
    // Roda dentro da transação de quem chamou, que já travou o participante.
    @Transactional
    fun cancelarEmAbertoDoParticipante(participanteId: Long): Int =
        repository.cancelarEmAbertoDoParticipante(
            participanteId,
            LocalDateTime.now(clock),
            StatusInscricao.QUE_OCUPAM_VAGA,
            StatusInscricao.CANCELADA,
        )

    private fun obter(id: Long): Inscricao =
        repository.findByIdOrNull(id) ?: throw ResourceNotFoundException("Inscrição $id não encontrada")

    private fun obterParaAtualizar(id: Long): Inscricao =
        repository.buscarParaAtualizar(id)
            ?: throw ResourceNotFoundException("Inscrição $id não encontrada")

    private companion object {
        // Para cada status atual, os status para os quais ele pode ir.
        val TRANSICOES =
            mapOf(
                StatusInscricao.PENDENTE to
                    setOf(StatusInscricao.CONFIRMADA, StatusInscricao.CANCELADA),
                StatusInscricao.CONFIRMADA to setOf(StatusInscricao.CANCELADA),
                StatusInscricao.CANCELADA to emptySet<StatusInscricao>(),
            )
    }
}

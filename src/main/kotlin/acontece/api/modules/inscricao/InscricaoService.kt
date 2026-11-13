package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao
import acontece.api.exception.ConflictException
import acontece.api.exception.ResourceNotFoundException
import acontece.api.modules.evento.EventoRepository
import acontece.api.modules.usuario.ParticipanteRepository
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InscricaoService(
    private val repository: InscricaoRepository,
    private val eventoRepository: EventoRepository,
    private val participanteRepository: ParticipanteRepository,
    private val clock: Clock,
) {

    @Transactional
    fun criar(request: InscricaoRequest): InscricaoResponse {
        // O @Valid do controller já barra null; o requireNotNull cobre quem chama o service direto.
        val participanteId = requireNotNull(request.participanteId) { "O participante é obrigatório" }
        val eventoId = requireNotNull(request.eventoId) { "O evento é obrigatório" }

        val participante =
            participanteRepository.findByIdOrNull(participanteId)
                ?: throw ResourceNotFoundException("Participante $participanteId não encontrado")

        // Aqui a linha do evento fica travada até o fim da transação: as checagens abaixo (duplicada
        // e vagas) rodam uma inscrição de cada vez por evento, então ninguém passa do limite.
        val evento =
            eventoRepository.buscarParaInscricao(eventoId)
                ?: throw ResourceNotFoundException("Evento $eventoId não encontrado")

        // "Agora" vem do Clock (horário de São Paulo), o mesmo fuso em que o evento foi cadastrado.
        val agora = LocalDateTime.now(clock)
        require(evento.dataFim.isAfter(agora)) { "O evento já foi encerrado" }

        if (
            repository.existsByParticipanteIdAndEventoIdAndStatusIn(
                participanteId,
                eventoId,
                STATUS_ATIVOS,
            )
        ) {
            throw ConflictException("Participante já possui inscrição ativa neste evento")
        }

        if (repository.countByEventoIdAndStatusIn(eventoId, STATUS_ATIVOS) >= evento.vagas) {
            throw ConflictException("Evento sem vagas disponíveis")
        }

        return repository
            .save(Inscricao(participante = participante, evento = evento, dataInscricao = agora))
            .toResponse()
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
        return inscricao.toResponse()
    }

    private fun obter(id: Long): Inscricao =
        repository.findByIdOrNull(id) ?: throw ResourceNotFoundException("Inscrição $id não encontrada")

    private fun obterParaAtualizar(id: Long): Inscricao =
        repository.buscarParaAtualizar(id)
            ?: throw ResourceNotFoundException("Inscrição $id não encontrada")

    private companion object {
        // Status que ocupam vaga. CANCELADA libera.
        val STATUS_ATIVOS = listOf(StatusInscricao.PENDENTE, StatusInscricao.CONFIRMADA)

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

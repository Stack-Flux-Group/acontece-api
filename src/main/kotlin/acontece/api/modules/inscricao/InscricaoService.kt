package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao
import acontece.api.exception.ConflictException
import acontece.api.exception.ResourceNotFoundException
import acontece.api.modules.evento.EventoRepository
import acontece.api.modules.usuario.ParticipanteRepository
import java.time.LocalDateTime
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InscricaoService(
    private val repository: InscricaoRepository,
    private val eventoRepository: EventoRepository,
    private val participanteRepository: ParticipanteRepository,
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

        require(evento.dataFim.isAfter(LocalDateTime.now())) { "O evento já foi encerrado" }

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

        return repository.save(Inscricao(participante = participante, evento = evento)).toResponse()
    }

    private companion object {
        // Status que ocupam vaga. CANCELADA libera.
        val STATUS_ATIVOS = listOf(StatusInscricao.PENDENTE, StatusInscricao.CONFIRMADA)
    }
}

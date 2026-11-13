package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class InscricaoRequest(

    @field:NotNull(message = "O participante é obrigatório")
    val participanteId: Long? = null,

    @field:NotNull(message = "O evento é obrigatório")
    val eventoId: Long? = null
)

data class AtualizarStatusRequest(
    @field:NotNull(message = "O status é obrigatório")
    val status: StatusInscricao? = null
)

data class InscricaoResponse(
    val id: Long,
    val participanteId: Long,
    val participanteNome: String,
    val eventoId: Long,
    val eventoTitulo: String,
    val status: StatusInscricao,
    val dataInscricao: LocalDateTime,
)

fun Inscricao.toResponse() =
    InscricaoResponse(
        id = requireNotNull(id) { "Inscrição ainda não persistida" },
        participanteId = requireNotNull(participante.id),
        participanteNome = participante.nome,
        eventoId = requireNotNull(evento.id),
        eventoTitulo = evento.titulo,
        status = status,
        dataInscricao = dataInscricao,
    )
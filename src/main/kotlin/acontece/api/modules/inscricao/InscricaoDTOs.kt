package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

// Long? com @NotNull: se o campo faltar no JSON, o cliente recebe um erro de validação por campo
// (fieldErrors) em vez de um "JSON malformado" genérico.
data class InscricaoRequest(
    @field:NotNull(message = "O participante é obrigatório") val participanteId: Long? = null,
    @field:NotNull(message = "O evento é obrigatório") val eventoId: Long? = null,
)

// Corpo do PATCH /inscricoes/{id}/status. Um status inexistente (ex.: "XYZ") vira 400 pelo handler
// de JSON ilegível; um corpo sem status vira 400 com fieldErrors, pelo @NotNull.
data class AtualizarStatusRequest(
    @field:NotNull(message = "O status é obrigatório") val status: StatusInscricao? = null
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

// Precisa rodar dentro de uma transação: participante e evento são LAZY e só carregam enquanto a
// sessão do banco está aberta (open-in-view=false).
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

package acontece.api.modules.evento

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

// @field: põe a validação no campo, que é onde o Bean Validation olha. Sem isso ela passa batido.
// @NotBlank recusa null, "" e " ".
// @NotNull é para números e datas.
// @Positive exige valor maior que zero.
// Datas chegam no JSON assim: "2026-12-01T19:00:00"

// O que o cliente envia (POST / PUT)
// Todo campo tem valor padrão: se faltar no JSON, quem reclama é a validação (400 com o nome do
// campo), e não o Jackson (400 genérico de "JSON malformado").
data class EventoRequestDTO(
    @field:NotBlank(message = "O título do evento é obrigatório")
    @field:Size(max = 100, message = "O título do evento deve ter no máximo 100 caracteres")
    val titulo: String = "",
    @field:Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres")
    val descricao: String = "",
    @field:NotNull(message = "A data de início é obrigatória") val dataInicio: LocalDateTime? = null,
    @field:NotNull(message = "A data de fim é obrigatória") val dataFim: LocalDateTime? = null,
    @field:Positive(message = "Vagas deve ser maior que zero") val vagas: Int = 0,

    // Aceita "01001000" ou "01001-000"; vazio também cai aqui
    @field:Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP deve ter 8 dígitos") val cep: String = "",
    val numero: String? = null,
    @field:Size(max = 1000, message = "A URL da foto deve ter no máximo 1000 caracteres")
    val fotoUrl: String? = null,
)

// O que a API devolve
data class EventoResponseDTO(
    val id: Long,
    val titulo: String,
    val descricao: String,
    val dataInicio: LocalDateTime,
    val dataFim: LocalDateTime,
    val vagas: Int,
    // Vagas menos inscrições PENDENTE e CONFIRMADA. Zero = evento lotado.
    val vagasDisponiveis: Int,
    val endereco: Endereco,
    val fotoUrl: String?,
)

// Função de extensão: escreve evento.toResponse(ocupadas) em vez de montar o DTO na mão.
// `vagasOcupadas` vem do EventoService (o Evento não enxerga as próprias inscrições).
fun Evento.toResponse(vagasOcupadas: Long) =
    EventoResponseDTO(
        id = id!!, // depois de salvo, o id nunca é null
        titulo = titulo,
        descricao = descricao,
        dataInicio = dataInicio,
        dataFim = dataFim,
        vagas = vagas,
        // Nunca negativo, mesmo que dado antigo tenha mais inscrições que vagas.
        vagasDisponiveis = (vagas - vagasOcupadas).coerceAtLeast(0).toInt(),
        endereco = endereco,
        fotoUrl = fotoUrl,
    )

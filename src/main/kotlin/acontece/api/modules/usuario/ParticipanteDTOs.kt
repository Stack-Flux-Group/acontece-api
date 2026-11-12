package acontece.api.modules.usuario

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.br.CPF

data class ParticipanteRequest(

    @field:NotBlank(message = "O nome é obrigatorio")
    @field:Size(max = 120, message = "O nome deve ter no maximo 120 caracteres")
    val nome: String = "",

    @field:NotBlank(message = "O e-mail é obrigatório")
    @field:Email(message = "E-mail inválido")
    val email: String = "",

    @field:NotBlank(message = "O CPF é obrigatório")
    @field:CPF(message = "CPF inválido")
    val cpf: String = "",

    @field:Pattern(regexp = """^\d{10,11}$""", message = "O telefone deve ter 10 ou 11 digitos, somente numeros")
    val telefone: String? = null
) {
    fun normalizado() = copy(
        nome = nome.trim(),
        email = email.trim().lowercase(),
        cpf = cpf.filter(Char::isDigit)
    )

    fun toEntity(): Participante = Participante(
        nome = nome,
        email = email,
        cpf = cpf,
        telefone = telefone
    )
}

data class ParticipantePatchRequest(
    @field:Size(max = 120, message = "O nome deve ter no maximo 120 caracteres")
    @field:Pattern(regexp = """.*\S.*""", message = "O nome não pode ficar em branco")
    val nome: String? = null,

    @field:Email(message = "Email inválido")
    @field:Pattern(regexp = """.*\S.*""", message = "O email não pode ficar em branco")
    val email: String? = null,

    @field:CPF(message = "CPF inválido")
    @field:Pattern(regexp = """.*\S.*""", message = "O CPF não pode ficar em branco")
    val cpf: String? = null,

    @field:Pattern(regexp = """^(\d{10,11})?$""", message = "O telefone deve ter 10 ou 11 digitos, somente numeros")
    val telefone: String? = null,
) {
    fun normalizado() = copy(
        nome = nome?.trim(),
        email = email?.trim()?.lowercase(),
        cpf = cpf?.filter(Char::isDigit)
    )
}

data class ParticipanteResponse(
    val id: Long,
    val nome: String,
    val email: String,
    val cpf: String,
    val telefone: String?
)

fun Participante.toResponse() = ParticipanteResponse(
    id = requireNotNull(id) { "Participante ainda não persistido" },
    nome = nome,
    email = email,
    cpf = cpf,
    telefone = telefone
)

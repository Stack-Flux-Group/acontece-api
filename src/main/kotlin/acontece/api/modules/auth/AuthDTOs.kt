package acontece.api.modules.auth

import acontece.api.enums.Papel
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class LoginRequest(

    @field:NotBlank(message = "O e-mail é obrigatório")
    val email: String = "",

    @field:NotBlank(message = "A senha é obrigatória")
    val senha: String = "",
)

data class TokenResponse(
    val token: String,
    val tipo: String = "Bearer",
    val expiraEmSegundos: Long,
    val usuarioId: Long,
    val papel: Papel,
)

// Corpo de PATCH /me/senha. A senha nova segue a mesma regra do cadastro.
data class TrocarSenhaRequest(

    @field:NotBlank(message = "A senha atual é obrigatória")
    val senhaAtual: String = "",

    // BCrypt só lê os primeiros 72 bytes, por isso o teto.
    @field:NotBlank(message = "A nova senha é obrigatória")
    @field:Size(min = 8, max = 72, message = "A nova senha deve ter entre 8 e 72 caracteres")
    val novaSenha: String = "",
)

// Resposta de GET /me. Os dados vêm do banco (nome e e-mail podem ter mudado depois do login);
// só a validade vem do próprio token.
data class UsuarioLogadoResponse(
    val id: Long,
    val nome: String,
    val email: String,
    val papel: Papel,
    val tokenExpiraEm: Instant?,
)

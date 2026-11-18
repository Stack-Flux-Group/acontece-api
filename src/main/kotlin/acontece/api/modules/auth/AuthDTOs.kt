package acontece.api.modules.auth

import acontece.api.enums.Papel
import jakarta.validation.constraints.NotBlank
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

// Resposta de GET /me. Os dados vêm do banco (nome e e-mail podem ter mudado depois do login);
// só a validade vem do próprio token.
data class UsuarioLogadoResponse(
    val id: Long,
    val nome: String,
    val email: String,
    val papel: Papel,
    val tokenExpiraEm: Instant?,
)

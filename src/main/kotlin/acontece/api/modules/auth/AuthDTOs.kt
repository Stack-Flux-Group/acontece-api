package acontece.api.modules.auth

import acontece.api.enums.Papel
import jakarta.validation.constraints.NotBlank

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

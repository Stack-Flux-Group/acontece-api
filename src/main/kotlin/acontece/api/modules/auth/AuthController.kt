package acontece.api.modules.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Autenticação", description = "Login e emissão do token JWT")
@RestController
@RequestMapping("/auth")
class AuthController(
    private val service: AuthService
) {

    // POST /auth/login -> devolve o token que o front manda em "Authorization: Bearer <token>"
    @Operation(
        summary = "Entrar",
        description = "Devolve o token JWT. Mande-o em `Authorization: Bearer <token>`. 401 se e-mail ou senha estiverem errados.",
    )
    @SecurityRequirements // rota pública: sem cadeado no Swagger
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse = service.login(request)
}

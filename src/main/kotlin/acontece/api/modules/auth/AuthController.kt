package acontece.api.modules.auth

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val service: AuthService
) {

    // POST /auth/login -> devolve o token que o front manda em "Authorization: Bearer <token>"
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse = service.login(request)
}

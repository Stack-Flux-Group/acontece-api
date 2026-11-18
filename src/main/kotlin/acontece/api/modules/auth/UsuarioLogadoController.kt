package acontece.api.modules.auth

import acontece.api.config.usuarioId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Autenticação", description = "Login e emissão do token JWT")
@RestController
class UsuarioLogadoController(
    private val service: AuthService
) {

    // GET /me -> quem é o dono do token. O front usa ao abrir o app, para saber quem está logado e
    // qual tela mostrar, sem depender do que guardou na hora do login.
    @Operation(
        summary = "Usuário logado",
        description =
            "Devolve id, nome, e-mail e papel de quem está logado, e quando o token expira. " +
                "401 sem token, com token vencido ou se o usuário não existe mais.",
    )
    @GetMapping("/me")
    fun usuarioLogado(@AuthenticationPrincipal jwt: Jwt): UsuarioLogadoResponse =
        service.usuarioLogado(jwt.usuarioId, jwt.expiresAt)
}

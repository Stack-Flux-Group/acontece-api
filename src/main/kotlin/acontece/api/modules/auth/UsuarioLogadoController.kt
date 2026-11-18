package acontece.api.modules.auth

import acontece.api.config.usuarioId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
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

    // PATCH /me/senha -> troca a senha de quem está logado (participante ou organizador).
    // 204 No Content: sucesso sem corpo. O token atual continua valendo até expirar.
    @Operation(
        summary = "Trocar a própria senha",
        description =
            "Participante ou organizador. Exige a senha atual; a nova deve ter de 8 a 72 caracteres e ser " +
                "diferente da atual (400). Erros da senha atual contam no limite do login: depois de 5, " +
                "responde 429 por 15 minutos.",
    )
    @PatchMapping("/me/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun trocarSenha(
        @Valid @RequestBody request: TrocarSenhaRequest,
        @AuthenticationPrincipal jwt: Jwt,
        http: HttpServletRequest,
    ) = service.trocarSenha(jwt.usuarioId, request, http.ipDoCliente())
}

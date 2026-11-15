package acontece.api.modules.auth

import acontece.api.modules.usuario.Usuario
import java.time.Clock
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service

@Service
class TokenService(
    private val encoder: JwtEncoder,
    private val clock: Clock,
    @Value("\${jwt.expiracao-minutos:120}") private val expiracaoMinutos: Long,
) {

    fun gerar(usuario: Usuario): TokenResponse {
        val id = requireNotNull(usuario.id) { "Usuário ainda não persistido" }
        val agora = clock.instant()
        val validade = Duration.ofMinutes(expiracaoMinutos)

        val claims =
            JwtClaimsSet.builder()
                .subject(id.toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(validade))
                .claim("email", usuario.email)
                .claim("papel", usuario.papel.name)
                .build()

        val token =
            encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .tokenValue

        return TokenResponse(
            token = token,
            expiraEmSegundos = validade.seconds,
            usuarioId = id,
            papel = usuario.papel,
        )
    }
}

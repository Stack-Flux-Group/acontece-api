package acontece.api.config

import com.nimbusds.jose.jwk.source.ImmutableSecret
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder

// Assina (encoder) e confere (decoder) os tokens com a mesma chave secreta, vinda de `jwt.secret`.
// Sem a variável JWT_SECRET a aplicação não sobe, para nunca rodar com uma chave conhecida.
@Configuration
class JwtConfig(
    @Value("\${jwt.secret:}") secret: String,
) {

    private val chave =
        secret.toByteArray().let { bytes ->
            require(bytes.size >= 32) {
                "jwt.secret (variável JWT_SECRET) precisa ter pelo menos 32 caracteres"
            }
            SecretKeySpec(bytes, "HmacSHA256")
        }

    @Bean fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(chave))

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build()
}

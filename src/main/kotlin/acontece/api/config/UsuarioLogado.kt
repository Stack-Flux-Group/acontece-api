package acontece.api.config

import acontece.api.enums.Papel
import org.springframework.security.oauth2.jwt.Jwt

// Atalhos para ler quem está logado a partir do token (subject = id, claim "papel" = papel).
val Jwt.usuarioId: Long
    get() = requireNotNull(subject) { "Token sem subject" }.toLong()

val Jwt.ehOrganizador: Boolean
    get() = getClaimAsString("papel") == Papel.ORGANIZADOR.name

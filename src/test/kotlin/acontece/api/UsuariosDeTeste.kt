package acontece.api

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor

// Nos testes de controller o filtro de JWT é pulado: este token já chega "autenticado", com o
// mesmo formato que o TokenService gera (subject = id, claim "papel") e a autoridade ROLE_<PAPEL>.
private fun comPapel(id: Long, papel: String): JwtRequestPostProcessor =
    jwt()
        .jwt { it.subject(id.toString()).claim("papel", papel) }
        .authorities(SimpleGrantedAuthority("ROLE_$papel"))

fun comoParticipante(id: Long = 1L) = comPapel(id, "PARTICIPANTE")

fun comoOrganizador(id: Long = 100L) = comPapel(id, "ORGANIZADOR")

package acontece.api.modules.auth

import kotlin.test.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Caminho real pelo endpoint: senhas erradas seguidas viram 429, e nem a senha certa passa
// enquanto o bloqueio durar. Banco próprio para não dividir contadores com outros testes.
@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:limitelogin;DB_CLOSE_DELAY=-1",
            "organizador.email=chefe@email.com",
            "organizador.senha=senha-do-chefe-1",
            "login.max-falhas=3",
        ]
)
@AutoConfigureMockMvc
class LimiteDeLoginTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun login(senha: String, ip: String) =
        mockMvc.perform(
            post("/auth/login")
                .with { it.apply { remoteAddr = ip } }
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"chefe@email.com","senha":"$senha"}""")
        )

    @Test
    fun `depois de errar o limite a senha certa tambem e recusada com 429`() {
        repeat(3) { login("errada-errada", "10.0.0.1").andExpect(status().isUnauthorized) }

        login("senha-do-chefe-1", "10.0.0.1")
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.status").value(429))

        // Outro IP não é afetado pelo bloqueio do primeiro.
        login("senha-do-chefe-1", "10.0.0.2").andExpect(status().isOk)
    }
}

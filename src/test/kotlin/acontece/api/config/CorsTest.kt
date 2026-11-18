package acontece.api.config

import kotlin.test.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Simula o "preflight" que o navegador faz antes de chamar a API a partir do front. Passa pela
// segurança de verdade, então prova também que o OPTIONS não é barrado por falta de token.
@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:cors;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
        ]
)
@AutoConfigureMockMvc
class CorsTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun preflight(origem: String) =
        mockMvc.perform(
            options("/eventos")
                .header(HttpHeaders.ORIGIN, origem)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
        )

    @Test
    fun `o front na Netlify e aceito`() {
        preflight("https://acontece.netlify.app")
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://acontece.netlify.app"))
    }

    @Test
    fun `o ambiente local continua aceito`() {
        preflight("http://localhost:5173").andExpect(status().isOk)
    }

    @Test
    fun `uma origem desconhecida e recusada`() {
        preflight("https://site-qualquer.com").andExpect(status().isForbidden)
    }
}

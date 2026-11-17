package acontece.api.modules.evento

import acontece.api.comoOrganizador
import acontece.api.config.SecurityConfig
import kotlin.test.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Só a camada web: o que o controller aceita, recusa e devolve. O service é um mock.
@WebMvcTest(EventoController::class)
@Import(SecurityConfig::class)
class EventoValidacaoTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var service: EventoService

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    private fun criar(corpo: String) =
        mockMvc.perform(
            post("/evento/criar")
                .with(comoOrganizador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
        )

    private fun corpo(descricao: String = "", fotoUrl: String = "https://cdn.exemplo.com/foto.png") =
        """
        {"titulo":"Semana de Tecnologia","descricao":"$descricao",
         "dataInicio":"2030-12-01T19:00:00","dataFim":"2030-12-01T22:00:00",
         "vagas":10,"cep":"01001-000","fotoUrl":"$fotoUrl"}
        """

    @Test
    fun `descricao acima de 2000 caracteres devolve 400 no campo`() {
        criar(corpo(descricao = "a".repeat(2001)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.fieldErrors[0].field").value("descricao"))
    }

    @Test
    fun `fotoUrl acima de 1000 caracteres devolve 400 no campo`() {
        criar(corpo(fotoUrl = "https://cdn.exemplo.com/" + "b".repeat(977)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.fieldErrors[0].field").value("fotoUrl"))
    }
}

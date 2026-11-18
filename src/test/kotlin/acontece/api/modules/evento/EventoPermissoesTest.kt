package acontece.api.modules.evento

import acontece.api.comoOrganizador
import acontece.api.comoParticipante
import acontece.api.config.SecurityConfig
import kotlin.test.Test
import org.mockito.BDDMockito.given
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Aqui só interessa QUEM pode chamar cada rota de evento; o service é um mock.
// Participante vê (GET) e nada mais; organizador administra.
@WebMvcTest(EventoController::class)
@Import(SecurityConfig::class)
class EventoPermissoesTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var service: EventoService

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `sem token nao ve os eventos`() {
        mockMvc.perform(get("/eventos")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `participante lista e busca eventos`() {
        // Sem parâmetros, o controller pede a primeira página com 20 itens.
        given(service.buscar(null, null, null, PageRequest.of(0, 20))).willReturn(Page.empty())

        mockMvc.perform(get("/eventos").with(comoParticipante())).andExpect(status().isOk)
        mockMvc.perform(get("/eventos/1").with(comoParticipante())).andExpect(status().isOk)
    }

    @Test
    fun `participante nao cria evento`() {
        mockMvc
            .perform(
                post("/eventos")
                    .with(comoParticipante())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `participante nao edita evento`() {
        mockMvc
            .perform(
                put("/eventos/1")
                    .with(comoParticipante())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `participante nao apaga evento`() {
        mockMvc
            .perform(delete("/eventos/1").with(comoParticipante()))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `organizador apaga evento`() {
        mockMvc
            .perform(delete("/eventos/1").with(comoOrganizador()))
            .andExpect(status().isNoContent)
    }
}

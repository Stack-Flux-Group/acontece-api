package acontece.api.modules.evento

import acontece.api.comoOrganizador
import acontece.api.comoParticipante
import acontece.api.config.SecurityConfig
import acontece.api.modules.usuario.UsuarioRepository
import java.time.LocalDateTime
import kotlin.test.Test
import org.mockito.BDDMockito.given
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// As rotas antigas (/evento/...) continuam vivas para o app atual não quebrar.
// Este teste garante que cada uma ainda responde e com as mesmas permissões de antes.
@WebMvcTest(EventoLegadoController::class)
@Import(SecurityConfig::class)
class EventoLegadoTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var service: EventoService

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    // A SecurityConfig confere o status da conta no banco; com o token simulado do teste ela nem é chamada.
    @MockitoBean lateinit var usuarios: UsuarioRepository

    private val evento =
        EventoResponseDTO(
            id = 1,
            titulo = "Semana de Tecnologia",
            descricao = "",
            dataInicio = LocalDateTime.of(2030, 12, 1, 19, 0),
            dataFim = LocalDateTime.of(2030, 12, 1, 22, 0),
            vagas = 10,
            vagasDisponiveis = 10,
            endereco = Endereco("01001-000", "Praça da Sé", "Sé", "São Paulo", "SP"),
            fotoUrl = null,
        )

    // O mesmo pedido que `corpo` descreve, já convertido em objeto.
    private val pedido =
        EventoRequestDTO(
            titulo = "Semana de Tecnologia",
            dataInicio = LocalDateTime.of(2030, 12, 1, 19, 0),
            dataFim = LocalDateTime.of(2030, 12, 1, 22, 0),
            vagas = 10,
            cep = "01001-000",
        )

    private val corpo =
        """
        {"titulo":"Semana de Tecnologia","dataInicio":"2030-12-01T19:00:00",
         "dataFim":"2030-12-01T22:00:00","vagas":10,"cep":"01001-000"}
        """

    @Test
    fun `rotas antigas de leitura continuam respondendo para o participante`() {
        given(service.listar()).willReturn(listOf(evento))
        given(service.buscarPorId(1)).willReturn(evento)
        given(service.buscarPorTitulo("semana")).willReturn(listOf(evento))
        given(service.buscarPorPeriodo(LocalDateTime.of(2030, 12, 1, 0, 0), LocalDateTime.of(2030, 12, 31, 23, 59, 59)))
            .willReturn(listOf(evento))

        mockMvc.perform(get("/evento/listar/").with(comoParticipante()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(1))
        mockMvc.perform(get("/evento/buscar/1").with(comoParticipante()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
        mockMvc.perform(get("/evento/buscar/titulo").param("titulo", "semana").with(comoParticipante()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(1))
        mockMvc
            .perform(
                get("/evento/buscar/periodo")
                    .param("inicio", "2030-12-01T00:00:00")
                    .param("fim", "2030-12-31T23:59:59")
                    .with(comoParticipante())
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(1))
    }

    @Test
    fun `rotas antigas de escrita continuam respondendo para o organizador`() {
        given(service.criar(pedido)).willReturn(evento)
        given(service.atualizar(1L, pedido)).willReturn(evento)

        mockMvc
            .perform(post("/evento/criar").with(comoOrganizador()).contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isCreated)
        mockMvc
            .perform(put("/evento/atualizar/1").with(comoOrganizador()).contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isOk)
        mockMvc.perform(delete("/evento/deletar/1").with(comoOrganizador())).andExpect(status().isNoContent)
    }

    @Test
    fun `rotas antigas mantem as permissoes`() {
        mockMvc.perform(get("/evento/listar/")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(post("/evento/criar").with(comoParticipante()).contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isForbidden)
        mockMvc.perform(delete("/evento/deletar/1").with(comoParticipante())).andExpect(status().isForbidden)
    }
}

package acontece.api.modules.usuario

import acontece.api.comoOrganizador
import acontece.api.comoParticipante
import acontece.api.config.SecurityConfig
import acontece.api.enums.StatusUsuario
import acontece.api.exception.ResourceNotFoundException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// @WebMvcTest sobe só a camada web (controller + GlobalExceptionHandler), sem banco de dados.
// O service é um mock: aqui testamos apenas as rotas e o formato das respostas.
@WebMvcTest(ParticipanteController::class)
@Import(SecurityConfig::class)
class ParticipanteControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var service: ParticipanteService

    // A SecurityConfig exige um decoder; aqui o token já vem autenticado, então ele nunca é usado.
    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    // A SecurityConfig confere o status da conta no banco; com o token simulado do teste ela nem é chamada.
    @MockitoBean lateinit var usuarios: UsuarioRepository

    private val participante =
        ParticipanteResponse(
            id = 1,
            nome = "Maria Silva",
            email = "maria@email.com",
            cpf = "52998224725",
            telefone = "11999998888",
            status = StatusUsuario.ATIVO,
        )

    @Test
    fun `PATCH status do participante pelo organizador devolve o participante`() {
        given(service.alterarStatus(1L, AlterarStatusParticipanteRequest(StatusUsuario.INATIVO)))
            .willReturn(participante.copy(status = StatusUsuario.INATIVO))

        mockMvc
            .perform(
                patch("/participantes/1/status")
                    .with(comoOrganizador())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status": "INATIVO"}""")
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$.status").value("INATIVO"))
    }

    @Test
    fun `PATCH status e so do organizador, nem o proprio participante pode`() {
        mockMvc
            .perform(
                patch("/participantes/1/status")
                    .with(comoParticipante(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status": "ATIVO"}""")
            )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `PATCH status sem status ou com valor desconhecido devolve 400`() {
        mockMvc
            .perform(
                patch("/participantes/1/status")
                    .with(comoOrganizador())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.fieldErrors[0].field").value("status"))

        mockMvc
            .perform(
                patch("/participantes/1/status")
                    .with(comoOrganizador())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status": "BLOQUEADO"}""")
            )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `DELETE do proprio participante devolve 204`() {
        mockMvc
            .perform(delete("/participantes/1").with(comoParticipante(1)))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `GET participantes por id devolve o participante`() {
        given(service.buscarPorId(1L)).willReturn(participante)

        mockMvc
            .perform(get("/participantes/1").with(comoParticipante(1)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$.id").value(1))
            .andExpect(jsonPath("\$.nome").value("Maria Silva"))
            .andExpect(jsonPath("\$.email").value("maria@email.com"))
    }

    @Test
    fun `GET participantes por id inexistente devolve 404`() {
        given(service.buscarPorId(99L))
            .willThrow(ResourceNotFoundException("Participante 99 não encontrado"))

        mockMvc
            .perform(get("/participantes/99").with(comoOrganizador()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("\$.message").value("Participante 99 não encontrado"))
    }

    @Test
    fun `PATCH participantes por id atualiza so os campos enviados`() {
        given(service.atualizar(1L, ParticipantePatchRequest(nome = "Maria Souza")))
            .willReturn(participante.copy(nome = "Maria Souza"))

        mockMvc
            .perform(
                patch("/participantes/1")
                    .with(comoParticipante(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nome": "Maria Souza"}""")
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$.nome").value("Maria Souza"))
    }

    @Test
    fun `PATCH participantes por id inexistente devolve 404`() {
        given(service.atualizar(99L, ParticipantePatchRequest(nome = "Alguém")))
            .willThrow(ResourceNotFoundException("Participante 99 não encontrado"))

        mockMvc
            .perform(
                patch("/participantes/99")
                    .with(comoOrganizador())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nome": "Alguém"}""")
            )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PATCH participantes com email invalido devolve 400`() {
        mockMvc
            .perform(
                patch("/participantes/1")
                    .with(comoParticipante(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email": "isso-nao-e-email"}""")
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.fieldErrors[0].field").value("email"))
    }

    @Test
    fun `POST participantes cadastra sem token e devolve 201`() {
        given(service.criar(requestValido)).willReturn(participante)

        mockMvc
            .perform(
                post("/participantes").contentType(MediaType.APPLICATION_JSON).content(corpoValido)
            )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("\$.senha").doesNotExist())
    }

    @Test
    fun `POST participantes sem senha devolve 400`() {
        mockMvc
            .perform(
                post("/participantes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nome":"Maria Silva","email":"maria@email.com","cpf":"52998224725"}""")
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.fieldErrors[0].field").value("senha"))
    }

    @Test
    fun `POST participantes com senha curta devolve 400`() {
        mockMvc
            .perform(
                post("/participantes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpoValido.replace("senha-forte-1", "curta"))
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.fieldErrors[0].field").value("senha"))
    }

    @Test
    fun `GET participante de outro participante devolve 403`() {
        mockMvc
            .perform(get("/participantes/1").with(comoParticipante(2)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET participante sem token devolve 401`() {
        mockMvc.perform(get("/participantes/1")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET lista de participantes e so do organizador`() {
        mockMvc
            .perform(get("/participantes").with(comoParticipante(1)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `DELETE participante de outro participante devolve 403`() {
        mockMvc
            .perform(delete("/participantes/1").with(comoParticipante(2)))
            .andExpect(status().isForbidden)
    }

    private val requestValido =
        ParticipanteRequest(
            nome = "Maria Silva",
            email = "maria@email.com",
            cpf = "52998224725",
            telefone = "11999998888",
            senha = "senha-forte-1",
        )

    private val corpoValido =
        """{"nome":"Maria Silva","email":"maria@email.com","cpf":"52998224725","telefone":"11999998888","senha":"senha-forte-1"}"""
}

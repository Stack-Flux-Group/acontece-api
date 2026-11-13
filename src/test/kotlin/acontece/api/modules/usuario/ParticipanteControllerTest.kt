package acontece.api.modules.usuario

import acontece.api.exception.ResourceNotFoundException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// @WebMvcTest sobe só a camada web (controller + GlobalExceptionHandler), sem banco de dados.
// O service é um mock: aqui testamos apenas as rotas e o formato das respostas.
@WebMvcTest(ParticipanteController::class)
class ParticipanteControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var service: ParticipanteService

    private val participante =
        ParticipanteResponse(
            id = 1,
            nome = "Maria Silva",
            email = "maria@email.com",
            cpf = "52998224725",
            telefone = "11999998888",
        )

    @Test
    fun `GET participantes por id devolve o participante`() {
        given(service.buscarPorId(1L)).willReturn(participante)

        mockMvc
            .perform(get("/participantes/1"))
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
            .perform(get("/participantes/99"))
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
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email": "isso-nao-e-email"}""")
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.fieldErrors[0].field").value("email"))
    }
}

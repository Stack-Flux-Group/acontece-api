package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao.CONFIRMADA
import acontece.api.enums.StatusInscricao.PENDENTE
import acontece.api.exception.ConflictException
import acontece.api.exception.ResourceNotFoundException
import java.time.LocalDateTime
import kotlin.test.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// @WebMvcTest sobe só a camada web (controller + GlobalExceptionHandler), sem banco de dados.
// O service é um mock: aqui testamos rotas, status HTTP e o formato das respostas.
@WebMvcTest(InscricaoController::class)
class InscricaoControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var service: InscricaoService

    private val inscricao =
        InscricaoResponse(
            id = 10,
            participanteId = 1,
            participanteNome = "Maria Silva",
            eventoId = 2,
            eventoTitulo = "Show de Jazz",
            status = PENDENTE,
            dataInscricao = LocalDateTime.of(2026, 11, 13, 10, 0),
        )

    @Test
    fun `POST cria e devolve 201 com Location`() {
        given(service.criar(InscricaoRequest(participanteId = 1, eventoId = 2))).willReturn(inscricao)

        mockMvc
            .perform(
                post("/inscricoes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"participanteId": 1, "eventoId": 2}""")
            )
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "http://localhost/inscricoes/10"))
            .andExpect(jsonPath("\$.id").value(10))
            .andExpect(jsonPath("\$.status").value("PENDENTE"))
    }

    @Test
    fun `POST sem participanteId devolve 400 com fieldErrors`() {
        mockMvc
            .perform(
                post("/inscricoes").contentType(MediaType.APPLICATION_JSON).content("""{"eventoId": 2}""")
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.fieldErrors[0].field").value("participanteId"))
    }

    @Test
    fun `POST com participante inexistente devolve 404`() {
        given(service.criar(InscricaoRequest(participanteId = 999, eventoId = 2)))
            .willThrow(ResourceNotFoundException("Participante 999 não encontrado"))

        mockMvc
            .perform(
                post("/inscricoes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"participanteId": 999, "eventoId": 2}""")
            )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("\$.message").value("Participante 999 não encontrado"))
    }

    @Test
    fun `POST sem vagas devolve 409`() {
        given(service.criar(InscricaoRequest(participanteId = 1, eventoId = 2)))
            .willThrow(ConflictException("Evento sem vagas disponíveis"))

        mockMvc
            .perform(
                post("/inscricoes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"participanteId": 1, "eventoId": 2}""")
            )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("\$.message").value("Evento sem vagas disponíveis"))
    }

    @Test
    fun `GET por id devolve 200`() {
        given(service.buscarPorId(10L)).willReturn(inscricao)

        mockMvc
            .perform(get("/inscricoes/10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$.participanteNome").value("Maria Silva"))
            .andExpect(jsonPath("\$.eventoTitulo").value("Show de Jazz"))
    }

    @Test
    fun `GET por id inexistente devolve 404`() {
        given(service.buscarPorId(99L))
            .willThrow(ResourceNotFoundException("Inscrição 99 não encontrada"))

        mockMvc
            .perform(get("/inscricoes/99"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("\$.message").value("Inscrição 99 não encontrada"))
    }

    @Test
    fun `GET lista repassa os filtros`() {
        val pageable = PageRequest.of(0, 20, Sort.by("id"))
        given(service.listar(2L, null, CONFIRMADA, pageable))
            .willReturn(PageImpl(listOf(inscricao.copy(status = CONFIRMADA)), pageable, 1))

        mockMvc
            .perform(get("/inscricoes").param("eventoId", "2").param("status", "CONFIRMADA"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$.content[0].id").value(10))
            .andExpect(jsonPath("\$.content[0].status").value("CONFIRMADA"))
    }

    @Test
    fun `GET lista com status invalido devolve 400`() {
        mockMvc.perform(get("/inscricoes").param("status", "XYZ")).andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH status devolve 200`() {
        given(service.atualizarStatus(10L, AtualizarStatusRequest(status = CONFIRMADA)))
            .willReturn(inscricao.copy(status = CONFIRMADA))

        mockMvc
            .perform(
                patch("/inscricoes/10/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status": "CONFIRMADA"}""")
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$.status").value("CONFIRMADA"))
    }

    @Test
    fun `PATCH status invalido devolve 400`() {
        mockMvc
            .perform(
                patch("/inscricoes/10/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status": "XYZ"}""")
            )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH sem status devolve 400 com fieldErrors`() {
        mockMvc
            .perform(
                patch("/inscricoes/10/status").contentType(MediaType.APPLICATION_JSON).content("{}")
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.fieldErrors[0].field").value("status"))
    }

    @Test
    fun `PATCH transicao invalida devolve 409`() {
        given(service.atualizarStatus(10L, AtualizarStatusRequest(status = PENDENTE)))
            .willThrow(ConflictException("Transição de status inválida: CONFIRMADA → PENDENTE"))

        mockMvc
            .perform(
                patch("/inscricoes/10/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status": "PENDENTE"}""")
            )
            .andExpect(status().isConflict)
    }
}

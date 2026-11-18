package acontece.api.modules.inscricao

import acontece.api.comoOrganizador
import acontece.api.comoParticipante
import acontece.api.config.SecurityConfig
import acontece.api.enums.StatusInscricao.CANCELADA
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
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.mockito.BDDMockito.never
import org.mockito.BDDMockito.then
import org.springframework.context.annotation.Import
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
@Import(SecurityConfig::class)
class InscricaoControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var service: InscricaoService

    // A SecurityConfig exige um decoder; aqui o token já vem autenticado, então ele nunca é usado.
    @MockitoBean lateinit var jwtDecoder: JwtDecoder

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
                    .with(comoParticipante(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"eventoId": 2}""")
            )
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "http://localhost/inscricoes/10"))
            .andExpect(jsonPath("\$.id").value(10))
            .andExpect(jsonPath("\$.status").value("PENDENTE"))
    }

    @Test
    fun `POST sem eventoId devolve 400 com fieldErrors`() {
        mockMvc
            .perform(
                post("/inscricoes")
                    .with(comoParticipante(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.fieldErrors[0].field").value("eventoId"))
    }

    @Test
    fun `POST ignora o participanteId do corpo e usa o do token`() {
        given(service.criar(InscricaoRequest(participanteId = 1, eventoId = 2))).willReturn(inscricao)

        mockMvc
            .perform(
                post("/inscricoes")
                    .with(comoParticipante(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"participanteId": 999, "eventoId": 2}""")
            )
            .andExpect(status().isCreated)

        then(service).should(never()).criar(InscricaoRequest(participanteId = 999, eventoId = 2))
    }

    @Test
    fun `POST sem token devolve 401`() {
        mockMvc
            .perform(
                post("/inscricoes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"eventoId": 2}""")
            )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST como organizador devolve 403`() {
        mockMvc
            .perform(
                post("/inscricoes")
                    .with(comoOrganizador())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"eventoId": 2}""")
            )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `POST com participante inexistente devolve 404`() {
        given(service.criar(InscricaoRequest(participanteId = 999, eventoId = 2)))
            .willThrow(ResourceNotFoundException("Participante 999 não encontrado"))

        mockMvc
            .perform(
                post("/inscricoes")
                    .with(comoParticipante(999))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"eventoId": 2}""")
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
                    .with(comoParticipante(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"eventoId": 2}""")
            )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("\$.message").value("Evento sem vagas disponíveis"))
    }

    @Test
    fun `GET por id devolve 200`() {
        given(service.buscarPorId(10L)).willReturn(inscricao)

        mockMvc
            .perform(get("/inscricoes/10").with(comoParticipante(1)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$.participanteNome").value("Maria Silva"))
            .andExpect(jsonPath("\$.eventoTitulo").value("Show de Jazz"))
    }

    @Test
    fun `GET por id de inscricao de outro participante devolve 403`() {
        given(service.buscarPorId(10L)).willReturn(inscricao)

        mockMvc
            .perform(get("/inscricoes/10").with(comoParticipante(2)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET por id como organizador devolve 200`() {
        given(service.buscarPorId(10L)).willReturn(inscricao)

        mockMvc
            .perform(get("/inscricoes/10").with(comoOrganizador()))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET sem token devolve 401`() {
        mockMvc.perform(get("/inscricoes/10")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET por id inexistente devolve 404`() {
        given(service.buscarPorId(99L))
            .willThrow(ResourceNotFoundException("Inscrição 99 não encontrada"))

        mockMvc
            .perform(get("/inscricoes/99").with(comoOrganizador()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("\$.message").value("Inscrição 99 não encontrada"))
    }

    @Test
    fun `GET lista repassa os filtros`() {
        val pageable = PageRequest.of(0, 20, Sort.by("id"))
        given(service.listar(2L, null, CONFIRMADA, pageable))
            .willReturn(PageImpl(listOf(inscricao.copy(status = CONFIRMADA)), pageable, 1))

        mockMvc
            .perform(
                get("/inscricoes")
                    .with(comoOrganizador())
                    .param("eventoId", "2")
                    .param("status", "CONFIRMADA")
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$.content[0].id").value(10))
            .andExpect(jsonPath("\$.content[0].status").value("CONFIRMADA"))
    }

    @Test
    fun `GET lista como participante filtra pelo proprio id mesmo se pedir outro`() {
        val pageable = PageRequest.of(0, 20, Sort.by("id"))
        given(service.listar(null, 1L, null, pageable))
            .willReturn(PageImpl(listOf(inscricao), pageable, 1))

        mockMvc
            .perform(get("/inscricoes").with(comoParticipante(1)).param("participanteId", "2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$.content[0].participanteId").value(1))
    }

    @Test
    fun `GET lista com status invalido devolve 400`() {
        mockMvc
            .perform(get("/inscricoes").with(comoOrganizador()).param("status", "XYZ"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH status devolve 200`() {
        given(service.atualizarStatus(10L, AtualizarStatusRequest(status = CONFIRMADA)))
            .willReturn(inscricao.copy(status = CONFIRMADA))

        mockMvc
            .perform(
                patch("/inscricoes/10/status")
                    .with(comoOrganizador())
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
                    .with(comoOrganizador())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status": "XYZ"}""")
            )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH sem status devolve 400 com fieldErrors`() {
        mockMvc
            .perform(
                patch("/inscricoes/10/status")
                    .with(comoOrganizador())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
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
                    .with(comoOrganizador())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status": "PENDENTE"}""")
            )
            .andExpect(status().isConflict)
    }

    @Test
    fun `PATCH cancelar usa o participante do token e devolve 200`() {
        given(service.cancelarPeloParticipante(10L, 1L)).willReturn(inscricao.copy(status = CANCELADA))

        mockMvc
            .perform(patch("/inscricoes/10/cancelar").with(comoParticipante(1)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$.status").value("CANCELADA"))
    }

    @Test
    fun `PATCH cancelar inscricao de outro participante devolve 403`() {
        given(service.cancelarPeloParticipante(10L, 2L))
            .willThrow(AccessDeniedException("Acesso negado a esta inscrição"))

        mockMvc
            .perform(patch("/inscricoes/10/cancelar").with(comoParticipante(2)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `PATCH cancelar ja cancelada devolve 409`() {
        given(service.cancelarPeloParticipante(10L, 1L))
            .willThrow(ConflictException("A inscrição já está cancelada"))

        mockMvc
            .perform(patch("/inscricoes/10/cancelar").with(comoParticipante(1)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("\$.message").value("A inscrição já está cancelada"))
    }

    @Test
    fun `PATCH cancelar como organizador devolve 403 e nem chega ao service`() {
        mockMvc
            .perform(patch("/inscricoes/10/cancelar").with(comoOrganizador()))
            .andExpect(status().isForbidden)

        then(service).should(never()).cancelarPeloParticipante(10L, 100L)
    }

    @Test
    fun `PATCH cancelar sem token devolve 401`() {
        mockMvc.perform(patch("/inscricoes/10/cancelar")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `PATCH status como participante devolve 403`() {
        mockMvc
            .perform(
                patch("/inscricoes/10/status")
                    .with(comoParticipante(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status": "CANCELADA"}""")
            )
            .andExpect(status().isForbidden)
    }
}

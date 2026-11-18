package acontece.api.modules.evento

import acontece.api.comoOrganizador
import acontece.api.config.SecurityConfig
import acontece.api.exception.ServicoExternoException
import java.time.LocalDateTime
import kotlin.test.Test
import org.hamcrest.Matchers.containsInAnyOrder
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
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
            post("/eventos")
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

    // O mesmo pedido que corpo() descreve, já convertido em objeto.
    private val pedidoDoCorpo =
        EventoRequestDTO(
            titulo = "Semana de Tecnologia",
            descricao = "",
            dataInicio = LocalDateTime.of(2030, 12, 1, 19, 0),
            dataFim = LocalDateTime.of(2030, 12, 1, 22, 0),
            vagas = 10,
            cep = "01001-000",
            fotoUrl = "https://cdn.exemplo.com/foto.png",
        )

    private val criado =
        EventoResponseDTO(
            id = 1,
            titulo = "Semana de Tecnologia",
            descricao = "",
            dataInicio = LocalDateTime.of(2030, 12, 1, 19, 0),
            dataFim = LocalDateTime.of(2030, 12, 1, 22, 0),
            vagas = 10,
            endereco = Endereco("01001-000", "Praça da Sé", "Sé", "São Paulo", "SP"),
            fotoUrl = "https://cdn.exemplo.com/foto.png",
        )

    @Test
    fun `descricao acima de 2000 caracteres devolve 400 no campo`() {
        criar(corpo(descricao = "a".repeat(2001)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.fieldErrors[0].field").value("descricao"))
    }

    @Test
    fun `criar evento devolve 201`() {
        given(service.criar(pedidoDoCorpo)).willReturn(criado)

        criar(corpo())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("\$.id").value(1))
            .andExpect(header().string("Location", "http://localhost/eventos/1"))
    }

    @Test
    fun `campos obrigatorios ausentes devolvem 400 com cada campo`() {
        criar("{}")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.message").value("Dados inválidos na requisição"))
            .andExpect(
                jsonPath("\$.fieldErrors[*].field")
                    .value(containsInAnyOrder("titulo", "dataInicio", "dataFim", "vagas", "cep"))
            )
    }

    @Test
    fun `ViaCEP fora do ar devolve 502`() {
        given(service.criar(pedidoDoCorpo)).willThrow(ServicoExternoException("Não foi possível consultar o CEP agora. Tente novamente."))

        criar(corpo())
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("\$.message").value("Não foi possível consultar o CEP agora. Tente novamente."))
    }

    @Test
    fun `listagem vem paginada e limita o tamanho da pagina a 100`() {
        // Pediu 500 por página: o Spring reduz para o máximo configurado antes de chegar ao service.
        val pedido = PageRequest.of(0, 100, Sort.by("titulo"))
        given(service.buscar(null, null, null, pedido)).willReturn(PageImpl(listOf(criado), pedido, 1))

        mockMvc
            .perform(get("/eventos").param("size", "500").param("sort", "titulo").with(comoOrganizador()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$.content[0].id").value(1))
            .andExpect(jsonPath("\$.page.size").value(100))
            .andExpect(jsonPath("\$.page.totalElements").value(1))
    }

    @Test
    fun `fotoUrl acima de 1000 caracteres devolve 400 no campo`() {
        criar(corpo(fotoUrl = "https://cdn.exemplo.com/" + "b".repeat(977)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("\$.fieldErrors[0].field").value("fotoUrl"))
    }
}

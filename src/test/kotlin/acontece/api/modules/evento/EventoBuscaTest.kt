package acontece.api.modules.evento

import java.time.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

// Os filtros e a paginação de GET /eventos rodando contra um banco de verdade (H2), sem mock.
@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:eventosbusca;DB_CLOSE_DELAY=-1"])
class EventoBuscaTest {

    @Autowired lateinit var service: EventoService
    @Autowired lateinit var eventos: EventoRepository

    private fun salvar(titulo: String, inicio: LocalDateTime, vagas: Int = 10) =
        eventos.save(
            Evento(
                titulo = titulo,
                descricao = "",
                dataInicio = inicio,
                dataFim = inicio.plusHours(2),
                vagas = vagas,
                endereco = Endereco("01001000", "Praça da Sé", "Sé", "São Paulo", "SP"),
            )
        )

    private fun titulos(
        titulo: String? = null,
        inicio: LocalDateTime? = null,
        fim: LocalDateTime? = null,
        pageable: Pageable = PageRequest.of(0, 20),
    ) = service.buscar(titulo, inicio, fim, pageable).content.map { it.titulo }

    @BeforeTest
    fun preparar() {
        eventos.deleteAll()
        // Salvos fora de ordem de data, para provar que a resposta vem ordenada.
        salvar("Festival de Música", LocalDateTime.of(2030, 3, 10, 18, 0), vagas = 50)
        salvar("Semana de Tecnologia", LocalDateTime.of(2030, 1, 5, 9, 0), vagas = 30)
        salvar("Oficina 100% prática", LocalDateTime.of(2030, 2, 20, 14, 0), vagas = 5)
    }

    @Test
    fun `sem filtros devolve todos em ordem de data de inicio`() {
        assertEquals(
            listOf("Semana de Tecnologia", "Oficina 100% prática", "Festival de Música"),
            titulos(),
        )
    }

    @Test
    fun `titulo busca por parte do texto sem diferenciar maiusculas`() {
        assertEquals(listOf("Semana de Tecnologia"), titulos(titulo = "TECNO"))
    }

    @Test
    fun `porcentagem no titulo vale como texto e nao como curinga`() {
        assertEquals(listOf("Oficina 100% prática"), titulos(titulo = "100%"))
        // Sem o escape, "%" casaria com todos os títulos; com ele, só com quem tem "%" no texto.
        assertEquals(listOf("Oficina 100% prática"), titulos(titulo = "%"))
    }

    @Test
    fun `periodo filtra pela data de inicio incluindo as pontas`() {
        assertEquals(
            listOf("Semana de Tecnologia", "Oficina 100% prática"),
            titulos(inicio = LocalDateTime.of(2030, 1, 5, 9, 0), fim = LocalDateTime.of(2030, 2, 20, 14, 0)),
        )
    }

    @Test
    fun `filtros se somam`() {
        assertEquals(
            listOf("Festival de Música"),
            titulos(titulo = "a", inicio = LocalDateTime.of(2030, 3, 1, 0, 0)),
        )
    }

    @Test
    fun `fim antes do inicio devolve erro de requisicao`() {
        assertFailsWith<IllegalArgumentException> {
            titulos(inicio = LocalDateTime.of(2030, 2, 1, 0, 0), fim = LocalDateTime.of(2030, 1, 1, 0, 0))
        }
    }

    @Test
    fun `pagina corta o resultado e informa o total`() {
        val segunda = service.buscar(null, null, null, PageRequest.of(1, 2))

        assertEquals(listOf("Festival de Música"), segunda.content.map { it.titulo })
        assertEquals(3, segunda.totalElements)
        assertEquals(2, segunda.totalPages)
    }

    @Test
    fun `aceita ordenar por um campo conhecido`() {
        assertEquals(
            listOf("Festival de Música", "Semana de Tecnologia", "Oficina 100% prática"),
            titulos(pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "vagas"))),
        )
    }

    @Test
    fun `ordenar por campo desconhecido devolve erro de requisicao`() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                titulos(pageable = PageRequest.of(0, 20, Sort.by("senha")))
            }
        assertEquals(
            "Não é possível ordenar por senha. Use: dataInicio, dataFim, titulo, vagas, id",
            ex.message,
        )
    }
}

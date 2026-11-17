package acontece.api.modules.evento

import acontece.api.client.ViaCepClient
import acontece.api.client.ViaCepResponseDTO
import acontece.api.exception.ResourceNotFoundException
import acontece.api.exception.ServicoExternoException
import feign.FeignException
import java.time.LocalDateTime
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionSynchronizationManager

// Sobe a aplicação inteira com um H2 em memória e o ViaCEP trocado por um mock (nada de internet).
// Não é @Transactional de propósito: cada chamada ao service abre e fecha a própria transação,
// como em produção. SEM as propriedades abaixo o teste conectaria no Supabase real.
@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:eventos;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
        ]
)
class EventoServiceTest {

    @Autowired lateinit var service: EventoService
    @Autowired lateinit var eventos: EventoRepository
    @Autowired lateinit var dataSource: DataSource

    @MockitoBean lateinit var viaCep: ViaCepClient

    @BeforeTest
    fun preparar() {
        garantirQueEstaNoH2()
        given(viaCep.buscarCep(anyString())).willReturn(SE)
    }

    // O JUnit roda o @AfterTest mesmo quando o @BeforeTest falha, então a guarda se repete aqui.
    @AfterTest
    fun limpar() {
        garantirQueEstaNoH2()
        eventos.deleteAll()
    }

    private fun garantirQueEstaNoH2() {
        val url = dataSource.connection.use { it.metaData.url }
        check(url.startsWith("jdbc:h2:mem")) { "Teste abortado: não está no H2 em memória ($url)" }
    }

    private fun pedido(descricao: String = "", fotoUrl: String? = null) =
        EventoRequestDTO(
            titulo = "Semana de Tecnologia",
            descricao = descricao,
            dataInicio = LocalDateTime.now().plusDays(1),
            dataFim = LocalDateTime.now().plusDays(2),
            vagas = 10,
            cep = "01001-000",
            fotoUrl = fotoUrl,
        )

    @Test
    fun `aceita descricao e foto maiores que 255 caracteres`() {
        val descricao = "a".repeat(2000)
        val fotoUrl = "https://cdn.exemplo.com/" + "b".repeat(976)

        val criado = service.criar(pedido(descricao = descricao, fotoUrl = fotoUrl))

        val salvo = eventos.findById(criado.id).orElseThrow()
        assertEquals(descricao, salvo.descricao)
        assertEquals(fotoUrl, salvo.fotoUrl)
    }

    @Test
    fun `consulta o ViaCEP fora de transacao ao criar e ao atualizar`() {
        val emTransacao = mutableListOf<Boolean>()
        given(viaCep.buscarCep(anyString())).willAnswer {
            emTransacao += TransactionSynchronizationManager.isActualTransactionActive()
            SE
        }

        val criado = service.criar(pedido())
        service.atualizar(criado.id, pedido(descricao = "nova"))

        assertEquals(listOf(false, false), emTransacao)
        assertEquals("nova", eventos.findById(criado.id).orElseThrow().descricao)
    }

    @Test
    fun `ViaCEP fora do ar vira ServicoExternoException e nada e gravado`() {
        given(viaCep.buscarCep(anyString())).willThrow(FeignException::class.java)

        assertFailsWith<ServicoExternoException> { service.criar(pedido()) }
        assertEquals(0, eventos.count())
    }

    @Test
    fun `atualizar evento inexistente devolve 404`() {
        assertFailsWith<ResourceNotFoundException> { service.atualizar(999999L, pedido()) }
    }

    private companion object {
        val SE =
            ViaCepResponseDTO(
                cep = "01001-000",
                logradouro = "Praça da Sé",
                bairro = "Sé",
                localidade = "São Paulo",
                uf = "SP",
            )
    }
}

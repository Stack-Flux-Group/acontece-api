package acontece.api.modules.evento

import acontece.api.client.ViaCepClient
import acontece.api.client.ViaCepResponseDTO
import acontece.api.enums.StatusInscricao
import acontece.api.enums.StatusInscricao.CANCELADA
import acontece.api.enums.StatusInscricao.CONFIRMADA
import acontece.api.enums.StatusInscricao.PENDENTE
import acontece.api.exception.ConflictException
import acontece.api.exception.ResourceNotFoundException
import acontece.api.modules.inscricao.Inscricao
import acontece.api.modules.inscricao.InscricaoRepository
import acontece.api.modules.usuario.Participante
import acontece.api.modules.usuario.ParticipanteRepository
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
import jakarta.persistence.EntityManagerFactory
import kotlin.test.assertTrue
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
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
            // Permite contar quantas consultas SQL o Hibernate executou.
            "spring.jpa.properties.hibernate.generate_statistics=true",
        ]
)
class EventoServiceTest {

    @Autowired lateinit var entityManagerFactory: EntityManagerFactory
    @Autowired lateinit var service: EventoService
    @Autowired lateinit var eventos: EventoRepository
    @Autowired lateinit var inscricoes: InscricaoRepository
    @Autowired lateinit var participantes: ParticipanteRepository
    @Autowired lateinit var dataSource: DataSource

    private var contador = 0

    @MockitoBean lateinit var viaCep: ViaCepClient

    @BeforeTest
    fun preparar() {
        garantirQueEstaNoH2()
        given(viaCep.buscarCep(anyString())).willReturn(SE)
    }

    // O JUnit roda o @AfterTest mesmo quando o @BeforeTest falha, então a guarda se repete aqui.
    // A ordem importa: inscrições referenciam participantes e eventos.
    @AfterTest
    fun limpar() {
        garantirQueEstaNoH2()
        inscricoes.deleteAll()
        participantes.deleteAll()
        eventos.deleteAll()
    }

    private fun inscrever(eventoId: Long, status: StatusInscricao) {
        val participante =
            participantes.save(
                Participante(
                    nome = "Participante ${++contador}",
                    email = "participante$contador@email.com",
                    cpf = contador.toString().padStart(11, '0'),
                )
            )
        val evento = eventos.findById(eventoId).orElseThrow()
        inscricoes.save(Inscricao(participante = participante, evento = evento, status = status))
    }

    private fun garantirQueEstaNoH2() {
        val url = dataSource.connection.use { it.metaData.url }
        check(url.startsWith("jdbc:h2:mem")) { "Teste abortado: não está no H2 em memória ($url)" }
    }

    private fun pedido(descricao: String = "", fotoUrl: String? = null, vagas: Int = 10) =
        EventoRequestDTO(
            titulo = "Semana de Tecnologia",
            descricao = descricao,
            dataInicio = LocalDateTime.now().plusDays(1),
            dataFim = LocalDateTime.now().plusDays(2),
            vagas = vagas,
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
    fun `nao reduz as vagas abaixo das inscricoes ativas`() {
        val criado = service.criar(pedido(vagas = 5))
        inscrever(criado.id, PENDENTE)
        inscrever(criado.id, CONFIRMADA)
        inscrever(criado.id, CANCELADA) // cancelada não ocupa vaga

        val erro = assertFailsWith<ConflictException> { service.atualizar(criado.id, pedido(vagas = 1)) }
        assertEquals(
            "O evento já tem 2 inscrições ativas; as vagas não podem ficar abaixo disso",
            erro.message,
        )
        assertEquals(5, eventos.findById(criado.id).orElseThrow().vagas)

        // Igual ao número de ativas pode: o evento só fica lotado.
        assertEquals(2, service.atualizar(criado.id, pedido(vagas = 2)).vagas)
    }

    @Test
    fun `atualizar evento inexistente devolve 404`() {
        assertFailsWith<ResourceNotFoundException> { service.atualizar(999999L, pedido()) }
    }

    @Test
    fun `evento recem criado tem todas as vagas disponiveis`() {
        assertEquals(5, service.criar(pedido(vagas = 5)).vagasDisponiveis)
    }

    @Test
    fun `vagas disponiveis descontam so as inscricoes ativas`() {
        val criado = service.criar(pedido(vagas = 5))
        inscrever(criado.id, PENDENTE)
        inscrever(criado.id, CONFIRMADA)
        inscrever(criado.id, CANCELADA) // cancelada libera a vaga

        assertEquals(3, service.buscarPorId(criado.id).vagasDisponiveis)
        // A edição também devolve o número já descontado.
        assertEquals(8, service.atualizar(criado.id, pedido(vagas = 10)).vagasDisponiveis)
    }

    @Test
    fun `evento lotado mostra zero vagas disponiveis`() {
        val criado = service.criar(pedido(vagas = 2))
        inscrever(criado.id, PENDENTE)
        inscrever(criado.id, CONFIRMADA)

        assertEquals(0, service.buscarPorId(criado.id).vagasDisponiveis)
    }

    @Test
    fun `listagens calculam as vagas de cada evento`() {
        val lotado = service.criar(pedido(vagas = 1))
        val comVaga = service.criar(pedido(vagas = 3))
        val vazio = service.criar(pedido(vagas = 4))
        inscrever(lotado.id, PENDENTE)
        inscrever(comVaga.id, CONFIRMADA)

        val esperado = mapOf(lotado.id to 0, comVaga.id to 2, vazio.id to 4)
        assertEquals(esperado, service.listar().associate { it.id to it.vagasDisponiveis })
        assertEquals(
            esperado,
            service.buscar(null, null, null, PageRequest.of(0, 20)).content.associate { it.id to it.vagasDisponiveis },
        )
    }

    @Test
    fun `listagem nao faz uma contagem por evento`() {
        repeat(5) {
            val evento = service.criar(pedido())
            inscrever(evento.id, PENDENTE)
        }
        val estatisticas = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        estatisticas.clear()

        val pagina = service.buscar(null, null, null, PageRequest.of(0, 20))

        assertEquals(5, pagina.content.size)
        // Uma consulta traz os eventos e outra conta as inscrições de todos de uma vez (GROUP BY).
        // Sem isso seriam 1 + 5 contagens, uma por evento.
        assertTrue(
            estatisticas.prepareStatementCount <= 2,
            "consultas executadas: ${estatisticas.prepareStatementCount}",
        )
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

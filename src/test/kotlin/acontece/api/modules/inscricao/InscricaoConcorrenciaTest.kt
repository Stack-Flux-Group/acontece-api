package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao.CONFIRMADA
import acontece.api.enums.StatusInscricao.PENDENTE
import acontece.api.exception.ConflictException
import acontece.api.modules.evento.Endereco
import acontece.api.modules.evento.Evento
import acontece.api.modules.evento.EventoRepository
import acontece.api.modules.usuario.Participante
import acontece.api.modules.usuario.ParticipanteRepository
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest

// Sobe a aplicação inteira, mas com o banco trocado por um H2 em memória. SEM essas propriedades o
// teste conectaria no Supabase real: não remova nem mude a URL abaixo.
// Esta classe não é @Transactional de propósito: cada chamada ao service abre e fecha a sua própria
// transação, como acontece em produção. É isso que permite testar pedidos simultâneos de verdade e
// provar que a resposta é montada dentro da transação (open-in-view=false).
@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:concorrencia;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
        ]
)
class InscricaoConcorrenciaTest {

    @Autowired lateinit var service: InscricaoService
    @Autowired lateinit var repository: InscricaoRepository
    @Autowired lateinit var eventos: EventoRepository
    @Autowired lateinit var participantes: ParticipanteRepository
    @Autowired lateinit var dataSource: DataSource

    private var contador = 0

    @BeforeTest
    fun garantirQueEstaNoH2() {
        val url = dataSource.connection.use { it.metaData.url }
        check(url.startsWith("jdbc:h2:mem")) { "Teste abortado: não está no H2 em memória ($url)" }
    }

    // A ordem importa: inscrições referenciam participantes e eventos.
    // O JUnit roda o @AfterTest mesmo quando o @BeforeTest falha, então a guarda se repete aqui:
    // sem ela, uma configuração errada apagaria os dados do banco que estivesse configurado.
    @AfterTest
    fun limpar() {
        garantirQueEstaNoH2()
        repository.deleteAll()
        participantes.deleteAll()
        eventos.deleteAll()
    }

    private fun novoEvento(vagas: Int) =
        eventos.save(
            Evento(
                titulo = "Evento ${++contador}",
                descricao = "",
                dataInicio = LocalDateTime.now().plusDays(1),
                dataFim = LocalDateTime.now().plusDays(2),
                vagas = vagas,
                endereco =
                    Endereco(
                        cep = "01001000",
                        logradouro = "Praça da Sé",
                        bairro = "Sé",
                        cidade = "São Paulo",
                        uf = "SP",
                    ),
            )
        )

    private fun novoParticipante() =
        participantes.save(
            Participante(
                nome = "Participante ${++contador}",
                email = "participante$contador@email.com",
                cpf = contador.toString().padStart(11, '0'),
            )
        )

    @Test
    fun `so uma de varias inscricoes simultaneas fica com a ultima vaga`() {
        val evento = novoEvento(vagas = 1)
        val concorrentes = (1..5).map { novoParticipante() }
        val pool = Executors.newFixedThreadPool(concorrentes.size)
        val largada = CountDownLatch(1)

        // Todas as threads esperam o sinal e disparam juntas. Cada uma devolve o erro que tomou
        // (ou null se conseguiu se inscrever).
        val tentativas =
            concorrentes.map { participante ->
                pool.submit(
                    Callable<Exception?> {
                        largada.await()
                        try {
                            service.criar(
                                InscricaoRequest(participanteId = participante.id, eventoId = evento.id)
                            )
                            null
                        } catch (e: Exception) {
                            e
                        }
                    }
                )
            }
        largada.countDown()
        val resultados = tentativas.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, resultados.count { it == null }, "inscrições aceitas")
        assertEquals(
            4,
            resultados.count { it is ConflictException },
            "rejeitadas com 409; erros recebidos: $resultados",
        )
        assertEquals(
            1L,
            repository.countByEventoIdAndStatusIn(evento.id!!, listOf(PENDENTE, CONFIRMADA)),
        )
    }

    @Test
    fun `varias confirmacoes simultaneas da mesma inscricao so uma vence`() {
        val evento = novoEvento(vagas = 10)
        val inscricao = repository.save(Inscricao(participante = novoParticipante(), evento = evento))
        val pool = Executors.newFixedThreadPool(5)
        val largada = CountDownLatch(1)

        val tentativas =
            (1..5).map {
                pool.submit(
                    Callable<Exception?> {
                        largada.await()
                        try {
                            service.atualizarStatus(
                                inscricao.id!!,
                                AtualizarStatusRequest(status = CONFIRMADA),
                            )
                            null
                        } catch (e: Exception) {
                            e
                        }
                    }
                )
            }
        largada.countDown()
        val resultados = tentativas.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        // PENDENTE→CONFIRMADA só pode acontecer uma vez; as outras já encontram CONFIRMADA (409).
        assertEquals(1, resultados.count { it == null }, "confirmações aceitas")
        assertEquals(
            4,
            resultados.count { it is ConflictException },
            "rejeitadas com 409; erros recebidos: $resultados",
        )
    }

    @Test
    fun `consultas fora de transacao devolvem a resposta completa`() {
        val evento = novoEvento(vagas = 10)
        val participante = novoParticipante()
        val criada =
            service.criar(InscricaoRequest(participanteId = participante.id, eventoId = evento.id))

        val buscada = service.buscarPorId(criada.id)
        val pagina = service.listar(null, null, null, PageRequest.of(0, 10))

        assertEquals(participante.nome, buscada.participanteNome)
        assertEquals(evento.titulo, buscada.eventoTitulo)
        assertEquals(1, pagina.content.size)
        assertEquals(participante.nome, pagina.content.single().participanteNome)
        assertEquals(evento.titulo, pagina.content.single().eventoTitulo)
        assertTrue(criada.participanteNome.isNotBlank() && criada.eventoTitulo.isNotBlank())
    }
}

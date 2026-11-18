package acontece.api

import acontece.api.enums.StatusInscricao.CANCELADA
import acontece.api.enums.StatusInscricao.CONFIRMADA
import acontece.api.enums.StatusInscricao.PENDENTE
import acontece.api.exception.ConflictException
import acontece.api.modules.evento.Endereco
import acontece.api.modules.evento.Evento
import acontece.api.modules.evento.EventoRepository
import acontece.api.modules.inscricao.Inscricao
import acontece.api.modules.inscricao.InscricaoRepository
import acontece.api.modules.inscricao.InscricaoRequest
import acontece.api.modules.inscricao.InscricaoService
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

// Os outros testes usam H2, que é rápido mas não é o banco de produção. Este roda as partes que
// mais dependem do banco contra um PostgreSQL de verdade, numa imagem Docker descartável:
// - consultas com filtros opcionais (":x IS NULL OR ..."), que o Postgres trata diferente do H2;
// - a trava de linha (SELECT ... FOR UPDATE) que segura as vagas sob concorrência.
//
// Sem Docker (por exemplo, na máquina de quem não instalou), a classe inteira é PULADA, não falha.
// No CI do GitHub sempre há Docker, então lá ela sempre roda.
//
// @ServiceConnection troca o datasource da aplicação pelo do container: nunca toca no Supabase.
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PostgresIntegracaoTest {

    companion object {
        // Mesma versão principal do Postgres usado em produção (Supabase).
        @Container @ServiceConnection @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @Autowired lateinit var inscricaoService: InscricaoService
    @Autowired lateinit var inscricoes: InscricaoRepository
    @Autowired lateinit var eventos: EventoRepository
    @Autowired lateinit var participantes: ParticipanteRepository
    @Autowired lateinit var dataSource: DataSource

    private var contador = 0

    // Trava de segurança: só mexe no banco se ele for o do container.
    @BeforeTest
    fun garantirQueEstaNoContainer() {
        val url = dataSource.connection.use { it.metaData.url }
        check(url == postgres.jdbcUrl) { "Teste abortado: não está no Postgres do container ($url)" }
    }

    @AfterTest
    fun limpar() {
        garantirQueEstaNoContainer()
        inscricoes.deleteAll()
        participantes.deleteAll()
        eventos.deleteAll()
    }

    private fun novoEvento(titulo: String = "Evento ${++contador}", vagas: Int = 10) =
        eventos.save(
            Evento(
                titulo = titulo,
                descricao = "",
                dataInicio = LocalDateTime.now().plusDays(1),
                dataFim = LocalDateTime.now().plusDays(2),
                vagas = vagas,
                endereco = Endereco("01001000", "Praça da Sé", "Sé", "São Paulo", "SP"),
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
    fun `listagem de inscricoes funciona com filtros nulos e preenchidos`() {
        val evento = novoEvento()
        val outroEvento = novoEvento()
        val ana = novoParticipante()
        val bia = novoParticipante()
        inscricoes.save(Inscricao(participante = ana, evento = evento, status = PENDENTE))
        inscricoes.save(Inscricao(participante = bia, evento = evento, status = CONFIRMADA))
        inscricoes.save(Inscricao(participante = ana, evento = outroEvento, status = CANCELADA))
        val pagina = PageRequest.of(0, 20)

        // Todos os filtros nulos: é aqui que o Postgres reclamaria do tipo do parâmetro.
        assertEquals(3, inscricaoService.listar(null, null, null, pagina).totalElements)
        assertEquals(2, inscricaoService.listar(evento.id, null, null, pagina).totalElements)
        assertEquals(2, inscricaoService.listar(null, ana.id, null, pagina).totalElements)
        assertEquals(1, inscricaoService.listar(evento.id, null, CONFIRMADA, pagina).totalElements)
    }

    @Test
    fun `contagem de inscricoes ativas agrupada por evento`() {
        val evento = novoEvento()
        val outroEvento = novoEvento()
        val semInscricao = novoEvento()
        inscricoes.save(Inscricao(participante = novoParticipante(), evento = evento, status = PENDENTE))
        inscricoes.save(Inscricao(participante = novoParticipante(), evento = evento, status = CONFIRMADA))
        inscricoes.save(Inscricao(participante = novoParticipante(), evento = outroEvento, status = CANCELADA))

        val ocupadas =
            inscricoes
                .contarPorEvento(listOf(evento.id!!, outroEvento.id!!, semInscricao.id!!), listOf(PENDENTE, CONFIRMADA))
                .associate { it.eventoId to it.ocupadas }

        // Só aparece quem tem inscrição ativa; cancelada não conta.
        assertEquals(mapOf(evento.id!! to 2L), ocupadas)
    }

    @Test
    fun `cancelamento em lote das inscricoes do participante`() {
        val ana = novoParticipante()
        val emAberto = inscricoes.save(Inscricao(participante = ana, evento = novoEvento(), status = CONFIRMADA))
        val deOutro = inscricoes.save(Inscricao(participante = novoParticipante(), evento = novoEvento(), status = PENDENTE))

        // UPDATE com subconsulta no evento: é o tipo de SQL que pode mudar entre H2 e Postgres.
        assertEquals(1, inscricaoService.cancelarEmAbertoDoParticipante(ana.id!!))

        assertEquals(CANCELADA, inscricoes.findById(emAberto.id!!).get().status)
        assertEquals(PENDENTE, inscricoes.findById(deOutro.id!!).get().status)
    }

    @Test
    fun `busca de evento por titulo ignora maiusculas`() {
        novoEvento(titulo = "Semana de Tecnologia")
        novoEvento(titulo = "Festival de Música")

        assertEquals(
            listOf("Semana de Tecnologia"),
            eventos.findByTituloContainingIgnoreCase("TECNO").map { it.titulo },
        )
    }

    @Test
    fun `so uma de varias inscricoes simultaneas fica com a ultima vaga`() {
        val evento = novoEvento(vagas = 1)
        val concorrentes = (1..5).map { novoParticipante() }
        val pool = Executors.newFixedThreadPool(concorrentes.size)
        val largada = CountDownLatch(1)

        val tentativas =
            concorrentes.map { participante ->
                pool.submit(
                    Callable<Exception?> {
                        largada.await()
                        try {
                            inscricaoService.criar(
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
        assertEquals(1L, inscricoes.countByEventoIdAndStatusIn(evento.id!!, listOf(PENDENTE, CONFIRMADA)))
    }
}

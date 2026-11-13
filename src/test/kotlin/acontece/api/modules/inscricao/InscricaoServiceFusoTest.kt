package acontece.api.modules.inscricao

import acontece.api.config.ClockConfig
import acontece.api.modules.evento.Endereco
import acontece.api.modules.evento.Evento
import acontece.api.modules.evento.EventoRepository
import acontece.api.modules.usuario.Participante
import acontece.api.modules.usuario.ParticipanteRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest

// As datas do evento são digitadas pelo organizador em horário de Brasília, sem fuso. O servidor
// (ex.: Render) roda em UTC; por isso o service lê a hora de um Clock com fuso de São Paulo, e não
// de LocalDateTime.now(), que usaria o fuso da máquina.
@DataJpaTest
class InscricaoServiceFusoTest {

    @Autowired lateinit var repository: InscricaoRepository
    @Autowired lateinit var eventos: EventoRepository
    @Autowired lateinit var participantes: ParticipanteRepository

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    private fun servicoComRelogioEm(instante: String) =
        InscricaoService(repository, eventos, participantes, Clock.fixed(Instant.parse(instante), saoPaulo))

    // Evento de 1/12/2026, das 10h às 19h (horário de Brasília)
    private fun eventoQueTerminaAs19h() =
        eventos.save(
            Evento(
                titulo = "Evento das 19h",
                descricao = "",
                dataInicio = LocalDateTime.of(2026, 12, 1, 10, 0),
                dataFim = LocalDateTime.of(2026, 12, 1, 19, 0),
                vagas = 10,
                endereco = Endereco("01001000", "Praça da Sé", "Sé", "São Paulo", "SP"),
            )
        )

    private fun novoParticipante() =
        participantes.save(Participante("Maria Silva", "maria@email.com", "52998224725"))

    @Test
    fun `evento que termina as 19h aceita inscricao as 18h em Sao Paulo`() {
        val evento = eventoQueTerminaAs19h()
        val participante = novoParticipante()
        // 21:00 UTC = 18:00 em São Paulo. Em UTC "puro" o evento pareceria encerrado há 2 horas.
        val servico = servicoComRelogioEm("2026-12-01T21:00:00Z")

        val resposta =
            servico.criar(InscricaoRequest(participanteId = participante.id, eventoId = evento.id))

        assertEquals(LocalDateTime.of(2026, 12, 1, 18, 0), resposta.dataInscricao)
    }

    @Test
    fun `evento que termina as 19h recusa inscricao as 20h em Sao Paulo`() {
        val evento = eventoQueTerminaAs19h()
        val participante = novoParticipante()
        val servico = servicoComRelogioEm("2026-12-01T23:00:00Z") // 20:00 em São Paulo

        val erro =
            assertFailsWith<IllegalArgumentException> {
                servico.criar(InscricaoRequest(participanteId = participante.id, eventoId = evento.id))
            }

        assertEquals("O evento já foi encerrado", erro.message)
    }

    @Test
    fun `o relogio da aplicacao usa o fuso de Sao Paulo`() {
        assertEquals(saoPaulo, ClockConfig().clock().zone)
    }
}

package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao
import acontece.api.enums.StatusInscricao.CANCELADA
import acontece.api.enums.StatusInscricao.CONFIRMADA
import acontece.api.enums.StatusInscricao.PENDENTE
import acontece.api.exception.ConflictException
import acontece.api.exception.ResourceNotFoundException
import acontece.api.modules.evento.Endereco
import acontece.api.modules.evento.Evento
import acontece.api.modules.evento.EventoRepository
import acontece.api.modules.usuario.Participante
import acontece.api.modules.usuario.ParticipanteRepository
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import

// @DataJpaTest + @Import: banco H2 em memória com o service real (nada de mock, nada de Supabase).
@DataJpaTest
@Import(InscricaoService::class)
class InscricaoServiceTest {

    @Autowired lateinit var service: InscricaoService
    @Autowired lateinit var repository: InscricaoRepository
    @Autowired lateinit var eventos: EventoRepository
    @Autowired lateinit var participantes: ParticipanteRepository

    private var contador = 0

    private fun novoEvento(
        vagas: Int = 10,
        inicio: LocalDateTime = LocalDateTime.now().plusDays(1),
        fim: LocalDateTime = LocalDateTime.now().plusDays(2),
    ) =
        eventos.save(
            Evento(
                titulo = "Evento ${++contador}",
                descricao = "",
                dataInicio = inicio,
                dataFim = fim,
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

    private fun inscrever(participante: Participante, evento: Evento, status: StatusInscricao) =
        repository.save(Inscricao(participante = participante, evento = evento, status = status))

    private fun pedido(participante: Participante, evento: Evento) =
        InscricaoRequest(participanteId = participante.id, eventoId = evento.id)

    @Test
    fun `criar inscreve como PENDENTE`() {
        val participante = novoParticipante()
        val evento = novoEvento()

        val resposta = service.criar(pedido(participante, evento))

        assertEquals(PENDENTE, resposta.status)
        assertEquals(participante.id, resposta.participanteId)
        assertEquals(participante.nome, resposta.participanteNome)
        assertEquals(evento.id, resposta.eventoId)
        assertEquals(evento.titulo, resposta.eventoTitulo)
        assertNotNull(resposta.dataInscricao)
        assertEquals(1L, repository.count())
    }

    @Test
    fun `criar com participante inexistente`() {
        val evento = novoEvento()

        val erro =
            assertFailsWith<ResourceNotFoundException> {
                service.criar(InscricaoRequest(participanteId = 999999L, eventoId = evento.id))
            }

        assertEquals("Participante 999999 não encontrado", erro.message)
    }

    @Test
    fun `criar com evento inexistente`() {
        val participante = novoParticipante()

        val erro =
            assertFailsWith<ResourceNotFoundException> {
                service.criar(InscricaoRequest(participanteId = participante.id, eventoId = 999999L))
            }

        assertEquals("Evento 999999 não encontrado", erro.message)
    }

    @Test
    fun `criar em evento encerrado`() {
        val participante = novoParticipante()
        val evento =
            novoEvento(
                inicio = LocalDateTime.now().minusDays(2),
                fim = LocalDateTime.now().minusDays(1),
            )

        val erro =
            assertFailsWith<IllegalArgumentException> { service.criar(pedido(participante, evento)) }

        assertEquals("O evento já foi encerrado", erro.message)
    }

    @Test
    fun `criar em evento em andamento`() {
        val participante = novoParticipante()
        val evento =
            novoEvento(
                inicio = LocalDateTime.now().minusDays(1),
                fim = LocalDateTime.now().plusDays(1),
            )

        val resposta = service.criar(pedido(participante, evento))

        assertEquals(PENDENTE, resposta.status)
    }

    @Test
    fun `criar duplicada ativa`() {
        val evento = novoEvento()
        val comPendente = novoParticipante()
        val comConfirmada = novoParticipante()
        inscrever(comPendente, evento, PENDENTE)
        inscrever(comConfirmada, evento, CONFIRMADA)

        val erroPendente =
            assertFailsWith<ConflictException> { service.criar(pedido(comPendente, evento)) }
        val erroConfirmada =
            assertFailsWith<ConflictException> { service.criar(pedido(comConfirmada, evento)) }

        assertEquals("Participante já possui inscrição ativa neste evento", erroPendente.message)
        assertEquals("Participante já possui inscrição ativa neste evento", erroConfirmada.message)
    }

    @Test
    fun `criar sem vagas`() {
        val evento = novoEvento(vagas = 1)
        inscrever(novoParticipante(), evento, PENDENTE)

        val erro =
            assertFailsWith<ConflictException> { service.criar(pedido(novoParticipante(), evento)) }

        assertEquals("Evento sem vagas disponíveis", erro.message)
    }

    @Test
    fun `cancelada libera a vaga`() {
        val evento = novoEvento(vagas = 1)
        inscrever(novoParticipante(), evento, CANCELADA)

        val resposta = service.criar(pedido(novoParticipante(), evento))

        assertEquals(PENDENTE, resposta.status)
    }

    @Test
    fun `reinscricao depois de cancelar`() {
        val participante = novoParticipante()
        val evento = novoEvento()
        inscrever(participante, evento, CANCELADA)

        val resposta = service.criar(pedido(participante, evento))

        assertEquals(PENDENTE, resposta.status)
        assertEquals(2L, repository.count())
    }
}

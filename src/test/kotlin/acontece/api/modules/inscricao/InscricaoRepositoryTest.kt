package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao
import acontece.api.enums.StatusInscricao.CANCELADA
import acontece.api.enums.StatusInscricao.CONFIRMADA
import acontece.api.enums.StatusInscricao.PENDENTE
import acontece.api.modules.evento.Endereco
import acontece.api.modules.evento.Evento
import acontece.api.modules.evento.EventoRepository
import acontece.api.modules.usuario.Participante
import acontece.api.modules.usuario.ParticipanteRepository
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.domain.PageRequest

// @DataJpaTest sobe só a camada JPA com um banco H2 em memória (nunca o Supabase).
@DataJpaTest
class InscricaoRepositoryTest {

    @Autowired lateinit var inscricoes: InscricaoRepository
    @Autowired lateinit var eventos: EventoRepository
    @Autowired lateinit var participantes: ParticipanteRepository

    private var contador = 0
    private val ativas = listOf(PENDENTE, CONFIRMADA)

    private fun novoEvento() =
        eventos.save(
            Evento(
                titulo = "Evento ${++contador}",
                descricao = "",
                dataInicio = LocalDateTime.now().plusDays(1),
                dataFim = LocalDateTime.now().plusDays(2),
                vagas = 10,
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
        inscricoes.save(Inscricao(participante = participante, evento = evento, status = status))

    // Cenário usado pelos testes de filtro:
    //   i1 = p1 em e1 (PENDENTE)   i2 = p1 em e2 (CONFIRMADA)   i3 = p2 em e1 (CONFIRMADA)
    private class Cenario(
        val e1: Evento,
        val p1: Participante,
        val p2: Participante,
        val i1: Inscricao,
        val i2: Inscricao,
        val i3: Inscricao,
    )

    private fun cenario(): Cenario {
        val e1 = novoEvento()
        val e2 = novoEvento()
        val p1 = novoParticipante()
        val p2 = novoParticipante()
        return Cenario(
            e1 = e1,
            p1 = p1,
            p2 = p2,
            i1 = inscrever(p1, e1, PENDENTE),
            i2 = inscrever(p1, e2, CONFIRMADA),
            i3 = inscrever(p2, e1, CONFIRMADA),
        )
    }

    private fun ids(vararg i: Inscricao) = i.map { it.id }.toSet()

    @Test
    fun `contagem considera so pendentes e confirmadas do evento`() {
        val a = novoEvento()
        val b = novoEvento()
        inscrever(novoParticipante(), a, PENDENTE)
        inscrever(novoParticipante(), a, CONFIRMADA)
        inscrever(novoParticipante(), a, CANCELADA)
        inscrever(novoParticipante(), b, PENDENTE)

        assertEquals(2L, inscricoes.countByEventoIdAndStatusIn(a.id!!, ativas))
    }

    @Test
    fun `existe inscricao ativa so para o mesmo participante e evento`() {
        val evento = novoEvento()
        val outroEvento = novoEvento()
        val ativo = novoParticipante()
        val soCancelado = novoParticipante()
        val semInscricao = novoParticipante()
        inscrever(ativo, evento, PENDENTE)
        inscrever(soCancelado, evento, CANCELADA)

        assertTrue(
            inscricoes.existsByParticipanteIdAndEventoIdAndStatusIn(ativo.id!!, evento.id!!, ativas)
        )
        assertFalse(
            inscricoes.existsByParticipanteIdAndEventoIdAndStatusIn(
                soCancelado.id!!,
                evento.id!!,
                ativas,
            )
        )
        assertFalse(
            inscricoes.existsByParticipanteIdAndEventoIdAndStatusIn(
                semInscricao.id!!,
                evento.id!!,
                ativas,
            )
        )
        assertFalse(
            inscricoes.existsByParticipanteIdAndEventoIdAndStatusIn(
                ativo.id!!,
                outroEvento.id!!,
                ativas,
            )
        )
    }

    @Test
    fun `buscar sem filtros devolve todas`() {
        val c = cenario()

        val pagina = inscricoes.buscar(null, null, null, PageRequest.of(0, 10))

        assertEquals(ids(c.i1, c.i2, c.i3), pagina.content.map { it.id }.toSet())
    }

    @Test
    fun `buscar filtra por evento`() {
        val c = cenario()

        val pagina = inscricoes.buscar(c.e1.id, null, null, PageRequest.of(0, 10))

        assertEquals(ids(c.i1, c.i3), pagina.content.map { it.id }.toSet())
    }

    @Test
    fun `buscar filtra por participante`() {
        val c = cenario()

        val pagina = inscricoes.buscar(null, c.p1.id, null, PageRequest.of(0, 10))

        assertEquals(ids(c.i1, c.i2), pagina.content.map { it.id }.toSet())
    }

    @Test
    fun `buscar filtra por status`() {
        val c = cenario()

        val pagina = inscricoes.buscar(null, null, CONFIRMADA, PageRequest.of(0, 10))

        assertEquals(ids(c.i2, c.i3), pagina.content.map { it.id }.toSet())
    }

    @Test
    fun `buscar combina filtros`() {
        val c = cenario()

        val porEventoEStatus = inscricoes.buscar(c.e1.id, null, CONFIRMADA, PageRequest.of(0, 10))
        val porParticipanteEStatus =
            inscricoes.buscar(null, c.p1.id, CONFIRMADA, PageRequest.of(0, 10))

        assertEquals(ids(c.i3), porEventoEStatus.content.map { it.id }.toSet())
        assertEquals(ids(c.i2), porParticipanteEStatus.content.map { it.id }.toSet())
    }

    @Test
    fun `buscarParaInscricao devolve o evento ou null`() {
        val evento = novoEvento()

        assertEquals(evento.id, eventos.buscarParaInscricao(evento.id!!)?.id)
        assertNull(eventos.buscarParaInscricao(999999L))
    }
}

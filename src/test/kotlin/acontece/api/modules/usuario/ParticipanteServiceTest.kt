package acontece.api.modules.usuario

import acontece.api.config.ClockConfig
import acontece.api.enums.StatusInscricao
import acontece.api.enums.StatusUsuario
import acontece.api.exception.ConflictException
import acontece.api.exception.ResourceNotFoundException
import acontece.api.modules.evento.Endereco
import acontece.api.modules.evento.Evento
import acontece.api.modules.evento.EventoRepository
import acontece.api.modules.inscricao.Inscricao
import acontece.api.modules.inscricao.InscricaoRepository
import acontece.api.modules.inscricao.InscricaoService
import acontece.api.notificacao.TelegramNotificador
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

// @DataJpaTest + @Import: banco H2 em memória com o service real (nada de mock, nada de Supabase).
@DataJpaTest
@Import(ParticipanteService::class, InscricaoService::class, ClockConfig::class, TelegramNotificador::class)
class ParticipanteServiceTest {

    // O PasswordEncoder de verdade mora na SecurityConfig, que o @DataJpaTest não carrega.
    @TestConfiguration
    class Config {
        @Bean fun passwordEncoder() = BCryptPasswordEncoder()
    }

    @Autowired lateinit var service: ParticipanteService
    @Autowired lateinit var usuarios: UsuarioRepository
    @Autowired lateinit var participantes: ParticipanteRepository
    @Autowired lateinit var eventos: EventoRepository
    @Autowired lateinit var inscricoes: InscricaoRepository
    @Autowired lateinit var em: EntityManager

    private var contador = 0

    private fun pedido(email: String, cpf: String = "52998224725") =
        ParticipanteRequest(nome = "Maria Silva", email = email, cpf = cpf, senha = "senha-forte-1")

    private fun novoEvento(fim: LocalDateTime = LocalDateTime.now().plusDays(2)) =
        eventos.save(
            Evento(
                titulo = "Evento ${++contador}",
                descricao = "",
                dataInicio = fim.minusDays(1),
                dataFim = fim,
                vagas = 10,
                endereco = Endereco("01001000", "Praça da Sé", "Sé", "São Paulo", "SP"),
            )
        )

    private fun inscrever(participanteId: Long, evento: Evento, status: StatusInscricao) =
        inscricoes.save(
            Inscricao(participante = participantes.getReferenceById(participanteId), evento = evento, status = status)
        )

    // O UPDATE em lote e as mudanças da entidade só chegam ao banco no flush; clear força reler de lá.
    private fun gravarERelerDoBanco() {
        em.flush()
        em.clear()
    }

    private fun statusDa(inscricao: Inscricao) = inscricoes.findById(inscricao.id!!).get().status

    @Test
    fun `excluir participante com inscricoes funciona e apaga os dados pessoais`() {
        val maria = service.criar(pedido("maria@email.com").copy(telefone = "11999998888"))
        val futura = inscrever(maria.id, novoEvento(), StatusInscricao.CONFIRMADA)
        val cancelada = inscrever(maria.id, novoEvento(), StatusInscricao.CANCELADA)
        val antiga = inscrever(maria.id, novoEvento(fim = LocalDateTime.now().minusDays(1)), StatusInscricao.CONFIRMADA)

        service.excluir(maria.id, peloProprioParticipante = false)
        gravarERelerDoBanco()

        // A linha continua (as inscrições apontam para ela), mas sem nenhum dado pessoal.
        val linha = participantes.findById(maria.id).get()
        assertEquals(StatusUsuario.EXCLUIDO, linha.status)
        assertEquals("Participante excluído", linha.nome)
        assertFalse(linha.email.contains("maria"))
        assertNotEquals("52998224725", linha.cpf)
        assertNull(linha.telefone)
        assertNull(linha.senha)

        // A vaga do evento futuro volta; o histórico de evento encerrado fica como estava.
        assertEquals(StatusInscricao.CANCELADA, statusDa(futura))
        assertEquals(StatusInscricao.CANCELADA, statusDa(cancelada))
        assertEquals(StatusInscricao.CONFIRMADA, statusDa(antiga))
    }

    @Test
    fun `participante excluido some da API`() {
        val maria = service.criar(pedido("maria@email.com"))
        service.excluir(maria.id, peloProprioParticipante = false)
        gravarERelerDoBanco()

        assertFailsWith<ResourceNotFoundException> { service.buscarPorId(maria.id) }
        assertFailsWith<ResourceNotFoundException> { service.atualizar(maria.id, ParticipantePatchRequest(nome = "X")) }
        assertFailsWith<ResourceNotFoundException> { service.excluir(maria.id, peloProprioParticipante = false) }
        assertFailsWith<ResourceNotFoundException> {
            service.alterarStatus(maria.id, AlterarStatusParticipanteRequest(StatusUsuario.ATIVO))
        }
        assertEquals(0, service.listar(PageRequest.of(0, 10)).totalElements)
    }

    @Test
    fun `e-mail e CPF de conta excluida ficam livres para novo cadastro`() {
        val maria = service.criar(pedido("maria@email.com"))
        service.excluir(maria.id, peloProprioParticipante = false)
        gravarERelerDoBanco()

        val deNovo = service.criar(pedido("maria@email.com"))

        assertNotEquals(maria.id, deNovo.id)
        assertEquals(StatusUsuario.ATIVO, deNovo.status)
    }

    @Test
    fun `duas contas excluidas nao colidem no e-mail nem no CPF`() {
        val ana = service.criar(pedido("ana@email.com", cpf = "52998224725"))
        val bia = service.criar(pedido("bia@email.com", cpf = "39053344705"))

        service.excluir(ana.id, peloProprioParticipante = false)
        service.excluir(bia.id, peloProprioParticipante = false)
        gravarERelerDoBanco() // as colunas são UNIQUE: valores repetidos falhariam aqui
    }

    @Test
    fun `inativar cancela as inscricoes em aberto e reativar nao as traz de volta`() {
        val maria = service.criar(pedido("maria@email.com"))
        val inscricao = inscrever(maria.id, novoEvento(), StatusInscricao.PENDENTE)

        val inativa = service.alterarStatus(maria.id, AlterarStatusParticipanteRequest(StatusUsuario.INATIVO))
        gravarERelerDoBanco()

        assertEquals(StatusUsuario.INATIVO, inativa.status)
        assertEquals(StatusInscricao.CANCELADA, statusDa(inscricao))
        // O organizador ainda vê a conta inativa, com os dados, para poder reativar.
        assertEquals("maria@email.com", service.buscarPorId(maria.id).email)
        assertEquals(StatusUsuario.INATIVO, service.listar(PageRequest.of(0, 10)).content.single().status)

        val ativa = service.alterarStatus(maria.id, AlterarStatusParticipanteRequest(StatusUsuario.ATIVO))
        gravarERelerDoBanco()

        assertEquals(StatusUsuario.ATIVO, ativa.status)
        assertEquals(StatusInscricao.CANCELADA, statusDa(inscricao))
    }

    @Test
    fun `alterar status para EXCLUIDO e recusado`() {
        val maria = service.criar(pedido("maria@email.com"))

        val erro =
            assertFailsWith<IllegalArgumentException> {
                service.alterarStatus(maria.id, AlterarStatusParticipanteRequest(StatusUsuario.EXCLUIDO))
            }

        assertEquals("Para excluir a conta, use DELETE /participantes/{id}", erro.message)
    }

    @Test
    fun `cadastro com e-mail de organizador devolve conflito`() {
        usuarios.save(Organizador("Chefe", "chefe@email.com", "hash"))

        val erro = assertFailsWith<ConflictException> { service.criar(pedido("Chefe@Email.com")) }

        assertEquals("Já existe um cadastro com esse e-mail", erro.message)
    }

    @Test
    fun `atualizar para e-mail de organizador devolve conflito`() {
        usuarios.save(Organizador("Chefe", "chefe@email.com", "hash"))
        val maria = service.criar(pedido("maria@email.com"))

        assertFailsWith<ConflictException> {
            service.atualizar(maria.id, ParticipantePatchRequest(email = "chefe@email.com"))
        }
    }

    @Test
    fun `atualizar mantendo o proprio e-mail funciona`() {
        val maria = service.criar(pedido("maria@email.com"))

        val atualizada =
            service.atualizar(maria.id, ParticipantePatchRequest(email = "maria@email.com"))

        assertEquals("maria@email.com", atualizada.email)
    }
}

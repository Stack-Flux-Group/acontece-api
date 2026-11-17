package acontece.api.modules.usuario

import acontece.api.exception.ConflictException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

// @DataJpaTest + @Import: banco H2 em memória com o service real (nada de mock, nada de Supabase).
@DataJpaTest
@Import(ParticipanteService::class)
class ParticipanteServiceTest {

    // O PasswordEncoder de verdade mora na SecurityConfig, que o @DataJpaTest não carrega.
    @TestConfiguration
    class Config {
        @Bean fun passwordEncoder() = BCryptPasswordEncoder()
    }

    @Autowired lateinit var service: ParticipanteService
    @Autowired lateinit var usuarios: UsuarioRepository

    private fun pedido(email: String, cpf: String = "52998224725") =
        ParticipanteRequest(nome = "Maria Silva", email = email, cpf = cpf, senha = "senha-forte-1")

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

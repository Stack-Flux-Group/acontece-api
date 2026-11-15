package acontece.api.config

import acontece.api.modules.usuario.Organizador
import acontece.api.modules.usuario.UsuarioRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

// Não existe cadastro público de organizador. O primeiro nasce aqui, na subida da aplicação,
// a partir de ORGANIZADOR_EMAIL e ORGANIZADOR_SENHA. Sem as duas variáveis, não faz nada.
@Component
class OrganizadorInicial(
    private val repository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${organizador.email:}") private val email: String,
    @Value("\${organizador.senha:}") private val senha: String,
    @Value("\${organizador.nome:Organizador}") private val nome: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (email.isBlank() || senha.isBlank()) return

        val emailNormalizado = email.trim().lowercase()
        if (repository.existsByEmail(emailNormalizado)) return

        repository.save(Organizador(nome.trim(), emailNormalizado, passwordEncoder.encode(senha)!!))
        log.info("Organizador inicial {} criado", emailNormalizado)
    }
}

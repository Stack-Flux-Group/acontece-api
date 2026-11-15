package acontece.api.modules.auth

import acontece.api.modules.usuario.UsuarioRepository
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val repository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
) {

    @Transactional(readOnly = true)
    fun login(request: LoginRequest): TokenResponse {
        val usuario = repository.findByEmail(request.email.trim().lowercase())
        val hash = usuario?.senha

        // Mesma mensagem para e-mail inexistente, conta sem senha e senha errada:
        // quem tenta não descobre quais e-mails estão cadastrados.
        if (usuario == null || hash == null || !passwordEncoder.matches(request.senha, hash)) {
            throw BadCredentialsException("E-mail ou senha inválidos")
        }

        return tokenService.gerar(usuario)
    }
}

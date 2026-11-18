package acontece.api.modules.auth

import acontece.api.modules.usuario.UsuarioRepository
import java.time.Instant
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val repository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val limite: LimiteDeTentativas,
) {

    // `ip` é de quem está tentando entrar; junto com o e-mail, decide se a tentativa é bloqueada.
    @Transactional(readOnly = true)
    fun login(request: LoginRequest, ip: String): TokenResponse {
        val email = request.email.trim().lowercase()
        // Quem já errou demais nem chega a ter a senha conferida.
        limite.verificar(ip, email)

        val usuario = repository.findByEmail(email)
        val hash = usuario?.senha

        // Mesma mensagem para e-mail inexistente, conta sem senha e senha errada:
        // quem tenta não descobre quais e-mails estão cadastrados.
        if (usuario == null || hash == null || !passwordEncoder.matches(request.senha, hash)) {
            limite.registrarFalha(ip, email)
            throw BadCredentialsException("E-mail ou senha inválidos")
        }

        limite.registrarSucesso(ip, email)
        return tokenService.gerar(usuario)
    }

    // O token continua válido até expirar, mesmo que a conta seja excluída nesse meio-tempo.
    // Nesse caso a resposta é 401, e não 404: para o front, a sessão acabou e é preciso entrar de novo.
    @Transactional(readOnly = true)
    fun usuarioLogado(id: Long, tokenExpiraEm: Instant?): UsuarioLogadoResponse {
        val usuario =
            repository.findByIdOrNull(id)
                ?: throw BadCredentialsException("Sua conta não existe mais. Entre novamente.")
        return UsuarioLogadoResponse(
            id = id,
            nome = usuario.nome,
            email = usuario.email,
            papel = usuario.papel,
            tokenExpiraEm = tokenExpiraEm,
        )
    }
}

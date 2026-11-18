package acontece.api.modules.auth

import org.springframework.security.core.AuthenticationException

// A conta existe, mas não pode ser usada (inativa ou excluída).
// - No login, com a senha certa: 403 com a mensagem (GlobalExceptionHandler).
// - Com um token emitido antes da inativação: 401 com a mensagem (SecurityConfig). Para o front,
//   a sessão acabou; ao tentar entrar de novo, a pessoa vê o motivo.
class ContaIndisponivelException(message: String) : AuthenticationException(message) {

    companion object {
        const val CONTA_INATIVA = "Sua conta está inativa. Entre em contato com o organizador."
        const val CONTA_EXCLUIDA = "Sua conta não existe mais. Entre novamente."
    }
}

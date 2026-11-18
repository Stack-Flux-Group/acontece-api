package acontece.api.modules.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

// Freia quem tenta adivinhar senhas: depois de `maxFalhas` logins errados do mesmo IP para o mesmo
// e-mail, novas tentativas dessa dupla são recusadas (429) até a janela de bloqueio acabar.
//
// A chave é IP + e-mail, e não só o e-mail, para que um atacante não consiga bloquear a conta de
// outra pessoa errando a senha dela de propósito: ele só bloqueia a si mesmo.
//
// Fica na memória da instância: reiniciar a aplicação zera os contadores, e com mais de uma
// instância cada uma conta as suas. Para o tamanho atual do projeto isso basta.
@Component
class LimiteDeTentativas(
    private val clock: Clock,
    @Value("\${login.max-falhas:5}") private val maxFalhas: Int,
    @Value("\${login.bloqueio-minutos:15}") bloqueioMinutos: Long,
) {

    private val janela: Duration = Duration.ofMinutes(bloqueioMinutos)

    // Falhas contadas a partir da primeira falha da janela atual.
    private data class Registro(val falhas: Int, val inicio: Instant)

    private val registros = ConcurrentHashMap<String, Registro>()

    // Lança LoginBloqueadoException se a dupla IP + e-mail já estourou o limite.
    fun verificar(ip: String, email: String) {
        val registro = registros[chave(ip, email)] ?: return
        val agora = clock.instant()
        val fim = registro.inicio.plus(janela)
        if (!agora.isBefore(fim)) return // janela venceu: a próxima falha começa do zero
        if (registro.falhas >= maxFalhas) {
            // Arredonda para cima: o cliente nunca tenta de novo um segundo antes da hora.
            val segundos = Duration.between(agora, fim).toMillis().let { (it + 999) / 1000 }
            throw LoginBloqueadoException(segundos.coerceAtLeast(1))
        }
    }

    fun registrarFalha(ip: String, email: String) {
        val agora = clock.instant()
        registros.compute(chave(ip, email)) { _, atual ->
            if (atual == null || !agora.isBefore(atual.inicio.plus(janela))) Registro(1, agora)
            else atual.copy(falhas = atual.falhas + 1)
        }
        limparVencidos(agora)
    }

    // Login certo zera o histórico da dupla.
    fun registrarSucesso(ip: String, email: String) {
        registros.remove(chave(ip, email))
    }

    // Evita que o mapa cresça sem limite com chaves de tentativas antigas.
    private fun limparVencidos(agora: Instant) {
        if (registros.size < LIMPAR_ACIMA_DE) return
        registros.entries.removeIf { !agora.isBefore(it.value.inicio.plus(janela)) }
    }

    private fun chave(ip: String, email: String) = "$ip|$email"

    private companion object {
        const val LIMPAR_ACIMA_DE = 10_000
    }
}

// Vira 429 Too Many Requests com o cabeçalho Retry-After (ver GlobalExceptionHandler).
class LoginBloqueadoException(val segundosParaTentarDeNovo: Long) :
    RuntimeException("Muitas tentativas de login. Tente novamente em alguns minutos.")

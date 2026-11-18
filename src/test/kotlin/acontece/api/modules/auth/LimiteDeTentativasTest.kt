package acontece.api.modules.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LimiteDeTentativasTest {

    // Relógio que o teste avança na mão, para não precisar esperar 15 minutos de verdade.
    private class RelogioManual(var agora: Instant) : Clock() {
        override fun instant() = agora
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
    }

    private val relogio = RelogioManual(Instant.parse("2026-11-17T12:00:00Z"))
    private val limite = LimiteDeTentativas(relogio, maxFalhas = 3, bloqueioMinutos = 15)

    private fun errar(vezes: Int, ip: String = "1.1.1.1", email: String = "ana@email.com") =
        repeat(vezes) { limite.registrarFalha(ip, email) }

    @Test
    fun `abaixo do limite continua liberado`() {
        errar(2)
        limite.verificar("1.1.1.1", "ana@email.com") // não lança
    }

    @Test
    fun `ao atingir o limite bloqueia e informa quantos segundos faltam`() {
        errar(3)
        relogio.agora = relogio.agora.plusSeconds(60)

        val ex = assertFailsWith<LoginBloqueadoException> { limite.verificar("1.1.1.1", "ana@email.com") }
        assertEquals(14 * 60L, ex.segundosParaTentarDeNovo)
    }

    @Test
    fun `bloqueio acaba quando a janela vence`() {
        errar(3)
        relogio.agora = relogio.agora.plus(Duration.ofMinutes(15))

        limite.verificar("1.1.1.1", "ana@email.com") // não lança

        // E a contagem recomeça do zero: uma falha nova não bloqueia de imediato.
        errar(1)
        limite.verificar("1.1.1.1", "ana@email.com")
    }

    @Test
    fun `login certo zera as falhas`() {
        errar(2)
        limite.registrarSucesso("1.1.1.1", "ana@email.com")
        errar(2)

        limite.verificar("1.1.1.1", "ana@email.com") // 2 falhas, não 4
    }

    @Test
    fun `bloqueio vale so para a dupla ip e email`() {
        errar(3)

        limite.verificar("2.2.2.2", "ana@email.com") // outro IP, mesmo e-mail
        limite.verificar("1.1.1.1", "bia@email.com") // mesmo IP, outro e-mail
    }
}

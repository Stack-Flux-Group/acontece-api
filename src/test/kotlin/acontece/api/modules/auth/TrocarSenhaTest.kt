package acontece.api.modules.auth

import com.jayway.jsonpath.JsonPath
import kotlin.test.Test
import org.hamcrest.Matchers.hasItem
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// PATCH /me/senha pelo caminho real: cadastrar, entrar, trocar e entrar de novo.
// Cada teste usa um e-mail próprio, porque o limite de tentativas conta por IP + e-mail.
@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:trocarsenha;DB_CLOSE_DELAY=-1",
            "organizador.email=chefe@email.com",
            "organizador.senha=senha-do-chefe-1",
            "login.max-falhas=3",
        ]
)
@AutoConfigureMockMvc
class TrocarSenhaTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun cadastrar(email: String, cpf: String) {
        mockMvc
            .perform(
                post("/participantes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nome":"Maria Silva","email":"$email","cpf":"$cpf","senha":"senha-antiga-1"}""")
            )
            .andExpect(status().isCreated)
    }

    private fun login(email: String, senha: String) =
        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"$senha"}""")
        )

    private fun entrar(email: String, senha: String = "senha-antiga-1"): String {
        val resposta = login(email, senha).andExpect(status().isOk).andReturn().response.contentAsString
        return "Bearer " + JsonPath.read<String>(resposta, "$.token")
    }

    private fun trocar(token: String, corpo: String) =
        mockMvc.perform(
            patch("/me/senha")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
        )

    @Test
    fun `troca a senha e passa a entrar so com a nova`() {
        cadastrar("ana@email.com", "52998224725")
        val token = entrar("ana@email.com")

        trocar(token, """{"senhaAtual":"senha-antiga-1","novaSenha":"senha-nova-12"}""")
            .andExpect(status().isNoContent)

        login("ana@email.com", "senha-antiga-1").andExpect(status().isUnauthorized)
        login("ana@email.com", "senha-nova-12").andExpect(status().isOk)
    }

    @Test
    fun `senha atual errada devolve 400 e nao troca nada`() {
        cadastrar("bia@email.com", "39053344705")
        val token = entrar("bia@email.com")

        trocar(token, """{"senhaAtual":"chute-errado","novaSenha":"senha-nova-12"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("A senha atual está incorreta"))

        login("bia@email.com", "senha-antiga-1").andExpect(status().isOk)
    }

    @Test
    fun `nova senha igual a atual devolve 400`() {
        cadastrar("caio@email.com", "28264519016")
        val token = entrar("caio@email.com")

        trocar(token, """{"senhaAtual":"senha-antiga-1","novaSenha":"senha-antiga-1"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("A nova senha deve ser diferente da atual"))
    }

    @Test
    fun `nova senha curta ou campos ausentes devolvem 400 com o campo`() {
        cadastrar("dina@email.com", "36148295709")
        val token = entrar("dina@email.com")

        trocar(token, """{"senhaAtual":"senha-antiga-1","novaSenha":"curta"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("novaSenha"))

        trocar(token, "{}")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("senhaAtual")))
    }

    @Test
    fun `organizador tambem troca a propria senha`() {
        val token = entrar("chefe@email.com", "senha-do-chefe-1")

        trocar(token, """{"senhaAtual":"senha-do-chefe-1","novaSenha":"chefe-senha-nova"}""")
            .andExpect(status().isNoContent)

        login("chefe@email.com", "chefe-senha-nova").andExpect(status().isOk)
    }

    @Test
    fun `errar a senha atual vezes demais bloqueia com 429`() {
        cadastrar("eva@email.com", "12345678909")
        val token = entrar("eva@email.com")

        repeat(3) {
            trocar(token, """{"senhaAtual":"chute-$it","novaSenha":"senha-nova-12"}""")
                .andExpect(status().isBadRequest)
        }

        // Bloqueado: nem a senha atual certa passa até a janela acabar.
        trocar(token, """{"senhaAtual":"senha-antiga-1","novaSenha":"senha-nova-12"}""")
            .andExpect(status().isTooManyRequests)
    }

    @Test
    fun `sem token devolve 401`() {
        mockMvc
            .perform(
                patch("/me/senha")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"senhaAtual":"a","novaSenha":"senha-nova-12"}""")
            )
            .andExpect(status().isUnauthorized)
    }
}

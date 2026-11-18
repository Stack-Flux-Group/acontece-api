package acontece.api.modules.auth

import com.jayway.jsonpath.JsonPath
import kotlin.test.Test
import kotlin.test.assertFalse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// GET /me pelo caminho real: cadastrar, entrar e perguntar quem está logado com o token.
@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:usuariologado;DB_CLOSE_DELAY=-1",
            "organizador.email=chefe@email.com",
            "organizador.senha=senha-do-chefe-1",
            "organizador.nome=Chefe",
        ]
)
@AutoConfigureMockMvc
class UsuarioLogadoTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun cadastrar(nome: String, email: String, cpf: String): Long {
        val resposta =
            mockMvc
                .perform(
                    post("/participantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"nome":"$nome","email":"$email","cpf":"$cpf","senha":"senha-forte-1"}""")
                )
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .contentAsString
        return JsonPath.read<Number>(resposta, "$.id").toLong()
    }

    private fun entrar(email: String, senha: String = "senha-forte-1"): String {
        val resposta =
            mockMvc
                .perform(
                    post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"$email","senha":"$senha"}""")
                )
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        return "Bearer " + JsonPath.read<String>(resposta, "$.token")
    }

    private fun me(token: String) = mockMvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, token))

    @Test
    fun `participante logado ve os proprios dados`() {
        val id = cadastrar("Ana Souza", "ana@email.com", "52998224725")
        val token = entrar("ana@email.com")

        val resposta =
            me(token)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana@email.com"))
                .andExpect(jsonPath("$.papel").value("PARTICIPANTE"))
                .andExpect(jsonPath("$.tokenExpiraEm").isNotEmpty)
                .andReturn()
                .response
                .contentAsString

        assertFalse(resposta.contains("senha"), "a resposta não pode trazer a senha")
    }

    @Test
    fun `organizador logado aparece com o papel de organizador`() {
        me(entrar("chefe@email.com", "senha-do-chefe-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nome").value("Chefe"))
            .andExpect(jsonPath("$.papel").value("ORGANIZADOR"))
    }

    @Test
    fun `dados vem do banco e nao do token`() {
        val id = cadastrar("Bia Lima", "bia@email.com", "39053344705")
        val token = entrar("bia@email.com")

        mockMvc
            .perform(
                patch("/participantes/$id")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nome":"Beatriz Lima"}""")
            )
            .andExpect(status().isOk)

        // O token é o mesmo de antes da edição, mas o nome já sai atualizado.
        me(token).andExpect(jsonPath("$.nome").value("Beatriz Lima"))
    }

    @Test
    fun `conta excluida com token ainda valido devolve 401`() {
        val id = cadastrar("Caio Reis", "caio@email.com", "28264519016")
        val token = entrar("caio@email.com")

        mockMvc
            .perform(delete("/participantes/$id").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isNoContent)

        me(token)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Sua conta não existe mais. Entre novamente."))
    }

    @Test
    fun `sem token devolve 401`() {
        mockMvc.perform(get("/me")).andExpect(status().isUnauthorized)
    }
}

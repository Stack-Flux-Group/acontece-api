package acontece.api.modules.auth

import com.jayway.jsonpath.JsonPath
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Sobe a aplicação inteira (filtro de JWT, BCrypt, banco H2) e percorre o caminho real:
// cadastrar, entrar, usar o token. É o teste que prova que as peças funcionam juntas.
@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:login;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "organizador.email=Chefe@Email.com",
            "organizador.senha=senha-do-chefe-1",
        ]
)
@AutoConfigureMockMvc
class FluxoDeLoginTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun json(body: String) = post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)

    private fun cadastrar(email: String, cpf: String): Long {
        val resposta =
            mockMvc
                .perform(
                    post("/participantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"nome":"Maria Silva","email":"$email","cpf":"$cpf","senha":"senha-forte-1"}"""
                        )
                )
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .contentAsString
        return JsonPath.read<Number>(resposta, "$.id").toLong()
    }

    private fun entrar(email: String, senha: String): String {
        val resposta =
            mockMvc
                .perform(json("""{"email":"$email","senha":"$senha"}"""))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        return JsonPath.read<String>(resposta, "$.token")
    }

    private fun bearer(token: String) = "Bearer $token"

    @Test
    fun `login com senha errada ou e-mail inexistente devolve 401 com a mesma mensagem`() {
        cadastrar("ana@email.com", "52998224725")

        mockMvc
            .perform(json("""{"email":"ana@email.com","senha":"errada-errada"}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"))

        mockMvc
            .perform(json("""{"email":"ninguem@email.com","senha":"senha-forte-1"}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"))
    }

    @Test
    fun `login sem campos devolve 400`() {
        mockMvc.perform(json("{}")).andExpect(status().isBadRequest)
    }

    @Test
    fun `resposta do cadastro nao devolve a senha`() {
        val resposta =
            mockMvc
                .perform(
                    post("/participantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"nome":"Rui","email":"rui@email.com","cpf":"11144477735","senha":"senha-forte-1"}"""
                        )
                )
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .contentAsString
        assertEquals(false, resposta.contains("senha"))
    }

    @Test
    fun `participante logado ve eventos mas nao os administra`() {
        cadastrar("bia@email.com", "39053344705")
        val token = entrar("bia@email.com", "senha-forte-1")

        mockMvc
            .perform(get("/eventos").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk)

        mockMvc
            .perform(
                post("/eventos")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isForbidden)

        mockMvc
            .perform(
                patch("/inscricoes/1/status")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"CONFIRMADA"}""")
            )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `participante so acessa o proprio cadastro`() {
        val id = cadastrar("caio@email.com", "28264519016")
        val outroId = cadastrar("dina@email.com", "36148295709")
        val token = entrar("caio@email.com", "senha-forte-1")

        mockMvc
            .perform(get("/participantes/$id").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk)

        mockMvc
            .perform(get("/participantes/$outroId").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isForbidden)

        mockMvc
            .perform(get("/participantes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `organizador inicial entra, ve participantes e eventos`() {
        // O e-mail foi configurado com maiúsculas: o cadastro e o login normalizam para minúsculas.
        val token = entrar("CHEFE@email.com", "senha-do-chefe-1")

        mockMvc
            .perform(get("/participantes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk)
        mockMvc
            .perform(get("/eventos").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk)
    }

    @Test
    fun `token adulterado ou ausente devolve 401`() {
        cadastrar("eva@email.com", "12345678909")
        val token = entrar("eva@email.com", "senha-forte-1")
        val adulterado = token.dropLast(3) + "abc"

        mockMvc
            .perform(get("/eventos").header(HttpHeaders.AUTHORIZATION, bearer(adulterado)))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/eventos")).andExpect(status().isUnauthorized)
    }
}

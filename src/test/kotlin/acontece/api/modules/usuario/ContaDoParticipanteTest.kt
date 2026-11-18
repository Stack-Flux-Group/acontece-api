package acontece.api.modules.usuario

import acontece.api.modules.auth.ContaIndisponivelException.Companion.CONTA_EXCLUIDA
import acontece.api.modules.auth.ContaIndisponivelException.Companion.CONTA_INATIVA
import acontece.api.modules.evento.Endereco
import acontece.api.modules.evento.Evento
import acontece.api.modules.evento.EventoRepository
import com.jayway.jsonpath.JsonPath
import java.time.LocalDateTime
import kotlin.test.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Exclusão e inativação da conta pelo caminho real (filtro de JWT, banco H2): o que acontece com o
// login, com o token que a pessoa já tinha e com as inscrições.
@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:contadoparticipante;DB_CLOSE_DELAY=-1",
            "organizador.email=chefe@email.com",
            "organizador.senha=senha-do-chefe-1",
        ]
)
@AutoConfigureMockMvc
class ContaDoParticipanteTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var eventos: EventoRepository

    private fun cadastrar(email: String, cpf: String): Long {
        val resposta =
            mockMvc
                .perform(
                    post("/participantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"nome":"Maria Silva","email":"$email","cpf":"$cpf","senha":"senha-forte-1"}""")
                )
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .contentAsString
        return JsonPath.read<Number>(resposta, "$.id").toLong()
    }

    private fun login(email: String, senha: String = "senha-forte-1"): ResultActions =
        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"$senha"}""")
        )

    private fun entrar(email: String, senha: String = "senha-forte-1"): String {
        val resposta = login(email, senha).andExpect(status().isOk).andReturn().response.contentAsString
        return "Bearer " + JsonPath.read<String>(resposta, "$.token")
    }

    private fun alterarStatus(id: Long, status: String, organizador: String) =
        mockMvc
            .perform(
                patch("/participantes/$id/status")
                    .header(HttpHeaders.AUTHORIZATION, organizador)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"$status"}""")
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(status))

    private fun novoEvento() =
        eventos.save(
            Evento(
                titulo = "Semana de Tecnologia",
                descricao = "",
                dataInicio = LocalDateTime.now().plusDays(1),
                dataFim = LocalDateTime.now().plusDays(2),
                vagas = 10,
                endereco = Endereco("01001000", "Praça da Sé", "Sé", "São Paulo", "SP"),
            )
        )

    private fun inscrever(token: String, eventoId: Long): Long {
        val resposta =
            mockMvc
                .perform(
                    post("/inscricoes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"eventoId":$eventoId}""")
                )
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .contentAsString
        return JsonPath.read<Number>(resposta, "$.id").toLong()
    }

    // O bug: a inscrição cancelada continuava apontando para o participante e o DELETE dava 409.
    @Test
    fun `participante com inscricao cancelada consegue excluir a propria conta`() {
        val id = cadastrar("ana@email.com", "52998224725")
        val token = entrar("ana@email.com")
        val inscricaoId = inscrever(token, novoEvento().id!!)
        mockMvc
            .perform(patch("/inscricoes/$inscricaoId/cancelar").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk)

        mockMvc
            .perform(delete("/participantes/$id").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isNoContent)

        // O token de antes da exclusão para de valer na hora.
        mockMvc
            .perform(get("/eventos").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value(CONTA_EXCLUIDA))

        // O e-mail antigo não entra mais, e a mensagem não diferencia de um e-mail que nunca existiu.
        login("ana@email.com")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"))

        // Para o organizador, o participante sumiu; a inscrição antiga fica sem os dados pessoais.
        val organizador = entrar("chefe@email.com", "senha-do-chefe-1")
        mockMvc
            .perform(get("/participantes/$id").header(HttpHeaders.AUTHORIZATION, organizador))
            .andExpect(status().isNotFound)
        mockMvc
            .perform(get("/inscricoes/$inscricaoId").header(HttpHeaders.AUTHORIZATION, organizador))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participanteNome").value("Participante excluído"))
            .andExpect(jsonPath("$.status").value("CANCELADA"))

        // Os mesmos e-mail e CPF podem ser usados num cadastro novo.
        cadastrar("ana@email.com", "52998224725")
    }

    @Test
    fun `organizador exclui participante com inscricao ativa e a vaga volta`() {
        val id = cadastrar("bia@email.com", "39053344705")
        val evento = novoEvento()
        val inscricaoId = inscrever(entrar("bia@email.com"), evento.id!!)
        val organizador = entrar("chefe@email.com", "senha-do-chefe-1")

        mockMvc
            .perform(delete("/participantes/$id").header(HttpHeaders.AUTHORIZATION, organizador))
            .andExpect(status().isNoContent)

        mockMvc
            .perform(get("/inscricoes/$inscricaoId").header(HttpHeaders.AUTHORIZATION, organizador))
            .andExpect(jsonPath("$.status").value("CANCELADA"))
        mockMvc
            .perform(get("/eventos/${evento.id}").header(HttpHeaders.AUTHORIZATION, organizador))
            .andExpect(jsonPath("$.vagasDisponiveis").value(10))
    }

    @Test
    fun `conta inativa nao entra nem usa o token, e volta ao ser reativada`() {
        val id = cadastrar("caio@email.com", "28264519016")
        val tokenAntigo = entrar("caio@email.com")
        val organizador = entrar("chefe@email.com", "senha-do-chefe-1")

        alterarStatus(id, "INATIVO", organizador)

        mockMvc
            .perform(get("/me").header(HttpHeaders.AUTHORIZATION, tokenAntigo))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value(CONTA_INATIVA))

        // Com a senha certa, a pessoa fica sabendo o motivo.
        login("caio@email.com")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value(CONTA_INATIVA))
        // Com a senha errada, a resposta é a de sempre: não revela que a conta existe.
        login("caio@email.com", "senha-errada-1")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"))

        alterarStatus(id, "ATIVO", organizador)

        val tokenNovo = entrar("caio@email.com")
        mockMvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, tokenNovo)).andExpect(status().isOk)
    }

    @Test
    fun `token invalido continua dando 401 sem corpo`() {
        mockMvc
            .perform(get("/eventos").header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-um-token"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().string(""))
    }
}

package acontece.api.config

import com.jayway.jsonpath.JsonPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:docs;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
        ]
)
@AutoConfigureMockMvc
class DocumentacaoTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun docs(): String =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

    @Test
    fun `a documentacao abre sem token`() {
        docs()
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk)
    }

    @Test
    fun `documenta o esquema Bearer JWT e exige por padrao`() {
        val json = docs()
        assertEquals("bearer", JsonPath.read<String>(json, "$.components.securitySchemes.bearerAuth.scheme"))
        assertEquals("JWT", JsonPath.read<String>(json, "$.components.securitySchemes.bearerAuth.bearerFormat"))
        assertTrue(JsonPath.read<List<Any>>(json, "$.security").isNotEmpty())
    }

    @Test
    fun `login e cadastro aparecem sem cadeado`() {
        val json = docs()
        assertTrue(JsonPath.read<List<Any>>(json, "$.paths['/auth/login'].post.security").isEmpty())
        assertTrue(JsonPath.read<List<Any>>(json, "$.paths['/participantes'].post.security").isEmpty())
    }

    @Test
    fun `todas as rotas aparecem agrupadas por tag`() {
        val tags = JsonPath.read<List<String>>(docs(), "$.tags[*].name")
        assertTrue(tags.containsAll(listOf("Autenticação", "Participantes", "Eventos", "Inscrições")), "tags: $tags")
    }
}

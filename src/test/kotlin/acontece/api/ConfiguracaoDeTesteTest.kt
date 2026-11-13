package acontece.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Teste comum, sem Spring e sem banco: só lê o application.properties que os testes enxergam.
// Garante que nenhum teste (nem o contextLoads) consegue conectar no banco real do Supabase.
// O arquivo em src/test/resources substitui o de src/main/resources durante os testes.
class ConfiguracaoDeTesteTest {

    @Test
    fun `as propriedades vistas pelos testes apontam para o H2 e nunca para o Supabase`() {
        val recurso = checkNotNull(javaClass.getResource("/application.properties"))
        // Só as linhas de configuração: os comentários podem citar o Supabase para explicar o motivo.
        val texto = recurso.readText().lines().filterNot { it.trimStart().startsWith("#") }.joinToString("\n")

        // As mensagens não incluem o conteúdo do arquivo, para nunca vazar credenciais no relatório.
        assertTrue(texto.contains("jdbc:h2:mem"), "os testes devem usar H2 em memória")
        assertFalse(texto.contains("supabase", ignoreCase = true), "testes não podem citar o Supabase")
        assertFalse(texto.contains("postgresql", ignoreCase = true), "testes não podem usar Postgres")
    }
}

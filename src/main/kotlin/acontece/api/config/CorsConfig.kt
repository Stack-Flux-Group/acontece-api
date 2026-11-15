package acontece.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// Libera o front-end (outra origem) a chamar a API. As origens vêm de `cors.allowed-origins`
// (separadas por vírgula); o padrão já inclui o front na Vercel e o ambiente local.
@Configuration
class CorsConfig(
    @Value("\${cors.allowed-origins:http://localhost:3000,http://localhost:5173,https://acontece-frontend.vercel.app}")
    private val origensPermitidas: List<String>,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            .addMapping("/**")
            .allowedOrigins(*origensPermitidas.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600)
    }
}

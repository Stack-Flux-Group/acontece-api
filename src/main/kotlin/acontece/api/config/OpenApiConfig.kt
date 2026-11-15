package acontece.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Documentação em /swagger-ui.html (JSON em /v3/api-docs).
// O esquema "bearerAuth" cria o botão "Authorize": cole o token devolvido por POST /auth/login
// e o Swagger passa a mandar "Authorization: Bearer <token>" em todas as chamadas.
@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Acontece API")
                    .version("0.0.1")
                    .description(
                        """
                        API de eventos e inscrições.

                        **Como autenticar:** cadastre-se em `POST /participantes`, entre em
                        `POST /auth/login` e clique em **Authorize** com o token recebido.

                        **Papéis:** o participante vê eventos e se inscreve; o organizador cria,
                        edita e apaga eventos e gerencia as inscrições.
                        """
                            .trimIndent()
                    )
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        ESQUEMA_JWT,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Token JWT obtido em POST /auth/login"),
                    )
            )
            // Vale para todas as rotas; as públicas (login e cadastro) o desligam com
            // @SecurityRequirements() vazio.
            .addSecurityItem(SecurityRequirement().addList(ESQUEMA_JWT))

    private companion object {
        const val ESQUEMA_JWT = "bearerAuth"
    }
}

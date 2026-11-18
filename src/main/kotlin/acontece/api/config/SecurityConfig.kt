package acontece.api.config

import acontece.api.enums.StatusUsuario
import acontece.api.exception.ApiErrorResponse
import acontece.api.modules.auth.ContaIndisponivelException
import acontece.api.modules.usuario.UsuarioRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.json.JsonMapper

@Configuration
@EnableMethodSecurity // liga o @PreAuthorize usado nos controllers (regras "só o dono")
class SecurityConfig(
    // Para conferir, a cada requisição, se a conta do token ainda está ativa.
    private val usuarios: UsuarioRepository,
    private val jsonMapper: JsonMapper,
) {

    @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    // As regras são lidas de cima para baixo e vale a primeira que casar.
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() } // API stateless com token no header: não há cookie para forjar
            .cors(Customizer.withDefaults()) // usa a CorsConfig
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { rotas ->
                rotas
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/auth/login", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    // cadastro de participante é aberto: ainda não existe login para quem se cadastra
                    .requestMatchers(HttpMethod.POST, "/participantes").permitAll()
                    .requestMatchers(HttpMethod.GET, "/participantes").hasRole("ORGANIZADOR")
                    // ativar/inativar conta é só do organizador
                    .requestMatchers(HttpMethod.PATCH, "/participantes/*/status").hasRole("ORGANIZADOR")
                    // qualquer usuário logado vê eventos; só o organizador cria, edita e apaga
                    // (/evento/** são as rotas antigas, mantidas enquanto o app migra para /eventos)
                    .requestMatchers(HttpMethod.GET, "/eventos", "/eventos/**", "/evento/**").authenticated()
                    .requestMatchers("/eventos", "/eventos/**", "/evento/**").hasRole("ORGANIZADOR")
                    .requestMatchers(HttpMethod.POST, "/inscricoes").hasRole("PARTICIPANTE")
                    .requestMatchers(HttpMethod.PATCH, "/inscricoes/*/status").hasRole("ORGANIZADOR")
                    // o participante cancela a própria inscrição (o service confere se é dele)
                    .requestMatchers(HttpMethod.PATCH, "/inscricoes/*/cancelar").hasRole("PARTICIPANTE")
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.jwt { jwt -> jwt.jwtAuthenticationConverter(conversor()) }
                it.authenticationEntryPoint(respostaDe401())
            }
            .build()

    // Transforma o token (já com a assinatura e a validade conferidas) no usuário logado.
    // O token sozinho não sabe que a conta foi inativada ou excluída depois de emitido, então o status
    // é lido do banco a cada requisição (uma consulta pela chave primária). Sem isso, a conta
    // inativada continuaria usando a API até o token expirar.
    private fun conversor(): Converter<Jwt, AbstractAuthenticationToken> {
        // O papel vem na claim "papel" do token e vira a autoridade ROLE_<PAPEL> (usada por hasRole).
        val padrao =
            JwtAuthenticationConverter().apply {
                setJwtGrantedAuthoritiesConverter { jwt ->
                    listOf(SimpleGrantedAuthority("ROLE_${jwt.getClaimAsString("papel")}"))
                }
            }
        return Converter { jwt ->
            when (usuarios.buscarStatus(jwt.usuarioId)) {
                StatusUsuario.ATIVO -> padrao.convert(jwt)
                StatusUsuario.INATIVO ->
                    throw ContaIndisponivelException(ContaIndisponivelException.CONTA_INATIVA)
                // excluída ou apagada do banco
                else -> throw ContaIndisponivelException(ContaIndisponivelException.CONTA_EXCLUIDA)
            }
        }
    }

    // O 401 padrão (sem token, token vencido ou adulterado) vem sem corpo. Quando o motivo é a conta,
    // vai também o JSON de erro de sempre, com a mensagem para o front mostrar.
    private fun respostaDe401(): AuthenticationEntryPoint {
        val padrao = BearerTokenAuthenticationEntryPoint() // status 401 + cabeçalho WWW-Authenticate
        return AuthenticationEntryPoint { request, response, ex ->
            padrao.commence(request, response, ex)
            if (ex is ContaIndisponivelException) {
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                response.characterEncoding = Charsets.UTF_8.name()
                val status = HttpStatus.UNAUTHORIZED
                jsonMapper.writeValue(
                    response.outputStream,
                    ApiErrorResponse(
                        status = status.value(),
                        error = status.reasonPhrase,
                        message = ex.message ?: "Conta indisponível",
                        path = request.requestURI,
                    ),
                )
            }
        }
    }
}

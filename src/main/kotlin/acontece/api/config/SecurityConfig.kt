package acontece.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity // liga o @PreAuthorize usado nos controllers (regras "só o dono")
class SecurityConfig {

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
                    // qualquer usuário logado vê eventos; só o organizador cria, edita e apaga
                    // (/evento/** são as rotas antigas, mantidas enquanto o app migra para /eventos)
                    .requestMatchers(HttpMethod.GET, "/eventos", "/eventos/**", "/evento/**").authenticated()
                    .requestMatchers("/eventos", "/eventos/**", "/evento/**").hasRole("ORGANIZADOR")
                    .requestMatchers(HttpMethod.POST, "/inscricoes").hasRole("PARTICIPANTE")
                    .requestMatchers(HttpMethod.PATCH, "/inscricoes/*/status").hasRole("ORGANIZADOR")
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(conversor()) } }
            .build()

    // O papel vem na claim "papel" do token e vira a autoridade ROLE_<PAPEL> (usada por hasRole).
    private fun conversor() =
        JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter { jwt ->
                listOf(SimpleGrantedAuthority("ROLE_${jwt.getClaimAsString("papel")}"))
            }
        }
}

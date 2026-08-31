package com.blogzip.config

import com.blogzip.common.error.ErrorCode
import com.blogzip.common.error.ErrorResponseFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * 인증 설정. docs/decisions/002-auth-strategy.md
 *
 * 아직 JwtAuthenticationFilter가 없으므로 인증이 필요한 경로는 전부 차단된다.
 * 실제 인증 흐름은 auth 기능 구현 PR에서 붙인다.
 */
@Configuration
class SecurityConfig(
    private val errorResponses: ErrorResponseFactory,
    private val corsProperties: CorsProperties,
    /**
     * Security 필터에서 발생한 실패도 Spec의 에러 포맷으로 응답해야 한다.
     * 필터는 @RestControllerAdvice 밖이므로 직렬화를 직접 해야 하고,
     * Spring MVC가 쓰는 것과 같은 메시지 컨버터를 재사용해 응답 형태를 일치시킨다.
     * ObjectMapper를 직접 주입하지 않는 이유는 Boot 4가 Jackson 3을 쓰기 때문이다.
     */
    private val messageConverters: List<HttpMessageConverter<*>>,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint())
                it.accessDeniedHandler(accessDeniedHandler())
            }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.requestMatchers(*PUBLIC_PATHS).permitAll()
                it.anyRequest().authenticated()
            }
        return http.build()
    }

    /**
     * refreshToken을 HttpOnly 쿠키로 주고받으므로 credentials가 필요하다.
     * credentials를 허용하면 와일드카드 origin을 쓸 수 없다. 명시적으로 설정한다.
     */
    private fun corsConfigurationSource(): CorsConfigurationSource {
        require(corsProperties.allowedOrigins.none { it == "*" }) {
            "app.cors.allowed-origins에 와일드카드를 쓸 수 없다. credentials가 필요한 설정이다."
        }
        val config = CorsConfiguration().apply {
            allowedOrigins = corsProperties.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = CORS_MAX_AGE_SECONDS
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    private fun authenticationEntryPoint() = AuthenticationEntryPoint { _, response, _ ->
        writeError(response, ErrorCode.UNAUTHORIZED)
    }

    private fun accessDeniedHandler() = AccessDeniedHandler { _, response, _ ->
        writeError(response, ErrorCode.FORBIDDEN)
    }

    @Suppress("UNCHECKED_CAST")
    private fun writeError(
        response: jakarta.servlet.http.HttpServletResponse,
        errorCode: ErrorCode,
    ) {
        val body = errorResponses.build(errorCode).body ?: return
        response.status = errorCode.status
        val converter = messageConverters
            .firstOrNull { it.canWrite(body.javaClass, MediaType.APPLICATION_JSON) }
            ?: error("JSON을 쓸 수 있는 HttpMessageConverter가 없다")
        (converter as HttpMessageConverter<Any>)
            .write(body, MediaType.APPLICATION_JSON, ServletServerHttpResponse(response))
    }

    companion object {
        private const val BCRYPT_STRENGTH = 10
        private const val CORS_MAX_AGE_SECONDS = 3600L

        private val PUBLIC_PATHS = arrayOf(
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/token/refresh",
            "/actuator/health",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
        )
    }
}

@ConfigurationProperties(prefix = "app.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)

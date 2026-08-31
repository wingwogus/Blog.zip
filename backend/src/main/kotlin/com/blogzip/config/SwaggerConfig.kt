package com.blogzip.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * docs/decisions/010-api-response-contract.md
 *
 * 각 엔드포인트에 발생 가능한 에러 코드를 문서에 남긴다.
 * 프론트가 분기할 코드를 Swagger에서 확인할 수 있어야 한다.
 */
@Configuration
class SwaggerConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Blog.zip API")
                .description(
                    "친구가 어디에서 블로그를 쓰든, 한곳에서 계속 만날 수 있도록 한다.\n\n" +
                        "모든 응답은 `{success, data, error}` 래퍼로 감싼다. " +
                        "클라이언트 분기는 `error.code`로만 한다.",
                )
                .version("v0.1"),
        )
        .addSecurityItem(SecurityRequirement().addList(BEARER_SCHEME))
        .components(
            io.swagger.v3.oas.models.Components().addSecuritySchemes(
                BEARER_SCHEME,
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"),
            ),
        )

    companion object {
        private const val BEARER_SCHEME = "bearerAuth"
    }
}

package com.blogzip.auth.infra

import com.blogzip.config.AuthProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class AuthInfraConfig {

    @Bean
    fun accessJwtProvider(properties: AuthProperties, clock: Clock): AccessJwtProvider =
        AccessJwtProvider(properties, clock)
}

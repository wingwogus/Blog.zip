package com.blogzip.auth.config

import com.blogzip.auth.service.AccessJwtProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class AuthServiceProviderConfig {

    @Bean
    fun accessJwtProvider(properties: AuthProperties, clock: Clock): AccessJwtProvider =
        AccessJwtProvider(properties, clock)
}

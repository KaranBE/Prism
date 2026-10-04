package com.airtribe.prism.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI prismOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Prism Gateway API").version("v1")
                        .description("OpenAI-compatible LLM gateway with smart routing and semantic caching"))
                .addSecurityItem(new SecurityRequirement().addList("VirtualKey"))
                .schemaRequirement("VirtualKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization"));
    }
}

package com.srllc.spring_ai_app.aichat.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring AI Chat API")
                        .version("1.0")
                        .description("""
                                API Documentation for Mini Shopping System Project
                              
                                **Developers**
                                - Jhon Paul Malubag
                                """)
                        .contact(new Contact()
                                .name("Jhon Paul Malubag")
                                .email("malubagjp.srbootcamp2025@gmail.com")));
    }
}
package com.saturday.pet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PetCorsConfig implements WebMvcConfigurer {
    @Value("${app.web.allowed-origins:http://localhost:[*],http://127.0.0.1:[*]}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/pet/**").allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}

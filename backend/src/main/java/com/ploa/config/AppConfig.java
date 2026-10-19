package com.ploa.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PloaProperties.class)
public class AppConfig {
    // Central config: PloaProperties is injected wherever needed
}

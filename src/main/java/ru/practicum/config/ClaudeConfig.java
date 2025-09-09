package ru.practicum.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "claude")
public class ClaudeConfig {
    private String baseUrl;
    private String apiKey;
    private String model;
}

package ru.practicum.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llama")
public class LlamaConfig {
    private String baseUrl;
    private String apiKey;
    private String model;
}

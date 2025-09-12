package ru.practicum.client;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AiClientFactory {
    private final AnthropicClient anthropicClient;

    public AiClient getDefaultAiClient() {
        return anthropicClient;
    }
}

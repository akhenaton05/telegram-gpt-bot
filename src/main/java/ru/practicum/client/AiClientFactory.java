package ru.practicum.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.stereotype.Service;
import ru.practicum.config.AiModelsConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiClientFactory {

    private final AiModelsConfig aiConfig;
    private final CloseableHttpClient httpClient;

    // Кэш: "chatId:modelKey" → AiClient
    private final Map<String, AiClient> clientCache = new ConcurrentHashMap<>();

    public AiClient create(String modelKey, Long chatId) {
        String cacheKey = chatId + ":" + modelKey;
        return clientCache.computeIfAbsent(cacheKey, k -> buildClient(modelKey));
    }

    public void invalidateCache(Long chatId) {
        clientCache.keySet().removeIf(key -> key.startsWith(chatId + ":"));
        log.debug("Cache invalidated for chat {}", chatId);
    }

    private AiClient buildClient(String modelKey) {
        AiModelsConfig.Model model = aiConfig.getModel(modelKey);
        if (model == null) {
            throw new IllegalArgumentException("Model not found: " + modelKey);
        }

        String baseUrl = model.getBaseUrl() != null ? model.getBaseUrl() :
                aiConfig.getProvider(model.getProvider()).getBaseUrl();

        String apiKey = model.getApiKey() != null ? model.getApiKey() :
                aiConfig.getProvider(model.getProvider()).getApiKey();

        log.info("Creating client: {} with {} provider, model: {}", modelKey, model.getProvider(), model.getModelName());

        return switch (model.getProvider()) {
            case "openai"      -> new OpenAiClient(baseUrl, apiKey, model.getModelName(), httpClient);
            case "openrouter"  -> new OpenRouterClient(baseUrl, apiKey, model.getModelName(), httpClient);
            case "grok"        -> new GrokClient(baseUrl, apiKey, model.getModelName(), httpClient);
            case "perplexity"  -> new SonarClient(baseUrl, apiKey, model.getModelName(), httpClient);
            case "gemini"      -> new GeminiClient(baseUrl, apiKey, model.getModelName(), httpClient);
            case "deepseek"    -> new DeepSeekClient(baseUrl, apiKey, model.getModelName(), httpClient);
            case "groq"        -> new LlamaClient(baseUrl, apiKey, model.getModelName(), httpClient);
            case "anthropic"   -> new AnthropicClient(baseUrl, apiKey, model.getModelName(), httpClient);
            default -> throw new IllegalArgumentException("Unknown provider: " + model.getProvider());
        };
    }
}

package ru.practicum.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiModelsConfig {
    Map<String, Provider> providers = new HashMap<>();
    Map<String, Model> models = new HashMap<>();
    private String defaultModel;

    @Data
    public static class Provider {
        private String baseUrl;
        private String apiKey;
    }

    @Data
    public static class Model {
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private String displayName;
    }

    public Provider getProvider(String key) {
        return providers.get(key);
    }

    public Model getModel(String key) {
        Model model = models.get(key);
        if (model == null) {
            log.warn("Model not found: '{}', fallback to default '{}'", key, defaultModel);
            return models.get(defaultModel);
        }
        return model;
    }

    @PostConstruct
    public void validate() {
        if (defaultModel == null || !models.containsKey(defaultModel)) {
            throw new IllegalStateException(
                    "Default model '" + defaultModel + "' not defined in ai.models");
        }

        log.info("Loaded {} AI models. Default model: {}", models.size(), defaultModel);
    }
}

package ru.practicum.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;
import ru.practicum.config.ClaudeConfig;
import ru.practicum.config.ProxyConfig;
import ru.practicum.utils.MarkdownToHtmlConverter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AnthropicClient implements AiClient {
    private final ClaudeConfig claudeConfig;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final MarkdownToHtmlConverter markdownConverter;

    public AnthropicClient(ClaudeConfig claudeConfig, CloseableHttpClient httpClient) {
        this.claudeConfig = claudeConfig;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.markdownConverter = new MarkdownToHtmlConverter();
    }

    public String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history) {
        try {
            String requestBody = createClaudeImageRequestBody(userMessage, base64Image, history);
            String apiUrl = claudeConfig.getBaseUrl() + "/v1/messages";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending Claude image request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("x-api-key", claudeConfig.getApiKey());
            httpPost.setHeader("anthropic-version", "2023-06-01");

            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (response.getCode() == 200) {
                    return parseClaudeResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("Claude API quota exceeded: {}", responseBody);
                    return "Превышена квота Claude API. Пожалуйста, проверьте баланс в аккаунте Anthropic.";
                } else if (response.getCode() == 401) {
                    log.error("Claude API authentication error: {}", responseBody);
                    return "Ошибка авторизации Claude API. Проверьте API ключ Anthropic.";
                } else {
                    log.error("Claude API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "Ошибка Claude API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending image to Claude", e);
            return "Извините, произошла ошибка при отправке изображения: " + e.getMessage();
        }
    }

    public String sendTextMessage(String userMessage, List<Map<String, String>> history) {
        try {
            String requestBody = createClaudeRequestBody(userMessage, history);
            String apiUrl = claudeConfig.getBaseUrl() + "/v1/messages";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending Claude request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("x-api-key", claudeConfig.getApiKey());
            httpPost.setHeader("anthropic-version", claudeConfig.getApiVersion());

            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (response.getCode() == 200) {
                    return parseClaudeResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("Claude API quota exceeded: {}", responseBody);
                    return "Превышена квота Claude API. Пожалуйста, проверьте баланс в аккаунте Anthropic.";
                } else if (response.getCode() == 401) {
                    log.error("Claude API authentication error: {}", responseBody);
                    return "Ошибка авторизации Claude API. Проверьте API ключ Anthropic.";
                } else {
                    log.error("Claude API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "Ошибка Claude API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending message to Claude", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    private String createClaudeRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = claudeConfig.getModel();
        String systemPrompt = claudeConfig.getSystemPrompt();
        List<Map<String, String>> messages = new ArrayList<>(history);
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 1000);
        requestBody.put("system", systemPrompt);
        requestBody.put("messages", messages);

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using Claude model: {} for request with system prompt {}", model, systemPrompt);
        return json;
    }

    private String createClaudeImageRequestBody(String userMessage, String base64Image, List<Map<String, String>> history) throws Exception {
        String model = claudeConfig.getModel();
        String systemPrompt = claudeConfig.getSystemPrompt();

        // Добавляем историю
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Map<String, String> historyMessage : history) {
            messages.add(Map.of("role", historyMessage.get("role"), "content", historyMessage.get("content")));
        }

        // Добавляем текущее сообщение с изображением
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of(
                "type", "image",
                "source", Map.of(
                        "type", "base64",
                        "media_type", "image/jpeg",
                        "data", base64Image
                )
        ));
        content.add(Map.of("type", "text", "text", userMessage));

        messages.add(Map.of("role", "user", "content", content));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 1000);
        requestBody.put("system", systemPrompt);
        requestBody.put("messages", messages);

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using Claude model: {} for image request", model);
        return json;
    }

    private String parseClaudeResponse(String responseBody) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode content = jsonNode.get("content");
        if (content != null && content.isArray() && !content.isEmpty()) {
            JsonNode firstContent = content.get(0);
            if (firstContent.has("text")) {
                String result = firstContent.get("text").asText().trim();
                result = markdownConverter.convertMarkdownToTelegramHtml(result);
                log.debug("Received Claude response of length: {}", result.length());
                return result;
            }
        }
        log.warn("Could not parse Claude response: {}", responseBody);
        return "Извините, не удалось получить ответ от Claude";
    }

    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
                log.info("HTTP client closed");
            }
        } catch (Exception e) {
            log.error("Error closing HTTP client", e);
        }
    }
}
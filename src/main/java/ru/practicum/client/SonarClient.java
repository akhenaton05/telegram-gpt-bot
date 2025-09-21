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
import ru.practicum.config.SonarConfig;
import ru.practicum.utils.MarkdownToHtmlConverter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SonarClient implements AiClient {
    private final SonarConfig sonarConfig;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final MarkdownToHtmlConverter markdownConverter;

    public SonarClient(SonarConfig sonarConfig, CloseableHttpClient httpClient) {
        this.sonarConfig = sonarConfig;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.markdownConverter = new MarkdownToHtmlConverter();
    }

    @Override
    public String sendTextMessage(String userMessage, List<Map<String, String>> history) {
        try {
            String requestBody = createSonarRequestBody(userMessage, history);
            String apiUrl = sonarConfig.getBaseUrl() + "/chat/completions";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.info("Sending Sonar request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + sonarConfig.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received Sonar response: {}", responseBody);
                if (response.getCode() == 200) {
                    return parseSonarResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("Sonar API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота Sonar API. Проверьте баланс в аккаунте Perplexity.";
                } else if (response.getCode() == 401) {
                    log.error("Sonar API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации Sonar API. Проверьте API ключ Perplexity.";
                } else {
                    log.error("Sonar API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка Sonar API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending message to Sonar", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    @Override
    public String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history) {
        try {
            String requestBody = createSonarImageRequestBody(userMessage, base64Image, history);
            String apiUrl = sonarConfig.getBaseUrl() + "/chat/completions";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending Sonar image request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + sonarConfig.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received Sonar response: {}", responseBody);
                if (response.getCode() == 200) {
                    return parseSonarResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("Sonar API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота Sonar API. Проверьте баланс в аккаунте Perplexity.";
                } else if (response.getCode() == 401) {
                    log.error("Sonar API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации Sonar API. Проверьте API ключ Perplexity.";
                } else {
                    log.error("Sonar API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка Sonar API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending image to Sonar", e);
            return "Извините, произошла ошибка при отправке изображения: " + e.getMessage();
        }
    }

    // Perplexity требует строгого чередования user/assistant после system сообщений
    private List<Map<String, String>> fixPerplexityMessageStructure(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, String>> fixedMessages = new ArrayList<>();

        // Разделяем сообщения на system и диалоговые
        List<Map<String, String>> systemMessages = new ArrayList<>();
        List<Map<String, String>> conversationMessages = new ArrayList<>();

        for (Map<String, String> message : history) {
            String role = message.get("role");
            String content = message.get("content");

            // Пропускаем пустые сообщения
            if (content == null || content.trim().isEmpty()) {
                continue;
            }

            if ("system".equals(role)) {
                systemMessages.add(message);
            } else if ("user".equals(role) || "assistant".equals(role)) {
                conversationMessages.add(message);
            }
        }

        // Добавляем системные сообщения в начало
        fixedMessages.addAll(systemMessages);

        // Исправляем чередование user/assistant для диалога
        if (!conversationMessages.isEmpty()) {
            String expectedRole = "user"; // Диалог должен начинаться с user

            for (Map<String, String> message : conversationMessages) {
                String role = message.get("role");
                String content = message.get("content");

                if (expectedRole.equals(role)) {
                    // Роль соответствует ожидаемой - добавляем сообщение
                    fixedMessages.add(message);
                    expectedRole = expectedRole.equals("user") ? "assistant" : "user";
                } else if ("user".equals(role) && "assistant".equals(expectedRole)) {
                    // Пропущен assistant ответ - добавляем фиктивный
                    fixedMessages.add(Map.of("role", "assistant", "content", "Понял."));
                    fixedMessages.add(message);
                    expectedRole = "assistant";
                } else if ("assistant".equals(role) && "user".equals(expectedRole)) {
                    // Лишний assistant ответ - пропускаем
                    log.debug("Skipping redundant assistant message: {}", content.substring(0, Math.min(50, content.length())));
                    continue;
                }
            }
        }

        log.debug("Fixed message structure: {} -> {} messages", history.size(), fixedMessages.size());
        return fixedMessages;
    }

    private String createSonarRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = sonarConfig.getModel();
        List<Map<String, Object>> messages = new ArrayList<>();

        // Исправляем структуру сообщений для Perplexity API
        List<Map<String, String>> fixedHistory = fixPerplexityMessageStructure(history);

        // Добавляем исправленную историю
        for (Map<String, String> historyMessage : fixedHistory) {
            messages.add(Map.of("role", historyMessage.get("role"), "content", historyMessage.get("content")));
        }

        // Добавляем текущее сообщение пользователя
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 2000);
        requestBody.put("temperature", 0.7);

        String json = objectMapper.writeValueAsString(requestBody);
        log.info("Using Sonar model: {} for request with {} messages", model, messages.size());
        return json;
    }

    private String createSonarImageRequestBody(String userMessage, String base64Image, List<Map<String, String>> history) throws Exception {
        String model = sonarConfig.getModel();
        List<Map<String, Object>> messages = new ArrayList<>();

        // Исправляем структуру сообщений для Perplexity API
        List<Map<String, String>> fixedHistory = fixPerplexityMessageStructure(history);

        // Добавляем исправленную историю
        for (Map<String, String> historyMessage : fixedHistory) {
            messages.add(Map.of("role", historyMessage.get("role"), "content", historyMessage.get("content")));
        }

        // Создаем multimodal сообщение с изображением и текстом
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:image/jpeg;base64," + base64Image)
        ));
        content.add(Map.of("type", "text", "text", userMessage));

        messages.add(Map.of("role", "user", "content", content));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 1000);
        requestBody.put("temperature", 0.7);

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using Sonar model: {} for image request with {} messages", model, messages.size());
        return json;
    }

    private String parseSonarResponse(String responseBody) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode choices = jsonNode.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message != null && message.has("content")) {
                String result = message.get("content").asText().trim();
                result = markdownConverter.convertMarkdownToTelegramHtml(result);
                log.debug("Received Sonar response of length: {}", result.length());
                return result;
            }
        }
        log.warn("Could not parse Sonar response: {}", responseBody);
        return "Извините, не удалось получить ответ от Sonar";
    }

    @Override
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

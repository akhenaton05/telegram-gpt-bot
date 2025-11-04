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
import ru.practicum.config.OpenRouterConfig;
import ru.practicum.utils.MarkdownToHtmlConverter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class OpenRouterClient implements AiClient {
    private final OpenRouterConfig openRouterConfig;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final MarkdownToHtmlConverter markdownConverter;

    public OpenRouterClient(OpenRouterConfig openRouterConfig, CloseableHttpClient httpClient) {
        this.openRouterConfig = openRouterConfig;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.markdownConverter = new MarkdownToHtmlConverter();
    }

    @Override
    public String sendTextMessage(String userMessage, List<Map<String, String>> history) {
        try {
            String requestBody = createOpenAiRequestBody(userMessage, history);
            String apiUrl = openRouterConfig.getBaseUrl();
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending OpenRouter request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + openRouterConfig.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received OpenRouter response: {}", responseBody);
                if (response.getCode() == 200) {
                    return parseOpenRouterResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("OpenRouter API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота OpenRouter API. Проверьте баланс в аккаунте OpenAI.";
                } else if (response.getCode() == 401) {
                    log.error("OpenRouter API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации OpenAI API. Проверьте API ключ OpenAI.";
                } else {
                    log.error("OpenRouter API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка OpenRouter API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending message to OpenRouter", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    @Override
    public String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history) {
        try {
            String requestBody = createOpenAiImageRequestBody(userMessage, base64Image, history);
            String apiUrl = openRouterConfig.getBaseUrl();
            HttpPost httpPost = new HttpPost(apiUrl);
            log.debug("Sending OpenRouter image request to: {}", apiUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + openRouterConfig.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received OpenRouter response: {}", responseBody);
                if (response.getCode() == 200) {
                    return parseOpenRouterResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("OpenRouter API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота OpenRouter API. Проверьте баланс в аккаунте OpenAI.";
                } else if (response.getCode() == 401) {
                    log.error("OpenRouter API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации OpenRouter API. Проверьте API ключ OpenAI.";
                } else {
                    log.error("OpenRouter API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка OpenRouter API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending image to OpenRouter", e);
            return "Извините, произошла ошибка при отправке изображения: " + e.getMessage();
        }
    }

    private String createOpenAiRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = openRouterConfig.getModel();
        List<Map<String, Object>> plugins = new ArrayList<>();

        plugins.add(Map.of("id", "web", "engine", "exa",  "max_results", 1));

        List<Map<String, String>> messages = new ArrayList<>();

        // Добавляем системное сообщение с текущей датой
        LocalDate now = LocalDate.now();
        String systemPrompt = String.format(
                "Текущая дата: %s.",
                now.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("ru")))
        );
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // Добавляем историю
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }

        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("plugins", plugins);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 3000);

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using {} model for request", model);
        return json;
    }

    private String createOpenAiImageRequestBody(String userMessage, String base64Image, List<Map<String, String>> history) throws Exception {
        String model = openRouterConfig.getModel();
        List<Map<String, Object>> plugins = new ArrayList<>();

        plugins.add(Map.of("id", "web", "engine", "exa",  "max_results", 1));

        List<Map<String, Object>> messages = new ArrayList<>();

        // Добавляем системное сообщение с текущей датой
        LocalDate now = LocalDate.now();
        String systemPrompt = String.format(
                "Текущая дата: %s. Ты умный помощник, который помогает пользователям с их вопросами и анализирует изображения.",
                now.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("ru")))
        );
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // Добавляем историю
        if (history != null) {
            for (Map<String, String> historyMessage : history) {
                messages.add(Map.of("role", historyMessage.get("role"), "content", historyMessage.get("content")));
            }
        }

        // Добавляем текущее сообщение с изображением
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:image/jpeg;base64," + base64Image)
        ));
        content.add(Map.of("type", "text", "text", userMessage));
        messages.add(Map.of("role", "user", "content", content));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("plugins", plugins);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 3000);

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using OpenAI model: {} for image request", model);
        return json;
    }

    private String parseOpenRouterResponse(String responseBody) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode choices = jsonNode.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message != null && message.has("content")) {
                String result = message.get("content").asText().trim();
                result = markdownConverter.convertMarkdownToTelegramHtml(result);
                log.debug("Received OpenRouter response of length: {}", result.length());
                return result;
            }
        }
        log.warn("Could not parse OpenRouter response: {}", responseBody);
        return "Извините, не удалось получить ответ от OpenRouter";
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
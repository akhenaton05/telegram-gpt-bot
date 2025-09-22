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
import ru.practicum.config.DeepSeekConfig;
import ru.practicum.config.OpenAiConfig;
import ru.practicum.utils.MarkdownToHtmlConverter;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class DeepSeekClient implements AiClient {
    private final DeepSeekConfig deepSeekConfig;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final MarkdownToHtmlConverter markdownConverter;

    public DeepSeekClient(DeepSeekConfig deepSeekConfig, CloseableHttpClient httpClient) {
        this.deepSeekConfig = deepSeekConfig;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.markdownConverter = new MarkdownToHtmlConverter();
    }

    @Override
    public String sendTextMessage(String userMessage, List<Map<String, String>> history) {
        try {
            String requestBody = createDeepSeekTextBody(userMessage, history);
            String apiUrl = deepSeekConfig.getBaseUrl() + "/chat/completions";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending DeepSeek request to: {}", apiUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + deepSeekConfig.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received DeepSeek response: {}", responseBody);
                if (response.getCode() == 200) {
                    return parseDeepSeekResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("DeepSeek API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота DeepSeek API. Проверьте баланс в аккаунте DeepSeek.";
                } else if (response.getCode() == 401) {
                    log.error("DeepSeek API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации DeepSeek API. Проверьте API ключ.";
                } else {
                    log.error("DeepSeek API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка DeepSeek API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending message to DeepSeek", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    @Override
    public String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history) {
        try {
            String requestBody = createDeepSeekImageBody(userMessage, base64Image, history);
            String apiUrl = deepSeekConfig.getBaseUrl() + "/chat/completions";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending DeepSeek image request to: {}", apiUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + deepSeekConfig.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received DeepSeek response: {}", responseBody);
                if (response.getCode() == 200) {
                    return parseDeepSeekResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("DeepSeek API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота DeepSeek API. Проверьте баланс в аккаунте DeepSeek.";
                } else if (response.getCode() == 401) {
                    log.error("DeepSeek API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации DeepSeek API. Проверьте API ключ.";
                } else {
                    log.error("DeepSeek API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка DeepSeek API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending image to DeepSeek", e);
            return "Извините, произошла ошибка при отправке изображения: " + e.getMessage();
        }
    }

    private String createDeepSeekTextBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = deepSeekConfig.getModel();
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 3000);

        String json = objectMapper.writeValueAsString(body);
        log.debug("Using DeepSeek model: {} for request", model);
        return json;
    }

    private String createDeepSeekImageBody(String userMessage, String base64Image, List<Map<String, String>> history) throws Exception {
        String model = deepSeekConfig.getModel();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "Ты дружелюбный ассистент, отвечай кратко и на русском."));
        for (Map<String, String> h : history) {
            messages.add(Map.of("role", h.get("role"), "content", h.get("content")));
        }
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", userMessage));
        content.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:image/jpeg;base64," + base64Image)
        ));
        messages.add(Map.of("role", "user", "content", content));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 3000);

        String json = objectMapper.writeValueAsString(body);
        log.debug("Using DeepSeek model: {} for image request", model);
        return json;
    }

    private String parseDeepSeekResponse(String responseBody) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode choices = jsonNode.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).get("message");
            if (message != null && message.has("content")) {
                String result = message.get("content").asText().trim();
                result = markdownConverter.convertMarkdownToTelegramHtml(result);
                log.debug("Received DeepSeek response of length: {}", result.length());
                return result;
            }
        }
        log.warn("Could not parse DeepSeek response: {}", responseBody);
        return "Извините, не удалось получить ответ от DeepSeek";
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

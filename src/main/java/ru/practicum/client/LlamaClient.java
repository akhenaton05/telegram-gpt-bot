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
import ru.practicum.config.LlamaConfig;
import ru.practicum.utils.MarkdownToHtmlConverter;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class LlamaClient implements AiClient {
    private final LlamaConfig llamaConfig;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final MarkdownToHtmlConverter markdownConverter;

    public LlamaClient(LlamaConfig llamaConfig, CloseableHttpClient httpClient) {
        this.llamaConfig = llamaConfig;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.markdownConverter = new MarkdownToHtmlConverter();
    }

    @Override
    public String sendTextMessage(String userMessage, List<Map<String, String>> history) {
        try {
            String requestBody = createLlamaRequestBody(userMessage, history);
            String apiUrl = llamaConfig.getBaseUrl() + "/chat/completions";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending Llama request to: {}", apiUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + llamaConfig.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received Llama response: {}", responseBody);
                if (response.getCode() == 200) {
                    return parseLlamaResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("Llama API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота Llama API. Проверьте баланс и лимиты.";
                } else if (response.getCode() == 401) {
                    log.error("Llama API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации Llama API. Проверьте API ключ.";
                } else {
                    log.error("Llama API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка Llama API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending message to Llama", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    @Override
    public String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history) {
        String hint = "ℹ️ Текущая Llama‑модель в chat/completions не обрабатывает изображения; используйте vision‑модель или Responses API при необходимости мультимодальности.";
        log.warn(hint);
        return hint;
    }

    private String createLlamaRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = llamaConfig.getModel(); // например, "llama-3.3-70b-versatile"
        List<Map<String, Object>> messages = new ArrayList<>();
        if (history != null) {
            for (Map<String, String> h : history) {
                messages.add(Map.of("role", h.get("role"), "content", h.get("content")));
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_completion_tokens", 3000);
        body.put("temperature", 0.7);

        String json = objectMapper.writeValueAsString(body);
        log.debug("Using Llama model: {} for request", model);
        return json;
    }

    private String parseLlamaResponse(String responseBody) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode choices = jsonNode.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).get("message");
            if (message != null && message.has("content")) {
                String result = message.get("content").asText().trim();
                result = markdownConverter.convertMarkdownToTelegramHtml(result);
                log.debug("Received Llama parsed response of length: {}", result.length());
                return result;
            }
        }
        log.warn("Could not parse Llama response: {}", responseBody);
        return "Извините, не удалось получить ответ от Llama";
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

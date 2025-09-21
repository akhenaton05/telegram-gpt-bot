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
import ru.practicum.config.OpenAiConfig;
import ru.practicum.utils.MarkdownToHtmlConverter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiClient implements AiClient {
    private final OpenAiConfig openAiConfig;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final MarkdownToHtmlConverter markdownConverter;

    public OpenAiClient(OpenAiConfig openAiConfig, CloseableHttpClient httpClient) {
        this.openAiConfig = openAiConfig;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.markdownConverter = new MarkdownToHtmlConverter();
    }

    @Override
    public String sendTextMessage(String userMessage, List<Map<String, String>> history) {
        try {
            String requestBody = createOpenAiRequestBody(userMessage, history);
            String apiUrl = openAiConfig.getBaseUrl() + "/chat/completions";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending OpenAI request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + openAiConfig.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received OpenAI response: {}", responseBody);
                if (response.getCode() == 200) {
                    return parseOpenAiResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("OpenAI API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота OpenAI API. Проверьте баланс в аккаунте OpenAI.";
                } else if (response.getCode() == 401) {
                    log.error("OpenAI API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации OpenAI API. Проверьте API ключ OpenAI.";
                } else {
                    log.error("OpenAI API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка OpenAI API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending message to OpenAI", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    @Override
    public String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history) {
        try {
            String requestBody = createOpenAiImageRequestBody(userMessage, base64Image, history);
            String apiUrl = openAiConfig.getBaseUrl() + "/chat/completions";
            HttpPost httpPost = new HttpPost(apiUrl);
            log.debug("Sending OpenAI image request to: {}", apiUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + openAiConfig.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received OpenAI response: {}", responseBody);
                if (response.getCode() == 200) {
                    return parseOpenAiResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("OpenAI API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота OpenAI API. Проверьте баланс в аккаунте OpenAI.";
                } else if (response.getCode() == 401) {
                    log.error("OpenAI API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации OpenAI API. Проверьте API ключ OpenAI.";
                } else {
                    log.error("OpenAI API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка OpenAI API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending image to OpenAI", e);
            return "Извините, произошла ошибка при отправке изображения: " + e.getMessage();
        }
    }

    private String createOpenAiRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = openAiConfig.getModel();
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userMessage));
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        if (model.equals("gpt-5-nano")) {
            requestBody.put("max_completion_tokens", 3000); // Для OpenAI всегда max_tokens
        } else {
            requestBody.put("max_tokens", 3000);
        }
        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using OpenAI model: {} for request", model);
        return json;
    }

    private String createOpenAiImageRequestBody(String userMessage, String base64Image, List<Map<String, String>> history) throws Exception {
        String model = openAiConfig.getModel();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "Ты дружелюбный ассистент, отвечай кратко и на русском."));
        for (Map<String, String> historyMessage : history) {
            messages.add(Map.of("role", historyMessage.get("role"), "content", historyMessage.get("content")));
        }
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
        requestBody.put("max_tokens", 3000);
        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using OpenAI model: {} for image request", model);
        return json;
    }

    private String parseOpenAiResponse(String responseBody) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode choices = jsonNode.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message != null && message.has("content")) {
                String result = message.get("content").asText().trim();
                result = markdownConverter.convertMarkdownToTelegramHtml(result);
                log.debug("Received OpenAI response of length: {}", result.length());
                return result;
            }
        }
        log.warn("Could not parse OpenAI response: {}", responseBody);
        return "Извините, не удалось получить ответ от OpenAI";
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

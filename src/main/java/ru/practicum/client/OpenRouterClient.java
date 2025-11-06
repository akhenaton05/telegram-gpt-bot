package ru.practicum.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import ru.practicum.dto.OpenRouterDto;
import ru.practicum.utils.MarkdownToHtmlConverter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class OpenRouterClient implements AiTextSender, AiImageSender {
    private final OpenRouterDto dto;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final MarkdownToHtmlConverter markdownConverter;

    public OpenRouterClient(String baseUrl, String apiKey, String modelName, CloseableHttpClient httpClient) {
        this.dto = new OpenRouterDto();
        this.dto.setBaseUrl(baseUrl);
        this.dto.setApiKey(apiKey);
        this.dto.setModel(modelName);

        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.markdownConverter = new MarkdownToHtmlConverter();
    }

    @Override
    public String sendTextMessage(String userMessage, List<Map<String, String>> history) {
        try {
            String requestBody = createOpenRouterRequestBody(userMessage, history);
            String apiUrl = dto.getBaseUrl();
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending OpenRouter request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + dto.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            return executeRequest(httpPost);
        } catch (Exception e) {
            log.error("Error sending message to OpenRouter", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    @Override
    public String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history) {
        try {
            String requestBody = createOpenRouterImageRequestBody(userMessage, base64Image, history);
            String apiUrl = dto.getBaseUrl();
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending OpenRouter image request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + dto.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            return executeRequest(httpPost);
        } catch (Exception e) {
            log.error("Error sending image to OpenRouter", e);
            return "Извините, произошла ошибка при отправке изображения: " + e.getMessage();
        }
    }

    private String createOpenRouterRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = dto.getModel();
        List<Map<String, Object>> plugins = new ArrayList<>();

        plugins.add(Map.of("id", "web", "engine", "exa", "max_results", 1));

        List<Map<String, String>> messages = new ArrayList<>();

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

    private String createOpenRouterImageRequestBody(String userMessage, String base64Image, List<Map<String, String>> history) throws Exception {
        String model = dto.getModel();
        List<Map<String, Object>> plugins = new ArrayList<>();

        plugins.add(Map.of("id", "web", "engine", "exa", "max_results", 1));

        List<Map<String, Object>> messages = new ArrayList<>();

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
        log.debug("Using OpenRouter model: {} for image request", model);
        return json;
    }

    private String executeRequest(HttpPost httpPost) {
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            log.debug("Received OpenRouter response: {}", responseBody);

            int status = response.getCode();
            return switch (status) {
                case 200 -> parseOpenRouterResponse(responseBody);
                case 429 -> "OpenRouter quota exceeded. Check account balance.";
                case 401 -> "Authorisation error OpenRouter API. Check API key.";
                default -> "OpenRouter Error (code: " + response.getCode() + "). Try again later.";
            };
        } catch (Exception e) {
            log.error("Error sending image to OpenRouter", e);
            return "Error sending request to OpenRouter: " + e.getMessage();
        }
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
}
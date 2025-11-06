package ru.practicum.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import ru.practicum.dto.ClaudeDto;
import ru.practicum.utils.MarkdownToHtmlConverter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class AnthropicClient implements AiTextSender, AiImageSender {
    private final ClaudeDto dto;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final MarkdownToHtmlConverter markdownConverter;

    public AnthropicClient(String baseUrl, String apiKey, String modelName, CloseableHttpClient httpClient) {
        this.dto = new ClaudeDto();
        this.dto.setBaseUrl(baseUrl);
        this.dto.setApiKey(apiKey);
        this.dto.setModel(modelName);

        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.markdownConverter = new MarkdownToHtmlConverter();
    }

    @Override
    public String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history) {
        try {
            String requestBody = createClaudeImageRequestBody(userMessage, base64Image, history);
            String apiUrl = dto.getBaseUrl() + "/v1/messages";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending Claude image request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("x-api-key", dto.getApiKey());
            httpPost.setHeader("anthropic-version", "2023-06-01");

            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            return executeRequest(httpPost);
        } catch (Exception e) {
            log.error("Error sending image to Claude", e);
            return "Извините, произошла ошибка при отправке изображения: " + e.getMessage();
        }
    }

    public String sendTextMessage(String userMessage, List<Map<String, String>> history) {
        try {
            String requestBody = createClaudeRequestBody(userMessage, history);
            String apiUrl = dto.getBaseUrl() + "/v1/messages";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending Claude request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("x-api-key", dto.getApiKey());

            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            return executeRequest(httpPost);
        } catch (Exception e) {
            log.error("Error sending message to Claude", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    private String createClaudeRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = dto.getModel();
        List<Map<String, String>> messages = new ArrayList<>(history);
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 1000);
        requestBody.put("messages", messages);

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using Claude model: {} for request", model);
        return json;
    }

    private String createClaudeImageRequestBody(String userMessage, String base64Image, List<Map<String, String>> history) throws Exception {
        String model = dto.getModel();

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
        requestBody.put("messages", messages);

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using Claude model: {} for image request", model);
        return json;
    }

    private String executeRequest(HttpPost httpPost) {
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            log.debug("Received Claude response: {}", responseBody);

            int status = response.getCode();
            return switch (status) {
                case 200 -> parseClaudeResponse(responseBody);
                case 429 -> "Claude API quota exceeded.";
                case 401 -> "Claude API authentication error. Check API key.";
                default -> "Claude API error. Try again later";
            };
        } catch (Exception e) {
            log.error("Error sending request to Claude", e);
            return "Error sending request to Claude: " + e.getMessage();
        }
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
}
package ru.practicum.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import ru.practicum.dto.GrokDto;
import ru.practicum.utils.MarkdownToHtmlConverter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class GrokClient implements AiTextSender {

    private final GrokDto dto;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final MarkdownToHtmlConverter markdownConverter;

    private static final int MAX_TOKENS_GROK_4 = 16384;
    private static final int MAX_TOKENS_GROK_3 = 8192;

    public GrokClient(String baseUrl, String apiKey, String modelName, CloseableHttpClient httpClient) {
        this.dto = new GrokDto();
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
            String requestBody = createRequestBody(userMessage, history);
            String apiUrl = dto.getBaseUrl();
            HttpPost httpPost = new HttpPost(apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + dto.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            return executeRequest(httpPost);
        } catch (Exception e) {
            log.error("Error in GrokClient", e);
            return "Ошибка: " + e.getMessage();
        }
    }

    private int getMaxTokens() {
        return dto.getModel().toLowerCase().contains("4") ? MAX_TOKENS_GROK_4 : MAX_TOKENS_GROK_3;
    }

    private String createRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Map<String, String> msg : history) {
            messages.add(Map.of("role", msg.get("role"), "content", msg.get("content")));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("model", dto.getModel());
        body.put("messages", messages);
        body.put("max_tokens", getMaxTokens());
        body.put("temperature", 0.7);

        return objectMapper.writeValueAsString(body);
    }

    private String executeRequest(HttpPost httpPost) {
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            log.debug("Received Grok response: {}", responseBody);

            int status = response.getCode();
            return switch (status) {
                case 200 -> parseResponse(responseBody);
                case 429 -> "Grok quota exceeded. Check account balance.";
                case 401 -> "Authorisation error Grok API. Check API key.";
                default -> "Grok Error (code: " + response.getCode() + "). Try again later.";
            };
        } catch (Exception e) {
            log.error("Error sending image to DeepSeek", e);
            return "Error sending request to DeepSeek: " + e.getMessage();
        }
    }

    private String parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");

        if (choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).path("message");
            if (message.has("content")) {
                String result = message.get("content").asText().trim();
                result = markdownConverter.convertMarkdownToTelegramHtml(result);
                log.debug("Grok response length: {}", result.length());
                return result;
            }
        }

        log.warn("Failed to parse Grok response: {}", responseBody);
        return "Извините, не удалось получить ответ от Grok.";
    }
}

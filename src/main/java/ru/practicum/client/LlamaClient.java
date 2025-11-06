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
import ru.practicum.dto.LlamaDto;
import ru.practicum.utils.MarkdownToHtmlConverter;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class LlamaClient implements AiTextSender {
    private final LlamaDto dto;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final MarkdownToHtmlConverter markdownConverter;

    public LlamaClient(String baseUrl, String apiKey, String modelName, CloseableHttpClient httpClient) {
        this.dto = new LlamaDto();
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
            String requestBody = createLlamaRequestBody(userMessage, history);
            String apiUrl = dto.getBaseUrl();
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending Llama request to: {}", apiUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + dto.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            return executeRequest(httpPost);
        } catch (Exception e) {
            log.error("Error sending message to Llama", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    private String createLlamaRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = dto.getModel();
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

    private String executeRequest(HttpPost httpPost) {
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            log.debug("Received Llama response: {}", responseBody);

            int status = response.getCode();
            return switch (status) {
                case 200 -> parseLlamaResponse(responseBody);
                case 429 -> "Llama quota exceeded. Check account balance.";
                case 401 -> "Authorisation error Llama API. Check API key.";
                default -> "Llama Error (code: " + response.getCode() + "). Try again later.";
            };
        } catch (Exception e) {
            log.error("Error sending image to LLama", e);
            return "Error sending request to Llama: " + e.getMessage();
        }
    }

    private String parseLlamaResponse(String responseBody) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode choices = jsonNode.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
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
}

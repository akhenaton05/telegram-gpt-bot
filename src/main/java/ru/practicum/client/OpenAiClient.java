package ru.practicum.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import ru.practicum.dto.OpenAiDto;
import ru.practicum.utils.MarkdownToHtmlConverter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class OpenAiClient implements AiTextSender, AiImageSender {
    private final OpenAiDto dto;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final MarkdownToHtmlConverter markdownConverter;

    public OpenAiClient(String baseUrl, String apiKey, String modelName, CloseableHttpClient httpClient) {
        this.dto = new OpenAiDto();
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
            String requestBody = createOpenAiRequestBody(userMessage, history);
            String apiUrl = dto.getBaseUrl();
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending OpenAI request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + dto.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            return executeRequest(httpPost);
        } catch (Exception e) {
            log.error("Error sending message to OpenAI", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    @Override
    public String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history) {
        try {
            String requestBody = createOpenAiImageRequestBody(userMessage, base64Image, history);
            String apiUrl = dto.getBaseUrl() + "/chat/completions";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending OpenAI image request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + dto.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            return executeRequest(httpPost);
        } catch (Exception e) {
            log.error("Error sending image to OpenAI", e);
            return "Извините, произошла ошибка при отправке изображения: " + e.getMessage();
        }
    }

    private String createOpenAiRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = dto.getModel();
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("input", userMessage);
        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using OpenAI model: {} for request", model);
        return json;
    }

    private String createOpenAiImageRequestBody(String userMessage, String base64Image, List<Map<String, String>> history) throws Exception {
        String model = dto.getModel();
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

    private String executeRequest(HttpPost httpPost) {
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            log.debug("Received OpenAi response: {}", responseBody);

            int status = response.getCode();
            return switch (status) {
                case 200 -> parseOpenAiResponse(responseBody);
                case 429 -> "OpenAi quota exceeded. Check account balance.";
                case 401 -> "Authorisation error OpenAi API. Check API key.";
                default -> "OpenAi Error (code: " + response.getCode() + "). Try again later.";
            };
        } catch (Exception e) {
            log.error("Error sending image to OpenAi", e);
            return "Error sending request to OpenAi: " + e.getMessage();
        }
    }

    private String parseOpenAiResponse(String responseBody) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);

//        // проверяем наличие output_text (удобное поле)
//        if (jsonNode.has("output_text")) {
//            String result = jsonNode.get("output_text").asText().trim();
//            result = markdownConverter.convertMarkdownToTelegramHtml(result);
//            log.debug("Received OpenAI response of length: {}", result.length());
//            return result;
//        }

        // парсим output массив вручную
        JsonNode output = jsonNode.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                // Ищем элемент с типом "message"
                if (item.has("type") && "message".equals(item.get("type").asText())) {
                    JsonNode content = item.get("content");
                    if (content != null && content.isArray() && !content.isEmpty()) {
                        for (JsonNode contentItem : content) {
                            // Ищем элемент с типом "output_text"
                            if (contentItem.has("type") && "output_text".equals(contentItem.get("type").asText())) {
                                String result = contentItem.get("text").asText().trim();
                                result = markdownConverter.convertMarkdownToTelegramHtml(result);
                                log.debug("Received OpenAI response of length: {}", result.length());
                                return result;
                            }
                        }
                    }
                }
            }
        }

        log.warn("Could not parse OpenAI response: {}", responseBody);
        return "Извините, не удалось получить ответ от OpenAI";
    }
}

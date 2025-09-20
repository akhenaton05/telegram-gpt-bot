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
import ru.practicum.config.GeminiConfig;
import ru.practicum.config.ProxyConfig;
import ru.practicum.utils.MarkdownToHtmlConverter;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class GeminiClient implements AiClient, AutoCloseable {
    private final GeminiConfig geminiConfig;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final MarkdownToHtmlConverter markdownConverter;

    private static final int MAX_OUTPUT_TOKENS_FLASH = 8192;
    private static final int MAX_OUTPUT_TOKENS_PRO = 32000;

    public GeminiClient(GeminiConfig geminiConfig, ProxyConfig proxyConfig) {
        this.geminiConfig = geminiConfig;
        this.objectMapper = new ObjectMapper();
        this.httpClient = proxyConfig.createHttpClient();
        this.markdownConverter = new MarkdownToHtmlConverter();
    }

    @Override
    public String sendTextMessage(String userMessage, List<Map<String, String>> history) {
        try {
            String requestBody = createGeminiRequestBody(userMessage, history);
            String apiUrl = geminiConfig.getBaseUrl()
                    + "/models/" + geminiConfig.getModel()
                    + ":generateContent?key=" + geminiConfig.getApiKey();

            HttpPost httpPost = new HttpPost(apiUrl);
            log.debug("Sending Gemini request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received Gemini response: {}", responseBody);

                if (response.getCode() == 200) {
                    return parseGeminiResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("Gemini API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота Gemini API. Проверьте баланс в Google AI Studio.";
                } else if (response.getCode() == 401) {
                    log.error("Gemini API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации Gemini API. Проверьте API ключ Google.";
                } else {
                    log.error("Gemini API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка Gemini API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending message to Gemini", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    @Override
    public String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history) {
        try {
            String requestBody = createGeminiImageRequestBody(userMessage, base64Image, history);
            String apiUrl = geminiConfig.getBaseUrl()
                    + "/models/" + geminiConfig.getModel()
                    + ":generateContent?key=" + geminiConfig.getApiKey();

            HttpPost httpPost = new HttpPost(apiUrl);
            log.debug("Sending Gemini image request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received Gemini response: {}", responseBody);

                if (response.getCode() == 200) {
                    return parseGeminiResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("Gemini API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота Gemini API. Проверьте баланс в Google AI Studio.";
                } else if (response.getCode() == 401) {
                    log.error("Gemini API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации Gemini API. Проверьте API ключ Google.";
                } else {
                    log.error("Gemini API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка Gemini API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending image to Gemini", e);
            return "Извините, произошла ошибка при отправке изображения: " + e.getMessage();
        }
    }

    private int getMaxOutputTokens() {
        String model = geminiConfig.getModel().toLowerCase();
        return (model.contains("pro"))
                ? MAX_OUTPUT_TOKENS_PRO
                : MAX_OUTPUT_TOKENS_FLASH;
    }

    private String createGeminiRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = geminiConfig.getModel();
        List<Map<String, Object>> contents = new ArrayList<>();

        // История с правильными ролями
        if (history != null) {
            for (Map<String, String> historyMessage : history) {
                String role = historyMessage.get("role");
                // Конвертируем assistant в model для Gemini API
                if ("assistant".equals(role)) {
                    role = "model";
                }
                contents.add(Map.of("role", role, "parts", List.of(Map.of("text", historyMessage.get("content")))));
            }
        }

        // Текущее сообщение пользователя
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", userMessage))));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", contents);
        requestBody.put("generationConfig", Map.of(
                "maxOutputTokens", getMaxOutputTokens(),
                "temperature", 0.7
        ));

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using Gemini model: {} for request", model);
        return json;
    }

    private String createGeminiImageRequestBody(String userMessage, String base64Image, List<Map<String, String>> history) throws Exception {
        String model = geminiConfig.getModel();
        List<Map<String, Object>> contents = new ArrayList<>();

        // История с правильными ролями
        if (history != null) {
            for (Map<String, String> historyMessage : history) {
                String role = historyMessage.get("role");
                // Конвертируем assistant в model для Gemini API
                if ("assistant".equals(role)) {
                    role = "model";
                }
                contents.add(Map.of("role", role, "parts", List.of(Map.of("text", historyMessage.get("content")))));
            }
        }

        // Текущее сообщение с изображением
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("inlineData", Map.of(
                "mimeType", "image/jpeg",
                "data", base64Image
        )));
        parts.add(Map.of("text", userMessage));
        contents.add(Map.of("role", "user", "parts", parts));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", contents);
        requestBody.put("generationConfig", Map.of(
                "maxOutputTokens", getMaxOutputTokens(),
                "temperature", 0.7
        ));

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using Gemini model: {} for image request", model);
        return json;
    }

    private String parseGeminiResponse(String responseBody) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode candidates = jsonNode.get("candidates");

        if (candidates != null && candidates.isArray() && candidates.size() > 0) {
            JsonNode firstCandidate = candidates.get(0);

            JsonNode finishReason = firstCandidate.get("finishReason");
            if (finishReason != null) {
                switch (finishReason.asText()) {
                    case "MAX_TOKENS" -> log.warn("Gemini response truncated (max tokens)");
                    case "SAFETY" -> { return "🚫 Ответ заблокирован из соображений безопасности."; }
                    case "RECITATION" -> { return "🚫 Ответ заблокирован из-за возможного нарушения авторских прав."; }
                }
            }

            JsonNode content = firstCandidate.get("content");
            if (content != null && content.has("parts")) {
                for (JsonNode part : content.get("parts")) {
                    JsonNode textNode = part.get("text");
                    if (textNode != null) {
                        String raw = textNode.asText().trim();
                        String result = markdownConverter.convertMarkdownToTelegramHtml(raw);

                        if ("MAX_TOKENS".equals(finishReason != null ? finishReason.asText() : "")) {
                            result += "\n\n⚠️ _Ответ мог быть обрезан. Попробуйте очистить историю командой /clear_";
                        }
                        return result;
                    }
                }
            }

            if ("MAX_TOKENS".equals(finishReason != null ? finishReason.asText() : "")) {
                return "⚠️ Превышен лимит токенов. Попробуйте:\n• Очистить историю (/clear)\n• Задать более короткий вопрос\n• Разбить запрос на части";
            }
        }

        log.warn("Could not parse Gemini response: {}", responseBody);
        return "Извините, не удалось получить ответ от Gemini";
    }

    @Override
    public void close() {
        try {
            httpClient.close();
            log.info("HTTP client closed");
        } catch (Exception e) {
            log.error("Error closing HTTP client", e);
        }
    }
}

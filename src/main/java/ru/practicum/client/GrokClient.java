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
import ru.practicum.config.GrokConfig;
import ru.practicum.config.ProxyConfig;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GrokClient implements AiClient {
    private final GrokConfig grokConfig;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public GrokClient(GrokConfig grokConfig, ProxyConfig proxyConfig) {
        this.grokConfig = grokConfig;
        this.objectMapper = new ObjectMapper();
        this.httpClient = proxyConfig.createHttpClient();
    }

    @Override
    public String sendTextMessage(String userMessage, List<Map<String, String>> history) {
        try {
            String requestBody = createGrokRequestBody(userMessage, history);
            String apiUrl = grokConfig.getBaseUrl() + "/chat/completions";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending Grok request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + grokConfig.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received Grok response: {}", responseBody);
                if (response.getCode() == 200) {
                    return parseGrokResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("Grok API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота Grok API. Проверьте баланс в аккаунте xAI.";
                } else if (response.getCode() == 401) {
                    log.error("Grok API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации Grok API. Проверьте API ключ xAI.";
                } else {
                    log.error("Grok API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка Grok API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending message to Grok", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    @Override
    public String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history) {
        try {
            String requestBody = createGrokImageRequestBody(userMessage, base64Image, history);
            String apiUrl = grokConfig.getBaseUrl() + "/chat/completions";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending Grok image request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + grokConfig.getApiKey());
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("Received Grok response: {}", responseBody);
                if (response.getCode() == 200) {
                    return parseGrokResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("Grok API quota exceeded: {}", responseBody);
                    return "🚫 Превышена квота Grok API. Проверьте баланс в аккаунте xAI.";
                } else if (response.getCode() == 401) {
                    log.error("Grok API authentication error: {}", responseBody);
                    return "🔐 Ошибка авторизации Grok API. Проверьте API ключ xAI.";
                } else if (response.getCode() == 400) {
                    log.error("Grok Image error: {}", responseBody);
                    return "❌ Данная модель Grok не поддерживает обработку изображений, попробуйте другую модель.";
                } else {
                    log.error("Grok API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "❌ Ошибка Grok API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending image to Grok", e);
            return "Извините, произошла ошибка при отправке изображения: " + e.getMessage();
        }
    }

    private String createGrokRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = grokConfig.getModel();
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Map<String, String> historyMessage : history) {
            messages.add(Map.of("role", historyMessage.get("role"), "content", historyMessage.get("content")));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 1000);
        requestBody.put("temperature", 0.7);

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using Grok model: {} for request", model);
        return json;
    }

    private String createGrokImageRequestBody(String userMessage, String base64Image, List<Map<String, String>> history) throws Exception {
        String model = grokConfig.getModel();
        List<Map<String, Object>> messages = new ArrayList<>();
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
        requestBody.put("max_tokens", 1000);
        requestBody.put("temperature", 0.7);

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using Grok model: {} for image request", model);
        return json;
    }

    private String parseGrokResponse(String responseBody) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode choices = jsonNode.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message != null && message.has("content")) {
                String result = message.get("content").asText().trim();
                result = convertMarkdownCodeToHtml(result);
                log.debug("Received Grok response of length: {}", result.length());
                return result;
            }
        }
        log.warn("Could not parse Grok response: {}", responseBody);
        return "Извините, не удалось получить ответ от Grok";
    }

    private String convertMarkdownCodeToHtml(String text) {
        String regex = "```(\\w+)?\\n([\\s\\S]*?)\\n```";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String language = matcher.group(1) != null ? matcher.group(1) : "";
            String code = matcher.group(2);
            String escapedCode = code.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String htmlCode = "<pre><code class=\"language-" + language + "\">" + escapedCode + "</code></pre>";
            matcher.appendReplacement(result, htmlCode);
        }
        matcher.appendTail(result);
        return result.toString();
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
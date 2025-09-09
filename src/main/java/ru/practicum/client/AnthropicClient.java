package ru.practicum.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;
import ru.practicum.config.ClaudeConfig;
import ru.practicum.config.ProxyConfig;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AnthropicClient {
    private final ClaudeConfig claudeConfig;
    private final ProxyConfig proxyConfig;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public AnthropicClient(ClaudeConfig claudeConfig, ProxyConfig proxyConfig) {
        this.claudeConfig = claudeConfig;
        this.proxyConfig = proxyConfig;
        this.objectMapper = new ObjectMapper();
        this.httpClient = createHttpClient();
    }

    private CloseableHttpClient createHttpClient() {
        var clientBuilder = HttpClients.custom();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(30, TimeUnit.SECONDS))
                .setResponseTimeout(Timeout.of(60, TimeUnit.SECONDS))
                .build();
        clientBuilder.setDefaultRequestConfig(requestConfig);

        if (proxyConfig.isEnabled()) {
            log.info("Using proxy: {}:{}", proxyConfig.getHost(), proxyConfig.getPort());
            HttpHost proxy = new HttpHost(proxyConfig.getHost(), proxyConfig.getPort());
            clientBuilder.setProxy(proxy);

            if (proxyConfig.getUsername() != null && !proxyConfig.getUsername().isEmpty()) {
                log.info("Using proxy authentication for user: {}", proxyConfig.getUsername());
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        new AuthScope(proxyConfig.getHost(), proxyConfig.getPort()),
                        new UsernamePasswordCredentials(proxyConfig.getUsername(), proxyConfig.getPassword().toCharArray())
                );
                clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }
        } else {
            log.info("Proxy disabled");
        }

        return clientBuilder.build();
    }

    public String sendMessage(String userMessage, List<Map<String, String>> history) {
        try {
            String requestBody = createClaudeRequestBody(userMessage, history);
            String apiUrl = claudeConfig.getBaseUrl() + "/v1/messages";
            HttpPost httpPost = new HttpPost(apiUrl);

            log.debug("Sending Claude request to: {}", apiUrl);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("x-api-key", claudeConfig.getApiKey());
            httpPost.setHeader("anthropic-version", "2023-06-01");

            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (response.getCode() == 200) {
                    return parseClaudeResponse(responseBody);
                } else if (response.getCode() == 429) {
                    log.error("Claude API quota exceeded: {}", responseBody);
                    return "Превышена квота Claude API. Пожалуйста, проверьте баланс в аккаунте Anthropic.";
                } else if (response.getCode() == 401) {
                    log.error("Claude API authentication error: {}", responseBody);
                    return "Ошибка авторизации Claude API. Проверьте API ключ Anthropic.";
                } else {
                    log.error("Claude API error: Status {}, Response: {}", response.getCode(), responseBody);
                    return "Ошибка Claude API (код: " + response.getCode() + "). Попробуйте позже.";
                }
            }
        } catch (Exception e) {
            log.error("Error sending message to Claude", e);
            return "Извините, произошла ошибка при отправке сообщения: " + e.getMessage();
        }
    }

    private String createClaudeRequestBody(String userMessage, List<Map<String, String>> history) throws Exception {
        String model = claudeConfig.getModel();
        String systemPrompt = claudeConfig.getSystemPrompt();
        List<Map<String, String>> messages = new ArrayList<>(history);
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 1000);
        requestBody.put("system", systemPrompt);
        requestBody.put("messages", messages);

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("Using Claude model: {} for request with system prompt {}", model, systemPrompt);
        return json;
    }

    private String parseClaudeResponse(String responseBody) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode content = jsonNode.get("content");
        if (content != null && content.isArray() && content.size() > 0) {
            JsonNode firstContent = content.get(0);
            if (firstContent.has("text")) {
                String result = firstContent.get("text").asText().trim();
                // Преобразуем Markdown код в HTML
                result = convertMarkdownCodeToHtml(result);
                log.debug("Received Claude response of length: {}", result.length());
                return result;
            }
        }
        log.warn("Could not parse Claude response: {}", responseBody);
        return "Извините, не удалось получить ответ от Claude";
    }

    private String convertMarkdownCodeToHtml(String text) {
        // Регулярное выражение для поиска Markdown кода (```language\n...\n```)
        String regex = "```(\\w+)?\\n([\\s\\S]*?)\\n```";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String language = matcher.group(1) != null ? matcher.group(1) : "";
            String code = matcher.group(2);
            // Экранируем HTML-символы в коде
            String escapedCode = code.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            // Формируем HTML-разметку
            String htmlCode = "<pre><code class=\"language-" + language + "\">" + escapedCode + "</code></pre>";
            matcher.appendReplacement(result, htmlCode);
        }
        matcher.appendTail(result);
        return result.toString();
    }

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
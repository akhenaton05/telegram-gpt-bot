package ru.practicum.client;

import java.util.List;
import java.util.Map;

public interface AiClient {
    String sendTextMessage(String userMessage, List<Map<String, String>> history);
    String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history);
    void close();
}

package ru.practicum.client;

import java.util.List;
import java.util.Map;

public interface AiImageSender extends AiClient {
    String sendMessageWithImage(String userMessage, String base64Image, List<Map<String, String>> history);
}

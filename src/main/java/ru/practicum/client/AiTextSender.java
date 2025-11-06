package ru.practicum.client;

import java.util.List;
import java.util.Map;

public interface AiTextSender extends AiClient {
    String sendTextMessage(String userMessage, List<Map<String, String>> history);
}

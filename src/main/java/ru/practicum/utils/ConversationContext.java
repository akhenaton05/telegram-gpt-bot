package ru.practicum.utils;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConversationContext {
    private final Map<Long, List<Map<String, String>>> history = new HashMap<>();

    public void addMessage(Long chatId, String role, String content) {
        history.computeIfAbsent(chatId, k -> new ArrayList<>());
        List<Map<String, String>> chatHistory = history.get(chatId);
        chatHistory.add(Map.of("role", role, "content", content));
        // Ограничение на длину контекста
        int maxMessages = 6;
        if (chatHistory.size() > maxMessages) {
            chatHistory.remove(0);
        }
    }

    public List<Map<String, String>> getHistory(Long chatId) {
        return history.getOrDefault(chatId, new ArrayList<>());
    }

    public void clearHistory(Long chatId) {
        history.remove(chatId);
    }
}
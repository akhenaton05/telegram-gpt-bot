package ru.practicum.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ConversationContext {
    private final Map<Long, List<Map<String, String>>> history = new HashMap<>();

    public void addMessage(Long chatId, String role, String content) {
        history.computeIfAbsent(chatId, k -> new ArrayList<>());
        List<Map<String, String>> chatHistory = history.get(chatId);
        chatHistory.add(Map.of("role", role, "content", content));

        // Ограничение на длину контекста
        int maxMessages = 7;
        if (chatHistory.size() > maxMessages) {
            chatHistory.remove(0);
            log.debug("Context trimmed for chat {}, now {} messages", chatId, chatHistory.size());
        }

        log.debug("Added {} message to chat {}: {}", role, chatId,
                content.length() > 50 ? content.substring(0, 50) + "..." : content);
    }

    public List<Map<String, String>> getHistory(Long chatId) {
        List<Map<String, String>> chatHistory = history.getOrDefault(chatId, new ArrayList<>());
        log.debug("Retrieved {} messages for chat {}", chatHistory.size(), chatId);
        return chatHistory;
    }

    public void clearHistory(Long chatId) {
        List<Map<String, String>> removed = history.remove(chatId);
        log.info("Cleared history for chat {}, removed {} messages",
                chatId, removed != null ? removed.size() : 0);
    }
}
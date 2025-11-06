package ru.practicum.utils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ConversationContext {
    private final Map<Long, ChatContext> contexts = new ConcurrentHashMap<>();
    private static final int MAX_MESSAGES = 7;

    @Data
    public static class ChatContext {
        private List<Map<String, String>> history = new ArrayList<>();
        private String currentModel = "gpt-5-nano"; //Модель по умолчанию
    }

    public ChatContext get(Long chatId) {
        return contexts.computeIfAbsent(chatId, k -> new ChatContext());
    }

    public void addMessage(Long chatId, String role, String content) {
        ChatContext ctx = get(chatId);
        ctx.getHistory().add(Map.of("role", role, "content", content));

        // Ограничение на длину контекста
        if (ctx.getHistory().size() > MAX_MESSAGES) {
            ctx.getHistory().removeFirst();
            log.debug("Context trimmed for chat {}, now {} messages", chatId, ctx.getHistory().size());
        }

        log.debug("Added {} message to chat {}: {}", role, chatId,
                content.length() > 50 ? content.substring(0, 50) + "..." : content);
    }

    public List<Map<String, String>> getHistory(Long chatId) {
        ChatContext ctx = get(chatId);
        log.debug("Retrieved {} messages for chat {}", ctx.getHistory().size(), chatId);
        return ctx.getHistory();
    }

    public void clearHistory(Long chatId) {
        ChatContext ctx = contexts.get(chatId);
        if (ctx != null) {
            int size = ctx.getHistory().size();
            ctx.getHistory().clear();
            log.info("Cleared history for chat {}, removed {} messages", chatId, size);
        }
    }

    public void setCurrentModel(Long chatId, String modelKey) {
        ChatContext ctx = get(chatId);
        ctx.setCurrentModel(modelKey);
        log.info("Chat {} switched to model: {}", chatId, modelKey);
    }

    public String getCurrentModel(Long chatId) {
        ChatContext ctx = get(chatId);
        return ctx.getCurrentModel();
    }

    public void clearAll(Long chatId) {
        contexts.remove(chatId);
        log.info("Cleared all context for chat {}", chatId);
    }
}
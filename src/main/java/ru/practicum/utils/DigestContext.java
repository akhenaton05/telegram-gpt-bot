package ru.practicum.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DigestContext {
    private final Map<Long, List<String>> topicsByChat = new ConcurrentHashMap<>();
    private static final int MAX_TOPICS = 3;

    // Добавить топик пользователю
    public void addTopic(Long chatId, String topic) {
        topicsByChat.computeIfAbsent(chatId, k -> new ArrayList<>()).add(topic);
        log.info("Topic '{}' added for user {}", topic, chatId);
    }

    // Удалить топик
    public boolean removeTopic(Long chatId, String topic) {
        List<String> topics = topicsByChat.get(chatId);
        if (topics != null && topics.remove(topic)) {
            log.info("Topic '{}' removed for user {}", topic, chatId);
            return true;
        }
        return false;
    }

    // Получить все топики пользователя
    public List<String> getTopics(Long chatId) {
        return topicsByChat.getOrDefault(chatId, new ArrayList<>());
    }

    // Проверить, есть ли топики
    public boolean hasTopics(Long chatId) {
        List<String> topics = topicsByChat.get(chatId);
        return topics != null && !topics.isEmpty();
    }

    // Проверить лимит
    public boolean canAddMoreTopics(Long chatId) {
        List<String> topics = topicsByChat.get(chatId);
        return topics == null || topics.size() < MAX_TOPICS;
    }

    // Очистить все топики пользователя
    public void clearTopics(Long chatId) {
        topicsByChat.remove(chatId);
        log.info("Topics cleared for user {}", chatId);
    }

    // Получить всех пользователей с дайджестами
    public Set<Long> getAllUsersWithDigest() {
        return topicsByChat.keySet();
    }

    // Форматированный вывод топиков
    public String getTopicsFormatted(Long chatId) {
        List<String> topics = getTopics(chatId);
        if (topics.isEmpty()) {
            return "У вас нет выбранных тем для дайджеста";
        }

        StringBuilder sb = new StringBuilder("📋 Ваши темы:\n\n");
        for (int i = 0; i < topics.size(); i++) {
            sb.append((i + 1)).append(". ").append(topics.get(i)).append("\n");
        }
        return sb.toString();
    }
}
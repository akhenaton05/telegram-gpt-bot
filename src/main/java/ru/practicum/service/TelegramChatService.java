package ru.practicum.service;

import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.practicum.client.AnthropicClient;
import ru.practicum.config.ClaudeConfig;
import ru.practicum.config.ProxyConfig;
import ru.practicum.config.TelegramBotConfig;
import ru.practicum.utils.ConversationContext;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class TelegramChatService extends TelegramLongPollingBot {
    private final ClaudeConfig claudeConfig;
    private final ProxyConfig proxyConfig;
    private final TelegramBotConfig telegramBotConfig;
    private final AnthropicClient anthropicClient;
    private final ConversationContext context;

    @Override
    public String getBotUsername() {
        String username = telegramBotConfig.getBotUsername();
        return username.startsWith("@") ? username.substring(1) : username;
    }

    @Override
    public String getBotToken() {
        return telegramBotConfig.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            String userMessage = message.getText();

            log.info("Received message from {}: {}", chatId, userMessage);

            // Обработка команд
            if (userMessage.startsWith("/")) {
                handleCommand(chatId, userMessage);
                return;
            }

            // Отправляем "печатает..." уведомление
            sendTypingAction(chatId);

            // Обработка сообщения с контекстом
            try {
                String response = anthropicClient.sendMessage(userMessage, context.getHistory(chatId));
                context.addMessage(chatId, "user", userMessage);
                context.addMessage(chatId, "assistant", response);
                sendMessage(chatId, response);
            } catch (Exception e) {
                log.error("Error processing message", e);
                sendMessage(chatId, "Произошла ошибка при обработке вашего сообщения.");
            }
        }
    }

    private void handleCommand(Long chatId, String command) {
        switch (command) {
            case "/start":
                sendMessage(chatId,
                        "Просто напишите мне любое сообщение, и я отвечу используя Claude AI.<br><br>" +
                                "<b>Claude особенно хорош в:</b><br>" +
                                "- 📝 Написании текстов и статей<br>" +
                                "- 💻 Программировании и отладке кода<br>" +
                                "- 🔍 Анализе и резюмировании информации<br>" +
                                "- 🌍 Переводах и работе с языками<br>" +
                                "- 🤔 Решении сложных задач<br><br>" +
                                "Используйте /clear, чтобы очистить контекст беседы.");
                break;

            case "/info":
                String modelInfo = claudeConfig.getModel().contains("haiku") ? "Claude 3 Haiku (быстрый и экономичный)"
                        : claudeConfig.getModel().contains("sonnet") ? "Claude 3 Sonnet (сбалансированный)"
                        : "Claude 3 Opus (самый умный)";
                sendMessage(chatId,
                        "🤖 <b>Telegram Claude Bot</b><br>" +
                                "Версия: 1.0<br>" +
                                "Модель: " + modelInfo + "<br>" +
                                "API: Anthropic Claude<br>" +
                                "Прокси: " + (proxyConfig.isEnabled() ? "включен" : "выключен") + "<br>" +
                                "Контекст: до 10 сообщений<br>" +
                                "Разработчик: @akhenaton05");
                break;

            case "/test":
                sendTypingAction(chatId);
                try {
                    String response = anthropicClient.sendMessage("Привет! Ответь кратко, что ты Claude от Anthropic и работаешь.", context.getHistory(chatId));
                    context.addMessage(chatId, "user", "Test connection");
                    context.addMessage(chatId, "assistant", response);
                    sendMessage(chatId, "✅ <b>Тест связи с Claude:</b><br><br>" + response);
                } catch (Exception e) {
                    log.error("Test command error", e);
                    sendMessage(chatId, "❌ Ошибка при тестировании связи с Claude.");
                }
                break;

            case "/clear":
                context.clearHistory(chatId);
                sendMessage(chatId, "🧹 Контекст беседы очищен.");
                break;

            default:
                sendMessage(chatId, "Неизвестная команда. Используйте /start, /info, /test или /clear.");
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("HTML");

        try {
            execute(message);
            log.info("Message sent to {}: {}", chatId, text.substring(0, Math.min(50, text.length())) + "...");
        } catch (TelegramApiException e) {
            log.error("Error sending message to {}", chatId, e);
        }
    }

    private void sendTypingAction(Long chatId) {
        try {
            SendChatAction chatAction = new SendChatAction();
            chatAction.setChatId(chatId);
            chatAction.setAction(ActionType.TYPING);
            execute(chatAction);
        } catch (Exception e) {
            log.debug("Could not send typing action", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down bot...");
        anthropicClient.close();
        log.info("Bot shutdown complete");
    }
}
package ru.practicum.service;

import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.practicum.client.AnthropicClient;
import ru.practicum.config.ClaudeConfig;
import ru.practicum.config.ProxyConfig;
import ru.practicum.config.TelegramBotConfig;

import java.io.File;

@Slf4j
@Service
@AllArgsConstructor
public class TelegramChatService extends TelegramLongPollingBot {
    private final ClaudeConfig claudeConfig;
    private final ProxyConfig proxyConfig;
    private final TelegramBotConfig telegramBotConfig;
    private final AnthropicClient anthropicClient;

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

            // Синхронная обработка сообщения
            try {
                String response = anthropicClient.sendMessage(userMessage);
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
                startAction(String.valueOf(chatId));
                break;

            case "/help":
                sendMessage(chatId,
                        "Просто напишите мне любое сообщение, и я отвечу используя Claude AI.\n\n" +
                                "Claude особенно хорош в:\n" +
                                "- 📝 Написании текстов и статей\n" +
                                "- 💻 Программировании и отладке кода\n" +
                                "- 🔍 Анализе и резюмировании информации\n" +
                                "- 🌍 Переводах и работе с языками\n" +
                                "- 🤔 Решении сложных задач");
                break;

            case "/info":
                String modelInfo = claudeConfig.getModel().contains("haiku") ? "Claude 3 Haiku (быстрый и экономичный)"
                        : claudeConfig.getModel().contains("sonnet") ? "Claude 3 Sonnet (сбалансированный)"
                        : "Claude 3 Opus (самый умный)";

                sendMessage(chatId,
                        "🤖 Telegram Claude Bot\n" +
                                "Версия: 1.0\n" +
                                "Модель: " + modelInfo + "\n" +
                                "API: Anthropic Claude\n" +
                                "Прокси: " + (proxyConfig != null && proxyConfig.isEnabled() ? "включен" : "выключен") + "\n" +
                                "Разработчик: @akhenaton05");
                break;

            case "/test":
                sendTypingAction(chatId);
                try {
                    String response = anthropicClient.sendMessage("Привет! Ответь кратко, что ты Claude от Anthropic и работаешь.");
                    sendMessage(chatId, "✅ Тест связи с Claude:\n\n" + response);
                } catch (Exception e) {
                    log.error("Test command error", e);
                    sendMessage(chatId, "❌ Ошибка при тестировании связи с Claude.");
                }
                break;

            default:
                sendMessage(chatId, "Неизвестная команда. Используйте /help для получения списка команд.");
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

    private void startAction(String chatId) {
        sendMessageWithPhoto(chatId,
                "\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11 \n \n" +
                        "WELCOME TO *DELETZ GPT* (based on Claude AI) \n \n" +
                        "\uD83E\uDDF1 /info - Deletz bot information \n" +
                        "\uD83E\uDDEE /help - for a help\n" +
                        "\uD83D\uDCE6 /test - to test connection with Claude \n \n" +
                        "\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8",
                "img_start.png");
    }

    private void sendMessageWithPhoto(String chatId, String text, String image) {
        SendPhoto msg = SendPhoto
                .builder()
                .chatId(chatId)
                .photo(new InputFile(new File(image)))
                .caption(text)
                .parseMode("Markdown")
                .build();
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Failed to send photo to Telegram chat {}: {}", chatId, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down bot...");
        anthropicClient.close();
        log.info("Bot shutdown complete");
    }
}
package ru.practicum.service;

import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.practicum.client.AiClient;
import ru.practicum.client.AiClientFactory;
import ru.practicum.client.AnthropicClient;
import ru.practicum.client.OpenAiClient;
import ru.practicum.config.ClaudeConfig;
import ru.practicum.config.OpenAiConfig;
import ru.practicum.config.ProxyConfig;
import ru.practicum.config.TelegramBotConfig;
import ru.practicum.utils.ConversationContext;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class TelegramChatService extends TelegramLongPollingBot {
    private final ClaudeConfig claudeConfig;
    private final OpenAiConfig openAiConfig;
    private final ProxyConfig proxyConfig;
    private final TelegramBotConfig telegramBotConfig;
    private final ConversationContext context;
    private AiClient aiClient;

    public TelegramChatService(ClaudeConfig claudeConfig, OpenAiConfig openAiConfig, ProxyConfig proxyConfig, TelegramBotConfig telegramBotConfig, AiClientFactory aiClientFactory, ConversationContext context) {
        this.claudeConfig = claudeConfig;
        this.openAiConfig = openAiConfig;
        this.proxyConfig = proxyConfig;
        this.telegramBotConfig = telegramBotConfig;
        this.context = context;
        this.aiClient = aiClientFactory.getDefaultAiClient();
    }

    @Override
    public String getBotUsername() {
        String username = telegramBotConfig.getBotUsername();
        return username.startsWith("@") ? username.substring(1) : username;
    }

    @Override
    public String getBotToken() {
        return telegramBotConfig.getBotToken();
    }

    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();

            log.info("Received message from {}", chatId);

            //Обработка фотографий
            if (message.hasPhoto()) {
                handlePhotoMessage(chatId, message);
                return;
            }

            // Существующая обработка текстовых сообщений
            if (message.hasText()) {
                String userMessage = message.getText();
                log.info("Text message: {}", userMessage);

                // Обработка команд
                if (userMessage.startsWith("/")) {
                    handleCommand(chatId, userMessage, update);
                    return;
                }

                // Отправляем "печатает..." уведомление
                sendTypingAction(chatId);

                // Обработка сообщения с контекстом
                try {
                    String response = aiClient.sendTextMessage(userMessage, context.getHistory(chatId));
                    context.addMessage(chatId, "user", userMessage);
                    context.addMessage(chatId, "assistant", response);
                    sendMessage(chatId, response);
                } catch (Exception e) {
                    log.error("Error processing message", e);
                    sendMessage(chatId, "Произошла ошибка при обработке вашего сообщения.");
                }
            }
            // Обработка кнопки
        }  else if (update.hasCallbackQuery()) {
            updateModelsButton(update.getCallbackQuery().getId(), update.getCallbackQuery().getData());
        }
    }

    // Обработка фотографий (синхронно)
    private void handlePhotoMessage(Long chatId, Message message) {
        log.info("Processing photo message from {}", chatId);

        sendTypingAction(chatId);

        try {
            // Получаем самое большое фото из массива
            PhotoSize photo = message.getPhoto().get(message.getPhoto().size() - 1);

            // Скачиваем фото
            byte[] imageBytes = downloadPhoto(photo.getFileId());

            // Конвертируем в base64
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // Получаем текст сообщения (если есть)
            String caption = message.getCaption() != null ? message.getCaption() : "Опиши что на изображении";

            // Отправляем в Claude с контекстом
            String response = aiClient.sendMessageWithImage(caption, base64Image, context.getHistory(chatId));

            // Сохраняем в контекст и отправляем ответ
            context.addMessage(chatId, "user", "[Изображение] " + caption);
            context.addMessage(chatId, "assistant", response);
            sendMessage(chatId, response);

        } catch (Exception e) {
            log.error("Error processing photo", e);
            sendMessage(chatId, "❌ Ошибка при обработке изображения: " + e.getMessage());
        }
    }

    // Скачивание фото через Telegram Bot API
    private byte[] downloadPhoto(String fileId) throws Exception {
        // Получаем информацию о файле
        GetFile getFileMethod = new GetFile();
        getFileMethod.setFileId(fileId);

        org.telegram.telegrambots.meta.api.objects.File file = execute(getFileMethod);

        // Формируем URL для скачивания
        String fileUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + file.getFilePath();
        log.debug("Downloading photo from: {}", fileUrl);

        // Скачиваем файл
        URL url = new URL(fileUrl);
        try (InputStream inputStream = url.openStream()) {
            return inputStream.readAllBytes();
        }
    }

    private void handleCommand(Long chatId, String command, Update update) throws TelegramApiException {
        switch (command) {
            case "/start":
                sendStartMessage(chatId);
                break;

            case "/info":
                sendInfo(chatId);
                break;

            case "/history":
                sendMessage(chatId, "📝 Текущий контекст:");
                context.getHistory(chatId);
                break;

            case "/clear":
                context.clearHistory(chatId);
                sendMessage(chatId, "🧹 Контекст беседы очищен.");
                break;

            case "/model":
                execute(setModelsButton(chatId));
                break;

            default:
                sendMessage(chatId, "Неизвестная команда.\n\n" +
                        "Доступные команды:\n" +
                        "/start - справка по использованию\n" +
                        "/info - информация о модели\n" +
                        "/history - история контекста\n" +
                        "/clear - очистить контекст");
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

    private void sendStartMessage(Long chatId) {
        sendMessage(chatId,
                "Я умею работать с текстом и изображениями!\n\n" +
                        "📝 Текстовые сообщения:\n" +
                        "- Просто напишите любой вопрос\n" +
                        "📷 Изображения:\n" +
                        "- Отправьте фото с подписью или без\n" +
                        "- Я опишу содержимое или отвечу на вопросы о фото\n\n" +
                        "Примеры:\n" +
                        "- Фото еды: 'Что это за блюдо?'\n" +
                        "- Скриншот кода: 'Найди ошибку'\n" +
                        "- Документ: 'Извлеки текст'\n" +
                        "- Просто фото: 'Опиши что видишь'\n\n" +
                        "Используйте /clear, чтобы очистить контекст беседы.");
    }

    public void sendInfo(Long chatId) {
        String modelInfo = claudeConfig.getModel().contains("haiku") ? "Claude 3.5 Haiku (быстрый и экономичный)"
                : claudeConfig.getModel().contains("sonnet") ? "Claude 4 Sonnet (сбалансированный)"
                : "Claude 4 Opus (самый умный)";
        sendMessage(chatId,
                "🤖 <b>Telegram Claude Bot</b>\n" +
                        "Версия: 1.1 (с поддержкой изображений)\n" +
                        "Модель: " + modelInfo + "\n" +
                        "API: Anthropic Claude\n" +
                        "Прокси: " + (proxyConfig.isEnabled() ? "включен" : "выключен") + "\n" +
                        "Контекст: до 7 сообщений\n" +
                        "Поддержка: текст + изображения\n" +
                        "Разработчик: @akhenaton05");
    }

    private SendMessage setModelsButton (Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Выбери модель из представленных ниже:");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        List<InlineKeyboardButton> rowInline1 = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton1 = new InlineKeyboardButton();
        inlineKeyboardButton1.setText("Gpt 4.1 mini");
        inlineKeyboardButton1.setCallbackData("Gpt 4.1 mini");
        InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
        inlineKeyboardButton2.setText("Claude 3 Haiku");
        inlineKeyboardButton2.setCallbackData("Claude 3 Haiku");
        rowInline1.add(inlineKeyboardButton1);
        rowInline1.add(inlineKeyboardButton2);

        List<InlineKeyboardButton> rowInline2 = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton3 = new InlineKeyboardButton();
        inlineKeyboardButton3.setText("Gpt 5 nano");
        inlineKeyboardButton3.setCallbackData("Gpt 5 nano");
        InlineKeyboardButton inlineKeyboardButton4 = new InlineKeyboardButton();
        inlineKeyboardButton4.setText("Claude 3.5 Haiku");
        inlineKeyboardButton4.setCallbackData("Claude 3.5 Haiku");
        rowInline2.add(inlineKeyboardButton3);
        rowInline2.add(inlineKeyboardButton4);

        List<InlineKeyboardButton> rowInline3 = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton5 = new InlineKeyboardButton();
        inlineKeyboardButton5.setText("Claude 4 Sonnet");
        inlineKeyboardButton5.setCallbackData("Claude 4 Sonnet");
        rowInline3.add(inlineKeyboardButton5);

        rowsInline.add(rowInline1);
        rowsInline.add(rowInline2);
        rowsInline.add(rowInline3);

        System.out.println("rowsInline");
        markupInline.setKeyboard(rowsInline);
        System.out.println("AFTER");
        message.setReplyMarkup(markupInline);

        return message;
    }

    private void updateModelsButton(String callbackQueryId, String callData) {
        // Подтверждение
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        answer.setText("Модель " + callData + " выбрана!");
        answer.setShowAlert(false);
        try {
            execute(answer);
            log.info("Callback confirmed for query: {}", callbackQueryId);
        } catch (TelegramApiException e) {
            log.error("Error answering callback query", e);
        }

        switch (callData) {
            case "Gpt 4.1 mini" -> {
                openAiConfig.setModel("gpt-4.1-mini-2025-04-14");
                aiClient = new OpenAiClient(openAiConfig, proxyConfig);
            }
            case "Claude 3 Haiku" -> {
                claudeConfig.setModel("claude-3-haiku-20240307");
                aiClient = new AnthropicClient(claudeConfig, proxyConfig);
            }
            case "Gpt 5 nano" -> {
                claudeConfig.setModel("gpt-5-nano");
                aiClient = new AnthropicClient(claudeConfig, proxyConfig);
            }
            case "Claude 3.5 Haiku" -> {
                claudeConfig.setModel("claude-3-5-haiku-20241022");
                aiClient = new AnthropicClient(claudeConfig, proxyConfig);
            }
            case "Claude 4 Sonnet" -> {
                claudeConfig.setModel("claude-sonnet-4-20250514");
                aiClient = new AnthropicClient(claudeConfig, proxyConfig);
            }
            default -> {
                log.warn("Unknown callback data: {}", callData);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down bot...");
        aiClient.close();
        log.info("Bot shutdown complete");
    }
}
package ru.practicum.service;

import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.practicum.client.*;
import ru.practicum.config.*;
import ru.practicum.utils.ConversationContext;
import ru.practicum.utils.MessageSplitter;

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
    private final GrokConfig grokConfig;
    private final SonarConfig sonarConfig;
    private final GeminiConfig geminiConfig;
    private final CloseableHttpClient httpClient;
    private final ProxyConfig proxyConfig;
    private final TelegramBotConfig telegramBotConfig;
    private final ConversationContext context;
    private AiClient aiClient;
    private final MessageSplitter messageSplitter;

    public TelegramChatService(ClaudeConfig claudeConfig, OpenAiConfig openAiConfig, GrokConfig grokConfig, SonarConfig sonarConfig, GeminiConfig geminiConfig, ProxyConfig proxyConfig, CloseableHttpClient httpClient, ProxyConfig proxyConfig1, TelegramBotConfig telegramBotConfig, AiClientFactory aiClientFactory, ConversationContext context, MessageSplitter messageSplitter) {
        this.claudeConfig = claudeConfig;
        this.openAiConfig = openAiConfig;
        this.grokConfig = grokConfig;
        this.sonarConfig = sonarConfig;
        this.geminiConfig = geminiConfig;
        this.httpClient = httpClient;
        this.proxyConfig = proxyConfig;
        this.telegramBotConfig = telegramBotConfig;
        this.context = context;
        this.aiClient = aiClientFactory.getDefaultAiClient();
        this.messageSplitter = messageSplitter;
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

            // Обработка фотографий
            if (message.hasPhoto()) {
                handlePhotoMessage(chatId, message);
                return;
            }

            // Обработка текстовых сообщений
            if (message.hasText()) {
                String userMessage = message.getText();
                log.info("Text message: {}", userMessage);

                // Обработка команд
                if (userMessage.startsWith("/")) {
                    handleCommand(chatId, userMessage);
                    return;
                }

                // Отправляем "печатает..." уведомление
                sendTypingAction(chatId);

                // Обработка сообщения
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
            updateModelsButton(update.getCallbackQuery());
        }
    }

    // Обработка фотографий
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

            // Отправляем с контекстом
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

        File file = execute(getFileMethod);

        // Формируем URL для скачивания
        String fileUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + file.getFilePath();
        log.debug("Downloading photo from: {}", fileUrl);

        // Скачиваем файл
        URL url = new URL(fileUrl);
        try (InputStream inputStream = url.openStream()) {
            return inputStream.readAllBytes();
        }
    }

    private void handleCommand(Long chatId, String command) throws TelegramApiException {
        switch (command) {
            case "/start":
                sendStartMessage(chatId);
                break;

            case "/info":
                sendInfo(chatId);
                break;

            case "/history":
                sendMessage(chatId, "📝 Текущий контекст: " + context.getHistory(chatId));
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
                        "/info - справка по использованию\n" +
                        "/models - выбор модели\n" +
                        "/history - история контекста\n" +
                        "/clear - очистить контекст");
        }
    }

    private void sendMessage(Long chatId, String text) {
        final int TG_LIMIT = 4096;

        List<String> chunks = messageSplitter.splitMessageForTelegram(text, TG_LIMIT);

        for (String chunk : chunks) {
            if (chunk == null || chunk.isEmpty()) continue;

            SendMessage msg = new SendMessage();
            msg.setChatId(chatId);
            msg.setText(chunk);
            msg.setParseMode("HTML");

            try {
                execute(msg);
                log.info("Chunk sent to {} ({} симв.)", chatId, chunk.length());
            } catch (TelegramApiException e) {
                log.error("Error sending chunk to {}", chatId, e);
                break;
            }
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
        sendMessage(chatId,
                "🤖 <b>Telegram GPT Bot</b>\n" +
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

        // OpenAi
        List<InlineKeyboardButton> rowInline1 = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton1 = new InlineKeyboardButton();
        inlineKeyboardButton1.setText("Gpt 4.1 Mini");
        inlineKeyboardButton1.setCallbackData("Gpt 4.1 Mini");
        InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
        inlineKeyboardButton2.setText("Gpt 5");
        inlineKeyboardButton2.setCallbackData("Gpt 5");
        rowInline1.add(inlineKeyboardButton1);
        rowInline1.add(inlineKeyboardButton2);

        // AnthropicAi
        List<InlineKeyboardButton> rowInline2 = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton3 = new InlineKeyboardButton();
        inlineKeyboardButton3.setText("Claude 3 Haiku");
        inlineKeyboardButton3.setCallbackData("Claude 3 Haiku");
        InlineKeyboardButton inlineKeyboardButton4 = new InlineKeyboardButton();
        inlineKeyboardButton4.setText("Claude 3.5 Haiku");
        inlineKeyboardButton4.setCallbackData("Claude 3.5 Haiku");
        rowInline2.add(inlineKeyboardButton3);
        rowInline2.add(inlineKeyboardButton4);

        // AnthropicAi
        List<InlineKeyboardButton> rowInline3 = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton5 = new InlineKeyboardButton();
        inlineKeyboardButton5.setText("Claude 4 Sonnet");
        inlineKeyboardButton5.setCallbackData("Claude 4 Sonnet");
        rowInline3.add(inlineKeyboardButton5);

        // xAi
        List<InlineKeyboardButton> rowInline4 = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton6 = new InlineKeyboardButton();
        inlineKeyboardButton6.setText("Grok 4");
        inlineKeyboardButton6.setCallbackData("Grok 4");
        InlineKeyboardButton inlineKeyboardButton7 = new InlineKeyboardButton();
        inlineKeyboardButton7.setText("Grok 3 Mini");
        inlineKeyboardButton7.setCallbackData("Grok 3 mini");
        rowInline4.add(inlineKeyboardButton6);
        rowInline4.add(inlineKeyboardButton7);

        // PerplexityAi
        List<InlineKeyboardButton> rowInline5 = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton8 = new InlineKeyboardButton();
        inlineKeyboardButton8.setText("Sonar");
        inlineKeyboardButton8.setCallbackData("Sonar");
        rowInline5.add(inlineKeyboardButton8);
        InlineKeyboardButton inlineKeyboardButton9 = new InlineKeyboardButton();
        inlineKeyboardButton9.setText("Sonar Pro");
        inlineKeyboardButton9.setCallbackData("Sonar Pro");
        rowInline5.add(inlineKeyboardButton9);

        // Gemini
        List<InlineKeyboardButton> rowInline6 = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton10 = new InlineKeyboardButton();
        inlineKeyboardButton10.setText("Gemini 2.5 Flash");
        inlineKeyboardButton10.setCallbackData("Gemini 2.5 Flash");
        InlineKeyboardButton inlineKeyboardButton11 = new InlineKeyboardButton();
        inlineKeyboardButton11.setText("Gemini 2.5 Pro");
        inlineKeyboardButton11.setCallbackData("Gemini 2.5 Pro");
        rowInline6.add(inlineKeyboardButton10);
        rowInline6.add(inlineKeyboardButton11);

        rowsInline.add(rowInline1);
        rowsInline.add(rowInline2);
        rowsInline.add(rowInline3);
        rowsInline.add(rowInline4);
        rowsInline.add(rowInline5);
        rowsInline.add(rowInline6);

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        return message;
    }

    private void updateModelsButton(CallbackQuery callbackQuery) {
        String callbackQueryId = callbackQuery.getId();
        String callData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        answer.setShowAlert(false);
        try {
            execute(answer);
            log.info("Callback confirmed for query: {}", callbackQueryId);
        } catch (TelegramApiException e) {
            log.error("Error answering callback query", e);
        }

        //o4-mini-deep-research
        switch (callData) {
            case "Gpt 4.1 Mini" -> {
                openAiConfig.setModel("gpt-4.1-mini-2025-04-14");
                aiClient = new OpenAiClient(openAiConfig, httpClient);
                sendMessage(chatId, "Выбрана модель: Gpt 4.1 mini (OpenAi)");
                context.clearHistory(chatId);
            }
            case "Gpt 5" -> {
                openAiConfig.setModel("gpt-5-chat-latest");
                aiClient = new OpenAiClient(openAiConfig, httpClient);
                sendMessage(chatId, "Выбрана модель: Gpt 5 (OpenAi)");
                context.clearHistory(chatId);
            }
            case "Claude 3 Haiku" -> {
                claudeConfig.setModel("claude-3-haiku-20240307");
                aiClient = new AnthropicClient(claudeConfig, httpClient);
                sendMessage(chatId, "Выбрана модель: Claude 3 Haiku (AnthropicAi)");
                context.clearHistory(chatId);
            }
            case "Claude 3.5 Haiku" -> {
                claudeConfig.setModel("claude-3-5-haiku-20241022");
                aiClient = new AnthropicClient(claudeConfig, httpClient);
                sendMessage(chatId, "Выбрана модель: Claude 3.5 Haiku (AnthropicAi)");
                context.clearHistory(chatId);
            }
            case "Claude 4 Sonnet" -> {
                claudeConfig.setModel("claude-sonnet-4-20250514");
                aiClient = new AnthropicClient(claudeConfig, httpClient);
                sendMessage(chatId, "Выбрана модель: Claude 4 Sonnet (AnthropicAi)");
                context.clearHistory(chatId);
            }
            case "Grok 4" -> {
                grokConfig.setModel("grok-code-fast-1");
                aiClient = new GrokClient(grokConfig, httpClient);
                sendMessage(chatId, "Выбрана модель: Grok 4 (xAi)");
                context.clearHistory(chatId);
            }
            case "Grok 3 mini" -> {
                grokConfig.setModel("grok-3-mini");
                aiClient = new GrokClient(grokConfig, httpClient);
                sendMessage(chatId, "Выбрана модель: Grok 3 Mini (xAi)");
                context.clearHistory(chatId);
            }
            case "Sonar" -> {
                sonarConfig.setModel("sonar");
                aiClient = new SonarClient(sonarConfig, httpClient);
                sendMessage(chatId, "Выбрана модель: Sonar (PerplexityAi)");
                context.clearHistory(chatId);
            }
            case "Sonar Pro" -> {
                sonarConfig.setModel("sonar-pro");
                aiClient = new SonarClient(sonarConfig, httpClient);
                sendMessage(chatId, "Выбрана модель: Sonar Pro (PerplexityAi)");
                context.clearHistory(chatId);
            }
            case "Gemini 2.5 Flash" -> {
                geminiConfig.setModel("gemini-2.5-flash");
                aiClient = new GeminiClient(geminiConfig, httpClient);
                sendMessage(chatId, "Выбрана модель: Gemini 2.5 Flash (Gemini)");
                context.clearHistory(chatId);
            }
            case "Gemini 2.5 Pro" -> {
                geminiConfig.setModel("gemini-2.5-pro");
                aiClient = new GeminiClient(geminiConfig, httpClient);
                sendMessage(chatId, "Выбрана модель: Gemini 2.5 Pro (Gemini)");
                context.clearHistory(chatId);
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

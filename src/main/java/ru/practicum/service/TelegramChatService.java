package ru.practicum.service;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import ru.practicum.utils.DigestContext;
import ru.practicum.utils.MessageSplitter;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class TelegramChatService extends TelegramLongPollingBot{
    private final TelegramBotConfig telegramBotConfig;
    private final ProxyConfig proxyConfig;
    private final ConversationContext context;
    private final AiClientFactory clientFactory;
    private final MessageSplitter messageSplitter;
    private final DigestContext digestContext;

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

            if (message.hasPhoto()) {
                handlePhotoMessage(chatId, message);
                return;
            }

            if (message.hasText()) {
                String userMessage = message.getText();
                log.info("Text message: {}", userMessage);

                if (userMessage.startsWith("/")) {
                    handleCommand(chatId, userMessage);
                    return;
                }

                sendTypingAction(chatId);

                try {
                    // Получаем текущую модель пользователя и создаем клиента
                    String modelKey = context.getCurrentModel(chatId);
                    AiTextSender client = (AiTextSender) clientFactory.create(modelKey, chatId);

                    String response = client.sendTextMessage(userMessage, context.getHistory(chatId));
                    context.addMessage(chatId, "user", userMessage);
                    context.addMessage(chatId, "assistant", response);
                    sendMessage(chatId, response);
                } catch (Exception e) {
                    log.error("Error processing message", e);
                    sendMessage(chatId, "Произошла ошибка при обработке вашего сообщения.");
                }
            }
        } else if (update.hasCallbackQuery()) {
            handleModelSelection(update.getCallbackQuery());
        }
    }

    private void handlePhotoMessage(Long chatId, Message message) {
        log.info("Processing photo message from {}", chatId);
        sendTypingAction(chatId);

        try {
            PhotoSize photo = message.getPhoto().get(message.getPhoto().size() - 1);
            byte[] imageBytes = downloadPhoto(photo.getFileId());
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String caption = message.getCaption() != null ? message.getCaption() : "Опиши что на изображении";

            // Получаем клиента для выбранной модели
            String modelKey = context.getCurrentModel(chatId);
            AiImageSender client = (AiImageSender) clientFactory.create(modelKey, chatId);

            String response = client.sendMessageWithImage(caption, base64Image, context.getHistory(chatId));
            context.addMessage(chatId, "user", "[Изображение] " + caption);
            context.addMessage(chatId, "assistant", response);
            sendMessage(chatId, response);

        } catch (Exception e) {
            log.error("Error processing photo", e);
            sendMessage(chatId, "Ошибка при обработке изображения: " + e.getMessage());
        }
    }

    private byte[] downloadPhoto(String fileId) throws Exception {
        GetFile getFileMethod = new GetFile();
        getFileMethod.setFileId(fileId);
        File file = execute(getFileMethod);
        String fileUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + file.getFilePath();
        log.debug("Downloading photo from: {}", fileUrl);
        URL url = new URL(fileUrl);
        try (InputStream inputStream = url.openStream()) {
            return inputStream.readAllBytes();
        }
    }

    private void handleCommand(Long chatId, String command) throws TelegramApiException {
        // Разбиваем команду на части: команда + аргументы
        String[] parts = command.trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase(); // команда (например, /digest_add)
        String arg = parts.length > 1 ? parts[1] : null; // аргумент (например, "погода")

        switch (cmd) {
            case "/start" -> sendStartMessage(chatId);
            case "/info" -> sendInfo(chatId);
            case "/history" -> sendMessage(chatId, "📝 Текущий контекст:\n" + context.getHistory(chatId));
            case "/clear" -> {
                context.clearHistory(chatId);
                sendMessage(chatId, "🧹 Контекст беседы очищен.");
            }
            case "/model" -> execute(createModelSelectionMenu(chatId));

            // Команды дайджеста
            case "/digest_add" -> handleDigestAdd(chatId, arg);
            case "/digest_remove" -> handleDigestRemove(chatId, arg);
            case "/digest_list" -> sendMessage(chatId, digestContext.getTopicsFormatted(chatId));
            case "/digest_clear" -> {
                digestContext.clearTopics(chatId);
                sendMessage(chatId, "🧹 Все темы дайджеста удалены");
            }

            default -> sendMessage(chatId, """
                Неизвестная команда.
                
                📋 <b>Основные команды:</b>
                /start - справка по использованию
                /info - информация о боте
                /model - выбор модели
                /history - история контекста
                /clear - очистить контекст
                
                📰 <b>Дайджест:</b>
                /digest_add <тема> - добавить тему
                /digest_remove <тема> - удалить тему
                /digest_list - показать темы
                /digest_clear - очистить все темы
                """);
        }
    }

    public void sendMessage(Long chatId, String text) {
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
                log.info("Chunk sent to {} ({} chars)", chatId, chunk.length());
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
                "🤖 <b>Deletz GPT Bot</b>\n\n" +
                        "Я умею работать с текстом и изображениями!\n\n" +
                        "📝 <b>Текстовые сообщения:</b>\n" +
                        "- Просто напишите любой вопрос\n\n" +
                        "📷 <b>Изображения:</b>\n" +
                        "- Отправьте фото с подписью или без\n" +
                        "- Я опишу содержимое или отвечу на вопросы о фото\n\n" +
                        "<b>Команды:</b>\n" +
                        "/model - выбрать AI модель\n" +
                        "/clear - очистить контекст беседы\n" +
                        "/info - информация о боте");
    }

    public void sendInfo(Long chatId) {
        String currentModel = context.getCurrentModel(chatId);
        sendMessage(chatId,
                "🤖 <b>Deletz GPT Bot</b>\n\n" +
                        "Текущая модель: <code>" + currentModel + "</code>\n" +
                        "Прокси: " + (proxyConfig.isEnabled() ? "включен" : "выключен") + "\n" +
                        "Контекст: до 7 сообщений\n" +
                        "Поддержка: текст + изображения\n\n" +
                        "Разработчик: @akhenaton05");
    }

    private SendMessage createModelSelectionMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("🤖 Выберите AI модель:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // OpenAI
        rows.add(createButtonRow(
                "GPT-5 Nano", "model:gpt-5-nano",
                "GPT-5", "model:gpt-5"
        ));

        // Anthropic
        rows.add(createButtonRow(
                "Claude 4.5 Haiku", "model:claude-haiku",
                "Claude 4.5 Sonnet", "model:claude-sonnet"
        ));

        // xAI
        rows.add(createButtonRow(
                "Grok 4 Fast", "model:grok-4-fast",
                "Grok 4 Code", "model:grok-4-code"
        ));

        // Perplexity
        rows.add(createButtonRow(
                "Sonar", "model:sonar",
                "Sonar Pro", "model:sonar-pro"
        ));

        // Google
        rows.add(createButtonRow(
                "Gemini 2.5 Flash", "model:gemini-flash",
                "Gemini 2.5 Pro", "model:gemini-pro"
        ));

        // DeepSeek
        rows.add(createButtonRow(
                "DeepSeek 3.1", "model:deepseek",
                "DeepSeek Reasoning", "model:deepseek-reasoning"
        ));

        // Meta
        rows.add(createButtonRow(
                "Llama 4 Scout", "model:llama-scout",
                "Llama 4 Maverick", "model:llama-maverick"
        ));

        // Other
        rows.add(createButtonRow(
                "Kimi K2", "model:kimi-k2",
                null, null
        ));

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        return message;
    }

    private List<InlineKeyboardButton> createButtonRow(String text1, String callback1, String text2, String callback2) {
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton btn1 = new InlineKeyboardButton();
        btn1.setText(text1);
        btn1.setCallbackData(callback1);
        row.add(btn1);

        if (text2 != null && callback2 != null) {
            InlineKeyboardButton btn2 = new InlineKeyboardButton();
            btn2.setText(text2);
            btn2.setCallbackData(callback2);
            row.add(btn2);
        }

        return row;
    }

    private void handleModelSelection(CallbackQuery callbackQuery) {
        String callbackQueryId = callbackQuery.getId();
        String callData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        // Подтверждаем callback
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        answer.setShowAlert(false);
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            log.error("Error answering callback query", e);
        }

        // Обрабатываем выбор модели
        if (callData.startsWith("model:")) {
            String modelKey = callData.substring(6); // убираем префикс "model:"

            context.setCurrentModel(chatId, modelKey);

            clientFactory.invalidateCache(chatId);

            context.clearHistory(chatId);

            sendMessage(chatId, "Выбрана модель: <b>" + modelKey + "</b>");

            log.info("User {} switched to model: {}", chatId, modelKey);
        } else {
            log.warn("Unknown callback data: {}", callData);
        }
    }

    private void handleDigestAdd(Long chatId, String fullCommand) {
        String topic = extractTopicFromCommand(fullCommand);
        if (topic == null || topic.isEmpty()) {
            sendMessage(chatId, "Укажите тему: /digest_add погода");
            return;
        }

        if (!digestContext.canAddMoreTopics(chatId)) {
            sendMessage(chatId, "Достигнут лимит тем (максимум 5)");
            return;
        }

        digestContext.addTopic(chatId, topic);
        sendMessage(chatId, "Тема добавлена: " + topic);
    }

    private void handleDigestRemove(Long chatId, String topic) {
        if (topic == null || topic.isEmpty()) {
            sendMessage(chatId, "Укажите тему: /digest_remove погода");
            return;
        }

        if (digestContext.removeTopic(chatId, topic)) {
            sendMessage(chatId, "Тема удалена: " + topic);
        } else {
            sendMessage(chatId, "Тема не найдена");
        }
    }

    private String extractTopicFromCommand(String fullCommand) {
        if (fullCommand == null || !fullCommand.startsWith("/digest_add")) {
            return null;
        }
        String afterPrefix = fullCommand.substring("/digest_add".length()).trim();
        return afterPrefix.isEmpty() ? null : afterPrefix;
    }
}

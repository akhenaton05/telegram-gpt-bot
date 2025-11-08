package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.practicum.client.AiClientFactory;
import ru.practicum.client.AiTextSender;
import ru.practicum.utils.DigestContext;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DigestService {

    private final DigestContext digestContext;
    private final AiClientFactory clientFactory;
    private final TelegramChatService telegramService;

    // Отправить дайджест конкретному пользователю
    public void sendDigest(Long chatId) {
        List<String> topics = digestContext.getTopics(chatId);

        if (topics.isEmpty()) {
            telegramService.sendMessage(chatId, "Нет выбранных тем для дайджеста");
            return;
        }

        telegramService.sendMessage(chatId, "🧾 Ваш дайджест на сегодня:");
        try {
            for (String topic : topics) {
                String prompt = buildDigestPrompt(topic);

                AiTextSender client = (AiTextSender) clientFactory.create("gemini-flash", chatId);
                String digestText = client.sendTextMessage(prompt, List.of());

                telegramService.sendMessage(chatId, digestText);

                log.info("Digest sent to user {}", chatId);
            }
        } catch (Exception e) {
            log.error("Error sending digest to {}", chatId, e);
            telegramService.sendMessage(chatId, "Ошибка при формировании дайджеста");
        }
    }

    // Планировщик: отправляет дайджесты каждый день в 9:00
    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Moscow")
    public void sendDailyDigests() {
        log.info("Starting daily digest distribution");

        for (Long chatId : digestContext.getAllUsersWithDigest()) {
            sendDigest(chatId);
        }

        log.info("Daily digest distribution completed");
    }

    private String buildDigestPrompt(String topic) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Сформируй краткий новостной дайджест на сегодня по теме:\n");
        prompt.append("• ").append(topic).append("\n\n");

        prompt.append("Формат вывода строго такой:\n");
        prompt.append("🔥 <b>{ТЕМА} — {дата}</b><br><br>\n");
        prompt.append("📊 1–2 строки с ключевыми цифрами (индекс, курс, температура и т.п.)<br>\n");
        prompt.append("💰 2–3 строки с основными новостями или фактами (используй эмодзи по смыслу темы)<br>\n");
        prompt.append("⚡️ 1 финальная строка с самым ярким событием или прогнозом<br>\n\n");

        prompt.append("Правила:\n");
        prompt.append("• Для финансовых тем используй тикеры (MOEX, SBER, GAZP, LKOH, USD/RUB и т.д.) и краткие цифры.\n");
        prompt.append("• Для нефинансовых тем (погода, спорт, технологии) подбирай эмодзи по смыслу: ☀️🌧️❄️, ⚽️🏆, 💻📱 и т.п.\n");
        prompt.append("• Не используй длинные описания, только конкретные факты.\n");
        prompt.append("• Не повторяй слово «Индекс MOEX» — просто тикер и число.\n");
        prompt.append("• Между блоками ставь <br>, не используй Markdown.\n");
        prompt.append("• Максимум 5 строк, без аналитики и выводов.\n\n");

        prompt.append("Примеры:\n");
        prompt.append("Финансовая тема:\n");
        prompt.append("🔥 <b>Рынок РФ — 08.11.2025</b><br><br>\n");
        prompt.append("📊 MOEX: 2572,64 п. (+1,18%)<br>\n");
        prompt.append("💰 USD/RUB: 81,20 ₽ (−0,04%), EUR/RUB: 93,83 ₽ (+0,07%)<br>\n");
        prompt.append("⚙️ GAZP +2,9%, SBER +1,0%, TATN +3,1%<br>\n");
        prompt.append("🧾 MOEX добавила 5 новых БПИФ к торгам по выходным<br>\n");
        prompt.append("⚡️ OZON: дивиденды 143,55 ₽/акцию (~3,5%)<br><br>\n");

        prompt.append("Нефинансовая тема (пример — погода):\n");
        prompt.append("🔥 <b>Погода в Москве — 08.11.2025</b><br><br>\n");
        prompt.append("🌤️ Утром +5 °C, облачно с прояснениями<br>\n");
        prompt.append("💨 Ветер юго-западный 5 м/с, влажность 82%<br>\n");
        prompt.append("🌧️ Днём до +7 °C, кратковременный дождь<br>\n");
        prompt.append("🌙 К вечеру похолодает до +3 °C<br>\n");
        prompt.append("⚡️ Завтра ожидается ясная погода без осадков<br>\n");

        return prompt.toString();
    }
}
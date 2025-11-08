package ru.practicum;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.practicum.service.TelegramChatService;

@Slf4j
@EnableScheduling
@AllArgsConstructor
@SpringBootApplication
public class TelegramBotApplication implements CommandLineRunner {
    private final TelegramChatService bot;

    public static void main(String[] args) {
        log.info("Starting Telegram ChatGPT Bot with Spring Boot...");
        SpringApplication.run(TelegramBotApplication.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            log.info("Registering Telegram bot...");
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            log.info("Telegram bot registered successfully!");
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot: {}", e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            log.error("Unexpected error during bot registration: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
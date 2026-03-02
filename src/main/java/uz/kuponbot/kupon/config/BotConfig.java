package uz.kuponbot.kupon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.bot.KuponBot;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BotConfig {
    
    private final KuponBot kuponBot;
    private static volatile TelegramBotsApi apiInstance = null;
    private static final Object lock = new Object();
    
    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        // Double-checked locking pattern - faqat bir marta yaratish
        if (apiInstance == null) {
            synchronized (lock) {
                if (apiInstance == null) {
                    log.info("Creating new TelegramBotsApi instance and registering bot...");
                    TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
                    api.registerBot(kuponBot);
                    apiInstance = api;
                    log.info("Bot successfully registered with username: {}", kuponBot.getBotUsername());
                } else {
                    log.warn("TelegramBotsApi instance already exists, skipping registration");
                }
            }
        } else {
            log.warn("TelegramBotsApi bean requested but instance already exists");
        }
        return apiInstance;
    }
}
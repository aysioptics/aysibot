package uz.kuponbot.kupon.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.entity.User;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastService {
    
    private final UserService userService;
    private final ApplicationContext applicationContext;
    
    public BroadcastResult sendBroadcastMessage(String message) {
        log.info("Starting broadcast message to all users");
        
        List<User> allUsers = userService.getAllUsers();
        List<User> registeredUsers = allUsers.stream()
            .filter(user -> user.getState() == User.UserState.REGISTERED)
            .toList();
        
        if (registeredUsers.isEmpty()) {
            log.warn("No registered users found for broadcast");
            return new BroadcastResult(0, 0, 0);
        }
        
        TelegramLongPollingBot bot = applicationContext.getBean("kuponBot", TelegramLongPollingBot.class);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // Parallel ravishda xabar yuborish (tezroq bo'lishi uchun)
        List<CompletableFuture<Void>> futures = registeredUsers.stream()
            .map(user -> CompletableFuture.runAsync(() -> {
                try {
                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setChatId(user.getTelegramId());
                    sendMessage.setText(message);
                    
                    bot.execute(sendMessage);
                    successCount.incrementAndGet();
                    
                    // Rate limiting uchun kichik kutish
                    Thread.sleep(50); // 50ms kutish
                    
                } catch (TelegramApiException e) {
                    failureCount.incrementAndGet();
                    log.error("Failed to send broadcast message to user {}: {}", 
                        user.getTelegramId(), e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                    log.error("Interrupted while sending to user {}", user.getTelegramId());
                }
            }))
            .toList();
        
        // Barcha xabarlar yuborilishini kutish
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        int total = registeredUsers.size();
        int success = successCount.get();
        int failure = failureCount.get();
        
        log.info("Broadcast completed: {} total, {} success, {} failed", total, success, failure);
        
        return new BroadcastResult(total, success, failure);
    }
    
    public boolean sendSingleMessage(Long telegramId, String message) {
        log.info("Sending single message to user: {}", telegramId);
        
        try {
            TelegramLongPollingBot bot = applicationContext.getBean("kuponBot", TelegramLongPollingBot.class);
            
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(telegramId);
            sendMessage.setText(message);
            
            bot.execute(sendMessage);
            log.info("Single message sent successfully to user: {}", telegramId);
            return true;
            
        } catch (TelegramApiException e) {
            log.error("Failed to send single message to user {}: {}", telegramId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error sending single message to user {}: ", telegramId, e);
            return false;
        }
    }
    
    /**
     * Video/rasm broadcast qilish metodi
     * CopyMessage API dan foydalanadi - bu eng tez va samarali usul
     */
    public BroadcastResult sendMediaBroadcast(Message originalMessage, TelegramLongPollingBot bot) {
        log.info("Starting media broadcast to all users");
        
        List<User> allUsers = userService.getAllUsers();
        List<User> registeredUsers = allUsers.stream()
            .filter(user -> user.getState() == User.UserState.REGISTERED)
            .toList();
        
        if (registeredUsers.isEmpty()) {
            log.warn("No registered users found for broadcast");
            return new BroadcastResult(0, 0, 0);
        }
        
        Long fromChatId = originalMessage.getChatId();
        Integer messageId = originalMessage.getMessageId();
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // Parallel ravishda xabar yuborish
        List<CompletableFuture<Void>> futures = registeredUsers.stream()
            .map(user -> CompletableFuture.runAsync(() -> {
                try {
                    CopyMessage copyMessage = new CopyMessage();
                    copyMessage.setChatId(user.getTelegramId().toString());
                    copyMessage.setFromChatId(fromChatId.toString());
                    copyMessage.setMessageId(messageId);
                    
                    bot.execute(copyMessage);
                    successCount.incrementAndGet();
                    
                    // Rate limiting uchun kichik kutish
                    Thread.sleep(50); // 50ms kutish
                    
                } catch (TelegramApiException e) {
                    failureCount.incrementAndGet();
                    log.error("Failed to send media broadcast to user {}: {}", 
                        user.getTelegramId(), e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                    log.error("Interrupted while sending to user {}", user.getTelegramId());
                }
            }))
            .toList();
        
        // Barcha xabarlar yuborilishini kutish
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        int total = registeredUsers.size();
        int success = successCount.get();
        int failure = failureCount.get();
        
        log.info("Media broadcast completed: {} total, {} success, {} failed", total, success, failure);
        
        return new BroadcastResult(total, success, failure);
    }
    
    public static class BroadcastResult {
        private final int totalUsers;
        private final int successCount;
        private final int failureCount;
        
        public BroadcastResult(int totalUsers, int successCount, int failureCount) {
            this.totalUsers = totalUsers;
            this.successCount = successCount;
            this.failureCount = failureCount;
        }
        
        public int getTotalUsers() { return totalUsers; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public double getSuccessRate() { 
            return totalUsers > 0 ? (double) successCount / totalUsers * 100 : 0; 
        }
    }
}
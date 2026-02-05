package uz.kuponbot.kupon.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.entity.User;
import uz.kuponbot.kupon.entity.Voucher;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final UserService userService;
    private final ApplicationContext applicationContext;
    private VoucherService voucherService; // Lazy injection to avoid circular dependency
    
    @Value("${admin.telegram.ids}")
    private String adminTelegramIds;
    
    // 3 daqiqalik notification yuborilgan foydalanuvchilarni saqlash
    private final java.util.Set<Long> notifiedUsers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    
    // Har daqiqada tekshirish (test uchun)
    @Scheduled(fixedRate = 60000) // 60 soniya = 1 daqiqa
    public void checkThreeMinuteRegistrations() {
        log.info("Checking 3-minute registrations...");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeMinutesAgo = now.minusMinutes(3);
        LocalDateTime fourMinutesAgo = now.minusMinutes(4);
        
        List<User> allUsers = userService.getAllUsers();
        
        for (User user : allUsers) {
            if (user.getCreatedAt() != null && user.getState() == User.UserState.REGISTERED) {
                // Aniq 3 daqiqa oldin ro'yxatdan o'tgan foydalanuvchilarni topish
                // 3-4 daqiqa oralig'ida ro'yxatdan o'tganlarni tekshirish
                if (user.getCreatedAt().isAfter(fourMinutesAgo) && 
                    user.getCreatedAt().isBefore(threeMinutesAgo)) {
                    
                    // Agar bu foydalanuvchiga notification yuborilmagan bo'lsa
                    if (!notifiedUsers.contains(user.getTelegramId())) {
                        log.info("Found user registered 3 minutes ago: {} at {}", 
                            user.getTelegramId(), user.getCreatedAt());
                        
                        sendThreeMinuteRegistrationNotification(user);
                        notifiedUsers.add(user.getTelegramId()); // Yuborilganligini belgilash
                    }
                }
            }
        }
        
        // Eski notification'larni tozalash (24 soatdan eski)
        // Bu yerda oddiy implementatsiya - har soat tozalash
        if (now.getMinute() == 0) { // Har soat boshida
            notifiedUsers.clear();
            log.info("Cleared notified users cache");
        }
    }
    
    @Scheduled(cron = "0 0 9 * * *")
    public void checkRegistrationAnniversary() {
        log.info("Checking 6-month registration anniversaries...");
        
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        List<User> allUsers = userService.getAllUsers();
        
        for (User user : allUsers) {
            if (user.getCreatedAt() != null && user.getState() == User.UserState.REGISTERED) {
                LocalDate registrationDate = user.getCreatedAt().toLocalDate();
                
                // 6 oy to'lgan foydalanuvchilarni topish
                if (registrationDate.equals(sixMonthsAgo)) {
                    sendRegistrationAnniversaryNotification(user);
                }
            }
        }
    }
    
    // Har kuni soat 09:00 da tug'ilgan kundan bir kun oldin tekshirish
    @Scheduled(cron = "0 0 9 * * *")
    public void checkBirthdayReminders() {
        log.info("Checking birthday reminders (1 day before)...");
        
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<User> allUsers = userService.getAllUsers();
        
        for (User user : allUsers) {
            if (user.getBirthDate() != null && user.getState() == User.UserState.REGISTERED) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                    LocalDate birthDate = LocalDate.parse(user.getBirthDate(), formatter);
                    
                    // Ertaga tug'ilgan kun bilan mos kelishini tekshirish (kun va oy)
                    if (birthDate.getDayOfMonth() == tomorrow.getDayOfMonth() && 
                        birthDate.getMonth() == tomorrow.getMonth()) {
                        sendBirthdayReminderToUser(user);
                    }
                } catch (Exception e) {
                    log.error("Error parsing birth date for user {}: {}", user.getTelegramId(), e.getMessage());
                }
            }
        }
    }
    
    // Har kuni soat 10:00 da tug'ilgan kunlarni tekshirish va voucher yaratish
    @Scheduled(cron = "0 0 10 * * *")
    public void checkBirthdays() {
        log.info("Checking user birthdays and creating vouchers...");
        
        LocalDate today = LocalDate.now();
        List<User> allUsers = userService.getAllUsers();
        
        for (User user : allUsers) {
            if (user.getBirthDate() != null && user.getState() == User.UserState.REGISTERED) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                    LocalDate birthDate = LocalDate.parse(user.getBirthDate(), formatter);
                    
                    // Bugungi kun tug'ilgan kun bilan mos kelishini tekshirish (kun va oy)
                    if (birthDate.getDayOfMonth() == today.getDayOfMonth() && 
                        birthDate.getMonth() == today.getMonth()) {
                        createBirthdayVoucherAndNotify(user);
                    }
                } catch (Exception e) {
                    log.error("Error parsing birth date for user {}: {}", user.getTelegramId(), e.getMessage());
                }
            }
        }
    }
    
    // Har kuni soat 11:00 da voucher reminder va expiry tekshirish
    @Scheduled(cron = "0 0 11 * * *")
    public void checkVoucherReminders() {
        log.info("Checking voucher reminders and expiry...");
        
        if (voucherService == null) {
            voucherService = applicationContext.getBean(VoucherService.class);
        }
        
        // Eski voucherlarni expire qilish
        voucherService.expireOldVouchers();
        
        // Reminder yuborish kerak bo'lgan voucherlarni topish
        List<Voucher> vouchersNeedingReminder = voucherService.getVouchersNeedingReminder();
        
        for (Voucher voucher : vouchersNeedingReminder) {
            sendVoucherReminderToUser(voucher);
            voucherService.markReminderSent(voucher);
        }
    }
    
    private void sendRegistrationAnniversaryNotification(User user) {
        String usernameInfo = user.getTelegramUsername() != null ? 
            user.getTelegramUsername() : "Username yo'q";
            
        String message = String.format(
            """
            🎉 6 Oylik Yubiley!
            
            👤 Foydalanuvchi: %s %s
            👤 Username: %s
            📱 Telefon: %s
            🎂 Tug'ilgan sana: %s
            📅 Ro'yxatdan o'tgan: %s
            🆔 Telegram ID: %d
            
            Bu foydalanuvchi 6 oy oldin botga ro'yxatdan o'tgan!
            """,
            user.getFirstName(),
            user.getLastName(),
            usernameInfo,
            user.getPhoneNumber(),
            user.getBirthDate(),
            user.getCreatedAt().toLocalDate(),
            user.getTelegramId()
        );
        
        sendNotificationToAdmin(message);
        log.info("Sent 6-month anniversary notification for user: {}", user.getTelegramId());
    }
    
    private void sendBirthdayReminderToUser(User user) {
        String message = getLocalizedBirthdayReminderMessage(user.getLanguage());
        sendMessageToUser(user.getTelegramId(), message);
        log.info("Sent birthday reminder to user: {}", user.getTelegramId());
    }
    
    private void createBirthdayVoucherAndNotify(User user) {
        try {
            if (voucherService == null) {
                voucherService = applicationContext.getBean(VoucherService.class);
            }
            
            // Birthday voucher yaratish
            Voucher voucher = voucherService.createBirthdayVoucher(user);
            
            // Foydalanuvchiga voucher haqida xabar yuborish
            String message = getLocalizedBirthdayVoucherMessage(user.getLanguage(), voucher.getCode());
            sendMessageToUser(user.getTelegramId(), message);
            
            // Adminga notification yuborish
            sendBirthdayNotificationToAdmin(user, voucher);
            
            log.info("Created birthday voucher {} for user: {}", voucher.getCode(), user.getTelegramId());
            
        } catch (Exception e) {
            log.error("Error creating birthday voucher for user {}: {}", user.getTelegramId(), e.getMessage());
        }
    }
    
    private void sendVoucherReminderToUser(Voucher voucher) {
        long daysLeft = voucher.getDaysUntilExpiry();
        String message = getLocalizedVoucherReminderMessage(voucher.getUser().getLanguage(), daysLeft);
        sendMessageToUser(voucher.getUser().getTelegramId(), message);
        log.info("Sent voucher reminder to user: {} for voucher: {}", voucher.getUser().getTelegramId(), voucher.getCode());
    }
    
    private String getLocalizedBirthdayReminderMessage(String language) {
        return switch (language != null ? language : "uz") {
            case "uz_cyrl" -> "Ассалому алайкум! Эртага туғилган кунингиз муносабати билан сизга совға тайёрлаб қўйдик. Тайёр туринг! 🎁";
            case "ru" -> "Ассалому алейкум! Завтра ваш день рождения, и мы приготовили для вас подарок. Будьте готовы! 🎁";
            default -> "Assalomu aleykum! Ertaga tug'ilgan kuningiz munosabati bilan sizga sovg'a tayyorlab qo'ydik. Tayyor turing! 🎁";
        };
    }
    
    private String getLocalizedBirthdayVoucherMessage(String language, String voucherCode) {
        return switch (language != null ? language : "uz") {
            case "uz_cyrl" -> String.format("""
                Ассалому алайкум! Туғилган кунингиз билан табриклайман! 🎉
                
                Сизга 50,000 сўмлик ваучер совға қилинди.
                Ваучер коди: %s
                
                Ушбу ваучер билан AYSI OPTICS га ташриф буюриб чегирмали маҳсулот харид қилишингиз мумкин.
                
                ⚠️ Ваучер 3 кун ичида амал қилади!
                
                🎂 Туғилган кунингиз муборак!
                """, voucherCode);
            case "ru" -> String.format("""
                Ассалому алейкум! Поздравляю с днем рождения! 🎉
                
                Вам подарен ваучер на 50,000 сум.
                Код ваучера: %s
                
                С этим ваучером вы можете посетить AYSI OPTICS и купить товары со скидкой.
                
                ⚠️ Ваучер действует 3 дня!
                
                🎂 С днем рождения!
                """, voucherCode);
            default -> String.format("""
                Assalomu aleykum! Tug'ilgan kuningiz bilan tabriklayman! 🎉
                
                Sizga 50,000 so'mlik voucher sovg'a qilindi.
                Voucher kodi: %s
                
                Ushbu voucher bilan AYSI OPTICS ga tashrif buyurib chegirmali mahsulot xarid qilishingiz mumkin.
                
                ⚠️ Voucher 3 kun ichida amal qiladi!
                
                🎂 Tug'ilgan kuningiz muborak!
                """, voucherCode);
        };
    }
    
    private String getLocalizedVoucherReminderMessage(String language, long daysLeft) {
        return switch (language != null ? language : "uz") {
            case "uz_cyrl" -> String.format("⚠️ Эслатма: Ваучерингиз амал қилиш муддати тугашига %d кун қолди! Улгириб қолинг! 🏃‍♂️", daysLeft);
            case "ru" -> String.format("⚠️ Напоминание: До истечения срока действия вашего ваучера осталось %d дней! Успейте воспользоваться! 🏃‍♂️", daysLeft);
            default -> String.format("⚠️ Eslatma: Voucheringiz amal qilish muddati tugashiga %d kun qoldi! Ulgurib qoling! 🏃‍♂️", daysLeft);
        };
    }
    
    private void sendBirthdayNotificationToAdmin(User user, Voucher voucher) {
        String usernameInfo = user.getTelegramUsername() != null ? 
            user.getTelegramUsername() : "Username yo'q";
            
        String message = String.format(
            """
            🎂 Tug'ilgan Kun Voucher Yaratildi!
            
            👤 Foydalanuvchi: %s %s
            👤 Username: %s
            📱 Telefon: %s
            🎂 Tug'ilgan sana: %s
            🆔 Telegram ID: %d
            
            🎟️ Voucher kodi: %s
            💰 Miqdor: %,d so'm
            ⏰ Amal qilish muddati: 3 kun
            📅 Yaratilgan: %s
            
            Foydalanuvchiga birthday voucher yuborildi!
            """,
            user.getFirstName(),
            user.getLastName(),
            usernameInfo,
            user.getPhoneNumber(),
            user.getBirthDate(),
            user.getTelegramId(),
            voucher.getCode(),
            voucher.getAmount(),
            voucher.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        );
        
        sendNotificationToAdmin(message);
    }
    
    private void sendMessageToUser(Long telegramId, String message) {
        TelegramLongPollingBot bot = applicationContext.getBean("kuponBot", TelegramLongPollingBot.class);
        
        try {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(telegramId);
            sendMessage.setText(message);
            
            bot.execute(sendMessage);
            log.info("Message sent to user: {}", telegramId);
            
        } catch (TelegramApiException e) {
            log.error("Error sending message to user {}: {}", telegramId, e.getMessage());
        }
    }
    
    private void sendNotificationToAdmin(String message) {
        String[] adminIds = adminTelegramIds.split(",");
        
        // ApplicationContext orqali KuponBot'ni olish (circular dependency'dan qochish uchun)
        TelegramLongPollingBot bot = applicationContext.getBean("kuponBot", TelegramLongPollingBot.class);
        
        for (String adminIdStr : adminIds) {
            try {
                Long adminId = Long.parseLong(adminIdStr.trim());
                
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(adminId);
                sendMessage.setText(message);
                
                bot.execute(sendMessage);
                log.info("Notification sent to admin: {}", adminId);
                
            } catch (NumberFormatException e) {
                log.error("Invalid admin ID format: {}", adminIdStr);
            } catch (TelegramApiException e) {
                log.error("Error sending notification to admin {}: {}", adminIdStr, e.getMessage());
            }
        }
    }
    
    // Manual test uchun
    public void testNotifications() {
        log.info("Testing notification system...");
        
        String testMessage = """
            🧪 Test Xabar
            
            Notification tizimi ishlayapti!
            Vaqt: %s
            """.formatted(LocalDateTime.now());
        
        sendNotificationToAdmin(testMessage);
    }
    
    private void sendThreeMinuteRegistrationNotification(User user) {
        String usernameInfo = user.getTelegramUsername() != null ? 
            user.getTelegramUsername() : "Username yo'q";
            
        String message = String.format(
            """
            🆕 3 Daqiqalik Test Notification!
            
            👤 Yangi foydalanuvchi: %s %s
            👤 Username: %s
            📱 Telefon: %s
            🎂 Tug'ilgan sana: %s
            📅 Ro'yxatdan o'tgan: %s
            🆔 Telegram ID: %d
            ⏰ 3 daqiqa oldin ro'yxatdan o'tdi!
            
            Bu test notification - haqiqiy tizimda 6 oy va tug'ilgan kunlar uchun ishlaydi.
            """,
            user.getFirstName(),
            user.getLastName(),
            usernameInfo,
            user.getPhoneNumber(),
            user.getBirthDate() != null ? user.getBirthDate() : "Kiritilmagan",
            user.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
            user.getTelegramId()
        );
        
        sendNotificationToAdmin(message);
        log.info("Sent 3-minute registration notification for user: {}", user.getTelegramId());
    }
    
    // 6 oylik yubiley test uchun
    public void testSixMonthAnniversary() {
        log.info("Testing 6-month anniversary notifications...");
        checkRegistrationAnniversary();
    }
    
    // Tug'ilgan kun test uchun  
    public void testBirthdays() {
        log.info("Testing birthday notifications...");
        checkBirthdays();
    }
    
    // Test 3 daqiqalik registration uchun
    public void testThreeMinuteRegistrations() {
        log.info("Testing 3-minute registration notifications...");
        checkThreeMinuteRegistrations();
    }
    
    // Test voucher reminders uchun
    public void testVoucherReminders() {
        log.info("Testing voucher reminder notifications...");
        checkVoucherReminders();
    }
}
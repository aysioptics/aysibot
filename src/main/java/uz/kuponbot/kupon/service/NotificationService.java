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
    
    // 3 kunlik notification yuborilgan foydalanuvchilarni saqlash
    private final java.util.Set<Long> notifiedUsers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    
    // Har kuni soat 12:00 da 3 kunlik registratsiyalarni tekshirish
    @Scheduled(cron = "0 0 12 * * *") // Har kuni soat 12:00 da
    public void checkThreeDayRegistrations() {
        log.info("Checking 3-day registrations...");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeDaysAgo = now.minusDays(3);
        LocalDateTime fourDaysAgo = now.minusDays(4);
        
        List<User> allUsers = userService.getAllUsers();
        
        for (User user : allUsers) {
            if (user.getCreatedAt() != null && user.getState() == User.UserState.REGISTERED) {
                // Aniq 3 kun oldin ro'yxatdan o'tgan foydalanuvchilarni topish
                // 3-4 kun oralig'ida ro'yxatdan o'tganlarni tekshirish
                if (user.getCreatedAt().isAfter(fourDaysAgo) && 
                    user.getCreatedAt().isBefore(threeDaysAgo)) {
                    
                    // Agar bu foydalanuvchiga notification yuborilmagan bo'lsa
                    if (!notifiedUsers.contains(user.getTelegramId())) {
                        log.info("Found user registered 3 days ago: {} at {}", 
                            user.getTelegramId(), user.getCreatedAt());
                        
                        sendThreeDayRegistrationNotification(user);
                        notifiedUsers.add(user.getTelegramId()); // Yuborilganligini belgilash
                    }
                }
            }
        }
    }
    
    // Har kuni soat 13:00 da 15 kunlik registratsiyalarni tekshirish (ko'zoynak parvarishi)
    @Scheduled(cron = "0 0 13 * * *") // Har kuni soat 13:00 da
    public void checkFifteenDayRegistrations() {
        log.info("Checking 15-day registrations for eyewear care reminder...");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fifteenDaysAgo = now.minusDays(15);
        LocalDateTime sixteenDaysAgo = now.minusDays(16);
        
        List<User> allUsers = userService.getAllUsers();
        
        for (User user : allUsers) {
            if (user.getCreatedAt() != null && user.getState() == User.UserState.REGISTERED) {
                // Aniq 15 kun oldin ro'yxatdan o'tgan foydalanuvchilarni topish
                if (user.getCreatedAt().isAfter(sixteenDaysAgo) && 
                    user.getCreatedAt().isBefore(fifteenDaysAgo)) {
                    
                    log.info("Found user registered 15 days ago: {} at {}", 
                        user.getTelegramId(), user.getCreatedAt());
                    
                    sendEyewearCareReminder(user);
                }
            }
        }
    }
    
    // Har kuni soat 14:00 da 3 oylik registratsiyalarni tekshirish (ko'z tekshiruvi eslatmasi)
    @Scheduled(cron = "0 0 14 * * *") // Har kuni soat 14:00 da
    public void checkThreeMonthRegistrations() {
        log.info("Checking 3-month registrations for eye checkup reminder...");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeMonthsAgo = now.minusMonths(3);
        LocalDateTime threeMonthsAndOneDayAgo = now.minusMonths(3).minusDays(1);
        
        List<User> allUsers = userService.getAllUsers();
        
        for (User user : allUsers) {
            if (user.getCreatedAt() != null && user.getState() == User.UserState.REGISTERED) {
                // Aniq 3 oy oldin ro'yxatdan o'tgan foydalanuvchilarni topish
                if (user.getCreatedAt().isAfter(threeMonthsAndOneDayAgo) && 
                    user.getCreatedAt().isBefore(threeMonthsAgo)) {
                    
                    log.info("Found user registered 3 months ago: {} at {}", 
                        user.getTelegramId(), user.getCreatedAt());
                    
                    sendEyeCheckupReminder(user);
                }
            }
        }
    }
    
    // Har kuni soat 15:00 da 6 oylik registratsiyalarni tekshirish (bepul konsultatsiya)
    @Scheduled(cron = "0 0 15 * * *") // Har kuni soat 15:00 da
    public void checkSixMonthRegistrations() {
        log.info("Checking 6-month registrations for free consultation reminder...");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sixMonthsAgo = now.minusMonths(6);
        LocalDateTime sixMonthsAndOneDayAgo = now.minusMonths(6).minusDays(1);
        
        List<User> allUsers = userService.getAllUsers();
        
        for (User user : allUsers) {
            if (user.getCreatedAt() != null && user.getState() == User.UserState.REGISTERED) {
                // Aniq 6 oy oldin ro'yxatdan o'tgan foydalanuvchilarni topish
                if (user.getCreatedAt().isAfter(sixMonthsAndOneDayAgo) && 
                    user.getCreatedAt().isBefore(sixMonthsAgo)) {
                    
                    log.info("Found user registered 6 months ago: {} at {}", 
                        user.getTelegramId(), user.getCreatedAt());
                    
                    sendFreeConsultationReminder(user);
                }
            }
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
            case "uz_cyrl" -> """
                Ассалому алайкум! Эртага сиз учун Aysi Optika томонидан кичик, аммо жуда ёқимли совға тайёрлаб қўйдик 🎁
                
                Бу совға сизнинг кўз саломатлигингиз ва қувончингиз учун тайёрланган… 👀✨
                
                Биз сизни хурсанд қилишни интиқлик билан кутяпмиз, эртага боғланамиз… 😉
                """;
            case "ru" -> """
                Ассалому алейкум! Завтра для вас Aysi Optika приготовила небольшой, но очень приятный подарок 🎁
                
                Этот подарок подготовлен для вашего здоровья глаз и радости… 👀✨
                
                Мы с нетерпением ждем возможности порадовать вас, свяжемся завтра… 😉
                """;
            default -> """
                Assalomu alaykum! Ertaga siz uchun Aysi Optika tomonidan kichik, ammo juda yoqimli sovg'a tayyorlab qo'ydik 🎁
                
                Bu sovg'a sizning ko'z salomatligingiz va quvonchingiz uchun tayyorlangan… 👀✨
                
                Biz sizni xursand qilishni intiqlik bilan kutyapmiz, ertaga bog'lanamiz… 😉
                """;
        };
    }
    
    private String getLocalizedBirthdayVoucherMessage(String language, String voucherCode) {
        return switch (language != null ? language : "uz") {
            case "uz_cyrl" -> String.format("""
                Ҳурматли мижозимиз! 🎉
                
                Бугунги туғилган кунингиз билан самимий табриклаймиз! Сизга мустаҳкам соғлиқ, қувонч ва ёрқин кунлар тилаймиз. Кўзингиз доимо равшан, нигоҳингиз эса ҳаётнинг энг гўзал рангларини кўра олсин 🤍
                
                🥳 Aysi Optika sizga бўлган миннатдорчилигимиз ва ғамхўрлигимиз рамзи сифатида "50 000 сўмлик совға ваучер" тақдим этади.
                
                🎁 Ваучер коди: %s
                ⏳ Амал қилиш муддати: 3 кун
                
                Vaucherдан фойдаланиш учун уни бизнинг оптика салонимизга ташриф буюрганингизда администраторга кўрсатинг. Ушбу ваучер кўзойнак, линза ёки бошқа оптик маҳсулотлар харидида амал қилади.
                
                Сизнинг кўз саломатлигингиз биз учун муҳим. Ҳар доим сизга янада тиниқ кўриш ва чиройли кўзойнаклар билан хизмат қилишдан мамнунмиз.
                """, voucherCode);
            case "ru" -> String.format("""
                Уважаемый клиент! 🎉
                
                Искренне поздравляем вас с сегодняшним днем рождения! Желаем вам крепкого здоровья, радости и ярких дней. Пусть ваши глаза всегда будут ясными, а взгляд видит самые прекрасные краски жизни 🤍
                
                🥳 Aysi Optika дарит вам "подарочный ваучер на 50 000 сум" в знак нашей благодарности и заботы.
                
                🎁 Код ваучера: %s
                ⏳ Срок действия: 3 дня
                
                Чтобы воспользоваться ваучером, покажите его администратору при посещении нашего оптического салона. Этот ваучер действует при покупке очков, линз или других оптических товаров.
                
                Здоровье ваших глаз важно для нас. Мы всегда рады служить вам более четким зрением и красивыми очками.
                """, voucherCode);
            default -> String.format("""
                Hurmatli mijozimiz! 🎉
                
                Bugungi tug'ilgan kuningiz bilan samimiy tabriklaymiz! Sizga mustahkam sog'liq, quvonch va yorqin kunlar tilaymiz. Ko'zingiz doimo ravshan, nigohingiz esa hayotning eng go'zal ranglarini ko'ra olsin 🤍
                
                🥳 Aysi Optika sizga bo'lgan minnatdorchiligimiz va g'amxo'rligimiz ramzi sifatida "50 000 so'mlik sovg'a vaucher" taqdim etadi.
                
                🎁 Vaucher kodi: %s
                ⏳ Amal qilish muddati: 3 kun
                
                Vaucherdan foydalanish uchun uni bizning optika salonimizga tashrif buyurganingizda administratorga ko'rsating. Ushbu vaucher ko'zoynak, linza yoki boshqa optik mahsulotlar xaridida amal qiladi.
                
                Sizning ko'z salomatligingiz biz uchun muhim. Har doim sizga yanada tiniq ko'rish va chiroyli ko'zoynaklar bilan xizmat qilishdan mamnunmiz.
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
    
    private void sendThreeDayRegistrationNotification(User user) {
        String usernameInfo = user.getTelegramUsername() != null ? 
            user.getTelegramUsername() : "Username yo'q";
            
        String message = String.format(
            """
            🆕 3 Kunlik Notification!
            
            👤 Yangi foydalanuvchi: %s %s
            👤 Username: %s
            📱 Telefon: %s
            🎂 Tug'ilgan sana: %s
            📅 Ro'yxatdan o'tgan: %s
            🆔 Telegram ID: %d
            ⏰ 3 kun oldin ro'yxatdan o'tdi!
            
            Bu foydalanuvchi 3 kun oldin botga ro'yxatdan o'tgan.
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
        log.info("Sent 3-day registration notification for user: {}", user.getTelegramId());
    }
    
    private void sendEyewearCareReminder(User user) {
        String message = getLocalizedEyewearCareMessage(user.getLanguage());
        sendMessageToUser(user.getTelegramId(), message);
        log.info("Sent 15-day eyewear care reminder to user: {}", user.getTelegramId());
    }
    
    private void sendEyeCheckupReminder(User user) {
        String message = getLocalizedEyeCheckupMessage(user.getLanguage());
        sendMessageToUser(user.getTelegramId(), message);
        log.info("Sent 3-month eye checkup reminder to user: {}", user.getTelegramId());
    }
    
    private void sendFreeConsultationReminder(User user) {
        String message = getLocalizedFreeConsultationMessage(user.getLanguage());
        sendMessageToUser(user.getTelegramId(), message);
        log.info("Sent 6-month free consultation reminder to user: {}", user.getTelegramId());
    }
    
    private String getLocalizedEyewearCareMessage(String language) {
        return switch (language != null ? language : "uz") {
            case "uz_cyrl" -> """
                Ҳурматли мижоз! 🤍
                
                Соғлигингизга эътиборли бўлганингиз ва уни бизга ишонганингиз учун ташаккур билдирамиз. Сиз харид қилган кўзойнак сизга узоқ вақт хизмат қилиши учун қуйидаги қоидаларга амал қилишингизни сўраймиз:
                
                🧼 Кўзойнакларни илиқ сув ва юмшоқ ювиш воситаси билан ювиб, фақат махсус салфетка билан артинг.
                🙌 Кўзойнакни тақиш ва ечишда икки қўлдан фойдаланинг — бу рамка ва маҳкамлагичларнинг шикастланишидан сақлайди.
                🕶 Кўзойнакларни ички қисми юмшоқ бўлган қаттиқ футлярда сақланг.
                🚫 Кўзойнакларни линзалари пастга қаратиб қўйманг.
                🚿 Душ, сауна, бассейн ва денгиз сувида кўзойнак тақиб юриш тавсия этилмайди.
                🔥 Кўзойнакларни очиқ олов, иссиқлик манбалари ёки автомобил панели яқинида қолдирманг.
                💥 Кўзойнакларни зарба ва кучли механик таъсирлардан асранг.
                🔧 Эслатиб ўтамиз, барча ҳаракатланувчи қисмлар ойига камида бир марта текширув ва маҳкамлашни талаб қилади.
                ✨ Шунингдек, кўзойнакларни ҳар 3 ойда бир марта ультратовушли тозалашга олиб келишингиз тавсия этилади.
                🛠 Агар кўзойнагингизда бирор носозлик юзага келса уни таъмирлаш Aysi Optika мутахассислари томонидан бепул амалга оширилади.
                
                Кўзойнаклардан фойдаланиш қоидаларига амал қилсангиз, улар сизга узоқ йиллар хизмат қилади.
                
                Ҳурмат билан, Aysi Optika жамоаси.
                """;
            case "ru" -> """
                Уважаемый клиент! 🤍
                
                Благодарим вас за внимание к своему здоровью и доверие к нам. Чтобы приобретенные вами очки служили вам долго, просим соблюдать следующие правила:
                
                🧼 Мойте очки теплой водой с мягким моющим средством и протирайте только специальной салфеткой.
                🙌 Надевайте и снимайте очки двумя руками — это защитит оправу и крепления от повреждений.
                🕶 Храните очки в жестком футляре с мягкой внутренней частью.
                🚫 Не кладите очки линзами вниз.
                🚿 Не рекомендуется носить очки в душе, сауне, бассейне и морской воде.
                🔥 Не оставляйте очки вблизи открытого огня, источников тепла или панели автомобиля.
                💥 Берегите очки от ударов и сильных механических воздействий.
                🔧 Напоминаем, что все подвижные части требуют проверки и затяжки не реже одного раза в месяц.
                ✨ Также рекомендуется приносить очки на ультразвуковую чистку каждые 3 месяца.
                🛠 Если в ваших очках возникнет какая-либо неисправность, ремонт будет выполнен специалистами Aysi Optika бесплатно.
                
                Соблюдая правила использования очков, они прослужат вам долгие годы.
                
                С уважением, команда Aysi Optika.
                """;
            default -> """
                Hurmatli mijoz! 🤍
                
                Sog'lig'ingizga e'tiborli bo'lganingiz va uni bizga ishonganingiz uchun tashakkur bildiramiz. Siz harid qilgan ko'zoynak sizga uzoq vaqt xizmat qilishi uchun quyidagi qoidalarga amal qilishingizni so'raymiz:
                
                🧼 Ko'zoynaklarni iliq suv va yumshoq yuvish vositasi bilan yuvib, faqat maxsus salfetka bilan arting.
                🙌 Ko'zoynakni taqish va yechishda ikki qo'ldan foydalaning — bu ramka va mahkamlagichlarning shikastlanishidan saqlaydi.
                🕶 Ko'zoynaklarni ichki qismi yumshoq bo'lgan qattiq futlyarda saqlang.
                🚫 Ko'zoynaklarni linzalari pastga qaratib qo'ymang.
                🚿 Dush, sauna, basseyn va dengiz suvida ko'zoynak taqib yurish tavsiya etilmaydi.
                🔥 Ko'zoynaklarni ochiq olov, issiqlik manbalari yoki avtomobil paneli yaqinida qoldirmang.
                💥 Ko'zoynaklarni zarba va kuchli mexanik ta'sirlardan asrang.
                🔧 Eslatib o'tamiz, barcha harakatlanuvchi qismlar oyiga kamida bir marta tekshiruv va mahkamlashni talab qiladi.
                ✨ Shuningdek, ko'zoynaklarni har 3 oyda bir marta ultratovushli tozalashga olib kelishingiz tavsiya etiladi.
                🛠 Agar ko'zoynagingizda biror nosozlik yuzaga kelsa uni ta'mirlash Aysi Optika mutaxassislari tomonidan bepul amalga oshiriladi.
                
                Ko'zoynaklardan foydalanish qoidalariga amal qilsangiz, ular sizga uzoq yillar xizmat qiladi.
                
                Hurmat bilan, Aysi Optika jamoasi.
                """;
        };
    }
    
    private String getLocalizedEyeCheckupMessage(String language) {
        return switch (language != null ? language : "uz") {
            case "uz_cyrl" -> """
                🔍 Олимлар айтишича, кўриш қобилиятини сақлаб қолиш учун кўз текширувини ҳар 6 ойда бир марта ўтказиш тавсия этилади.
                
                Мунтазам текширув:
                ✅ Кўриш ўткирлиги ёмонлашиб кетишини олдини олади
                ✅ Чарчаш синдроми олди олинади (бош оғриғи, доимий ҳолсизлик, кўзлар тез чарчаши, …)
                ✅ Даволаш таъсирини оширади.
                
                Кўз соғлигингизни эътиборсиз қолдирманг. 🤍
                
                Ҳурмат билан, Aysi Optika жамоаси.
                """;
            case "ru" -> """
                🔍 Ученые утверждают, что для сохранения зрения рекомендуется проходить проверку зрения каждые 6 месяцев.
                
                Регулярная проверка:
                ✅ Предотвращает ухудшение остроты зрения
                ✅ Предупреждает синдром усталости (головная боль, постоянная слабость, быстрая утомляемость глаз, …)
                ✅ Повышает эффективность лечения.
                
                Не оставляйте здоровье глаз без внимания. 🤍
                
                С уважением, команда Aysi Optika.
                """;
            default -> """
                🔍 Olimlar aytishicha, ko'rish qobiliyatini saqlab qolish uchun ko'z tekshiruvini har 6 oyda bir marta o'tkazish tavsiya etiladi.
                
                Muntazam tekshiruv:
                ✅ Ko'rish o'tkirligi yomonlashib ketishini oldini oladi
                ✅ Charchash sindromi oldi olinadi (bosh og'rigi, doimiy holsizlik, ko'zlar tez charchashi, …)
                ✅ Davolash ta'sirini oshiradi.
                
                Ko'z sog'lig'ingizni e'tiborsiz qoldirmang. 🤍
                
                Hurmat bilan, Aysi Optika jamoasi.
                """;
        };
    }
    
    private String getLocalizedFreeConsultationMessage(String language) {
        return switch (language != null ? language : "uz") {
            case "uz_cyrl" -> """
                🧠 Мутахассислар таъкидлашича, кўз саломатлигини назорат қилиш учун кўз текширувини мунтазам равишда ўтказиб туриш муҳим.
                
                Вақтида текширувдан ўтиш кўришдаги ўзгаришларни аниқлаш ва даволаш таъсирини оширишда ёрдам беради.
                
                ❗️Эслатма: Сиз кўзойнак харид қилганингизга 6 ой бўлибди. Ҳозирда сиз учун бепул шифокор консультациясига ёзилиш имкониятини мавжуд.
                
                📩 Ёзилиш учун биз билан боғланинг
                ☎️ +998 93 874 03 05
                """;
            case "ru" -> """
                🧠 Специалисты подчеркивают, что для контроля здоровья глаз важно регулярно проходить проверку зрения.
                
                Своевременная проверка помогает выявить изменения в зрении и повысить эффективность лечения.
                
                ❗️Напоминание: Прошло 6 месяцев с момента покупки очков. Сейчас для вас доступна возможность записаться на бесплатную консультацию врача.
                
                📩 Для записи свяжитесь с нами
                ☎️ +998 93 874 03 05
                """;
            default -> """
                🧠 Mutaxassislar ta'kidlashicha, ko'z salomatligini nazorat qilish uchun ko'z tekshiruvini muntazam ravishda o'tkazib turish muhim.
                
                Vaqtida tekshiruvdan o'tish ko'rishdagi o'zgarishlarni aniqlash va davolash ta'sirini oshirishda yordam beradi.
                
                ❗️Eslatma: Siz ko'zoynak xarid qilganingizga 6 oy bo'libdi. Hozirda siz uchun bepul shifokor konsultatsiyasiga yozilish imkoniyati mavjud.
                
                📩 Yozilish uchun biz bilan bog'laning
                ☎️ +998 93 874 03 05
                """;
        };
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
    
    // Test 3 kunlik registration uchun
    public void testThreeDayRegistrations() {
        log.info("Testing 3-day registration notifications...");
        checkThreeDayRegistrations();
    }
    
    // Test voucher reminders uchun
    public void testVoucherReminders() {
        log.info("Testing voucher reminder notifications...");
        checkVoucherReminders();
    }
}
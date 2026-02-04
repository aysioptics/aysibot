package uz.kuponbot.kupon.bot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.entity.Coupon;
import uz.kuponbot.kupon.entity.Order;
import uz.kuponbot.kupon.entity.User;
import uz.kuponbot.kupon.service.BroadcastService;
import uz.kuponbot.kupon.service.CouponService;
import uz.kuponbot.kupon.service.NotificationService;
import uz.kuponbot.kupon.service.OrderService;
import uz.kuponbot.kupon.service.UserService;

@Component
@RequiredArgsConstructor
@Slf4j
public class KuponBot extends TelegramLongPollingBot {
    
    private final UserService userService;
    private final CouponService couponService;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final BroadcastService broadcastService;
    
    @Value("${telegram.bot.token}")
    private String botToken;
    
    @Value("${telegram.bot.username}")
    private String botUsername;
    
    @Value("${telegram.channel.username}")
    private String channelUsername;
    
    @Value("${telegram.channel.id}")
    private String channelId;
    
    @Override
    public String getBotToken() {
        return botToken;
    }
    
    @Override
    public String getBotUsername() {
        return botUsername;
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            Long userId = message.getFrom().getId();
            
            try {
                handleMessage(message, chatId, userId);
            } catch (Exception e) {
                log.error("Error processing message: ", e);
                // Foydalanuvchining tilini aniqlash
                Optional<User> userOpt = userService.findByTelegramId(userId);
                String errorMessage = "Произошла ошибка. Пожалуйста, попробуйте еще раз.";
                if (userOpt.isPresent() && "uz".equals(userOpt.get().getLanguage())) {
                    errorMessage = "Xatolik yuz berdi. Iltimos, qaytadan urinib ko'ring.";
                }
                sendMessage(chatId, errorMessage);
            }
        }
    }
    
    private void handleMessage(Message message, Long chatId, Long userId) {
        // ✅ HAR SAFAR bazadan yangisini yuklash
        Optional<User> userOpt = userService.findByTelegramId(userId);
        
        if (userOpt.isEmpty()) {
            User newUser = userService.createUser(userId);
            if (message.getFrom().getUserName() != null) {
                newUser.setTelegramUsername("@" + message.getFrom().getUserName());
                userService.save(newUser);
            }
            sendWelcomeMessage(chatId);
            return;
        }
        
        User user = userOpt.get();
        
        // ✅ MUHIM: User ma'lumotlarini logga yozish
        log.info("User {} current state: {}, language: {}", 
            user.getTelegramId(), user.getState(), user.getLanguage());
        
        // Username'ni yangilash
        if (message.getFrom().getUserName() != null) {
            String currentUsername = "@" + message.getFrom().getUserName();
            if (!currentUsername.equals(user.getTelegramUsername())) {
                user.setTelegramUsername(currentUsername);
                user = userService.save(user);
            }
        }
        
        switch (user.getState()) {
            case START -> {
                if (message.hasText() && "/start".equals(message.getText())) {
                    sendWelcomeMessage(chatId);
                } else {
                    user.setState(User.UserState.WAITING_LANGUAGE);
                    user = userService.save(user);
                    sendWelcomeMessage(chatId);
                }
            }
            case WAITING_LANGUAGE -> handleLanguageState(message, user, chatId);
            case WAITING_CONTACT -> handleContactState(message, user, chatId);
            case WAITING_FIRST_NAME -> handleFirstNameState(message, user, chatId);
            case WAITING_LAST_NAME -> handleLastNameState(message, user, chatId);
            case WAITING_BIRTH_DATE -> handleBirthDateState(message, user, chatId);
            case WAITING_CHANNEL_SUBSCRIPTION -> handleChannelSubscriptionState(message, user, chatId);
            case REGISTERED -> handleRegisteredUserCommands(message, user, chatId);
            default -> {
                user.setState(User.UserState.START);
                userService.save(user);
                sendWelcomeMessage(chatId);
            }
        }
    }
    
    private void sendWelcomeMessage(Long chatId) {
        String welcomeText = "🎉 Kupon botiga xush kelibsiz!\n\n" +
                "Iltimos, tilni tanlang / Пожалуйста, выберите язык:";
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(welcomeText);
        sendMessage.setReplyMarkup(createLanguageKeyboard());
        
        sendMessage(sendMessage);
    }
    
    private ReplyKeyboardMarkup createLanguageKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);
        
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        
        row.add("🇺🇿 O'zbek tili");
        row.add("🇷🇺 Русский язык");
        
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        
        return keyboardMarkup;
    }
    
    private ReplyKeyboardMarkup createContactKeyboard() {
        return createContactKeyboard("📱 Telefon raqamni yuborish");
    }
    
    private ReplyKeyboardMarkup createContactKeyboard(String buttonText) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);
        
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        
        KeyboardButton contactButton = new KeyboardButton();
        contactButton.setText(buttonText);
        contactButton.setRequestContact(true);
        
        row.add(contactButton);
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        
        return keyboardMarkup;
    }
    
    private void handleLanguageState(Message message, User user, Long chatId) {
        log.info("handleLanguageState called for user {} with current language: {}", user.getTelegramId(), user.getLanguage());
        
        if (message.hasText()) {
            String text = message.getText();
            log.info("User {} sent text: '{}'", user.getTelegramId(), text);
            
            if ("🇺🇿 O'zbek tili".equals(text)) {
                log.info("User {} selected Uzbek language", user.getTelegramId());
                log.info("Before setting language: {}", user.getLanguage());
                user.setLanguage("uz");
                user.setState(User.UserState.WAITING_CONTACT);
                log.info("After setting language: {}", user.getLanguage());
                User savedUser = userService.save(user);
                log.info("User {} language saved as: {}", savedUser.getTelegramId(), savedUser.getLanguage());
                
                sendContactRequestMessage(chatId, "uz");
            } else if ("🇷🇺 Русский язык".equals(text)) {
                log.info("User {} selected Russian language", user.getTelegramId());
                log.info("Before setting language: {}", user.getLanguage());
                user.setLanguage("ru");
                user.setState(User.UserState.WAITING_CONTACT);
                log.info("After setting language: {}", user.getLanguage());
                User savedUser = userService.save(user);
                log.info("User {} language saved as: {}", savedUser.getTelegramId(), savedUser.getLanguage());
                
                sendContactRequestMessage(chatId, "ru");
            } else {
                // Foydalanuvchining hozirgi tilini tekshirish
                log.info("User {} sent invalid language selection: {}, current language: {}", 
                    user.getTelegramId(), text, user.getLanguage());
                String errorMessage = "Iltimos, tilni tanlang / Пожалуйста, выберите язык";
                if ("ru".equals(user.getLanguage())) {
                    errorMessage = "Пожалуйста, выберите язык";
                } else if ("uz".equals(user.getLanguage())) {
                    errorMessage = "Iltimos, tilni tanlang";
                }
                sendMessage(chatId, errorMessage);
            }
        } else {
            // Foydalanuvchining hozirgi tilini tekshirish
            log.info("User {} sent non-text message in language state, current language: {}", 
                user.getTelegramId(), user.getLanguage());
            String errorMessage = "Iltimos, tilni tanlang / Пожалуйста, выберите язык";
            if ("ru".equals(user.getLanguage())) {
                errorMessage = "Пожалуйста, выберите язык";
            } else if ("uz".equals(user.getLanguage())) {
                errorMessage = "Iltimos, tilni tanlang";
            }
            sendMessage(chatId, errorMessage);
        }
    }
    
    private void sendContactRequestMessage(Long chatId, String language) {
        String contactText;
        String buttonText;
        
        if ("uz".equals(language)) {
            contactText = "✅ Til tanlandi: O'zbek tili\n\n" +
                    "Ro'yxatdan o'tish uchun telefon raqamingizni yuboring.";
            buttonText = "📱 Telefon raqamni yuborish";
        } else {
            contactText = "✅ Язык выбран: Русский\n\n" +
                    "Для регистрации отправьте свой номер телефона.";
            buttonText = "📱 Отправить номер телефона";
        }
        
        log.info("Sending contact request message to chatId: {} with language: {}", chatId, language);
        log.info("Contact text: {}", contactText);
        log.info("Button text: {}", buttonText);
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(contactText);
        sendMessage.setReplyMarkup(createContactKeyboard(buttonText));
        
        sendMessage(sendMessage);
    }
    
    private void handleContactState(Message message, User user, Long chatId) {
        String lang = user.getLanguage(); // ✅ faqat shu
        log.info("handleContactState called for user {} with language: {}", 
            user.getTelegramId(), lang);
        
        if (message.hasContact()) {
            user.setPhoneNumber(message.getContact().getPhoneNumber());
            user.setState(User.UserState.WAITING_FIRST_NAME);
            userService.save(user);
            
            if ("ru".equals(lang)) {
                sendMessage(chatId, "✅ Номер телефона принят!\n\nТеперь введите ваше имя:");
            } else {
                sendMessage(chatId, "✅ Telefon raqam qabul qilindi!\n\nEndi ismingizni kiriting:");
            }
        } else {
            if ("ru".equals(lang)) {
                SendMessage sm = new SendMessage();
                sm.setChatId(chatId);
                sm.setText("❌ Пожалуйста, нажмите кнопку отправки номера телефона.");
                sm.setReplyMarkup(createContactKeyboard("📱 Отправить номер телефона"));
                sendMessage(sm);
            } else {
                SendMessage sm = new SendMessage();
                sm.setChatId(chatId);
                sm.setText("❌ Iltimos, telefon raqamingizni yuborish tugmasini bosing.");
                sm.setReplyMarkup(createContactKeyboard("📱 Telefon raqamni yuborish"));
                sendMessage(sm);
            }
        }
    }

    private void handleFirstNameState(Message message, User user, Long chatId) {
        if (message.hasText()) {
            String firstName = message.getText().trim();
            if (firstName.length() >= 2) {
                user.setFirstName(firstName);
                user.setState(User.UserState.WAITING_LAST_NAME);
                userService.save(user);
                
                String successMessage;
                
                if ("ru".equals(user.getLanguage())) {
                    successMessage = "✅ Имя принято!\n\nТеперь введите вашу фамилию:";
                } else {
                    successMessage = "✅ Ism qabul qilindi!\n\nEndi familiyangizni kiriting:";
                }
                
                sendMessage(chatId, successMessage);
            } else {
                String errorMessage;
                
                if ("ru".equals(user.getLanguage())) {
                    errorMessage = "❌ Имя должно содержать минимум 2 символа.";
                } else {
                    errorMessage = "❌ Ism kamida 2 ta harfdan iborat bo'lishi kerak.";
                }
                
                sendMessage(chatId, errorMessage);
            }
        } else {
            String errorMessage;
            
            if ("ru".equals(user.getLanguage())) {
                errorMessage = "❌ Пожалуйста, отправьте ваше имя в текстовом виде.";
            } else {
                errorMessage = "❌ Iltimos, ismingizni matn ko'rinishida yuboring.";
            }
            
            sendMessage(chatId, errorMessage);
        }
    }
    
    private void handleLastNameState(Message message, User user, Long chatId) {
        if (message.hasText()) {
            String lastName = message.getText().trim();
            if (lastName.length() >= 2) {
                user.setLastName(lastName);
                user.setState(User.UserState.WAITING_BIRTH_DATE);
                userService.save(user);
                
                String successMessage;
                if ("ru".equals(user.getLanguage())) {
                    successMessage = "✅ Фамилия принята!\n\nТеперь введите дату рождения (в формате ДД.ММ.ГГГГ):\n\nПример: 15.03.1995";
                } else {
                    successMessage = "✅ Familiya qabul qilindi!\n\nEndi tug'ilgan sanangizni kiriting (DD.MM.YYYY formatida):\n\nMisol: 15.03.1995";
                }
                
                sendMessage(chatId, successMessage);
            } else {
                String errorMessage;
                if ("ru".equals(user.getLanguage())) {
                    errorMessage = "❌ Фамилия должна содержать минимум 2 символа.";
                } else {
                    errorMessage = "❌ Familiya kamida 2 ta harfdan iborat bo'lishi kerak.";
                }
                sendMessage(chatId, errorMessage);
            }
        } else {
            String errorMessage;
            if ("ru".equals(user.getLanguage())) {
                errorMessage = "❌ Пожалуйста, отправьте фамилию в текстовом виде.";
            } else {
                errorMessage = "❌ Iltimos, familiyangizni matn ko'rinishida yuboring.";
            }
            sendMessage(chatId, errorMessage);
        }
    }
    
    private void handleBirthDateState(Message message, User user, Long chatId) {
        if (message.hasText()) {
            String birthDateText = message.getText().trim();
            
            if (isValidBirthDate(birthDateText)) {
                user.setBirthDate(birthDateText);
                user.setState(User.UserState.WAITING_CHANNEL_SUBSCRIPTION);
                userService.save(user);
                
                sendChannelSubscriptionMessage(chatId, user.getLanguage());
            } else {
                String errorMessage;
                if ("ru".equals(user.getLanguage())) {
                    errorMessage = "❌ Неправильный формат даты. Пожалуйста, введите в формате ДД.ММ.ГГГГ.\n\nПример: 15.03.1995";
                } else {
                    errorMessage = "❌ Noto'g'ri sana formati. Iltimos, DD.MM.YYYY formatida kiriting.\n\nMisol: 15.03.1995";
                }
                sendMessage(chatId, errorMessage);
            }
        } else {
            String errorMessage;
            if ("ru".equals(user.getLanguage())) {
                errorMessage = "❌ Пожалуйста, отправьте дату рождения в текстовом виде.";
            } else {
                errorMessage = "❌ Iltimos, tug'ilgan sanangizni matn ko'rinishida yuboring.";
            }
            sendMessage(chatId, errorMessage);
        }
    }
    
    private boolean isValidBirthDate(String dateText) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            LocalDate birthDate = LocalDate.parse(dateText, formatter);
            LocalDate now = LocalDate.now();
            
            // 10 yoshdan katta va 100 yoshdan kichik bo'lishi kerak
            return birthDate.isBefore(now.minusYears(10)) && birthDate.isAfter(now.minusYears(100));
        } catch (DateTimeParseException e) {
            return false;
        }
    }
    
    private void sendChannelSubscriptionMessage(Long chatId, String language) {
        String subscriptionMessage;
        String buttonText;
        
        if ("ru".equals(language)) {
            subscriptionMessage = String.format(
                """
                ✅ Дата рождения принята!
                
                📢 Для завершения регистрации подпишитесь на наш канал:
                
                👇 Нажмите на ссылку ниже, перейдите в канал и подпишитесь:
                %s
                
                После подписки нажмите кнопку "✅ Проверить подписку".
                """,
                "https://t.me/" + channelUsername.replace("@", "")
            );
            buttonText = "✅ Проверить подписку";
        } else {
            subscriptionMessage = String.format(
                """
                ✅ Tug'ilgan sana qabul qilindi!
                
                📢 Ro'yxatdan o'tishni yakunlash uchun bizning kanalimizga obuna bo'ling:
                
                👇 Quyidagi havolani bosib kanalga o'ting va obuna bo'ling:
                %s
                
                Obuna bo'lgandan keyin "✅ Obunani tekshirish" tugmasini bosing.
                """,
                "https://t.me/" + channelUsername.replace("@", "")
            );
            buttonText = "✅ Obunani tekshirish";
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(subscriptionMessage);
        sendMessage.setReplyMarkup(createChannelSubscriptionKeyboard(buttonText));
        
        sendMessage(sendMessage);
    }
    
    private ReplyKeyboardMarkup createChannelSubscriptionKeyboard(String buttonText) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        
        List<KeyboardRow> keyboard = new ArrayList<>();
        
        // Obunani tekshirish tugmasi
        KeyboardRow row = new KeyboardRow();
        row.add(buttonText);
        
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        
        return keyboardMarkup;
    }
    
    private void handleChannelSubscriptionState(Message message, User user, Long chatId) {
        String checkButtonUz = "✅ Obunani tekshirish";
        String checkButtonRu = "✅ Проверить подписку";
        
        if (message.hasText() && (message.getText().equals(checkButtonUz) || message.getText().equals(checkButtonRu))) {
            if (checkChannelSubscription(user.getTelegramId())) {
                // Obuna tasdiqlandi - kupon yaratish
                user.setState(User.UserState.REGISTERED);
                userService.save(user);
                
                Coupon coupon = couponService.createCouponForUser(user);
                
                String successMessage;
                if ("ru".equals(user.getLanguage())) {
                    successMessage = String.format(
                        "🎉 Поздравляем! Регистрация успешно завершена!\n\n" +
                        "👤 Имя: %s\n" +
                        "👤 Фамилия: %s\n" +
                        "📱 Телефон: %s\n" +
                        "🎂 Дата рождения: %s\n\n" +
                        "🎫 Ваш код купона: *%s*\n\n" +
                        "Сохраните этот код и используйте его при необходимости!",
                        user.getFirstName(), 
                        user.getLastName(), 
                        user.getPhoneNumber(),
                        user.getBirthDate(),
                        coupon.getCode()
                    );
                } else {
                    successMessage = String.format(
                        "🎉 Tabriklaymiz! Ro'yxatdan o'tish muvaffaqiyatli yakunlandi!\n\n" +
                        "👤 Ism: %s\n" +
                        "👤 Familiya: %s\n" +
                        "📱 Telefon: %s\n" +
                        "🎂 Tug'ilgan sana: %s\n\n" +
                        "🎫 Sizning kupon kodingiz: *%s*\n\n" +
                        "Bu kodni saqlang va kerak bo'lganda ishlatishingiz mumkin!",
                        user.getFirstName(), 
                        user.getLastName(), 
                        user.getPhoneNumber(),
                        user.getBirthDate(),
                        coupon.getCode()
                    );
                }
                
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);
                sendMessage.setText(successMessage);
                sendMessage.setParseMode("Markdown");
                sendMessage.setReplyMarkup(createMainMenuKeyboard(user.getLanguage()));
                
                sendMessage(sendMessage);
            } else {
                String errorMessage;
                if ("ru".equals(user.getLanguage())) {
                    errorMessage = "❌ Вы еще не подписались на канал!\n\n" +
                        "Пожалуйста, сначала подпишитесь на канал, затем нажмите \"✅ Проверить подписку\".";
                } else {
                    errorMessage = "❌ Siz hali kanalga obuna bo'lmagansiz!\n\n" +
                        "Iltimos, avval kanalga obuna bo'ling, keyin \"✅ Obunani tekshirish\" tugmasini bosing.";
                }
                sendMessage(chatId, errorMessage);
            }
        } else {
            String errorMessage;
            if ("ru".equals(user.getLanguage())) {
                errorMessage = "❌ Пожалуйста, сначала подпишитесь на канал и нажмите \"✅ Проверить подписку\".";
            } else {
                errorMessage = "❌ Iltimos, avval kanalga obuna bo'ling va \"✅ Obunani tekshirish\" tugmasini bosing.";
            }
            sendMessage(chatId, errorMessage);
        }
    }
    
    private boolean checkChannelSubscription(Long userId) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(channelId);
            getChatMember.setUserId(userId);
            
            ChatMember chatMember = execute(getChatMember);
            String status = chatMember.getStatus();
            
            // Obuna bo'lgan holatlar: "member", "administrator", "creator"
            return "member".equals(status) || "administrator".equals(status) || "creator".equals(status);
        } catch (TelegramApiException e) {
            log.error("Error checking channel subscription for user {}: ", userId, e);
            return false;
        }
    }
    
    private ReplyKeyboardMarkup createMainMenuKeyboard(String language) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        
        List<KeyboardRow> keyboard = new ArrayList<>();
        
        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();
        
        if ("ru".equals(language)) {
            row1.add("🛒 Магазин");
            row1.add("📦 Мои заказы");
            
            row2.add("👤 Профиль");
            row2.add("ℹ️ Помощь");
        } else {
            row1.add("🛒 Do'kon");
            row1.add("📦 Buyurtmalarim");
            
            row2.add("👤 Profil");
            row2.add("ℹ️ Yordam");
        }
        
        keyboard.add(row1);
        keyboard.add(row2);
        keyboardMarkup.setKeyboard(keyboard);
        
        return keyboardMarkup;
    }
    
    private void handleRegisteredUserCommands(Message message, User user, Long chatId) {
        if (!message.hasText()) {
            return;
        }
        
        String text = message.getText();
        
        switch (text) {
            // Uzbek menu items
            case "🛒 Do'kon" -> openShop(chatId, user.getLanguage());
            case "📦 Buyurtmalarim" -> showUserOrders(user, chatId);
            case "👤 Profil" -> showUserProfile(user, chatId);
            case "ℹ️ Yordam" -> showHelp(chatId, user.getLanguage());
            
            // Russian menu items
            case "🛒 Магазин" -> openShop(chatId, user.getLanguage());
            case "📦 Мои заказы" -> showUserOrders(user, chatId);
            case "👤 Профиль" -> showUserProfile(user, chatId);
            case "ℹ️ Помощь" -> showHelp(chatId, user.getLanguage());
            
            // Common commands
            case "/start" -> sendRegisteredUserWelcome(user, chatId);
            case "/admin" -> handleAdminCommand(user, chatId);
            case "/myid" -> {
                String idMessage = "ru".equals(user.getLanguage()) ? 
                    "🆔 Ваш Telegram ID: " + user.getTelegramId() :
                    "🆔 Sizning Telegram ID: " + user.getTelegramId();
                sendMessage(chatId, idMessage);
            }
            case "/testnotify" -> handleTestNotificationCommand(user, chatId);
            case "/testanniversary" -> handleTestAnniversaryCommand(user, chatId);
            case "/testbirthday" -> handleTestBirthdayCommand(user, chatId);
            case "/test3minute" -> handleTest3MinuteCommand(user, chatId);
            case "/broadcast" -> handleBroadcastCommand(message, user, chatId);
            default -> {
                String errorMessage = "ru".equals(user.getLanguage()) ? 
                    "❌ Неизвестная команда. Пожалуйста, выберите из меню." :
                    "❌ Noma'lum buyruq. Iltimos, menyudan tanlang.";
                sendMessage(chatId, errorMessage);
            }
        }
    }
    
    private void showUserCoupons(User user, Long chatId) {
        List<Coupon> coupons = couponService.getUserCoupons(user);
        
        if (coupons.isEmpty()) {
            String emptyMessage = "ru".equals(user.getLanguage()) ? 
                "❌ У вас пока нет купонов." :
                "❌ Sizda hozircha kuponlar yo'q.";
            sendMessage(chatId, emptyMessage);
            return;
        }
        
        StringBuilder message = new StringBuilder();
        if ("ru".equals(user.getLanguage())) {
            message.append("🎫 Ваши купоны:\n\n");
        } else {
            message.append("🎫 Sizning kuponlaringiz:\n\n");
        }
        
        for (int i = 0; i < coupons.size(); i++) {
            Coupon coupon = coupons.get(i);
            String status;
            if ("ru".equals(user.getLanguage())) {
                status = coupon.getStatus() == Coupon.CouponStatus.ACTIVE ? "✅ Активен" : "❌ Использован";
            } else {
                status = coupon.getStatus() == Coupon.CouponStatus.ACTIVE ? "✅ Faol" : "❌ Ishlatilgan";
            }
            
            String codeLabel = "ru".equals(user.getLanguage()) ? "Код" : "Kod";
            message.append(String.format("%d. %s: *%s* - %s\n", i + 1, codeLabel, coupon.getCode(), status));
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message.toString());
        sendMessage.setParseMode("Markdown");
        
        sendMessage(sendMessage);
    }
    
    private void generateNewCoupon(User user, Long chatId) {
        Coupon newCoupon = couponService.createCouponForUser(user);
        
        String message;
        if ("ru".equals(user.getLanguage())) {
            message = String.format(
                "🎉 Новый купон создан!\n\n🎫 Код купона: *%s*\n\nСохраните этот код!",
                newCoupon.getCode()
            );
        } else {
            message = String.format(
                "🎉 Yangi kupon yaratildi!\n\n🎫 Kupon kodi: *%s*\n\nBu kodni saqlang!",
                newCoupon.getCode()
            );
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        sendMessage.setParseMode("Markdown");
        
        sendMessage(sendMessage);
    }
    
    private void showUserProfile(User user, Long chatId) {
        List<Coupon> userCoupons = couponService.getUserCoupons(user);
        long activeCoupons = userCoupons.stream()
            .filter(c -> c.getStatus() == Coupon.CouponStatus.ACTIVE)
            .count();
        
        String profileMessage;
        if ("ru".equals(user.getLanguage())) {
            profileMessage = String.format(
                "� Ваш профиль:\n\n" +
                "📝 Имя: %s\n" +
                "� Фамилия: %s\n" +
                "📱 Телефон: %s\n" +
                "👤 Username: %s\n" +
                "� Дата рождения: %s\n" +
                "🎫 Всего купонов: %d\n" +
                "✅ Активных купонов: %d\n" +
                "📅 Зарегистрирован: %s",
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getTelegramUsername() != null ? user.getTelegramUsername() : "Username нет",
                user.getBirthDate() != null ? user.getBirthDate() : "Не указано",
                userCoupons.size(),
                (int) activeCoupons,
                user.getCreatedAt().toLocalDate()
            );
        } else {
            profileMessage = String.format(
                "👤 Sizning profilingiz:\n\n" +
                "📝 Ism: %s\n" +
                "📝 Familiya: %s\n" +
                "📱 Telefon: %s\n" +
                "👤 Username: %s\n" +
                "🎂 Tug'ilgan sana: %s\n" +
                "🎫 Jami kuponlar: %d\n" +
                "✅ Faol kuponlar: %d\n" +
                "📅 Ro'yxatdan o'tgan: %s",
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getTelegramUsername() != null ? user.getTelegramUsername() : "Username yo'q",
                user.getBirthDate() != null ? user.getBirthDate() : "Kiritilmagan",
                userCoupons.size(),
                (int) activeCoupons,
                user.getCreatedAt().toLocalDate()
            );
        }
        
        sendMessage(chatId, profileMessage);
    }
    
    private void showHelp(Long chatId, String language) {
        String helpMessage;
        
        if ("ru".equals(language)) {
            helpMessage = """
                ℹ️ Помощь:
                
                🛒 Магазин - просмотр каталога очков и покупки
                📦 Мои заказы - история заказов
                👤 Профиль - просмотр личной информации
                ℹ️ Помощь - это сообщение помощи
                
                Если есть вопросы, свяжитесь с администратором.
                """;
        } else {
            helpMessage = """
                ℹ️ Yordam:
                
                🛒 Do'kon - ko'zoynaklar katalogini ko'rish va xarid qilish
                📦 Buyurtmalarim - buyurtmalar tarixi
                👤 Profil - shaxsiy ma'lumotlaringizni ko'rish
                ℹ️ Yordam - bu yordam xabari
                
                Savollar bo'lsa, admin bilan bog'laning.
                """;
        }
        
        sendMessage(chatId, helpMessage);
    }
    
    private void openShop(Long chatId, String language) {
        String shopMessage;
        String buttonText;
        
        if ("ru".equals(language)) {
            shopMessage = """
                🛒 Магазин очков
                
                В нашем магазине представлены самые качественные очки!
                
                Нажмите кнопку ниже, чтобы открыть магазин:
                """;
            buttonText = "🛒 Открыть магазин";
        } else {
            shopMessage = """
                🛒 Ko'zoynak Do'koni
                
                Bizning do'konimizda eng sifatli ko'zoynaklar mavjud!
                
                Do'konni ochish uchun quyidagi tugmani bosing:
                """;
            buttonText = "🛒 Do'konni ochish";
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(shopMessage);
        
        // Create inline keyboard with web app button
        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup inlineKeyboard = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
        
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton shopButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        shopButton.setText(buttonText);
        
        // Production Vercel HTTPS domain
        shopButton.setUrl("https://bott-ondv.vercel.app/shop.html");
        
        row.add(shopButton);
        keyboard.add(row);
        inlineKeyboard.setKeyboard(keyboard);
        
        sendMessage.setReplyMarkup(inlineKeyboard);
        sendMessage(sendMessage);
    }
    
    private void showUserOrders(User user, Long chatId) {
        List<Order> userOrders = orderService.getUserOrders(user);
        
        if (userOrders.isEmpty()) {
            String ordersMessage;
            if ("ru".equals(user.getLanguage())) {
                ordersMessage = """
                    📦 Ваши заказы:
                    
                    Пока заказов нет.
                    
                    Сделайте первый заказ в нашем магазине!
                    """;
            } else {
                ordersMessage = """
                    📦 Sizning buyurtmalaringiz:
                    
                    Hozircha buyurtmalar yo'q.
                    
                    Birinchi buyurtmangizni berish uchun do'konni oching!
                    """;
            }
            sendMessage(chatId, ordersMessage);
            return;
        }
        
        StringBuilder message = new StringBuilder();
        if ("ru".equals(user.getLanguage())) {
            message.append("📦 Ваши заказы:\n\n");
        } else {
            message.append("📦 Sizning buyurtmalaringiz:\n\n");
        }
        
        for (int i = 0; i < userOrders.size(); i++) {
            Order order = userOrders.get(i);
            String statusEmoji = getOrderStatusEmoji(order.getStatus());
            String statusText = getOrderStatusText(order.getStatus(), user.getLanguage());
            
            String amountLabel = "ru".equals(user.getLanguage()) ? "Сумма" : "Summa";
            String dateLabel = "ru".equals(user.getLanguage()) ? "Дата" : "Sana";
            String currency = "ru".equals(user.getLanguage()) ? "сум" : "so'm";
            
            message.append(String.format(
                "%d. 🧾 *%s*\n" +
                "   %s %s\n" +
                "   💰 %s: %s %s\n" +
                "   📅 %s: %s\n\n",
                i + 1,
                order.getOrderNumber(),
                statusEmoji,
                statusText,
                amountLabel,
                order.getTotalAmount(),
                currency,
                dateLabel,
                order.getCreatedAt().toLocalDate()
            ));
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message.toString());
        sendMessage.setParseMode("Markdown");
        
        sendMessage(sendMessage);
    }
    
    private String getOrderStatusEmoji(Order.OrderStatus status) {
        return switch (status) {
            case PENDING -> "⏳";
            case CONFIRMED -> "✅";
            case PREPARING -> "👨‍🍳";
            case SHIPPED -> "🚚";
            case DELIVERED -> "📦";
            case CANCELLED -> "❌";
        };
    }
    
    private String getOrderStatusText(Order.OrderStatus status, String language) {
        if ("ru".equals(language)) {
            return switch (status) {
                case PENDING -> "Ожидает";
                case CONFIRMED -> "Подтвержден";
                case PREPARING -> "Готовится";
                case SHIPPED -> "Доставляется";
                case DELIVERED -> "Доставлен";
                case CANCELLED -> "Отменен";
            };
        } else {
            return switch (status) {
                case PENDING -> "Kutilmoqda";
                case CONFIRMED -> "Tasdiqlandi";
                case PREPARING -> "Tayyorlanmoqda";
                case SHIPPED -> "Yetkazilmoqda";
                case DELIVERED -> "Yetkazildi";
                case CANCELLED -> "Bekor qilindi";
            };
        }
    }
    
    private void sendRegisteredUserWelcome(User user, Long chatId) {
        String welcomeMessage;
        if ("ru".equals(user.getLanguage())) {
            welcomeMessage = String.format(
                "👋 Привет, %s!\n\nВы уже зарегистрированы. Выберите нужный раздел из меню.",
                user.getFirstName()
            );
        } else {
            welcomeMessage = String.format(
                "👋 Salom, %s!\n\nSiz allaqachon ro'yxatdan o'tgansiz. Menyudan kerakli bo'limni tanlang.",
                user.getFirstName()
            );
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(welcomeMessage);
        sendMessage.setReplyMarkup(createMainMenuKeyboard(user.getLanguage()));
        
        sendMessage(sendMessage);
    }
    
    private void handleAdminCommand(User user, Long chatId) {
        // Admin Telegram ID'larini tekshirish
        Long[] adminTelegramIds = {1807166165L, 7543576887L}; // IbodullaR va DeveloperAdmin23
        
        boolean isAdmin = false;
        for (Long adminId : adminTelegramIds) {
            if (user.getTelegramId().equals(adminId)) {
                isAdmin = true;
                break;
            }
        }
        
        if (!isAdmin) {
            String errorMessage = "ru".equals(user.getLanguage()) ? 
                "❌ У вас нет прав администратора." :
                "❌ Sizda admin huquqlari yo'q.";
            sendMessage(chatId, errorMessage);
            return;
        }
        
        String adminMessage;
        if ("ru".equals(user.getLanguage())) {
            adminMessage = "🔐 Панель администратора\n\n" +
                "Для входа в админ панель:\n" +
                "1. В браузере: http://localhost:8080/login.html\n" +
                "2. Код администратора: ADMIN2024\n\n" +
                "📊 Быстрая статистика:\n" +
                "👥 Всего пользователей: " + userService.getTotalUsersCount() + "\n" +
                "🎫 Всего купонов: " + couponService.getTotalCouponsCount() + "\n\n" +
                "Администраторы: @IbodullaR, @developeradmin23";
        } else {
            adminMessage = "🔐 Admin Panel\n\n" +
                "Admin panelga kirish uchun:\n" +
                "1. Brauzerda: http://localhost:8080/login.html\n" +
                "2. Admin kodi: ADMIN2024\n\n" +
                "📊 Tezkor statistika:\n" +
                "👥 Jami foydalanuvchilar: " + userService.getTotalUsersCount() + "\n" +
                "🎫 Jami kuponlar: " + couponService.getTotalCouponsCount() + "\n\n" +
                "Adminlar: @IbodullaR, @developeradmin23";
        }
        
        sendMessage(chatId, adminMessage);
    }
    
    private void handleTestNotificationCommand(User user, Long chatId) {
        // Admin huquqlarini tekshirish
        Long[] adminTelegramIds = {1807166165L, 7543576887L};
        
        boolean isAdmin = false;
        for (Long adminId : adminTelegramIds) {
            if (user.getTelegramId().equals(adminId)) {
                isAdmin = true;
                break;
            }
        }
        
        if (!isAdmin) {
            String errorMessage = "ru".equals(user.getLanguage()) ? 
                "❌ У вас нет прав администратора." :
                "❌ Sizda admin huquqlari yo'q.";
            sendMessage(chatId, errorMessage);
            return;
        }
        
        // Test notification yuborish
        notificationService.testNotifications();
        String successMessage = "ru".equals(user.getLanguage()) ? 
            "✅ Тестовое сообщение отправлено!" :
            "✅ Test xabar yuborildi!";
        sendMessage(chatId, successMessage);
    }
    
    private void handleTestAnniversaryCommand(User user, Long chatId) {
        // Admin huquqlarini tekshirish
        Long[] adminTelegramIds = {1807166165L, 7543576887L};
        
        boolean isAdmin = false;
        for (Long adminId : adminTelegramIds) {
            if (user.getTelegramId().equals(adminId)) {
                isAdmin = true;
                break;
            }
        }
        
        if (!isAdmin) {
            String errorMessage = "ru".equals(user.getLanguage()) ? 
                "❌ У вас нет прав администратора." :
                "❌ Sizda admin huquqlari yo'q.";
            sendMessage(chatId, errorMessage);
            return;
        }
        
        // 6 oylik yubiley test
        notificationService.testSixMonthAnniversary();
        String successMessage = "ru".equals(user.getLanguage()) ? 
            "✅ Тест 6-месячного юбилея выполнен!" :
            "✅ 6 oylik yubiley test bajarildi!";
        sendMessage(chatId, successMessage);
    }
    
    private void handleTestBirthdayCommand(User user, Long chatId) {
        // Admin huquqlarini tekshirish
        Long[] adminTelegramIds = {1807166165L, 7543576887L};
        
        boolean isAdmin = false;
        for (Long adminId : adminTelegramIds) {
            if (user.getTelegramId().equals(adminId)) {
                isAdmin = true;
                break;
            }
        }
        
        if (!isAdmin) {
            String errorMessage = "ru".equals(user.getLanguage()) ? 
                "❌ У вас нет прав администратора." :
                "❌ Sizda admin huquqlari yo'q.";
            sendMessage(chatId, errorMessage);
            return;
        }
        
        // Tug'ilgan kun test
        notificationService.testBirthdays();
        String successMessage = "ru".equals(user.getLanguage()) ? 
            "✅ Тест дня рождения выполнен!" :
            "✅ Tug'ilgan kun test bajarildi!";
        sendMessage(chatId, successMessage);
    }
    
    private void handleTest3MinuteCommand(User user, Long chatId) {
        // Admin huquqlarini tekshirish
        Long[] adminTelegramIds = {1807166165L, 7543576887L};
        
        boolean isAdmin = false;
        for (Long adminId : adminTelegramIds) {
            if (user.getTelegramId().equals(adminId)) {
                isAdmin = true;
                break;
            }
        }
        
        if (!isAdmin) {
            String errorMessage = "ru".equals(user.getLanguage()) ? 
                "❌ У вас нет прав администратора." :
                "❌ Sizda admin huquqlari yo'q.";
            sendMessage(chatId, errorMessage);
            return;
        }
        
        // 3 daqiqa test
        notificationService.testThreeMinuteRegistrations();
        String successMessage = "ru".equals(user.getLanguage()) ? 
            "✅ 3-минутный тест выполнен!" :
            "✅ 3 daqiqa test bajarildi!";
        sendMessage(chatId, successMessage);
    }
    
    private void handleBroadcastCommand(Message message, User user, Long chatId) {
        // Admin huquqlarini tekshirish
        Long[] adminTelegramIds = {1807166165L, 7543576887L};
        
        boolean isAdmin = false;
        for (Long adminId : adminTelegramIds) {
            if (user.getTelegramId().equals(adminId)) {
                isAdmin = true;
                break;
            }
        }
        
        if (!isAdmin) {
            sendMessage(chatId, "❌ Sizda admin huquqlari yo'q.");
            return;
        }
        
        String text = message.getText();
        String[] parts = text.split(" ", 2);
        
        if (parts.length < 2) {
            String helpMessage;
            if ("ru".equals(user.getLanguage())) {
                helpMessage = """
                    📢 Отправка рассылки:
                    
                    Использование: /broadcast [текст сообщения]
                    
                    Пример: /broadcast Привет! Новые товары поступили!
                    
                    ⚠️ Это сообщение будет отправлено всем зарегистрированным пользователям.
                    """;
            } else {
                helpMessage = """
                    📢 Broadcast xabar yuborish:
                    
                    Foydalanish: /broadcast [xabar matni]
                    
                    Misol: /broadcast Assalomu alaykum! Yangi mahsulotlar keldi!
                    
                    ⚠️ Bu xabar barcha ro'yxatdan o'tgan foydalanuvchilarga yuboriladi.
                    """;
            }
            sendMessage(chatId, helpMessage);
            return;
        }
        
        String broadcastMessage = parts[1].trim();
        
        if (broadcastMessage.isEmpty()) {
            String errorMessage = "ru".equals(user.getLanguage()) ? 
                "❌ Текст сообщения не может быть пустым." :
                "❌ Xabar matni bo'sh bo'lishi mumkin emas.";
            sendMessage(chatId, errorMessage);
            return;
        }
        
        String sendingMessage = "ru".equals(user.getLanguage()) ? 
            "📤 Сообщение отправляется всем пользователям..." :
            "📤 Xabar barcha foydalanuvchilarga yuborilmoqda...";
        sendMessage(chatId, sendingMessage);
        
        // Async ravishda yuborish
        CompletableFuture.runAsync(() -> {
            try {
                BroadcastService.BroadcastResult result = broadcastService.sendBroadcastMessage(broadcastMessage);
                
                String resultMessage;
                if ("ru".equals(user.getLanguage())) {
                    resultMessage = String.format(
                        """
                        ✅ Рассылка отправлена!
                        
                        📊 Результаты:
                        👥 Всего пользователей: %d
                        ✅ Успешно: %d
                        ❌ Ошибок: %d
                        📈 Процент успеха: %.1f%%
                        """,
                        result.getTotalUsers(),
                        result.getSuccessCount(),
                        result.getFailureCount(),
                        result.getSuccessRate()
                    );
                } else {
                    resultMessage = String.format(
                        """
                        ✅ Broadcast xabar yuborildi!
                        
                        📊 Natijalar:
                        👥 Jami foydalanuvchilar: %d
                        ✅ Muvaffaqiyatli: %d
                        ❌ Xatolik: %d
                        📈 Muvaffaqiyat darajasi: %.1f%%
                        """,
                        result.getTotalUsers(),
                        result.getSuccessCount(),
                        result.getFailureCount(),
                        result.getSuccessRate()
                    );
                }
                
                sendMessage(chatId, resultMessage);
                
            } catch (Exception e) {
                log.error("Error in broadcast command: ", e);
                String errorMessage = "ru".equals(user.getLanguage()) ? 
                    "❌ Ошибка при отправке сообщения: " + e.getMessage() :
                    "❌ Xabar yuborishda xatolik yuz berdi: " + e.getMessage();
                sendMessage(chatId, errorMessage);
            }
        });
    }
    
    private void sendMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage(sendMessage);
    }
    
    private void sendMessage(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending message: ", e);
        }
    }
}

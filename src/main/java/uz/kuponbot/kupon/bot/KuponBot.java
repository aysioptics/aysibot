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
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.entity.Coupon;
import uz.kuponbot.kupon.entity.User;
import uz.kuponbot.kupon.entity.Voucher;
import uz.kuponbot.kupon.service.BroadcastService;
import uz.kuponbot.kupon.service.CashbackService;
import uz.kuponbot.kupon.service.CouponService;
import uz.kuponbot.kupon.service.NotificationService;
import uz.kuponbot.kupon.service.UserService;
import uz.kuponbot.kupon.service.VoucherService;

@Component
@RequiredArgsConstructor
@Slf4j
public class KuponBot extends TelegramLongPollingBot {
    
    private final UserService userService;
    private final CouponService couponService;
    private final NotificationService notificationService;
    private final BroadcastService broadcastService;
    private final VoucherService voucherService;
    private final CashbackService cashbackService;
    
    @Value("${telegram.bot.token}")
    private String botToken;
    
    @Value("${telegram.bot.username}")
    private String botUsername;
    
    @Value("${telegram.channel.username}")
    private String channelUsername;
    
    @Value("${telegram.channel.id}")
    private String channelId;
    
    // Pending broadcast message storage
    private Message pendingBroadcastMessage = null;
    private Long pendingBroadcastAdminId = null;
    
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
        } else if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            Long chatId = callbackQuery.getMessage().getChatId();
            Long userId = callbackQuery.getFrom().getId();
            String callbackData = callbackQuery.getData();
            
            try {
                handleCallbackQuery(callbackQuery, chatId, userId, callbackData);
            } catch (Exception e) {
                log.error("Error processing callback query: ", e);
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
        
        // Admin uchun video/rasm broadcast funksiyasi
        if (user.getState() == User.UserState.REGISTERED && isAdmin(userId)) {
            if (message.hasPhoto() || message.hasVideo()) {
                handleAdminMediaBroadcast(message, user, chatId);
                return;
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
            case WAITING_FULL_NAME -> handleFullNameState(message, user, chatId);
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
        String welcomeText = "🎉 AYSI OPTICS botiga xush kelibsiz!\n\n" +
                "Iltimos, tilni tanlang / Пожалуйста, выберите язык / Илтимос, тилни танланг:";
        
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
        
        // Birinchi qator - O'zbek tillar
        KeyboardRow row1 = new KeyboardRow();
        row1.add("🇺🇿 O'zbek (lotin)");
        row1.add("🇺🇿 Ўзбек (кирил)");
        
        // Ikkinchi qator - Rus tili
        KeyboardRow row2 = new KeyboardRow();
        row2.add("🇷🇺 Русский язык");
        
        keyboard.add(row1);
        keyboard.add(row2);
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
    
    // ✅ Keyboard ni olib tashlash uchun metod
    private ReplyKeyboardRemove createRemoveKeyboard() {
        ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
        keyboardRemove.setRemoveKeyboard(true);
        return keyboardRemove;
    }
    
    private void handleLanguageState(Message message, User user, Long chatId) {
        log.info("handleLanguageState called for user {} with current language: {}", user.getTelegramId(), user.getLanguage());
        
        if (message.hasText()) {
            String text = message.getText();
            log.info("User {} sent text: '{}'", user.getTelegramId(), text);
            
            if ("🇺🇿 O'zbek (lotin)".equals(text)) {
                log.info("User {} selected Uzbek Latin language", user.getTelegramId());
                user.setLanguage("uz");
                user.setState(User.UserState.WAITING_CONTACT);
                User savedUser = userService.save(user);
                log.info("User {} language saved as: {}", savedUser.getTelegramId(), savedUser.getLanguage());
                
                sendContactRequestMessage(chatId, "uz");
            } else if ("🇺🇿 Ўзбек (кирил)".equals(text)) {
                log.info("User {} selected Uzbek Cyrillic language", user.getTelegramId());
                user.setLanguage("uz_cyrl");
                user.setState(User.UserState.WAITING_CONTACT);
                User savedUser = userService.save(user);
                log.info("User {} language saved as: {}", savedUser.getTelegramId(), savedUser.getLanguage());
                
                sendContactRequestMessage(chatId, "uz_cyrl");
            } else if ("🇷🇺 Русский язык".equals(text)) {
                log.info("User {} selected Russian language", user.getTelegramId());
                user.setLanguage("ru");
                user.setState(User.UserState.WAITING_CONTACT);
                User savedUser = userService.save(user);
                log.info("User {} language saved as: {}", savedUser.getTelegramId(), savedUser.getLanguage());
                
                sendContactRequestMessage(chatId, "ru");
            } else {
                // Foydalanuvchining hozirgi tilini tekshirish
                log.info("User {} sent invalid language selection: {}, current language: {}", 
                    user.getTelegramId(), text, user.getLanguage());
                String errorMessage = getLocalizedMessage(user.getLanguage(),
                    "Iltimos, tilni tanlang",
                    "Илтимос, тилни танланг", 
                    "Пожалуйста, выберите язык");
                sendMessage(chatId, errorMessage);
            }
        } else {
            // Foydalanuvchining hozirgi tilini tekshirish
            log.info("User {} sent non-text message in language state, current language: {}", 
                user.getTelegramId(), user.getLanguage());
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "Iltimos, tilni tanlang",
                "Илтимос, тилни танланг", 
                "Пожалуйста, выберите язык");
            sendMessage(chatId, errorMessage);
        }
    }
    
    // Helper metod - uch tilli xabarlar uchun
    private String getLocalizedMessage(String language, String uzMessage, String uzCyrlMessage, String ruMessage) {
        return switch (language) {
            case "uz_cyrl" -> uzCyrlMessage;
            case "ru" -> ruMessage;
            default -> uzMessage; // "uz" yoki null uchun
        };
    }
    
    private void sendContactRequestMessage(Long chatId, String language) {
        String contactText;
        String buttonText;
        
        switch (language) {
            case "uz" -> {
                contactText = "✅ Til tanlandi: O'zbek (lotin)\n\n" +
                        "Ro'yxatdan o'tish uchun telefon raqamingizni yuboring.";
                buttonText = "📱 Telefon raqamni yuborish";
            }
            case "uz_cyrl" -> {
                contactText = "✅ Тил танланди: Ўзбек (кирил)\n\n" +
                        "Рўйхатдан ўтиш учун телефон рақамингизни юборинг.";
                buttonText = "📱 Телефон рақамни юбориш";
            }
            case "ru" -> {
                contactText = "✅ Язык выбран: Русский\n\n" +
                        "Для регистрации отправьте свой номер телефона.";
                buttonText = "📱 Отправить номер телефона";
            }
            default -> {
                contactText = "✅ Til tanlandi: O'zbek (lotin)\n\n" +
                        "Ro'yxatdan o'tish uchun telefon raqamingizni yuboring.";
                buttonText = "📱 Telefon raqamni yuborish";
            }
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
            user.setState(User.UserState.WAITING_FULL_NAME);
            userService.save(user);
            
            String successMessage = getLocalizedMessage(lang,
                "✅ Telefon raqam qabul qilindi!\n\nEndi to'liq ismingizni kiriting (ism va familiya):",
                "✅ Телефон рақам қабул қилинди!\n\nЭнди тўлиқ исмингизни киритинг (исм ва фамилия):",
                "✅ Номер телефона принят!\n\nТеперь введите ваше полное имя (имя и фамилию):");
            
            // ✅ MUHIM: Keyboard ni olib tashlash
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(successMessage);
            sendMessage.setReplyMarkup(createRemoveKeyboard()); // Keyboard ni olib tashlash
            sendMessage(sendMessage);
        } else {
            String errorMessage = getLocalizedMessage(lang,
                "❌ Iltimos, telefon raqamingizni yuborish tugmasini bosing.",
                "❌ Илтимос, телефон рақамингизни юбориш тугмасини босинг.",
                "❌ Пожалуйста, нажмите кнопку отправки номера телефона.");
            
            String buttonText = getLocalizedMessage(lang,
                "📱 Telefon raqamni yuborish",
                "📱 Телефон рақамни юбориш",
                "📱 Отправить номер телефона");
            
            SendMessage sm = new SendMessage();
            sm.setChatId(chatId);
            sm.setText(errorMessage);
            sm.setReplyMarkup(createContactKeyboard(buttonText));
            sendMessage(sm);
        }
    }

    private void handleFullNameState(Message message, User user, Long chatId) {
        if (message.hasText()) {
            String fullName = message.getText().trim();
            if (fullName.length() >= 3 && fullName.contains(" ")) {
                user.setFullName(fullName);
                user.setState(User.UserState.WAITING_BIRTH_DATE);
                userService.save(user);
                
                String successMessage = getLocalizedMessage(user.getLanguage(),
                    "✅ To'liq ism qabul qilindi!\n\nEndi tug'ilgan sanangizni kiriting (DD.MM.YYYY formatida):\n\nMisol: 15.03.1995",
                    "✅ Тўлиқ исм қабул қилинди!\n\nЭнди туғилган санангизни киритинг (DD.MM.YYYY форматида):\n\nМисол: 15.03.1995",
                    "✅ Полное имя принято!\n\nТеперь введите дату рождения (в формате ДД.ММ.ГГГГ):\n\nПример: 15.03.1995");
                
                // ✅ MUHIM: Keyboard ni olib tashlash
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);
                sendMessage.setText(successMessage);
                sendMessage.setReplyMarkup(createRemoveKeyboard()); // Keyboard ni olib tashlash
                sendMessage(sendMessage);
            } else {
                String errorMessage = getLocalizedMessage(user.getLanguage(),
                    "❌ Iltimos, to'liq ismingizni kiriting (ism va familiya bo'sh joy bilan).\n\nMisol: Akmal Karimov",
                    "❌ Илтимос, тўлиқ исмингизни киритинг (исм ва фамилия бўш жой билан).\n\nМисол: Акмал Каримов",
                    "❌ Пожалуйста, введите полное имя (имя и фамилию через пробел).\n\nПример: Иван Петров");
                
                // ✅ MUHIM: Keyboard ni olib tashlash
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);
                sendMessage.setText(errorMessage);
                sendMessage.setReplyMarkup(createRemoveKeyboard()); // Keyboard ni olib tashlash
                sendMessage(sendMessage);
            }
        } else {
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "❌ Iltimos, to'liq ismingizni matn ko'rinishida yuboring.",
                "❌ Илтимос, тўлиқ исмингизни матн кўринишида юборинг.",
                "❌ Пожалуйста, отправьте ваше полное имя в текстовом виде.");
            
            // ✅ MUHIM: Keyboard ni olib tashlash
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(errorMessage);
            sendMessage.setReplyMarkup(createRemoveKeyboard()); // Keyboard ni olib tashlash
            sendMessage(sendMessage);
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
                String errorMessage = getLocalizedMessage(user.getLanguage(),
                    "❌ Noto'g'ri sana formati. Iltimos, DD.MM.YYYY formatida kiriting.\n\nMisol: 15.03.1995",
                    "❌ Нотўғри сана формати. Илтимос, DD.MM.YYYY форматида киритинг.\n\nМисол: 15.03.1995",
                    "❌ Неправильный формат даты. Пожалуйста, введите в формате ДД.ММ.ГГГГ.\n\nПример: 15.03.1995");
                
                // ✅ MUHIM: Keyboard ni olib tashlash
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);
                sendMessage.setText(errorMessage);
                sendMessage.setReplyMarkup(createRemoveKeyboard()); // Keyboard ni olib tashlash
                sendMessage(sendMessage);
            }
        } else {
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "❌ Iltimos, tug'ilgan sanangizni matn ko'rinishida yuboring.",
                "❌ Илтимос, туғилган санангизни матн кўринишида юборинг.",
                "❌ Пожалуйста, отправьте дату рождения в текстовом виде.");
            
            // ✅ MUHIM: Keyboard ni olib tashlash
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(errorMessage);
            sendMessage.setReplyMarkup(createRemoveKeyboard()); // Keyboard ni olib tashlash
            sendMessage(sendMessage);
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
        String subscribeButtonText;
        String checkButtonText;
        
        switch (language) {
            case "uz_cyrl" -> {
                subscriptionMessage = """
                    ✅ Туғилган сана қабул қилинди!
                    
                    📢 Рўйхатдан ўтишни якунлаш учун бизнинг каналимизга обуна бўлинг:
                    
                    👇 Қуйидаги тугмани босиб каналга ўтинг ва обуна бўлинг, кейин "Обунани текшириш" тугмасини босинг.
                    """;
                subscribeButtonText = "📢 Каналга обуна бўлиш";
                checkButtonText = "✅ Обунани текшириш";
            }
            case "ru" -> {
                subscriptionMessage = """
                    ✅ Дата рождения принята!
                    
                    📢 Для завершения регистрации подпишитесь на наш канал:
                    
                    👇 Нажмите кнопку ниже, перейдите в канал и подпишитесь, затем нажмите "Проверить подписку".
                    """;
                subscribeButtonText = "📢 Подписаться на канал";
                checkButtonText = "✅ Проверить подписку";
            }
            default -> {
                subscriptionMessage = """
                    ✅ Tug'ilgan sana qabul qilindi!
                    
                    📢 Ro'yxatdan o'tishni yakunlash uchun bizning kanalimizga obuna bo'ling:
                    
                    👇 Quyidagi tugmani bosib kanalga o'ting va obuna bo'ling, keyin "Obunani tekshirish" tugmasini bosing.
                    """;
                subscribeButtonText = "📢 Kanalga obuna bo'lish";
                checkButtonText = "✅ Obunani tekshirish";
            }
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(subscriptionMessage);
        sendMessage.setReplyMarkup(createChannelSubscriptionInlineKeyboard(subscribeButtonText, checkButtonText));
        
        sendMessage(sendMessage);
    }
    
    private InlineKeyboardMarkup createChannelSubscriptionInlineKeyboard(String subscribeButtonText, String checkButtonText) {
        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // First row - Subscribe button
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton subscribeButton = new InlineKeyboardButton();
        subscribeButton.setText(subscribeButtonText);
        subscribeButton.setUrl("https://t.me/" + channelUsername.replace("@", ""));
        row1.add(subscribeButton);
        
        // Second row - Check subscription button
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton checkButton = new InlineKeyboardButton();
        checkButton.setText(checkButtonText);
        checkButton.setCallbackData("check_subscription");
        row2.add(checkButton);
        
        keyboard.add(row1);
        keyboard.add(row2);
        inlineKeyboard.setKeyboard(keyboard);
        
        return inlineKeyboard;
    }
    
    private void handleCallbackQuery(CallbackQuery callbackQuery, Long chatId, Long userId, String callbackData) {
        Optional<User> userOpt = userService.findByTelegramId(userId);
        
        if (userOpt.isEmpty()) {
            return;
        }
        
        User user = userOpt.get();
        
        // Broadcast confirmation callback
        if (callbackData.equals("confirm_broadcast") && isAdmin(userId)) {
            handleBroadcastConfirmation(callbackQuery, user, chatId);
            return;
        }
        
        if (callbackData.equals("cancel_broadcast") && isAdmin(userId)) {
            handleBroadcastCancellation(callbackQuery, user, chatId);
            return;
        }
        
        if ("check_subscription".equals(callbackData) && user.getState() == User.UserState.WAITING_CHANNEL_SUBSCRIPTION) {
            // Answer the callback query first
            try {
                AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
                answerCallbackQuery.setCallbackQueryId(callbackQuery.getId());
                answerCallbackQuery.setText("Obuna tekshirilmoqda...");
                answerCallbackQuery.setShowAlert(false);
                execute(answerCallbackQuery);
            } catch (TelegramApiException e) {
                log.error("Error answering callback query: ", e);
            }
            
            if (checkChannelSubscription(user.getTelegramId())) {
                // Obuna tasdiqlandi - kupon yaratish
                user.setState(User.UserState.REGISTERED);
                userService.save(user);
                
                Coupon coupon = couponService.createCouponForUser(user);
                
                String successMessage = getLocalizedMessage(user.getLanguage(),
                    String.format(
                        "🎉 Tabriklaymiz! AYSI OPTICS ga ro'yxatdan o'tish muvaffaqiyatli yakunlandi!\n\n" +
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
                    ),
                    String.format(
                        "🎉 Табриклаймиз! AYSI OPTICS га рўйхатдан ўтиш муваффақиятли якунланди!\n\n" +
                        "👤 Исм: %s\n" +
                        "👤 Фамилия: %s\n" +
                        "📱 Телефон: %s\n" +
                        "🎂 Туғилган сана: %s\n\n" +
                        "🎫 Сизнинг купон кодингиз: *%s*\n\n" +
                        "Бу кодни сақланг ва керак бўлганда ишлатишингиз мумкин!",
                        user.getFirstName(), 
                        user.getLastName(), 
                        user.getPhoneNumber(),
                        user.getBirthDate(),
                        coupon.getCode()
                    ),
                    String.format(
                        "🎉 Поздравляем! Регистрация в AYSI OPTICS успешно завершена!\n\n" +
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
                    )
                );
                
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);
                sendMessage.setText(successMessage);
                sendMessage.setParseMode("Markdown");
                sendMessage.setReplyMarkup(createMainMenuKeyboard(user.getLanguage()));
                
                sendMessage(sendMessage);
            } else {
                String errorMessage = getLocalizedMessage(user.getLanguage(),
                    "❌ Siz hali kanalga obuna bo'lmagansiz!\n\n" +
                    "Iltimos, avval \"📢 Kanalga obuna bo'lish\" tugmasini bosib kanalga obuna bo'ling, keyin \"✅ Obunani tekshirish\" tugmasini bosing.",
                    "❌ Сиз ҳали каналга обуна бўлмагансиз!\n\n" +
                    "Илтимос, аввал \"📢 Каналга обуна бўлиш\" тугмасини босиб каналга обуна бўлинг, кейин \"✅ Обунани текшириш\" тугмасини босинг.",
                    "❌ Вы еще не подписались на канал!\n\n" +
                    "Пожалуйста, сначала нажмите \"📢 Подписаться на канал\" и подпишитесь, затем нажмите \"✅ Проверить подписку\".");
                sendMessage(chatId, errorMessage);
            }
        }
    }
    
    private void handleChannelSubscriptionState(Message message, User user, Long chatId) {
        // This method is now mainly for handling any text messages during subscription state
        // The actual subscription checking is handled via inline button callbacks
        String waitingMessage = getLocalizedMessage(user.getLanguage(),
            "⏳ Iltimos, avval kanalga obuna bo'ling va \"✅ Obunani tekshirish\" tugmasini bosing.",
            "⏳ Илтимос, аввал каналга обуна бўлинг ва \"✅ Обунани текшириш\" тугмасини босинг.",
            "⏳ Пожалуйста, сначала подпишитесь на канал и нажмите \"✅ Проверить подписку\".");
        sendMessage(chatId, waitingMessage);
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
        KeyboardRow row3 = new KeyboardRow();
        
        switch (language) {
            case "uz_cyrl" -> {
                row1.add("🛒 Дўкон");
                row1.add("👤 Профил");
                
                row2.add("💬 Фикр билдириш");
                row2.add("📋 Сўровномада қатнашиш");
                
                row3.add("ℹ️ Ёрдам");
            }
            case "ru" -> {
                row1.add("🛒 Магазин");
                row1.add("👤 Профиль");
                
                row2.add("💬 Оставить отзыв");
                row2.add("📋 Участвовать в опросе");
                
                row3.add("ℹ️ Помощь");
            }
            default -> {
                row1.add("🛒 Do'kon");
                row1.add("👤 Profil");
                
                row2.add("💬 Fikr bildirish");
                row2.add("📋 So'rovnomada qatnashish");
                
                row3.add("ℹ️ Yordam");
            }
        }
        
        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboardMarkup.setKeyboard(keyboard);
        
        return keyboardMarkup;
    }
    
    private void handleRegisteredUserCommands(Message message, User user, Long chatId) {
        if (!message.hasText()) {
            return;
        }
        
        String text = message.getText();
        
        // Broadcast komandasi uchun alohida tekshirish
        if (text.startsWith("/broadcast")) {
            handleBroadcastCommand(message, user, chatId);
            return;
        }
        
        switch (text) {
            // Uzbek Latin menu items
            case "🛒 Do'kon" -> openShop(chatId, user.getLanguage());
            case "👤 Profil" -> showUserProfile(user, chatId);
            case "💬 Fikr bildirish" -> showReviewRequest(chatId, user.getLanguage());
            case "📋 So'rovnomada qatnashish" -> showSurveyRequest(chatId, user.getLanguage());
            case "ℹ️ Yordam" -> {
                showHelp(chatId, user.getLanguage());
                notifyAdminAboutHelpRequest(user);
            }
            
            // Uzbek Cyrillic menu items
            case "🛒 Дўкон" -> openShop(chatId, user.getLanguage());
            case "👤 Профил" -> showUserProfile(user, chatId);
            case "💬 Фикр билдириш" -> showReviewRequest(chatId, user.getLanguage());
            case "📋 Сўровномада қатнашиш" -> showSurveyRequest(chatId, user.getLanguage());
            case "ℹ️ Ёрдам" -> {
                showHelp(chatId, user.getLanguage());
                notifyAdminAboutHelpRequest(user);
            }
            
            // Russian menu items
            case "🛒 Магазин" -> openShop(chatId, user.getLanguage());
            case "👤 Профиль" -> showUserProfile(user, chatId);
            case "💬 Оставить отзыв" -> showReviewRequest(chatId, user.getLanguage());
            case "📋 Участвовать в опросе" -> showSurveyRequest(chatId, user.getLanguage());
            case "ℹ️ Помощь" -> {
                showHelp(chatId, user.getLanguage());
                notifyAdminAboutHelpRequest(user);
            }
            
            // Common commands
            case "/start" -> sendRegisteredUserWelcome(user, chatId);
            case "/admin" -> handleAdminCommand(user, chatId);
            case "/myid" -> {
                String idMessage = getLocalizedMessage(user.getLanguage(),
                    "🆔 Sizning Telegram ID: " + user.getTelegramId(),
                    "🆔 Сизнинг Telegram ID: " + user.getTelegramId(),
                    "🆔 Ваш Telegram ID: " + user.getTelegramId());
                sendMessage(chatId, idMessage);
            }
            case "/testnotify" -> handleTestNotificationCommand(user, chatId);
            case "/test3day" -> handleTest3DayCommand(user, chatId);
            case "/testanniversary" -> handleTestAnniversaryCommand(user, chatId);
            case "/testbirthday" -> handleTestBirthdayCommand(user, chatId);
            default -> {
                // /senduser command tekshirish
                if (text.startsWith("/senduser")) {
                    handleSendUserCommand(message, user, chatId);
                    return;
                }
                
                // Foydalanuvchi oddiy xabar yozgan - adminga yuborish
                forwardMessageToAdmin(message, user);
                
                String confirmMessage = getLocalizedMessage(user.getLanguage(),
                    "✅ Xabaringiz adminga yuborildi. Tez orada javob beramiz!",
                    "✅ Хабарингиз админга юборилди. Тез орада жавоб берамиз!",
                    "✅ Ваше сообщение отправлено администратору. Скоро ответим!");
                sendMessage(chatId, confirmMessage);
            }
        }
    }
    
    
    private void showUserProfile(User user, Long chatId) {
        // Voucher ma'lumotlarini olish
        List<Voucher> userVouchers = voucherService.getUserVouchers(user);
        long activeVouchers = userVouchers.stream()
            .filter(v -> v.getStatus() == Voucher.VoucherStatus.ACTIVE)
            .count();
        long usedVouchers = userVouchers.stream()
            .filter(v -> v.getStatus() == Voucher.VoucherStatus.USED)
            .count();
        
        // Keshbek statistikasini olish
        CashbackService.UserCashbackStats cashbackStats = cashbackService.getUserCashbackStats(user);
        
        String profileMessage;
        switch (user.getLanguage()) {
            case "uz_cyrl" -> profileMessage = String.format(
                "👤 Сизнинг профилингиз:\n\n" +
                "📝 Исм: %s\n" +
                "📝 Фамилия: %s\n" +
                "📱 Телефон: %s\n" +
                "👤 Username: %s\n" +
                "🎂 Туғилган сана: %s\n\n" +
                "💰 Кешбек маълумотлари:\n" +
                "💳 Жорий баланс: %s сўм\n" +
                "➕ Жами олинган: %s сўм\n" +
                "➖ Жами ишлатилган: %s сўм\n\n" +
                "🎟️ Жами ваучерлар: %d\n" +
                "✅ Фаол ваучерлар: %d\n" +
                "✅ Ишлатилган ваучерлар: %d\n" +
                "📅 Рўйхатдан ўтган: %s",
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getTelegramUsername() != null ? user.getTelegramUsername() : "Username йўқ",
                user.getBirthDate() != null ? user.getBirthDate() : "Киритилмаган",
                String.format("%,d", cashbackStats.getCurrentBalance()),
                String.format("%,d", cashbackStats.getTotalEarned()),
                String.format("%,d", cashbackStats.getTotalUsed()),
                userVouchers.size(),
                (int) activeVouchers,
                (int) usedVouchers,
                user.getCreatedAt().toLocalDate()
            );
            case "ru" -> profileMessage = String.format(
                "👤 Ваш профиль:\n\n" +
                "📝 Имя: %s\n" +
                "📝 Фамилия: %s\n" +
                "📱 Телефон: %s\n" +
                "👤 Username: %s\n" +
                "🎂 Дата рождения: %s\n\n" +
                "💰 Информация о кешбэке:\n" +
                "💳 Текущий баланс: %s сум\n" +
                "➕ Всего начислено: %s сум\n" +
                "➖ Всего использовано: %s сум\n\n" +
                "🎟️ Всего ваучеров: %d\n" +
                "✅ Активных ваучеров: %d\n" +
                "✅ Использованных ваучеров: %d\n" +
                "📅 Зарегистрирован: %s",
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getTelegramUsername() != null ? user.getTelegramUsername() : "Username нет",
                user.getBirthDate() != null ? user.getBirthDate() : "Не указано",
                String.format("%,d", cashbackStats.getCurrentBalance()),
                String.format("%,d", cashbackStats.getTotalEarned()),
                String.format("%,d", cashbackStats.getTotalUsed()),
                userVouchers.size(),
                (int) activeVouchers,
                (int) usedVouchers,
                user.getCreatedAt().toLocalDate()
            );
            default -> profileMessage = String.format(
                "👤 Sizning profilingiz:\n\n" +
                "📝 Ism: %s\n" +
                "📝 Familiya: %s\n" +
                "📱 Telefon: %s\n" +
                "👤 Username: %s\n" +
                "🎂 Tug'ilgan sana: %s\n\n" +
                "💰 Keshbek ma'lumotlari:\n" +
                "💳 Joriy balans: %s so'm\n" +
                "➕ Jami olingan: %s so'm\n" +
                "➖ Jami ishlatilgan: %s so'm\n\n" +
                "🎟️ Jami voucherlar: %d\n" +
                "✅ Faol voucherlar: %d\n" +
                "✅ Ishlatilgan voucherlar: %d\n" +
                "📅 Ro'yxatdan o'tgan: %s",
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getTelegramUsername() != null ? user.getTelegramUsername() : "Username yo'q",
                user.getBirthDate() != null ? user.getBirthDate() : "Kiritilmagan",
                String.format("%,d", cashbackStats.getCurrentBalance()),
                String.format("%,d", cashbackStats.getTotalEarned()),
                String.format("%,d", cashbackStats.getTotalUsed()),
                userVouchers.size(),
                (int) activeVouchers,
                (int) usedVouchers,
                user.getCreatedAt().toLocalDate()
            );
        }
        
        // Agar faol voucherlar bo'lsa, ularni alohida ko'rsatish
        if (activeVouchers > 0) {
            String voucherDetails = getActiveVoucherDetails(userVouchers, user.getLanguage());
            profileMessage += "\n\n" + voucherDetails;
        }
        
        // Agar ishlatilgan voucherlar bo'lsa, ularni ham ko'rsatish
        if (usedVouchers > 0) {
            String usedVoucherDetails = getUsedVoucherDetails(userVouchers, user.getLanguage());
            profileMessage += "\n\n" + usedVoucherDetails;
        }
        
        sendMessage(chatId, profileMessage);
    }
    
    private String getActiveVoucherDetails(List<Voucher> vouchers, String language) {
        StringBuilder details = new StringBuilder();
        
        String header = switch (language) {
            case "uz_cyrl" -> "🎟️ Фаол ваучерларингиз:";
            case "ru" -> "🎟️ Ваши активные ваучеры:";
            default -> "🎟️ Faol voucherlaringiz:";
        };
        
        details.append(header).append("\n");
        
        vouchers.stream()
            .filter(v -> v.getStatus() == Voucher.VoucherStatus.ACTIVE)
            .forEach(voucher -> {
                String typeText = switch (voucher.getType()) {
                    case BIRTHDAY -> switch (language) {
                        case "uz_cyrl" -> "🎂 Туғилган кун";
                        case "ru" -> "🎂 День рождения";
                        default -> "🎂 Tug'ilgan kun";
                    };
                    case ANNIVERSARY -> switch (language) {
                        case "uz_cyrl" -> "🎉 Юбилей";
                        case "ru" -> "🎉 Юбилей";
                        default -> "🎉 Yubiley";
                    };
                    case SPECIAL -> switch (language) {
                        case "uz_cyrl" -> "⭐ Махсус";
                        case "ru" -> "⭐ Специальный";
                        default -> "⭐ Maxsus";
                    };
                };
                
                long daysLeft = voucher.getDaysUntilExpiry();
                String expiryText = switch (language) {
                    case "uz_cyrl" -> daysLeft > 0 ? 
                        String.format("⏰ %d кун қолди", daysLeft) : "⚠️ Бугун тугайди";
                    case "ru" -> daysLeft > 0 ? 
                        String.format("⏰ %d дней осталось", daysLeft) : "⚠️ Истекает сегодня";
                    default -> daysLeft > 0 ? 
                        String.format("⏰ %d kun qoldi", daysLeft) : "⚠️ Bugun tugaydi";
                };
                
                details.append(String.format(
                    "\n• %s\n  💰 %,d so'm\n  🔑 %s\n  %s\n",
                    typeText,
                    voucher.getAmount(),
                    voucher.getCode().toUpperCase(),
                    expiryText
                ));
            });
        
        return details.toString();
    }
    
    private String getUsedVoucherDetails(List<Voucher> vouchers, String language) {
        StringBuilder details = new StringBuilder();
        
        String header = switch (language) {
            case "uz_cyrl" -> "🎟️ Ишлатилган ваучерларингиз:";
            case "ru" -> "🎟️ Ваши использованные ваучеры:";
            default -> "🎟️ Ishlatilgan voucherlaringiz:";
        };
        
        details.append(header).append("\n");
        
        vouchers.stream()
            .filter(v -> v.getStatus() == Voucher.VoucherStatus.USED)
            .forEach(voucher -> {
                String typeText = switch (voucher.getType()) {
                    case BIRTHDAY -> switch (language) {
                        case "uz_cyrl" -> "🎂 Туғилган кун";
                        case "ru" -> "🎂 День рождения";
                        default -> "🎂 Tug'ilgan kun";
                    };
                    case ANNIVERSARY -> switch (language) {
                        case "uz_cyrl" -> "🎉 Юбилей";
                        case "ru" -> "🎉 Юбилей";
                        default -> "🎉 Yubiley";
                    };
                    case SPECIAL -> switch (language) {
                        case "uz_cyrl" -> "⭐ Махсус";
                        case "ru" -> "⭐ Специальный";
                        default -> "⭐ Maxsus";
                    };
                };
                
                String usedDateText = switch (language) {
                    case "uz_cyrl" -> "✅ Ишлатилган: " + voucher.getUsedAt().toLocalDate();
                    case "ru" -> "✅ Использован: " + voucher.getUsedAt().toLocalDate();
                    default -> "✅ Ishlatilgan: " + voucher.getUsedAt().toLocalDate();
                };
                
                details.append(String.format(
                    "\n• %s\n  💰 %,d so'm\n  🔑 %s\n  %s\n",
                    typeText,
                    voucher.getAmount(),
                    voucher.getCode().toUpperCase(),
                    usedDateText
                ));
            });
        
        return details.toString();
    }
    
    private void showHelp(Long chatId, String language) {
        String helpMessage = getLocalizedMessage(language,
            """
            ℹ️ Yordam:
            
            🛒 Do'kon - AYSI OPTICS ko'zoynaklar katalogini ko'rish va xarid qilish
            👤 Profil - shaxsiy ma'lumotlaringizni ko'rish
            💬 Fikr bildirish - Yandex Maps'da biz haqimizda fikr qoldirish
            📋 So'rovnomada qatnashish - Google Forms orqali so'rovnomani to'ldirish
            ℹ️ Yordam - bu yordam xabari
            
            📞 Bog'lanish:
            👩‍💻 @aysi_menejer
            ☎️ +998938740305
            """,
            """
            ℹ️ Ёрдам:
            
            🛒 Дўкон - AYSI OPTICS кўзойнаклар каталогини кўриш ва харид қилиш
            👤 Профил - шахсий маълумотларингизни кўриш
            💬 Фикр билдириш - Yandex Maps'да биз ҳақимизда фикр қолдириш
            📋 Сўровномада қатнашиш - Google Forms орқали сўровномани тўлдириш
            ℹ️ Ёрдам - бу ёрдам хабари
            
            📞 Боғланиш:
            👩‍💻 @aysi_menejer
            ☎️ +998938740305
            """,
            """
            ℹ️ Помощь:
            
            🛒 Магазин - просмотр каталога очков AYSI OPTICS и покупки
            👤 Профиль - просмотр личной информации
            💬 Оставить отзыв - оставить отзыв о нас на Yandex Maps
            📋 Участвовать в опросе - заполнить опрос через Google Forms
            ℹ️ Помощь - это сообщение помощи
            
            📞 Связаться:
            👩‍💻 @aysi_menejer
            ☎️ +998938740305
            """
        );
        
        sendMessage(chatId, helpMessage);
    }
    
    private void showReviewRequest(Long chatId, String language) {
        String reviewMessage = getLocalizedMessage(language,
            """
            Aysi Optika xizmatlaridan foydalanganingiz uchun rahmat 🤍
            
            Agar 1 daqiqa vaqtingizni ajratsangiz, quyidagi havola orqali biz haqimizda fikringizni yozib qoldirishingizni so'raymiz:
            
            👉 https://yandex.uz/maps/org/200173416586/reviews/?ll=60.631547%2C41.557659&z=16
            
            Sizning fikringiz bizni yanada yaxshiroq bo'lishga undaydi. Oldindan rahmat! 🙏
            """,
            """
            Aysi Optika хизматларидан фойдаланганингиз учун раҳмат 🤍
            
            Агар 1 дақиқа вақтингизни ажратсангиз, қуйидаги ҳавола орқали биз ҳақимизда фикрингизни ёзиб қолдиришингизни сўраймиз:
            
            👉 https://yandex.uz/maps/org/200173416586/reviews/?ll=60.631547%2C41.557659&z=16
            
            Сизнинг фикрингиз бизни янада яхшироқ бўлишга ундайди. Олдиндан раҳмат! 🙏
            """,
            """
            Спасибо за использование услуг Aysi Optika 🤍
            
            Если вы можете уделить 1 минуту своего времени, мы просим вас оставить отзыв о нас по следующей ссылке:
            
            👉 https://yandex.uz/maps/org/200173416586/reviews/?ll=60.631547%2C41.557659&z=16
            
            Ваше мнение мотивирует нас становиться еще лучше. Заранее спасибо! 🙏
            """
        );
        
        sendMessage(chatId, reviewMessage);
    }
    
    private void showSurveyRequest(Long chatId, String language) {
        String surveyMessage = getLocalizedMessage(language,
            """
            📋 So'rovnomada qatnashing!
            
            Bizning xizmatlarimizni yaxshilash uchun sizning fikringiz muhim. Iltimos, quyidagi so'rovnomani to'ldiring:
            
            👉 https://docs.google.com/forms/d/e/1FAIpQLSfkeOTsYmrDmmDL0U3CNzN0htnC71M551K_8h8Q_23AKxtXlg/viewform?usp=header
            
            So'rovnoma 2-3 daqiqa vaqt oladi. Sizning javoblaringiz bizga yanada yaxshi xizmat ko'rsatishga yordam beradi! 🙏
            """,
            """
            📋 Сўровномада қатнашинг!
            
            Бизнинг хизматларимизни яхшилаш учун сизнинг фикрингиз муҳим. Илтимос, қуйидаги сўровномани тўлдиринг:
            
            👉 https://docs.google.com/forms/d/e/1FAIpQLSfkeOTsYmrDmmDL0U3CNzN0htnC71M551K_8h8Q_23AKxtXlg/viewform?usp=header
            
            Сўровнома 2-3 дақиқа вақт олади. Сизнинг жавобларингиз бизга янада яхши хизмат кўрсатишга ёрдам беради! 🙏
            """,
            """
            📋 Участвуйте в опросе!
            
            Ваше мнение важно для улучшения наших услуг. Пожалуйста, заполните следующий опрос:
            
            👉 https://docs.google.com/forms/d/e/1FAIpQLSfkeOTsYmrDmmDL0U3CNzN0htnC71M551K_8h8Q_23AKxtXlg/viewform?usp=header
            
            Опрос займет 2-3 минуты. Ваши ответы помогут нам предоставлять еще лучший сервис! 🙏
            """
        );
        
        sendMessage(chatId, surveyMessage);
    }
    
    private void openShop(Long chatId, String language) {
        String shopMessage;
        String buttonText;
        
        switch (language) {
            case "uz_cyrl" -> {
                shopMessage = """
                    🛒 AYSI OPTICS Дўкони
                    
                    Бизнинг дўконимизда энг сифатли кўзойнаклар мавжуд!
                    
                    Дўконни очиш учун қуйидаги тугмани босинг:
                    """;
                buttonText = "🛒 AYSI OPTICS ни очиш";
            }
            case "ru" -> {
                shopMessage = """
                    🛒 Магазин AYSI OPTICS
                    
                    В нашем магазине представлены самые качественные очки!
                    
                    Нажмите кнопку ниже, чтобы открыть магазин:
                    """;
                buttonText = "🛒 Открыть AYSI OPTICS";
            }
            default -> {
                shopMessage = """
                    🛒 AYSI OPTICS Do'koni
                    
                    Bizning do'konimizda eng sifatli ko'zoynaklar mavjud!
                    
                    Do'konni ochish uchun quyidagi tugmani bosing:
                    """;
                buttonText = "🛒 AYSI OPTICS ni ochish";
            }
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
        
        // Production Hetzner HTTPS domain
        shopButton.setUrl("https://aysioptics.uz/shop.html");
        
        row.add(shopButton);
        keyboard.add(row);
        inlineKeyboard.setKeyboard(keyboard);
        
        sendMessage.setReplyMarkup(inlineKeyboard);
        sendMessage(sendMessage);
    }
    
    private void sendRegisteredUserWelcome(User user, Long chatId) {
        String welcomeMessage = getLocalizedMessage(user.getLanguage(),
            String.format(
                "👋 Salom, %s!\n\nSiz allaqachon ro'yxatdan o'tgansiz. Menyudan kerakli bo'limni tanlang.",
                user.getFirstName()
            ),
            String.format(
                "👋 Салом, %s!\n\nСиз аллақачон рўйхатдан ўтгансиз. Менюдан керакли бўлимни танланг.",
                user.getFirstName()
            ),
            String.format(
                "👋 Привет, %s!\n\nВы уже зарегистрированы. Выберите нужный раздел из меню.",
                user.getFirstName()
            )
        );
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(welcomeMessage);
        sendMessage.setReplyMarkup(createMainMenuKeyboard(user.getLanguage()));
        
        sendMessage(sendMessage);
    }
    
    private void handleAdminCommand(User user, Long chatId) {
        // Admin Telegram ID'larini tekshirish
        Long[] adminTelegramIds = {1807166165L, 6051364132L}; // IbodullaR va aysi_menejer
        
        boolean isAdmin = false;
        for (Long adminId : adminTelegramIds) {
            if (user.getTelegramId().equals(adminId)) {
                isAdmin = true;
                break;
            }
        }
        
        if (!isAdmin) {
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "❌ Sizda admin huquqlari yo'q.",
                "❌ Сизда админ ҳуқуқлари йўқ.",
                "❌ У вас нет прав администратора.");
            sendMessage(chatId, errorMessage);
            return;
        }
        
        // Admin panel URL
        String adminPanelUrl = "https://aysioptics.uz/login.html";
        
        String adminMessage = getLocalizedMessage(user.getLanguage(),
            "🔐 Admin Panel\n\n" +
            "📊 Tezkor statistika:\n" +
            "👥 Jami foydalanuvchilar: " + userService.getTotalUsersCount() + "\n" +
            "🎟️ Jami voucherlar: " + voucherService.getTotalVouchersCount() + "\n" +
            "💰 Keshbek tizimi: Faol\n\n" +
            "Adminlar: @IbodullaR, @aysi_menejer\n\n" +
            "🌐 Admin panelga kirish uchun quyidagi tugmani bosing:",
            "🔐 Админ Панел\n\n" +
            "📊 Тезкор статистика:\n" +
            "👥 Жами фойдаланувчилар: " + userService.getTotalUsersCount() + "\n" +
            "🎟️ Жами ваучерлар: " + voucherService.getTotalVouchersCount() + "\n" +
            "💰 Кешбек тизими: Фаол\n\n" +
            "Админлар: @IbodullaR, @aysi_menejer\n\n" +
            "🌐 Админ панелга кириш учун қуйидаги тугмани босинг:",
            "🔐 Панель администратора\n\n" +
            "📊 Быстрая статистика:\n" +
            "👥 Всего пользователей: " + userService.getTotalUsersCount() + "\n" +
            "🎟️ Всего ваучеров: " + voucherService.getTotalVouchersCount() + "\n" +
            "💰 Система кешбэка: Активна\n\n" +
            "Администраторы: @IbodullaR, @aysi_menejer\n\n" +
            "🌐 Для входа в админ панель нажмите кнопку ниже:"
        );
        
        // Tugma bilan yuborish
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(adminMessage);
        
        // Inline keyboard yaratish
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("🌐 Admin Panelga kirish");
        button.setUrl(adminPanelUrl);
        row.add(button);
        
        keyboard.add(row);
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending admin panel message: ", e);
        }
    }
    
    private void handleTestNotificationCommand(User user, Long chatId) {
        // Admin huquqlarini tekshirish
        Long[] adminTelegramIds = {1807166165L, 6051364132L};
        
        boolean isAdmin = false;
        for (Long adminId : adminTelegramIds) {
            if (user.getTelegramId().equals(adminId)) {
                isAdmin = true;
                break;
            }
        }
        
        if (!isAdmin) {
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "❌ Sizda admin huquqlari yo'q.",
                "❌ Сизда админ ҳуқуқлари йўқ.",
                "❌ У вас нет прав администратора.");
            sendMessage(chatId, errorMessage);
            return;
        }
        
        // Test notification yuborish
        notificationService.testNotifications();
        String successMessage = getLocalizedMessage(user.getLanguage(),
            "✅ Test xabar yuborildi!",
            "✅ Тест хабар юборилди!",
            "✅ Тестовое сообщение отправлено!");
        sendMessage(chatId, successMessage);
    }
    
    private void handleTest3DayCommand(User user, Long chatId) {
        // Admin huquqlarini tekshirish
        Long[] adminTelegramIds = {1807166165L, 6051364132L};
        
        boolean isAdmin = false;
        for (Long adminId : adminTelegramIds) {
            if (user.getTelegramId().equals(adminId)) {
                isAdmin = true;
                break;
            }
        }
        
        if (!isAdmin) {
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "❌ Sizda admin huquqlari yo'q.",
                "❌ Сизда админ ҳуқуқлари йўқ.",
                "❌ У вас нет прав администратора.");
            sendMessage(chatId, errorMessage);
            return;
        }
        
        // 3 kunlik test notification yuborish
        notificationService.testThreeDayRegistrations();
        String successMessage = getLocalizedMessage(user.getLanguage(),
            "✅ 3 kunlik test bajarildi!",
            "✅ 3 кунлик тест бажарилди!",
            "✅ 3-дневный тест выполнен!");
        sendMessage(chatId, successMessage);
    }
    
    private void handleTestAnniversaryCommand(User user, Long chatId) {
        // Admin huquqlarini tekshirish
        Long[] adminTelegramIds = {1807166165L, 6051364132L};
        
        boolean isAdmin = false;
        for (Long adminId : adminTelegramIds) {
            if (user.getTelegramId().equals(adminId)) {
                isAdmin = true;
                break;
            }
        }
        
        if (!isAdmin) {
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "❌ Sizda admin huquqlari yo'q.",
                "❌ Сизда админ ҳуқуқлари йўқ.",
                "❌ У вас нет прав администратора.");
            sendMessage(chatId, errorMessage);
            return;
        }
        
        // 6 oylik yubiley test
        notificationService.testSixMonthAnniversary();
        String successMessage = getLocalizedMessage(user.getLanguage(),
            "✅ 6 oylik yubiley test bajarildi!",
            "✅ 6 ойлик юбилей тест бажарилди!",
            "✅ Тест 6-месячного юбилея выполнен!");
        sendMessage(chatId, successMessage);
    }
    
    private void handleTestBirthdayCommand(User user, Long chatId) {
        // Admin huquqlarini tekshirish
        Long[] adminTelegramIds = {1807166165L, 6051364132L};
        
        boolean isAdmin = false;
        for (Long adminId : adminTelegramIds) {
            if (user.getTelegramId().equals(adminId)) {
                isAdmin = true;
                break;
            }
        }
        
        if (!isAdmin) {
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "❌ Sizda admin huquqlari yo'q.",
                "❌ Сизда админ ҳуқуқлари йўқ.",
                "❌ У вас нет прав администратора.");
            sendMessage(chatId, errorMessage);
            return;
        }
        
        // Tug'ilgan kun test
        notificationService.testBirthdays();
        String successMessage = getLocalizedMessage(user.getLanguage(),
            "✅ Tug'ilgan kun test bajarildi!",
            "✅ Туғилган кун тест бажарилди!",
            "✅ Тест дня рождения выполнен!");
        sendMessage(chatId, successMessage);
    }
    
    private void handleBroadcastCommand(Message message, User user, Long chatId) {
        // Admin huquqlarini tekshirish
        Long[] adminTelegramIds = {1807166165L, 6051364132L};
        
        boolean isAdmin = false;
        for (Long adminId : adminTelegramIds) {
            if (user.getTelegramId().equals(adminId)) {
                isAdmin = true;
                break;
            }
        }
        
        if (!isAdmin) {
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "❌ Sizda admin huquqlari yo'q.",
                "❌ Сизда админ ҳуқуқлари йўқ.",
                "❌ У вас нет прав администратора.");
            sendMessage(chatId, errorMessage);
            return;
        }
        
        String text = message.getText();
        String[] parts = text.split(" ", 2);
        
        if (parts.length < 2) {
            String helpMessage = getLocalizedMessage(user.getLanguage(),
                """
                📢 Broadcast xabar yuborish:
                
                Foydalanish: /broadcast [xabar matni]
                
                Misol: /broadcast Assalomu alaykum! Yangi mahsulotlar keldi!
                
                ⚠️ Bu xabar barcha ro'yxatdan o'tgan foydalanuvchilarga yuboriladi.
                """,
                """
                📢 Broadcast хабар юбориш:
                
                Фойдаланиш: /broadcast [хабар матни]
                
                Мисол: /broadcast Ассалому алайкум! Янги маҳсулотлар келди!
                
                ⚠️ Бу хабар барча рўйхатдан ўтган фойдаланувчиларга юборилади.
                """,
                """
                📢 Отправка рассылки:
                
                Использование: /broadcast [текст сообщения]
                
                Пример: /broadcast Привет! Новые товары поступили!
                
                ⚠️ Это сообщение будет отправлено всем зарегистрированным пользователям.
                """
            );
            sendMessage(chatId, helpMessage);
            return;
        }
        
        String broadcastMessage = parts[1].trim();
        
        if (broadcastMessage.isEmpty()) {
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "❌ Xabar matni bo'sh bo'lishi mumkin emas.",
                "❌ Хабар матни бўш бўлиши мумкин эмас.",
                "❌ Текст сообщения не может быть пустым.");
            sendMessage(chatId, errorMessage);
            return;
        }
        
        String sendingMessage = getLocalizedMessage(user.getLanguage(),
            "📤 Xabar barcha foydalanuvchilarga yuborilmoqda...",
            "📤 Хабар барча фойдаланувчиларга юборилмоқда...",
            "� Сообщение отправляется всем пользователям...");
        sendMessage(chatId, sendingMessage);
        
        // Async ravishda yuborish
        CompletableFuture.runAsync(() -> {
            try {
                BroadcastService.BroadcastResult result = broadcastService.sendBroadcastMessage(broadcastMessage);
                
                String resultMessage = getLocalizedMessage(user.getLanguage(),
                    String.format(
                        """
                        ✅ Broadcast xabar yuborildi!
                        
                        📊 Natijalar:
                        👥 Jami foydalanuvchilar: %d
                        ✅ Muvaffaqiyatli: %d
                        ❌ Xatolik: %d
                        � Muvaffaqiyat darajasi: %.1f%%
                        """,
                        result.getTotalUsers(),
                        result.getSuccessCount(),
                        result.getFailureCount(),
                        result.getSuccessRate()
                    ),
                    String.format(
                        """
                        ✅ Broadcast хабар юборилди!
                        
                        📊 Натижалар:
                        👥 Жами фойдаланувчилар: %d
                        ✅ Муваффақиятли: %d
                        ❌ Хатолик: %d
                        📈 Муваффақият даражаси: %.1f%%
                        """,
                        result.getTotalUsers(),
                        result.getSuccessCount(),
                        result.getFailureCount(),
                        result.getSuccessRate()
                    ),
                    String.format(
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
                    )
                );
                
                sendMessage(chatId, resultMessage);
                
            } catch (Exception e) {
                log.error("Error in broadcast command: ", e);
                String errorMessage = getLocalizedMessage(user.getLanguage(),
                    "❌ Xabar yuborishda xatolik yuz berdi: " + e.getMessage(),
                    "❌ Хабар юборишда хатолик юз берди: " + e.getMessage(),
                    "❌ Ошибка при отправке сообщения: " + e.getMessage());
                sendMessage(chatId, errorMessage);
            }
        });
    }
    
    private void forwardMessageToAdmin(Message message, User user) {
        // Admin ID'larini olish
        String[] adminIds = {"1807166165", "6051364132"}; // Admin 1 va Admin 2
        
        String userInfo = String.format(
            "📩 Yangi xabar foydalanuvchidan:\n\n" +
            "👤 Ism: %s %s\n" +
            "📱 Telefon: %s\n" +
            "👤 Username: %s\n" +
            "🆔 Telegram ID: %d\n" +
            "🎂 Tug'ilgan sana: %s\n\n" +
            "💬 Xabar:\n%s",
            user.getFirstName(),
            user.getLastName(),
            user.getPhoneNumber(),
            user.getTelegramUsername() != null ? user.getTelegramUsername() : "Yo'q",
            user.getTelegramId(),
            user.getBirthDate() != null ? user.getBirthDate() : "Kiritilmagan",
            message.getText()
        );
        
        // Har bir adminga yuborish
        for (String adminId : adminIds) {
            try {
                sendMessage(Long.parseLong(adminId), userInfo);
            } catch (Exception e) {
                log.error("Error sending message to admin {}: ", adminId, e);
            }
        }
    }
    
    private void notifyAdminAboutHelpRequest(User user) {
        // Admin ID'larini olish
        String[] adminIds = {"1807166165", "6051364132"}; // Admin 1 va Admin 2
        
        String notification = String.format(
            "ℹ️ Yordam so'raldi!\n\n" +
            "👤 Foydalanuvchi: %s %s\n" +
            "📱 Telefon: %s\n" +
            "👤 Username: %s\n" +
            "🆔 Telegram ID: %d\n" +
            "🎂 Tug'ilgan sana: %s\n" +
            "📅 Ro'yxatdan o'tgan: %s\n\n" +
            "Foydalanuvchi 'Yordam' tugmasini bosdi.",
            user.getFirstName(),
            user.getLastName(),
            user.getPhoneNumber(),
            user.getTelegramUsername() != null ? user.getTelegramUsername() : "Yo'q",
            user.getTelegramId(),
            user.getBirthDate() != null ? user.getBirthDate() : "Kiritilmagan",
            user.getCreatedAt().toLocalDate()
        );
        
        // Har bir adminga yuborish
        for (String adminId : adminIds) {
            try {
                sendMessage(Long.parseLong(adminId), notification);
            } catch (Exception e) {
                log.error("Error sending help notification to admin {}: ", adminId, e);
            }
        }
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
    
    // ========== VIDEO/RASM BROADCAST METODLARI ==========
    
    private boolean isAdmin(Long userId) {
        Long[] adminTelegramIds = {1807166165L, 6051364132L};
        for (Long adminId : adminTelegramIds) {
            if (userId.equals(adminId)) {
                return true;
            }
        }
        return false;
    }
    
    private void handleAdminMediaBroadcast(Message message, User user, Long chatId) {
        log.info("Admin {} sent media for broadcast", user.getTelegramId());
        
        // Xabarni saqlash
        pendingBroadcastMessage = message;
        pendingBroadcastAdminId = chatId;
        
        // Tasdiqlash tugmasini ko'rsatish
        String confirmText = getLocalizedMessage(user.getLanguage(),
            "📢 Bu postni barcha ro'yxatdan o'tgan foydalanuvchilarga yuborasizmi?",
            "📢 Бу постни барча рўйхатдан ўтган фойдаланувчиларга юборасизми?",
            "📢 Отправить этот пост всем зарегистрированным пользователям?");
        
        SendMessage confirmMessage = new SendMessage();
        confirmMessage.setChatId(chatId);
        confirmMessage.setText(confirmText);
        confirmMessage.setReplyMarkup(createBroadcastConfirmationKeyboard(user.getLanguage()));
        
        sendMessage(confirmMessage);
    }
    
    private InlineKeyboardMarkup createBroadcastConfirmationKeyboard(String language) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText(getLocalizedMessage(language, "✅ Ha", "✅ Ҳа", "✅ Да"));
        confirmButton.setCallbackData("confirm_broadcast");
        
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText(getLocalizedMessage(language, "❌ Yo'q", "❌ Йўқ", "❌ Нет"));
        cancelButton.setCallbackData("cancel_broadcast");
        
        row.add(confirmButton);
        row.add(cancelButton);
        keyboard.add(row);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    private void handleBroadcastConfirmation(CallbackQuery callbackQuery, User user, Long chatId) {
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            answer.setText("Yuborilmoqda...");
            execute(answer);
        } catch (TelegramApiException e) {
            log.error("Error answering callback: ", e);
        }
        
        if (pendingBroadcastMessage == null) {
            String errorMsg = getLocalizedMessage(user.getLanguage(),
                "❌ Xatolik: Yuborish uchun xabar topilmadi.",
                "❌ Хатолик: Юбориш учун хабар топилмади.",
                "❌ Ошибка: Сообщение для отправки не найдено.");
            sendMessage(chatId, errorMsg);
            return;
        }
        
        String sendingMsg = getLocalizedMessage(user.getLanguage(),
            "📤 Xabar barcha foydalanuvchilarga yuborilmoqda...",
            "📤 Хабар барча фойдаланувчиларга юборилмоқда...",
            "📤 Сообщение отправляется всем пользователям...");
        sendMessage(chatId, sendingMsg);
        
        // Async ravishda yuborish
        Message messageToSend = pendingBroadcastMessage;
        KuponBot botInstance = this;
        CompletableFuture.runAsync(() -> {
            try {
                BroadcastService.BroadcastResult result = 
                    broadcastService.sendMediaBroadcast(messageToSend, botInstance);
                
                String resultMsg = getLocalizedMessage(user.getLanguage(),
                    String.format(
                        "✅ Broadcast yuborildi!\n\n" +
                        "📊 Natijalar:\n" +
                        "👥 Jami: %d\n" +
                        "✅ Muvaffaqiyatli: %d\n" +
                        "❌ Xatolik: %d\n" +
                        "📈 Muvaffaqiyat: %.1f%%",
                        result.getTotalUsers(),
                        result.getSuccessCount(),
                        result.getFailureCount(),
                        result.getSuccessRate()
                    ),
                    String.format(
                        "✅ Broadcast юборилди!\n\n" +
                        "📊 Натижалар:\n" +
                        "👥 Жами: %d\n" +
                        "✅ Муваффақиятли: %d\n" +
                        "❌ Хатолик: %d\n" +
                        "📈 Муваффақият: %.1f%%",
                        result.getTotalUsers(),
                        result.getSuccessCount(),
                        result.getFailureCount(),
                        result.getSuccessRate()
                    ),
                    String.format(
                        "✅ Рассылка отправлена!\n\n" +
                        "📊 Результаты:\n" +
                        "👥 Всего: %d\n" +
                        "✅ Успешно: %d\n" +
                        "❌ Ошибок: %d\n" +
                        "📈 Процент успеха: %.1f%%",
                        result.getTotalUsers(),
                        result.getSuccessCount(),
                        result.getFailureCount(),
                        result.getSuccessRate()
                    )
                );
                
                sendMessage(chatId, resultMsg);
                
            } catch (Exception e) {
                log.error("Error in media broadcast: ", e);
                String errorMsg = getLocalizedMessage(user.getLanguage(),
                    "❌ Xatolik: " + e.getMessage(),
                    "❌ Хатолик: " + e.getMessage(),
                    "❌ Ошибка: " + e.getMessage());
                sendMessage(chatId, errorMsg);
            }
        });
        
        // Pending message ni tozalash
        pendingBroadcastMessage = null;
        pendingBroadcastAdminId = null;
    }
    
    private void handleBroadcastCancellation(CallbackQuery callbackQuery, User user, Long chatId) {
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            answer.setText("Bekor qilindi");
            execute(answer);
        } catch (TelegramApiException e) {
            log.error("Error answering callback: ", e);
        }
        
        pendingBroadcastMessage = null;
        pendingBroadcastAdminId = null;
        
        String cancelMsg = getLocalizedMessage(user.getLanguage(),
            "❌ Broadcast bekor qilindi.",
            "❌ Broadcast бекор қилинди.",
            "❌ Рассылка отменена.");
        sendMessage(chatId, cancelMsg);
    }
    
    private void handleSendUserCommand(Message message, User user, Long chatId) {
        // Admin huquqlarini tekshirish
        if (!isAdmin(user.getTelegramId())) {
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "❌ Sizda admin huquqlari yo'q.",
                "❌ Сизда админ ҳуқуқлари йўқ.",
                "❌ У вас нет прав администратора.");
            sendMessage(chatId, errorMessage);
            return;
        }
        
        String text = message.getText();
        String[] parts = text.split(" ", 3);
        
        if (parts.length < 3) {
            String helpMessage = getLocalizedMessage(user.getLanguage(),
                """
                📤 Bitta foydalanuvchiga xabar yuborish:
                
                Foydalanish: /senduser <telegram_id> <xabar matni>
                
                Misol: /senduser 123456789 Assalomu alaykum! Sizga maxsus taklif...
                
                💡 Foydalanuvchining Telegram ID sini admin paneldan yoki /myid commandidan olishingiz mumkin.
                """,
                """
                📤 Битта фойдаланувчига хабар юбориш:
                
                Фойдаланиш: /senduser <telegram_id> <хабар матни>
                
                Мисол: /senduser 123456789 Ассалому алайкум! Сизга махсус таклиф...
                
                💡 Фойдаланувчининг Telegram ID сини админ панелдан ёки /myid командасидан олишингиз мумкин.
                """,
                """
                📤 Отправка сообщения одному пользователю:
                
                Использование: /senduser <telegram_id> <текст сообщения>
                
                Пример: /senduser 123456789 Привет! Для вас специальное предложение...
                
                💡 Telegram ID пользователя можно получить из админ панели или командой /myid.
                """
            );
            sendMessage(chatId, helpMessage);
            return;
        }
        
        try {
            Long targetUserId = Long.parseLong(parts[1].trim());
            String messageText = parts[2].trim();
            
            if (messageText.isEmpty()) {
                String errorMessage = getLocalizedMessage(user.getLanguage(),
                    "❌ Xabar matni bo'sh bo'lishi mumkin emas.",
                    "❌ Хабар матни бўш бўлиши мумкин эмас.",
                    "❌ Текст сообщения не может быть пустым.");
                sendMessage(chatId, errorMessage);
                return;
            }
            
            // Foydalanuvchini tekshirish
            Optional<User> targetUserOpt = userService.findByTelegramId(targetUserId);
            if (targetUserOpt.isEmpty()) {
                String errorMessage = getLocalizedMessage(user.getLanguage(),
                    "❌ Foydalanuvchi topilmadi. Telegram ID: " + targetUserId,
                    "❌ Фойдаланувчи топилмади. Telegram ID: " + targetUserId,
                    "❌ Пользователь не найден. Telegram ID: " + targetUserId);
                sendMessage(chatId, errorMessage);
                return;
            }
            
            User targetUser = targetUserOpt.get();
            
            // Xabarni yuborish
            boolean success = broadcastService.sendSingleMessage(targetUserId, messageText);
            
            if (success) {
                String successMessage = getLocalizedMessage(user.getLanguage(),
                    String.format(
                        """
                        ✅ Xabar yuborildi!
                        
                        👤 Foydalanuvchi: %s %s
                        👤 Username: %s
                        📱 Telefon: %s
                        🆔 Telegram ID: %d
                        
                        💬 Xabar: %s
                        """,
                        targetUser.getFirstName(),
                        targetUser.getLastName(),
                        targetUser.getTelegramUsername() != null ? targetUser.getTelegramUsername() : "Yo'q",
                        targetUser.getPhoneNumber(),
                        targetUserId,
                        messageText.length() > 100 ? messageText.substring(0, 100) + "..." : messageText
                    ),
                    String.format(
                        """
                        ✅ Хабар юборилди!
                        
                        👤 Фойдаланувчи: %s %s
                        👤 Username: %s
                        📱 Телефон: %s
                        🆔 Telegram ID: %d
                        
                        💬 Хабар: %s
                        """,
                        targetUser.getFirstName(),
                        targetUser.getLastName(),
                        targetUser.getTelegramUsername() != null ? targetUser.getTelegramUsername() : "Йўқ",
                        targetUser.getPhoneNumber(),
                        targetUserId,
                        messageText.length() > 100 ? messageText.substring(0, 100) + "..." : messageText
                    ),
                    String.format(
                        """
                        ✅ Сообщение отправлено!
                        
                        👤 Пользователь: %s %s
                        👤 Username: %s
                        📱 Телефон: %s
                        🆔 Telegram ID: %d
                        
                        💬 Сообщение: %s
                        """,
                        targetUser.getFirstName(),
                        targetUser.getLastName(),
                        targetUser.getTelegramUsername() != null ? targetUser.getTelegramUsername() : "Нет",
                        targetUser.getPhoneNumber(),
                        targetUserId,
                        messageText.length() > 100 ? messageText.substring(0, 100) + "..." : messageText
                    )
                );
                sendMessage(chatId, successMessage);
            } else {
                String errorMessage = getLocalizedMessage(user.getLanguage(),
                    "❌ Xabar yuborishda xatolik yuz berdi. Foydalanuvchi botni block qilgan bo'lishi mumkin.",
                    "❌ Хабар юборишда хатолик юз берди. Фойдаланувчи ботни блок қилган бўлиши мумкин.",
                    "❌ Ошибка при отправке сообщения. Возможно, пользователь заблокировал бота.");
                sendMessage(chatId, errorMessage);
            }
            
        } catch (NumberFormatException e) {
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "❌ Noto'g'ri Telegram ID formati. Faqat raqamlar kiriting.",
                "❌ Нотўғри Telegram ID формати. Фақат рақамлар киритинг.",
                "❌ Неправильный формат Telegram ID. Введите только цифры.");
            sendMessage(chatId, errorMessage);
        } catch (Exception e) {
            log.error("Error in senduser command: ", e);
            String errorMessage = getLocalizedMessage(user.getLanguage(),
                "❌ Xatolik yuz berdi: " + e.getMessage(),
                "❌ Хатолик юз берди: " + e.getMessage(),
                "❌ Произошла ошибка: " + e.getMessage());
            sendMessage(chatId, errorMessage);
        }
    }
}



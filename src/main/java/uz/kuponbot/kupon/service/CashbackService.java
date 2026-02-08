package uz.kuponbot.kupon.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.dto.CashbackDto;
import uz.kuponbot.kupon.entity.Cashback;
import uz.kuponbot.kupon.entity.User;
import uz.kuponbot.kupon.repository.CashbackRepository;
import uz.kuponbot.kupon.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashbackService {
    
    private final CashbackRepository cashbackRepository;
    private final UserRepository userRepository;
    private final BroadcastService broadcastService;
    private static final Double DEFAULT_CASHBACK_PERCENTAGE = 5.0;
    
    /**
     * Yangi harid qo'shish va keshbek hisoblash
     */
    @Transactional
    public CashbackDto addPurchase(Long telegramId, Integer purchaseAmount, String description) {
        User user = userRepository.findByTelegramId(telegramId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Keshbek hisoblash (5%)
        Integer cashbackAmount = (int) Math.round(purchaseAmount * DEFAULT_CASHBACK_PERCENTAGE / 100);
        
        // Cashback yaratish
        Cashback cashback = new Cashback();
        cashback.setUser(user);
        cashback.setPurchaseAmount(purchaseAmount);
        cashback.setCashbackAmount(cashbackAmount);
        cashback.setCashbackPercentage(DEFAULT_CASHBACK_PERCENTAGE);
        cashback.setType(Cashback.CashbackType.EARNED);
        cashback.setStatus(Cashback.CashbackStatus.ACTIVE);
        cashback.setDescription(description);
        cashback.setCreatedAt(LocalDateTime.now());
        
        cashback = cashbackRepository.save(cashback);
        
        // User balansini yangilash
        user.setCashbackBalance(user.getCashbackBalance() + cashbackAmount);
        userRepository.save(user);
        
        log.info("Cashback added for user {}: {} so'm ({}%)", telegramId, cashbackAmount, DEFAULT_CASHBACK_PERCENTAGE);
        
        // Userga notification yuborish
        sendCashbackNotification(user, purchaseAmount, cashbackAmount);
        
        return convertToDto(cashback);
    }
    
    /**
     * Keshbek berilganligi haqida userga xabar yuborish
     */
    private void sendCashbackNotification(User user, Integer purchaseAmount, Integer cashbackAmount) {
        try {
            String message = formatCashbackMessage(user, purchaseAmount, cashbackAmount);
            broadcastService.sendSingleMessage(user.getTelegramId(), message);
            log.info("Cashback notification sent to user {}", user.getTelegramId());
        } catch (Exception e) {
            log.error("Error sending cashback notification to user {}: ", user.getTelegramId(), e);
        }
    }
    
    /**
     * Keshbek xabarini formatlash (3 tilda)
     */
    private String formatCashbackMessage(User user, Integer purchaseAmount, Integer cashbackAmount) {
        String formattedPurchase = String.format("%,d", purchaseAmount);
        String formattedCashback = String.format("%,d", cashbackAmount);
        String formattedTotal = String.format("%,d", user.getCashbackBalance());
        
        return switch (user.getLanguage()) {
            case "uz_cyrl" -> String.format(
                "🎉 Табриклаймиз!\n\n" +
                "Сиз Aysi Optikadan амалга оширган %s сўм харидингиз учун миннатдорчилик сифатида сизга кешбек тақдим этилди.\n\n" +
                "💰 Берилган кешбек: %s сўм\n" +
                "💳 Жами йиғилган кешбек: %s сўм\n\n" +
                "Ушбу кешбекдан кейинги харидларингизда кўзойнак, линза ва бошқа оптик маҳсулотлар учун фойдаланишингиз мумкин.\n\n" +
                "Сизнинг кўз саломатлигингиз биз учун муҳим! 👓✨",
                formattedPurchase, formattedCashback, formattedTotal
            );
            case "ru" -> String.format(
                "🎉 Поздравляем!\n\n" +
                "В знак благодарности за вашу покупку в Aysi Optika на сумму %s сум вам начислен кешбэк.\n\n" +
                "💰 Начислено кешбэка: %s сум\n" +
                "💳 Всего накоплено кешбэка: %s сум\n\n" +
                "Вы можете использовать этот кешбэк при следующих покупках очков, линз и других оптических товаров.\n\n" +
                "Здоровье ваших глаз важно для нас! 👓✨",
                formattedPurchase, formattedCashback, formattedTotal
            );
            default -> String.format(
                "🎉 Tabriklaymiz!\n\n" +
                "Siz Aysi Optikadan amalga oshirgan %s so'm xaridingiz uchun minnatdorchilik sifatida sizga keshbek taqdim etildi.\n\n" +
                "💰 Berilgan keshbek: %s so'm\n" +
                "💳 Jami yig'ilgan keshbek: %s so'm\n\n" +
                "Ushbu keshbekdan keyingi xaridlaringizda ko'zoynak, linza va boshqa optik mahsulotlar uchun foydalanishingiz mumkin.\n\n" +
                "Sizning ko'z salomatligingiz biz uchun muhim! 👓✨",
                formattedPurchase, formattedCashback, formattedTotal
            );
        };
    }
    
    /**
     * Keshbekni ishlatish
     */
    @Transactional
    public CashbackDto useCashback(Long telegramId, Integer amount, String description) {
        User user = userRepository.findByTelegramId(telegramId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.getCashbackBalance() < amount) {
            throw new RuntimeException("Insufficient cashback balance");
        }
        
        // Cashback ishlatish yozuvi
        Cashback cashback = new Cashback();
        cashback.setUser(user);
        cashback.setPurchaseAmount(0);
        cashback.setCashbackAmount(amount);
        cashback.setCashbackPercentage(0.0);
        cashback.setType(Cashback.CashbackType.USED);
        cashback.setStatus(Cashback.CashbackStatus.USED);
        cashback.setDescription(description);
        cashback.setCreatedAt(LocalDateTime.now());
        cashback.setUsedAt(LocalDateTime.now());
        
        cashback = cashbackRepository.save(cashback);
        
        // User balansini kamaytirish
        user.setCashbackBalance(user.getCashbackBalance() - amount);
        userRepository.save(user);
        
        log.info("Cashback used by user {}: {} so'm", telegramId, amount);
        
        return convertToDto(cashback);
    }
    
    /**
     * Keshbekni qaytarish
     */
    @Transactional
    public CashbackDto refundCashback(Long telegramId, Integer amount, String description) {
        User user = userRepository.findByTelegramId(telegramId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Cashback qaytarish yozuvi
        Cashback cashback = new Cashback();
        cashback.setUser(user);
        cashback.setPurchaseAmount(0);
        cashback.setCashbackAmount(amount);
        cashback.setCashbackPercentage(0.0);
        cashback.setType(Cashback.CashbackType.REFUNDED);
        cashback.setStatus(Cashback.CashbackStatus.ACTIVE);
        cashback.setDescription(description);
        cashback.setCreatedAt(LocalDateTime.now());
        
        cashback = cashbackRepository.save(cashback);
        
        // User balansini oshirish
        user.setCashbackBalance(user.getCashbackBalance() + amount);
        userRepository.save(user);
        
        log.info("Cashback refunded to user {}: {} so'm", telegramId, amount);
        
        return convertToDto(cashback);
    }
    
    /**
     * Foydalanuvchining keshbek tarixini olish
     */
    public List<CashbackDto> getUserCashbackHistory(Long telegramId) {
        User user = userRepository.findByTelegramId(telegramId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        return cashbackRepository.findByUserOrderByCreatedAtDesc(user)
            .stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }
    
    /**
     * Barcha foydalanuvchilarning keshbek balansini olish
     */
    public List<UserCashbackBalance> getAllUsersCashbackBalance() {
        return userRepository.findAll()
            .stream()
            .filter(user -> user.getState() == User.UserState.REGISTERED)
            .map(user -> new UserCashbackBalance(
                user.getTelegramId(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getCashbackBalance(),
                getTotalEarned(user),
                getTotalUsed(user)
            ))
            .collect(Collectors.toList());
    }
    
    /**
     * Barcha keshbek tarixini olish
     */
    public List<CashbackDto> getAllCashbackHistory() {
        return cashbackRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }
    
    /**
     * User uchun keshbek statistikasini olish
     */
    public UserCashbackStats getUserCashbackStats(User user) {
        Integer totalEarned = getTotalEarned(user);
        Integer totalUsed = getTotalUsed(user);
        return new UserCashbackStats(
            user.getCashbackBalance(),
            totalEarned,
            totalUsed
        );
    }
    
    private Integer getTotalEarned(User user) {
        return cashbackRepository.findByUserAndStatusOrderByCreatedAtDesc(user, Cashback.CashbackStatus.ACTIVE)
            .stream()
            .filter(c -> c.getType() == Cashback.CashbackType.EARNED)
            .mapToInt(Cashback::getCashbackAmount)
            .sum();
    }
    
    private Integer getTotalUsed(User user) {
        return cashbackRepository.findByUserAndStatusOrderByCreatedAtDesc(user, Cashback.CashbackStatus.USED)
            .stream()
            .filter(c -> c.getType() == Cashback.CashbackType.USED)
            .mapToInt(Cashback::getCashbackAmount)
            .sum();
    }
    
    private CashbackDto convertToDto(Cashback cashback) {
        CashbackDto dto = new CashbackDto();
        dto.setId(cashback.getId());
        dto.setUserId(cashback.getUser().getId());
        dto.setTelegramId(cashback.getUser().getTelegramId());
        dto.setFullName(cashback.getUser().getFullName());
        dto.setPhoneNumber(cashback.getUser().getPhoneNumber());
        dto.setPurchaseAmount(cashback.getPurchaseAmount());
        dto.setCashbackAmount(cashback.getCashbackAmount());
        dto.setCashbackPercentage(cashback.getCashbackPercentage());
        dto.setType(cashback.getType().name());
        dto.setStatus(cashback.getStatus().name());
        dto.setDescription(cashback.getDescription());
        dto.setCreatedAt(cashback.getCreatedAt());
        dto.setUsedAt(cashback.getUsedAt());
        return dto;
    }
    
    @Data
    @AllArgsConstructor
    public static class UserCashbackBalance {
        private Long telegramId;
        private String fullName;
        private String phoneNumber;
        private Integer currentBalance;
        private Integer totalEarned;
        private Integer totalUsed;
    }
    
    @Data
    @AllArgsConstructor
    public static class UserCashbackStats {
        private Integer currentBalance;
        private Integer totalEarned;
        private Integer totalUsed;
    }
}

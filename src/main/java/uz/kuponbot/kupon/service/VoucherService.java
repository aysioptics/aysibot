package uz.kuponbot.kupon.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.entity.User;
import uz.kuponbot.kupon.entity.Voucher;
import uz.kuponbot.kupon.repository.VoucherRepository;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class VoucherService {
    
    private final VoucherRepository voucherRepository;
    private static final String VOUCHER_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int VOUCHER_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();
    
    public Voucher createBirthdayVoucher(User user) {
        log.info("Creating birthday voucher for user: {}", user.getTelegramId());
        
        Voucher voucher = new Voucher();
        voucher.setCode(generateUniqueVoucherCode());
        voucher.setUser(user);
        voucher.setAmount(50000); // 50,000 so'm
        voucher.setType(Voucher.VoucherType.BIRTHDAY);
        voucher.setStatus(Voucher.VoucherStatus.ACTIVE);
        voucher.setCreatedAt(LocalDateTime.now());
        voucher.setExpiresAt(LocalDateTime.now().plusDays(3)); // 3 kun amal qiladi
        
        Voucher saved = voucherRepository.save(voucher);
        log.info("Birthday voucher created: {} for user: {}", saved.getCode(), user.getTelegramId());
        
        return saved;
    }
    
    public Voucher createSpecialVoucher(User user, int amount, int validDays) {
        log.info("Creating special voucher for user: {} with amount: {}", user.getTelegramId(), amount);
        
        Voucher voucher = new Voucher();
        voucher.setCode(generateUniqueVoucherCode());
        voucher.setUser(user);
        voucher.setAmount(amount);
        voucher.setType(Voucher.VoucherType.SPECIAL);
        voucher.setStatus(Voucher.VoucherStatus.ACTIVE);
        voucher.setCreatedAt(LocalDateTime.now());
        voucher.setExpiresAt(LocalDateTime.now().plusDays(validDays));
        
        return voucherRepository.save(voucher);
    }
    
    private String generateUniqueVoucherCode() {
        String code;
        do {
            code = generateVoucherCode();
        } while (voucherRepository.existsByCode(code));
        return code;
    }
    
    private String generateVoucherCode() {
        StringBuilder code = new StringBuilder(VOUCHER_LENGTH);
        for (int i = 0; i < VOUCHER_LENGTH; i++) {
            code.append(VOUCHER_CHARS.charAt(random.nextInt(VOUCHER_CHARS.length())));
        }
        return code.toString();
    }
    
    public Optional<Voucher> findByCode(String code) {
        return voucherRepository.findByCode(code.toLowerCase());
    }
    
    public List<Voucher> getUserVouchers(User user) {
        return voucherRepository.findByUser(user);
    }
    
    public List<Voucher> getActiveUserVouchers(User user) {
        return voucherRepository.findByUserAndStatus(user, Voucher.VoucherStatus.ACTIVE);
    }
    
    public Voucher useVoucher(String code) {
        Optional<Voucher> voucherOpt = findByCode(code);
        if (voucherOpt.isEmpty()) {
            throw new RuntimeException("Voucher topilmadi: " + code);
        }
        
        Voucher voucher = voucherOpt.get();
        
        if (voucher.getStatus() != Voucher.VoucherStatus.ACTIVE) {
            throw new RuntimeException("Voucher faol emas: " + code);
        }
        
        if (voucher.isExpired()) {
            voucher.setStatus(Voucher.VoucherStatus.EXPIRED);
            voucherRepository.save(voucher);
            throw new RuntimeException("Voucher muddati tugagan: " + code);
        }
        
        voucher.setStatus(Voucher.VoucherStatus.USED);
        voucher.setUsedAt(LocalDateTime.now());
        
        log.info("Voucher used: {} by user: {}", code, voucher.getUser().getTelegramId());
        return voucherRepository.save(voucher);
    }
    
    public void expireOldVouchers() {
        List<Voucher> expiredVouchers = voucherRepository.findExpiredVouchers(LocalDateTime.now());
        
        for (Voucher voucher : expiredVouchers) {
            voucher.setStatus(Voucher.VoucherStatus.EXPIRED);
            log.info("Voucher expired: {} for user: {}", voucher.getCode(), voucher.getUser().getTelegramId());
        }
        
        if (!expiredVouchers.isEmpty()) {
            voucherRepository.saveAll(expiredVouchers);
            log.info("Expired {} vouchers", expiredVouchers.size());
        }
    }
    
    public List<Voucher> getVouchersNeedingReminder() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAfterTomorrow = now.plusDays(2);
        LocalDateTime yesterday = now.minusDays(1);
        
        return voucherRepository.findVouchersNeedingReminder(now, dayAfterTomorrow, yesterday);
    }
    
    public void markReminderSent(Voucher voucher) {
        voucher.setLastReminderSent(LocalDateTime.now());
        voucherRepository.save(voucher);
    }
    
    public List<Voucher> getAllVouchers() {
        return voucherRepository.findAll();
    }
    
    public long getTotalVouchersCount() {
        return voucherRepository.count();
    }
    
    public long getActiveVouchersCount() {
        return voucherRepository.countByStatus(Voucher.VoucherStatus.ACTIVE);
    }
    
    public long getUsedVouchersCount() {
        return voucherRepository.countByStatus(Voucher.VoucherStatus.USED);
    }
    
    public long getExpiredVouchersCount() {
        return voucherRepository.countByStatus(Voucher.VoucherStatus.EXPIRED);
    }
}
package uz.kuponbot.kupon.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.kuponbot.kupon.entity.User;
import uz.kuponbot.kupon.entity.Voucher;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    
    Optional<Voucher> findByCode(String code);
    
    List<Voucher> findByUser(User user);
    
    List<Voucher> findByUserAndStatus(User user, Voucher.VoucherStatus status);
    
    List<Voucher> findByStatus(Voucher.VoucherStatus status);
    
    List<Voucher> findByType(Voucher.VoucherType type);
    
    @Query("SELECT v FROM Voucher v WHERE v.status = 'ACTIVE' AND v.expiresAt < :now")
    List<Voucher> findExpiredVouchers(@Param("now") LocalDateTime now);
    
    @Query("SELECT v FROM Voucher v WHERE v.status = 'ACTIVE' AND v.expiresAt BETWEEN :now AND :tomorrow")
    List<Voucher> findVouchersExpiringTomorrow(@Param("now") LocalDateTime now, @Param("tomorrow") LocalDateTime tomorrow);
    
    @Query("SELECT v FROM Voucher v WHERE v.status = 'ACTIVE' AND v.expiresAt BETWEEN :now AND :dayAfterTomorrow AND (v.lastReminderSent IS NULL OR v.lastReminderSent < :yesterday)")
    List<Voucher> findVouchersNeedingReminder(@Param("now") LocalDateTime now, @Param("dayAfterTomorrow") LocalDateTime dayAfterTomorrow, @Param("yesterday") LocalDateTime yesterday);
    
    boolean existsByCode(String code);
    
    long countByStatus(Voucher.VoucherStatus status);
    
    long countByType(Voucher.VoucherType type);
}
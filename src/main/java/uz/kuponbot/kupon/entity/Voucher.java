package uz.kuponbot.kupon.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "vouchers")
@Data
public class Voucher {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 10)
    private String code;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private Integer amount; // 50000 so'm
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoucherStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoucherType type;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "used_at")
    private LocalDateTime usedAt;
    
    @Column(name = "last_reminder_sent")
    private LocalDateTime lastReminderSent;
    
    public enum VoucherStatus {
        ACTIVE,
        USED,
        EXPIRED
    }
    
    public enum VoucherType {
        BIRTHDAY,
        ANNIVERSARY,
        SPECIAL
    }
    
    public Voucher() {
        this.createdAt = LocalDateTime.now();
        this.status = VoucherStatus.ACTIVE;
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    public long getDaysUntilExpiry() {
        if (expiresAt == null) return 0;
        long days = java.time.Duration.between(LocalDateTime.now(), expiresAt).toDays();
        return Math.max(0, days); // Manfiy qiymat qaytarmaslik
    }
}
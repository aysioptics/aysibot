package uz.kuponbot.kupon.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VoucherDto {
    private Long id;
    private String code;
    private Long userTelegramId;
    private String userFullName;
    private Integer amount;
    private String status;
    private String type;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private long daysUntilExpiry;
}
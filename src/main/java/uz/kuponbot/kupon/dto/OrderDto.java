package uz.kuponbot.kupon.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    private Long id;
    private Long userId;
    private String userFullName;
    private String userPhone;
    private Long productId;
    private String productName;
    private Integer quantity;
    private Integer totalPrice;
    private String customerNote;
    private String status;
    private LocalDateTime createdAt;
}

package uz.kuponbot.kupon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private List<String> imageUrls; // Ko'p rasmlar
    private Integer stockQuantity;
    private String status;
    private LocalDateTime createdAt;
}
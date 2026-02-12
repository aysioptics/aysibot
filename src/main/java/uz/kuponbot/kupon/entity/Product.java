package uz.kuponbot.kupon.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 500)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private BigDecimal price;
    
    // Ko'p rasmlar uchun JSON array
    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;
    
    @Column(name = "stock_quantity")
    private Integer stockQuantity = 0;
    
    @Enumerated(EnumType.STRING)
    private ProductStatus status = ProductStatus.ACTIVE;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    public enum ProductStatus {
        ACTIVE, INACTIVE, OUT_OF_STOCK
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // Helper metodlar - JSON array bilan ishlash uchun
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public List<String> getImageUrlsList() {
        if (imageUrls == null || imageUrls.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(imageUrls, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
    
    public void setImageUrlsList(List<String> urls) {
        try {
            this.imageUrls = objectMapper.writeValueAsString(urls);
        } catch (JsonProcessingException e) {
            this.imageUrls = "[]";
        }
    }
    
    // Birinchi rasmni olish (asosiy rasm)
    public String getFirstImageUrl() {
        List<String> urls = getImageUrlsList();
        return urls.isEmpty() ? null : urls.get(0);
    }
}
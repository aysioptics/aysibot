package uz.kuponbot.kupon.controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.dto.ProductDto;
import uz.kuponbot.kupon.entity.Order;
import uz.kuponbot.kupon.entity.Product;
import uz.kuponbot.kupon.entity.User;
import uz.kuponbot.kupon.service.NotificationService;
import uz.kuponbot.kupon.service.OrderService;
import uz.kuponbot.kupon.service.ProductService;
import uz.kuponbot.kupon.service.UserService;

@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
@Slf4j
public class ShopController {
    
    private final ProductService productService;
    private final UserService userService;
    private final OrderService orderService;
    private final NotificationService notificationService;
    
    @GetMapping("/products")
    public ResponseEntity<List<ProductDto>> getProducts() {
        List<Product> products = productService.getAvailableProducts();
        List<ProductDto> productDtos = products.stream()
            .map(this::convertToProductDtoWithFirstImage)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(productDtos);
    }
    
    @GetMapping("/products/{id}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable Long id) {
        Optional<Product> productOpt = productService.findById(id);
        if (productOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        // Bitta mahsulot uchun barcha rasmlarni yuborish
        return ResponseEntity.ok(convertToProductDto(productOpt.get()));
    }
    
    @GetMapping("/user/{telegramId}")
    public ResponseEntity<?> getUserInfo(@PathVariable Long telegramId) {
        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        User user = userOpt.get();
        return ResponseEntity.ok(new UserInfoResponse(
            user.getFirstName(),
            user.getLastName(),
            user.getPhoneNumber(),
            user.getTelegramUsername()
        ));
    }
    
    private ProductDto convertToProductDto(Product product) {
        return new ProductDto(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getImageUrlsList(), // Ko'p rasmlar
            product.getStockQuantity(),
            product.getStatus().toString(),
            product.getCreatedAt()
        );
    }
    
    private ProductDto convertToProductDtoWithFirstImage(Product product) {
        List<String> imageUrls = product.getImageUrlsList();
        List<String> firstImageOnly = imageUrls.isEmpty() ? List.of() : List.of(imageUrls.get(0));
        
        return new ProductDto(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            firstImageOnly, // Faqat birinchi rasm
            product.getStockQuantity(),
            product.getStatus().toString(),
            product.getCreatedAt()
        );
    }
    
    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        log.info("Creating order: telegramId={}, productId={}, quantity={}", 
            request.getTelegramId(), request.getProductId(), request.getQuantity());
        
        try {
            // Find user
            Optional<User> userOpt = userService.findByTelegramId(request.getTelegramId());
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Foydalanuvchi topilmadi");
            }
            
            // Find product
            Optional<Product> productOpt = productService.findById(request.getProductId());
            if (productOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Mahsulot topilmadi");
            }
            
            // Create order
            Order order = orderService.createOrder(
                userOpt.get(),
                productOpt.get(),
                request.getQuantity(),
                request.getCustomerNote()
            );
            
            return ResponseEntity.ok(new CreateOrderResponse(
                order.getId(),
                "Buyurtma muvaffaqiyatli qabul qilindi! Tez orada admin siz bilan bog'lanadi."
            ));
            
        } catch (RuntimeException e) {
            log.error("Error creating order: ", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PostMapping("/cart-order")
    public ResponseEntity<?> createCartOrder(@RequestBody CartOrderRequest request) {
        log.info("Creating cart order: telegramId={}, items={}", 
            request.getTelegramId(), request.getItems().size());
        
        try {
            // Find user
            Optional<User> userOpt = userService.findByTelegramId(request.getTelegramId());
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Foydalanuvchi topilmadi");
            }
            
            User user = userOpt.get();
            
            // Build order message
            StringBuilder message = new StringBuilder();
            message.append("🛒 YANGI BUYURTMA\n\n");
            message.append("👤 Mijoz: ").append(user.getFirstName()).append(" ").append(user.getLastName()).append("\n");
            message.append("📞 Telefon: ").append(user.getPhoneNumber() != null ? user.getPhoneNumber() : "Kiritilmagan").append("\n");
            
            // Add username if available
            if (user.getTelegramUsername() != null && !user.getTelegramUsername().isEmpty()) {
                message.append("👨‍💼 Username: @").append(user.getTelegramUsername()).append("\n");
            }
            
            message.append("🆔 Telegram ID: ").append(user.getTelegramId()).append("\n\n");
            message.append("📦 Mahsulotlar:\n");
            
            double totalPrice = 0;
            for (CartItem item : request.getItems()) {
                Optional<Product> productOpt = productService.findById(item.getProductId());
                if (productOpt.isPresent()) {
                    Product product = productOpt.get();
                    double price = product.getPrice().doubleValue();
                    double itemTotal = price * item.getQuantity();
                    totalPrice += itemTotal;
                    
                    message.append(String.format("• %s\n", product.getName()));
                    message.append(String.format("  Narxi: %,.0f so'm x %d = %,.0f so'm\n", 
                        price, item.getQuantity(), itemTotal));
                }
            }
            
            message.append(String.format("\n💵 Jami: %,.0f so'm", totalPrice));
            
            // Send to notification service (will send to channel)
            notificationService.sendOrderNotification(message.toString());
            
            return ResponseEntity.ok(new CreateOrderResponse(
                null,
                "Buyurtma qabul qilindi! Tez orada admin siz bilan bog'lanadi."
            ));
            
        } catch (Exception e) {
            log.error("Error creating cart order: ", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @Data
    public static class CreateOrderRequest {
        private Long telegramId;
        private Long productId;
        private Integer quantity;
        private String customerNote;
    }
    
    @Data
    public static class CartOrderRequest {
        private Long telegramId;
        private List<CartItem> items;
    }
    
    @Data
    public static class CartItem {
        private Long productId;
        private Integer quantity;
    }
    
    @Data
    public static class CreateOrderResponse {
        private final Long orderId;
        private final String message;
    }
    
    @Data
    public static class UserInfoResponse {
        private final String firstName;
        private final String lastName;
        private final String phoneNumber;
        private final String username;
    }
}
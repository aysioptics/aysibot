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
    
    @GetMapping("/products")
    public ResponseEntity<List<ProductDto>> getProducts() {
        List<Product> products = productService.getAvailableProducts();
        List<ProductDto> productDtos = products.stream()
            .map(this::convertToProductDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(productDtos);
    }
    
    @GetMapping("/products/{id}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable Long id) {
        Optional<Product> productOpt = productService.findById(id);
        if (productOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(convertToProductDto(productOpt.get()));
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
    
    @Data
    public static class CreateOrderRequest {
        private Long telegramId;
        private Long productId;
        private Integer quantity;
        private String customerNote;
    }
    
    @Data
    public static class CreateOrderResponse {
        private final Long orderId;
        private final String message;
    }
}
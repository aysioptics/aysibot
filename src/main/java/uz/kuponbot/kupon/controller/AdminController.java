package uz.kuponbot.kupon.controller;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.dto.AdminStatsDto;
import uz.kuponbot.kupon.dto.OrderDto;
import uz.kuponbot.kupon.dto.OrderItemDto;
import uz.kuponbot.kupon.dto.ProductDto;
import uz.kuponbot.kupon.dto.UserDto;
import uz.kuponbot.kupon.entity.Coupon;
import uz.kuponbot.kupon.entity.Order;
import uz.kuponbot.kupon.entity.OrderItem;
import uz.kuponbot.kupon.entity.Product;
import uz.kuponbot.kupon.entity.User;
import uz.kuponbot.kupon.service.BroadcastService;
import uz.kuponbot.kupon.service.CouponService;
import uz.kuponbot.kupon.service.ExcelExportService;
import uz.kuponbot.kupon.service.NotificationService;
import uz.kuponbot.kupon.service.OrderService;
import uz.kuponbot.kupon.service.ProductService;
import uz.kuponbot.kupon.service.UserService;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    
    private final UserService userService;
    private final CouponService couponService;
    private final ProductService productService;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final ExcelExportService excelExportService;
    private final BroadcastService broadcastService;
    
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> getStats() {
        long totalUsers = userService.getTotalUsersCount();
        long totalCoupons = couponService.getTotalCouponsCount();
        long totalProducts = productService.getTotalProductsCount();
        long totalOrders = orderService.getTotalOrdersCount();
        
        List<Coupon> allCoupons = couponService.getAllCoupons();
        long activeCoupons = allCoupons.stream()
            .filter(c -> c.getStatus() == Coupon.CouponStatus.ACTIVE)
            .count();
        long usedCoupons = allCoupons.stream()
            .filter(c -> c.getStatus() == Coupon.CouponStatus.USED)
            .count();
        
        AdminStatsDto stats = new AdminStatsDto(
            totalUsers,
            totalCoupons,
            activeCoupons,
            usedCoupons,
            totalProducts,
            totalOrders
        );
        
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        List<UserDto> userDtos = users.stream()
            .map(this::convertToUserDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(userDtos);
    }
    
    @GetMapping("/users-filtered")
    public ResponseEntity<List<UserDto>> getFilteredUsers(@RequestParam String filter) {
        List<User> users = userService.getUsersByDateFilter(filter);
        List<UserDto> userDtos = users.stream()
            .map(this::convertToUserDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(userDtos);
    }
    
    @GetMapping("/products")
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        List<ProductDto> productDtos = products.stream()
            .map(this::convertToProductDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(productDtos);
    }
    
    @PostMapping("/products")
    public ResponseEntity<ProductDto> createProduct(@RequestBody CreateProductRequest request) {
        log.info("=== Create Product Request ===");
        log.info("Name: {}", request.getName());
        log.info("Description: {}", request.getDescription());
        log.info("Price: {}", request.getPrice());
        log.info("Stock: {}", request.getStockQuantity());
        
        try {
            Product product = productService.createProduct(
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getImageUrl(),
                request.getStockQuantity()
            );
            
            log.info("Product created successfully with ID: {}", product.getId());
            return ResponseEntity.ok(convertToProductDto(product));
        } catch (Exception e) {
            log.error("Error creating product: ", e);
            throw e;
        }
    }
    
    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/orders")
    public ResponseEntity<List<OrderDto>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        List<OrderDto> orderDtos = orders.stream()
            .map(this::convertToOrderDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(orderDtos);
    }
    
    @PutMapping("/orders/{id}/status")
    public ResponseEntity<OrderDto> updateOrderStatus(@PathVariable Long id, @RequestBody UpdateOrderStatusRequest request) {
        Order order = orderService.updateOrderStatus(id, Order.OrderStatus.valueOf(request.getStatus()));
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(convertToOrderDto(order));
    }
    
    @PostMapping("/test-notifications")
    public ResponseEntity<String> testNotifications() {
        notificationService.testNotifications();
        return ResponseEntity.ok("Test notification sent!");
    }
    
    @PostMapping("/test-anniversary")
    public ResponseEntity<String> testAnniversary() {
        notificationService.testSixMonthAnniversary();
        return ResponseEntity.ok("Anniversary check completed!");
    }
    
    @PostMapping("/test-birthdays")
    public ResponseEntity<String> testBirthdays() {
        notificationService.testBirthdays();
        return ResponseEntity.ok("Birthday check completed!");
    }
    
    @PostMapping("/test-3minute")
    public ResponseEntity<String> testThreeMinute() {
        notificationService.testThreeMinuteRegistrations();
        return ResponseEntity.ok("3-minute registration check completed!");
    }
    
    @GetMapping("/export-users")
    public ResponseEntity<byte[]> exportUsers() {
        try {
            List<User> users = userService.getAllUsers();
            List<UserDto> userDtos = users.stream()
                .map(this::convertToUserDto)
                .collect(Collectors.toList());
            
            byte[] excelData = excelExportService.exportUsersToExcel(userDtos);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "foydalanuvchilar.xlsx");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
                
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/export-users-filtered")
    public ResponseEntity<byte[]> exportUsersFiltered(@RequestParam String filter) {
        try {
            List<User> users = userService.getUsersByDateFilter(filter);
            List<UserDto> userDtos = users.stream()
                .map(this::convertToUserDto)
                .collect(Collectors.toList());
            
            byte[] excelData = excelExportService.exportUsersToExcel(userDtos);
            
            String fileName = getFilteredFileName(filter);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
                
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private String getFilteredFileName(String filter) {
        return switch (filter) {
            case "today" -> "bugungi-foydalanuvchilar.xlsx";
            case "this_month" -> "oylik-foydalanuvchilar.xlsx";
            case "this_year" -> "yillik-foydalanuvchilar.xlsx";
            default -> "foydalanuvchilar.xlsx";
        };
    }
    
    @PostMapping("/broadcast")
    public ResponseEntity<BroadcastResponse> sendBroadcast(@RequestBody BroadcastRequest request) {
        try {
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            BroadcastService.BroadcastResult result = broadcastService.sendBroadcastMessage(request.getMessage());
            
            BroadcastResponse response = new BroadcastResponse(
                result.getTotalUsers(),
                result.getSuccessCount(),
                result.getFailureCount(),
                result.getSuccessRate()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error sending broadcast message: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/find-user/{telegramId}")
    public ResponseEntity<UserDto> findUserByTelegramId(@PathVariable Long telegramId) {
        try {
            Optional<User> userOptional = userService.findByTelegramId(telegramId);
            if (userOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            UserDto userDto = convertToUserDto(userOptional.get());
            return ResponseEntity.ok(userDto);
            
        } catch (Exception e) {
            log.error("Error finding user by telegram ID {}: ", telegramId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/send-single-message")
    public ResponseEntity<SingleMessageResponse> sendSingleMessage(@RequestBody SingleMessageRequest request) {
        try {
            if (request.getTelegramId() == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            Optional<User> userOptional = userService.findByTelegramId(request.getTelegramId());
            if (userOptional.isEmpty() || userOptional.get().getState() != User.UserState.REGISTERED) {
                return ResponseEntity.notFound().build();
            }
            
            boolean success = broadcastService.sendSingleMessage(request.getTelegramId(), request.getMessage());
            
            SingleMessageResponse response = new SingleMessageResponse(success, 
                success ? "Xabar muvaffaqiyatli yuborildi" : "Xabar yuborishda xatolik");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error sending single message to {}: ", request.getTelegramId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private UserDto convertToUserDto(User user) {
        List<Coupon> userCoupons = couponService.getUserCoupons(user);
        long activeCoupons = userCoupons.stream()
            .filter(c -> c.getStatus() == Coupon.CouponStatus.ACTIVE)
            .count();
        
        return new UserDto(
            user.getId(),
            user.getTelegramId(),
            user.getFirstName(),
            user.getLastName(),
            user.getTelegramUsername(),
            user.getPhoneNumber(),
            user.getBirthDate(),
            user.getState().toString(),
            user.getCreatedAt(),
            userCoupons.size(),
            activeCoupons
        );
    }
    
    private ProductDto convertToProductDto(Product product) {
        return new ProductDto(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getImageUrl(),
            product.getStockQuantity(),
            product.getStatus().toString(),
            product.getCreatedAt()
        );
    }
    
    private OrderDto convertToOrderDto(Order order) {
        List<OrderItemDto> itemDtos = order.getOrderItems().stream()
            .map(this::convertToOrderItemDto)
            .collect(Collectors.toList());
        
        return new OrderDto(
            order.getId(),
            order.getOrderNumber(),
            order.getUser().getTelegramId(),
            order.getCustomerName(),
            order.getPhoneNumber(),
            order.getDeliveryAddress(),
            order.getTotalAmount(),
            order.getStatus().toString(),
            order.getNotes(),
            itemDtos,
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
    
    private OrderItemDto convertToOrderItemDto(OrderItem item) {
        return new OrderItemDto(
            item.getId(),
            item.getProduct().getId(),
            item.getProduct().getName(),
            item.getProduct().getImageUrl(),
            item.getQuantity(),
            item.getUnitPrice(),
            item.getTotalPrice()
        );
    }
    
    @Data
    public static class CreateProductRequest {
        private String name;
        private String description;
        private String price;
        private String imageUrl;
        private Integer stockQuantity;
    }
    
    @Data
    public static class UpdateOrderStatusRequest {
        private String status;
    }
    
    @Data
    public static class BroadcastRequest {
        private String message;
    }
    
    @Data
    public static class BroadcastResponse {
        private final int totalUsers;
        private final int successCount;
        private final int failureCount;
        private final double successRate;
        
        public BroadcastResponse(int totalUsers, int successCount, int failureCount, double successRate) {
            this.totalUsers = totalUsers;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.successRate = successRate;
        }
    }
    
    @Data
    public static class SingleMessageRequest {
        private Long telegramId;
        private String message;
    }
    
    @Data
    public static class SingleMessageResponse {
        private final boolean success;
        private final String message;
        
        public SingleMessageResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
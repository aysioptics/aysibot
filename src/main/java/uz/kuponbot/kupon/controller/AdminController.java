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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.dto.AdminStatsDto;
import uz.kuponbot.kupon.dto.OrderDto;
import uz.kuponbot.kupon.dto.ProductDto;
import uz.kuponbot.kupon.dto.UserDto;
import uz.kuponbot.kupon.dto.VoucherDto;
import uz.kuponbot.kupon.entity.Coupon;
import uz.kuponbot.kupon.entity.Order;
import uz.kuponbot.kupon.entity.Product;
import uz.kuponbot.kupon.entity.User;
import uz.kuponbot.kupon.entity.Voucher;
import uz.kuponbot.kupon.service.BroadcastService;
import uz.kuponbot.kupon.service.CouponService;
import uz.kuponbot.kupon.service.ExcelExportService;
import uz.kuponbot.kupon.service.NotificationService;
import uz.kuponbot.kupon.service.OrderService;
import uz.kuponbot.kupon.service.ProductService;
import uz.kuponbot.kupon.service.UserService;
import uz.kuponbot.kupon.service.VoucherService;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    
    private final UserService userService;
    private final CouponService couponService;
    private final ProductService productService;
    private final NotificationService notificationService;
    private final ExcelExportService excelExportService;
    private final BroadcastService broadcastService;
    private final VoucherService voucherService;
    private final uz.kuponbot.kupon.service.CashbackService cashbackService;
    private final OrderService orderService;
    
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> getStats() {
        long totalUsers = userService.getTotalUsersCount();
        long totalProducts = productService.getTotalProductsCount();
        long totalVouchers = voucherService.getTotalVouchersCount();
        
        AdminStatsDto stats = new AdminStatsDto(
            totalUsers,
            totalProducts,
            totalVouchers,
            voucherService.getActiveVouchersCount(),
            voucherService.getUsedVouchersCount(),
            voucherService.getExpiredVouchersCount()
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
        log.info("Image URLs count: {}", request.getImageUrls() != null ? request.getImageUrls().size() : 0);
        
        try {
            Product product = productService.createProduct(
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getImageUrls(),
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
    
    @PostMapping("/test-3day")
    public ResponseEntity<String> testThreeDay() {
        notificationService.testThreeDayPurchases();
        return ResponseEntity.ok("3-day purchase check completed!");
    }
    
    @PostMapping("/test-voucher-reminders")
    public ResponseEntity<String> testVoucherReminders() {
        notificationService.testVoucherReminders();
        return ResponseEntity.ok("Voucher reminder check completed!");
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
    
    @GetMapping("/vouchers")
    public ResponseEntity<List<VoucherDto>> getAllVouchers() {
        List<Voucher> vouchers = voucherService.getAllVouchers();
        List<VoucherDto> voucherDtos = vouchers.stream()
            .map(this::convertToVoucherDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(voucherDtos);
    }
    
    @GetMapping("/vouchers/{code}")
    public ResponseEntity<VoucherDto> getVoucherByCode(@PathVariable String code) {
        Optional<Voucher> voucherOpt = voucherService.findByCode(code);
        if (voucherOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(convertToVoucherDto(voucherOpt.get()));
    }
    
    @PostMapping("/vouchers/{code}/use")
    public ResponseEntity<VoucherDto> useVoucher(@PathVariable String code) {
        try {
            Voucher voucher = voucherService.useVoucher(code);
            return ResponseEntity.ok(convertToVoucherDto(voucher));
        } catch (RuntimeException e) {
            log.error("Error using voucher {}: {}", code, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/vouchers/create")
    public ResponseEntity<VoucherDto> createSpecialVoucher(@RequestBody CreateVoucherRequest request) {
        try {
            Optional<User> userOpt = userService.findByTelegramId(request.getTelegramId());
            if (userOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Voucher voucher = voucherService.createSpecialVoucher(
                userOpt.get(), 
                request.getAmount(), 
                request.getValidDays()
            );
            
            return ResponseEntity.ok(convertToVoucherDto(voucher));
        } catch (Exception e) {
            log.error("Error creating special voucher: ", e);
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
            product.getImageUrlsList(), // Ko'p rasmlar
            product.getStockQuantity(),
            product.getStatus().toString(),
            product.getCreatedAt()
        );
    }
    
    
    private VoucherDto convertToVoucherDto(Voucher voucher) {
        return new VoucherDto(
            voucher.getId(),
            voucher.getCode(),
            voucher.getUser().getTelegramId(),
            voucher.getUser().getFullName(),
            voucher.getAmount(),
            voucher.getStatus().toString(),
            voucher.getType().toString(),
            voucher.getCreatedAt(),
            voucher.getExpiresAt(),
            voucher.getUsedAt(),
            voucher.getDaysUntilExpiry()
        );
    }
    
    @Data
    public static class CreateProductRequest {
        private String name;
        private String description;
        private String price;
        private List<String> imageUrls; // Ko'p rasmlar
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
    
    @Data
    public static class CreateVoucherRequest {
        private Long telegramId;
        private Integer amount;
        private Integer validDays;
    }
    
    // ========== CASHBACK ENDPOINTS ==========
    
    @PostMapping("/cashback/add-purchase")
    public ResponseEntity<?> addPurchase(@RequestBody AddPurchaseRequest request) {
        try {
            uz.kuponbot.kupon.dto.CashbackDto cashback = cashbackService.addPurchase(
                request.getTelegramId(),
                request.getPurchaseAmount(),
                request.getDescription()
            );
            return ResponseEntity.ok(cashback);
        } catch (Exception e) {
            log.error("Error adding purchase: ", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PostMapping("/cashback/use")
    public ResponseEntity<?> useCashback(@RequestBody UseCashbackRequest request) {
        try {
            uz.kuponbot.kupon.dto.CashbackDto cashback = cashbackService.useCashback(
                request.getTelegramId(),
                request.getAmount(),
                request.getDescription()
            );
            return ResponseEntity.ok(cashback);
        } catch (Exception e) {
            log.error("Error using cashback: ", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PostMapping("/cashback/refund")
    public ResponseEntity<?> refundCashback(@RequestBody RefundCashbackRequest request) {
        try {
            uz.kuponbot.kupon.dto.CashbackDto cashback = cashbackService.refundCashback(
                request.getTelegramId(),
                request.getAmount(),
                request.getDescription()
            );
            return ResponseEntity.ok(cashback);
        } catch (Exception e) {
            log.error("Error refunding cashback: ", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @GetMapping("/cashback/user/{telegramId}")
    public ResponseEntity<?> getUserCashbackHistory(@PathVariable Long telegramId) {
        try {
            List<uz.kuponbot.kupon.dto.CashbackDto> history = cashbackService.getUserCashbackHistory(telegramId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error getting user cashback history: ", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @GetMapping("/cashback/all-balances")
    public ResponseEntity<?> getAllUsersCashbackBalance() {
        try {
            List<uz.kuponbot.kupon.service.CashbackService.UserCashbackBalance> balances = 
                cashbackService.getAllUsersCashbackBalance();
            return ResponseEntity.ok(balances);
        } catch (Exception e) {
            log.error("Error getting all users cashback balance: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/cashback/all-history")
    public ResponseEntity<?> getAllCashbackHistory() {
        try {
            List<uz.kuponbot.kupon.dto.CashbackDto> history = cashbackService.getAllCashbackHistory();
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error getting all cashback history: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @Data
    public static class AddPurchaseRequest {
        private Long telegramId;
        private Integer purchaseAmount;
        private String description;
    }
    
    @Data
    public static class UseCashbackRequest {
        private Long telegramId;
        private Integer amount;
        private String description;
    }
    
    @Data
    public static class RefundCashbackRequest {
        private Long telegramId;
        private Integer amount;
        private String description;
    }
    
    // ========== ORDER ENDPOINTS ==========
    
    @GetMapping("/orders")
    public ResponseEntity<List<OrderDto>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        List<OrderDto> orderDtos = orders.stream()
            .map(this::convertToOrderDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(orderDtos);
    }
    
    @GetMapping("/orders/pending")
    public ResponseEntity<List<OrderDto>> getPendingOrders() {
        List<Order> orders = orderService.getPendingOrders();
        List<OrderDto> orderDtos = orders.stream()
            .map(this::convertToOrderDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(orderDtos);
    }
    
    @PostMapping("/orders/{id}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long id, @RequestBody UpdateOrderStatusRequest request) {
        try {
            Order.OrderStatus newStatus = Order.OrderStatus.valueOf(request.getStatus());
            Order order = orderService.updateOrderStatus(id, newStatus);
            return ResponseEntity.ok(convertToOrderDto(order));
        } catch (Exception e) {
            log.error("Error updating order status: ", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    private OrderDto convertToOrderDto(Order order) {
        return new OrderDto(
            order.getId(),
            order.getUser().getTelegramId(),
            order.getUser().getFullName(),
            order.getUser().getPhoneNumber(),
            order.getProduct().getId(),
            order.getProduct().getName(),
            order.getQuantity(),
            order.getTotalPrice(),
            order.getCustomerNote(),
            order.getStatus().toString(),
            order.getCreatedAt()
        );
    }
}

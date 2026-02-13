package uz.kuponbot.kupon.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.entity.Order;
import uz.kuponbot.kupon.entity.Product;
import uz.kuponbot.kupon.entity.User;
import uz.kuponbot.kupon.repository.OrderRepository;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final BroadcastService broadcastService;
    
    public Order createOrder(User user, Product product, Integer quantity, String customerNote) {
        // Check stock
        if (product.getStockQuantity() < quantity) {
            throw new RuntimeException("Mahsulot yetarli emas. Mavjud: " + product.getStockQuantity());
        }
        
        // Calculate total price
        Integer totalPrice = product.getPrice().intValue() * quantity;
        
        // Create order
        Order order = new Order();
        order.setUser(user);
        order.setProduct(product);
        order.setQuantity(quantity);
        order.setTotalPrice(totalPrice);
        order.setCustomerNote(customerNote);
        order.setStatus(Order.OrderStatus.PENDING);
        
        Order savedOrder = orderRepository.save(order);
        
        // Send notification to admins
        notifyAdminsAboutNewOrder(savedOrder);
        
        log.info("Order created: ID={}, User={}, Product={}, Quantity={}", 
            savedOrder.getId(), user.getTelegramId(), product.getName(), quantity);
        
        return savedOrder;
    }
    
    private void notifyAdminsAboutNewOrder(Order order) {
        String message = String.format(
            "🛒 YANGI BUYURTMA!\n\n" +
            "📦 Mahsulot: %s\n" +
            "📊 Miqdor: %d dona\n" +
            "💰 Summa: %,d so'm\n\n" +
            "👤 Mijoz: %s\n" +
            "📱 Telefon: %s\n" +
            "💬 Izoh: %s\n\n" +
            "🆔 Buyurtma ID: #%d",
            order.getProduct().getName(),
            order.getQuantity(),
            order.getTotalPrice(),
            order.getUser().getFullName(),
            order.getUser().getPhoneNumber(),
            order.getCustomerNote() != null ? order.getCustomerNote() : "Yo'q",
            order.getId()
        );
        
        // Send to all admins
        broadcastService.sendAdminNotification(message);
    }
    
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }
    
    public List<Order> getPendingOrders() {
        return orderRepository.findByStatusOrderByCreatedAtDesc(Order.OrderStatus.PENDING);
    }
    
    public List<Order> getUserOrders(User user) {
        return orderRepository.findByUserOrderByCreatedAtDesc(user);
    }
    
    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }
    
    public Order updateOrderStatus(Long orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        
        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        
        // Notify user about status change
        notifyUserAboutStatusChange(updatedOrder);
        
        return updatedOrder;
    }
    
    private void notifyUserAboutStatusChange(Order order) {
        String statusText = switch (order.getStatus()) {
            case CONFIRMED -> "✅ Tasdiqlandi";
            case PROCESSING -> "⏳ Tayyorlanmoqda";
            case COMPLETED -> "✅ Bajarildi";
            case CANCELLED -> "❌ Bekor qilindi";
            default -> "⏱ Kutilmoqda";
        };
        
        String message = String.format(
            "📦 Buyurtma holati o'zgardi!\n\n" +
            "Buyurtma #%d\n" +
            "Mahsulot: %s\n" +
            "Holat: %s",
            order.getId(),
            order.getProduct().getName(),
            statusText
        );
        
        broadcastService.sendSingleMessage(order.getUser().getTelegramId(), message);
    }
    
    public long getPendingOrdersCount() {
        return orderRepository.countPendingOrders();
    }
}

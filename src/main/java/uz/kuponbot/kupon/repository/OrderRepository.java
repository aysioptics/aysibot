package uz.kuponbot.kupon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import uz.kuponbot.kupon.entity.Order;
import uz.kuponbot.kupon.entity.User;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    List<Order> findByUserOrderByCreatedAtDesc(User user);
    
    List<Order> findByStatusOrderByCreatedAtDesc(Order.OrderStatus status);
    
    List<Order> findAllByOrderByCreatedAtDesc();
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 'PENDING'")
    long countPendingOrders();
}

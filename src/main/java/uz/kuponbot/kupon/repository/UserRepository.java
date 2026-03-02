package uz.kuponbot.kupon.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.kuponbot.kupon.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByTelegramId(Long telegramId);
    
    boolean existsByTelegramId(Long telegramId);
    
    List<User> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    // So'nggi ro'yxatdan o'tganlar birinchi
    List<User> findAllByOrderByCreatedAtDesc();
    
    List<User> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startDate, LocalDateTime endDate);
}
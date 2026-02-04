package uz.kuponbot.kupon.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.entity.User;
import uz.kuponbot.kupon.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    
    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }
    
    public User save(User user) {
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        log.info("User saved to DB: id={}, language={}, state={}", 
            saved.getTelegramId(), saved.getLanguage(), saved.getState());
        return saved;
    }
    
    public User createUser(Long telegramId) {
        User user = new User();
        user.setTelegramId(telegramId);
        user.setState(User.UserState.WAITING_LANGUAGE);
        return save(user);
    }
    
    public boolean existsByTelegramId(Long telegramId) {
        return userRepository.existsByTelegramId(telegramId);
    }
    
    public List<User> getAllUsers() {
        // Admin ID'larini belgilash
        List<Long> adminIds = List.of(1807166165L, 7543576887L);
        
        // Barcha userlarni olish va adminlarni chiqarib tashlash
        return userRepository.findAll().stream()
            .filter(user -> !adminIds.contains(user.getTelegramId()))
            .collect(Collectors.toList());
    }
    
    public long getTotalUsersCount() {
        // Admin ID'larini belgilash
        List<Long> adminIds = List.of(1807166165L, 7543576887L);
        
        // Barcha userlarni sanash va adminlarni chiqarib tashlash
        return userRepository.findAll().stream()
            .filter(user -> !adminIds.contains(user.getTelegramId()))
            .count();
    }
    
    public List<User> getUsersByDateFilter(String filter) {
        LocalDateTime startDate;
        LocalDateTime endDate = LocalDateTime.now();
        
        switch (filter) {
            case "today":
                startDate = LocalDate.now().atStartOfDay();
                break;
            case "this_month":
                startDate = LocalDate.now().withDayOfMonth(1).atStartOfDay();
                break;
            case "this_year":
                startDate = LocalDate.now().withDayOfYear(1).atStartOfDay();
                break;
            default:
                return getAllUsers(); // Bu allaqachon adminlarsiz
        }
        
        // Admin ID'larini belgilash
        List<Long> adminIds = List.of(1807166165L, 7543576887L);
        
        // Filterlangan userlarni olish va adminlarni chiqarib tashlash
        return userRepository.findByCreatedAtBetween(startDate, endDate).stream()
            .filter(user -> !adminIds.contains(user.getTelegramId()))
            .collect(Collectors.toList());
    }
}
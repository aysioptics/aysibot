package uz.kuponbot.kupon.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@DynamicUpdate
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private Long telegramId;
    
    @Column(nullable = true)
    private String telegramUsername; // @username
    
    @Column(nullable = true)
    private String phoneNumber;
    
    private String fullName; // To'liq ism-familiya
    
    // Backward compatibility uchun
    public String getFirstName() {
        if (fullName != null && fullName.contains(" ")) {
            return fullName.split(" ")[0];
        }
        return fullName;
    }
    
    public String getLastName() {
        if (fullName != null && fullName.contains(" ")) {
            String[] parts = fullName.split(" ");
            if (parts.length > 1) {
                return parts[parts.length - 1];
            }
        }
        return "";
    }
    
    public void setFirstName(String firstName) {
        this.fullName = firstName;
    }
    
    public void setLastName(String lastName) {
        // Bu metod endi ishlatilmaydi, lekin backward compatibility uchun qoldiramiz
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    private String birthDate; // Format: DD.MM.YYYY
    
    @Column(name = "language", nullable = true)
    private String language = "uz"; // "uz" (lotin), "ru" (rus), "uz_cyrl" (kiril)
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserState state = UserState.START;
    
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    public enum UserState {
        START,
        WAITING_LANGUAGE,
        WAITING_CONTACT,
        WAITING_FULL_NAME, // Eski WAITING_FIRST_NAME o'rniga
        WAITING_BIRTH_DATE,
        WAITING_CHANNEL_SUBSCRIPTION,
        REGISTERED
    }
}
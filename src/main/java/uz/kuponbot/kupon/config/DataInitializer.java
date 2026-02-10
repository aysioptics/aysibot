package uz.kuponbot.kupon.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import uz.kuponbot.kupon.entity.User;
import uz.kuponbot.kupon.service.UserService;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final UserService userService;
    
    @Override
    public void run(String... args) throws Exception {
        // Create admin users if not exist
        createAdminUser();
    }
    
    private void createAdminUser() {
        // Admin 1: IbodullaR
        if (userService.findByTelegramId(1807166165L).isEmpty()) {
            User admin1 = new User();
            admin1.setTelegramId(1807166165L);
            admin1.setFirstName("Ibodulla");
            admin1.setLastName("Raxmonberganov");
            admin1.setTelegramUsername("@IbodullaR");
            admin1.setPhoneNumber("+998904297729");
            admin1.setBirthDate("19.03.2005");
            admin1.setState(User.UserState.REGISTERED);
            
            userService.save(admin1);
            System.out.println("✅ Admin 1 (Ibodulla Raxmonberganov) created successfully!");
        }
        
        // Admin 2: Aysi Manager
        if (userService.findByTelegramId(6051364132L).isEmpty()) {
            User admin2 = new User();
            admin2.setTelegramId(6051364132L);
            admin2.setFirstName("Aysi");
            admin2.setLastName("Manager");
            admin2.setTelegramUsername("@aysi_menejer");
            admin2.setPhoneNumber("+998938740305");
            admin2.setBirthDate("01.01.2000");
            admin2.setState(User.UserState.REGISTERED);
            
            userService.save(admin2);
            System.out.println("✅ Admin 2 (Aysi Manager) created successfully!");
        }
    }
}
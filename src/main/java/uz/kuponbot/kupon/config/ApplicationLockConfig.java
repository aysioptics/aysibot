package uz.kuponbot.kupon.config;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class ApplicationLockConfig {
    
    @Value("${application.lock.file:/tmp/kupon-bot.lock}")
    private String lockFilePath;
    
    private FileChannel fileChannel;
    private FileLock fileLock;
    
    @PostConstruct
    public void acquireLock() {
        try {
            File lockFile = new File(lockFilePath);
            
            // Lock file yaratish yoki ochish
            RandomAccessFile randomAccessFile = new RandomAccessFile(lockFile, "rw");
            fileChannel = randomAccessFile.getChannel();
            
            // Lock olishga urinish (non-blocking)
            fileLock = fileChannel.tryLock();
            
            if (fileLock == null) {
                log.error("═══════════════════════════════════════════════════════════");
                log.error("❌ CRITICAL ERROR: Another instance of the application is already running!");
                log.error("❌ Lock file: {}", lockFilePath);
                log.error("❌ Cannot start multiple instances with the same Telegram bot token!");
                log.error("═══════════════════════════════════════════════════════════");
                
                // Application ni to'xtatish
                System.exit(1);
            }
            
            log.info("✅ Application lock acquired successfully");
            log.info("✅ Lock file: {}", lockFilePath);
            
            // Shutdown hook qo'shish - application to'xtaganda lock ni bo'shatish
            Runtime.getRuntime().addShutdownHook(new Thread(this::releaseLock));
            
        } catch (IOException e) {
            log.error("Failed to acquire application lock", e);
            System.exit(1);
        }
    }
    
    @PreDestroy
    public void releaseLock() {
        try {
            if (fileLock != null && fileLock.isValid()) {
                fileLock.release();
                log.info("✅ Application lock released");
            }
            
            if (fileChannel != null && fileChannel.isOpen()) {
                fileChannel.close();
            }
            
            // Lock file ni o'chirish
            File lockFile = new File(lockFilePath);
            if (lockFile.exists()) {
                lockFile.delete();
                log.info("✅ Lock file deleted: {}", lockFilePath);
            }
            
        } catch (IOException e) {
            log.error("Error releasing application lock", e);
        }
    }
}

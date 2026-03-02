package uz.kuponbot.kupon.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileUploadService {
    
    @Value("${file.upload.dir:/opt/aysibot/uploads}")
    private String uploadDir;
    
    public List<String> uploadImages(List<MultipartFile> files) throws IOException {
        List<String> fileNames = new ArrayList<>();
        
        // Create upload directory if not exists
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("Created upload directory: {}", uploadDir);
        }
        
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString() + extension;
            
            // Save file
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            fileNames.add(fileName);
            log.info("Uploaded file: {}", fileName);
        }
        
        return fileNames;
    }
    
    public void deleteImage(String fileName) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(fileName);
            Files.deleteIfExists(filePath);
            log.info("Deleted file: {}", fileName);
        } catch (IOException e) {
            log.error("Error deleting file: {}", fileName, e);
        }
    }
    
    public void deleteImages(List<String> fileNames) {
        for (String fileName : fileNames) {
            deleteImage(fileName);
        }
    }
}

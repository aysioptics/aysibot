package uz.kuponbot.kupon.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uz.kuponbot.kupon.dto.UserDto;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelExportService {
    
    public byte[] exportUsersToExcel(List<UserDto> users) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Foydalanuvchilar");
            
            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            
            // Data style
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "ID", "Ism", "Familiya", "Username", "Telefon", 
                "Tug'ilgan kun", "Holat", "Ro'yxatdan o'tgan", 
                "Jami kuponlar", "Faol kuponlar"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Create data rows
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            int rowNum = 1;
            
            for (UserDto user : users) {
                Row row = sheet.createRow(rowNum++);
                
                // ID
                Cell cell0 = row.createCell(0);
                cell0.setCellValue(user.telegramId());
                cell0.setCellStyle(dataStyle);
                
                // Ism
                Cell cell1 = row.createCell(1);
                cell1.setCellValue(user.firstName() != null ? user.firstName() : "-");
                cell1.setCellStyle(dataStyle);
                
                // Familiya
                Cell cell2 = row.createCell(2);
                cell2.setCellValue(user.lastName() != null ? user.lastName() : "-");
                cell2.setCellStyle(dataStyle);
                
                // Username
                Cell cell3 = row.createCell(3);
                cell3.setCellValue(user.telegramUsername() != null ? user.telegramUsername() : "Username yo'q");
                cell3.setCellStyle(dataStyle);
                
                // Telefon
                Cell cell4 = row.createCell(4);
                cell4.setCellValue(user.phoneNumber() != null ? user.phoneNumber() : "-");
                cell4.setCellStyle(dataStyle);
                
                // Tug'ilgan kun
                Cell cell5 = row.createCell(5);
                cell5.setCellValue(user.birthDate() != null ? user.birthDate() : "Kiritilmagan");
                cell5.setCellStyle(dataStyle);
                
                // Holat
                Cell cell6 = row.createCell(6);
                cell6.setCellValue(getStateText(user.state()));
                cell6.setCellStyle(dataStyle);
                
                // Ro'yxatdan o'tgan
                Cell cell7 = row.createCell(7);
                cell7.setCellValue(user.createdAt().format(dateFormatter));
                cell7.setCellStyle(dataStyle);
                
                // Jami kuponlar
                Cell cell8 = row.createCell(8);
                cell8.setCellValue(user.totalCoupons());
                cell8.setCellStyle(dataStyle);
                
                // Faol kuponlar
                Cell cell9 = row.createCell(9);
                cell9.setCellValue(user.activeCoupons());
                cell9.setCellStyle(dataStyle);
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Add some extra width for better readability
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
            }
            
            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            
            log.info("Excel file created successfully with {} users", users.size());
            return outputStream.toByteArray();
        }
    }
    
    private String getStateText(String state) {
        return switch (state) {
            case "START" -> "Boshlangan";
            case "WAITING_CONTACT" -> "Telefon kutilmoqda";
            case "WAITING_FULL_NAME" -> "To'liq ism kutilmoqda";
            case "WAITING_BIRTH_DATE" -> "Tug'ilgan sana kutilmoqda";
            case "WAITING_CHANNEL_SUBSCRIPTION" -> "Kanal obunasi kutilmoqda";
            case "REGISTERED" -> "Ro'yxatdan o'tgan";
            default -> state;
        };
    }
}
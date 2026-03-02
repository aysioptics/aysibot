# Kupon Bot - Deployment Muammolari va Yechimlar

## Muammolar

### 1. Port 8080 Band
**Sabab:** Bir nechta Java process bir vaqtning o'zida ishga tushgan.

**Yechim:**
```bash
# Barcha Java processlarni ko'rish
ps aux | grep java

# Kerakli processni to'xtatish
kill -9 <PID>
```

### 2. Telegram Bot Conflict (Error 409)
**Sabab:** Bir nechta bot instance bir xil bot token bilan getUpdates so'rovini yuborgan.

**Xato xabari:**
```
Error 409: Conflict: terminated by other getUpdates request
```

**Yechim:** Faqat BITTA bot instance ishga tushishi kerak.

## Qo'shilgan Yechimlar

### 1. BotConfig.java - Singleton Pattern
**Fayl:** `src/main/java/uz/kuponbot/kupon/config/BotConfig.java`

**O'zgarishlar:**
- Double-checked locking pattern qo'shildi
- TelegramBotsApi faqat bir marta yaratiladi
- Bot faqat bir marta ro'yxatdan o'tkaziladi
- Logging qo'shildi

**Kod:**
```java
private static volatile TelegramBotsApi apiInstance = null;
private static final Object lock = new Object();

@Bean
public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
    if (apiInstance == null) {
        synchronized (lock) {
            if (apiInstance == null) {
                log.info("Creating new TelegramBotsApi instance...");
                TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
                api.registerBot(kuponBot);
                apiInstance = api;
                log.info("Bot successfully registered");
            }
        }
    }
    return apiInstance;
}
```

### 2. ApplicationLockConfig.java - Application Lock
**Fayl:** `src/main/java/uz/kuponbot/kupon/config/ApplicationLockConfig.java`

**Maqsad:** Bir vaqtning o'zida faqat BITTA application instance ishga tushishini ta'minlash.

**Qanday ishlaydi:**
1. Application ishga tushganda `/tmp/kupon-bot.lock` faylini yaratadi
2. File lock oladi (OS level)
3. Agar lock olinmasa (boshqa instance ishlayotgan bo'lsa), application to'xtaydi
4. Application to'xtaganda lock avtomatik bo'shatiladi

**Xususiyatlar:**
- OS level file locking (cross-process)
- Automatic cleanup on shutdown
- Shutdown hook qo'shilgan
- Detailed logging

### 3. KuponBot.java - Instance Control
**Fayl:** `src/main/java/uz/kuponbot/kupon/bot/KuponBot.java`

**O'zgarishlar:**
- Singleton pattern qo'shildi
- Constructor'da instance checking
- Agar ikkinchi instance yaratilsa, exception throw qiladi

**Kod:**
```java
private static volatile boolean instanceCreated = false;
private static final Object instanceLock = new Object();

public KuponBot(...) {
    synchronized (instanceLock) {
        if (instanceCreated) {
            throw new IllegalStateException("KuponBot instance already exists!");
        }
        instanceCreated = true;
    }
    // ...
}
```

## Deployment Qo'llanma

### 1. Serverda Ishlaydigan Processlarni To'xtatish

```bash
# SSH orqali serverga kirish
ssh root@23.88.63.148

# Barcha Java processlarni ko'rish
ps aux | grep java

# Har bir Java processni to'xtatish
kill -9 <PID1> <PID2> <PID3>

# Yoki barcha Java processlarni to'xtatish
pkill -9 java
```

### 2. Yangi Versiyani Deploy Qilish

```bash
# Loyihani build qilish (local)
./mvnw clean package -DskipTests

# JAR faylni serverga yuklash
scp target/kupon-0.0.1-SNAPSHOT.jar root@23.88.63.148:/opt/aysibot/

# Serverda ishga tushirish
ssh root@23.88.63.148
cd /opt/aysibot
nohup java -jar -Dspring.profiles.active=prod kupon-0.0.1-SNAPSHOT.jar > app.log 2>&1 &

# Loglarni kuzatish
tail -f app.log
```

### 3. Tekshirish

```bash
# Application ishga tushganini tekshirish
ps aux | grep java

# Port 8080 band ekanligini tekshirish
netstat -tulpn | grep 8080

# Loglarni ko'rish
tail -f /opt/aysibot/app.log

# Lock file mavjudligini tekshirish
ls -la /tmp/kupon-bot.lock
```

### 4. Muammolarni Bartaraf Etish

#### Agar "Lock file already exists" xatosi chiqsa:

```bash
# Lock file ni o'chirish
rm -f /tmp/kupon-bot.lock

# Application ni qayta ishga tushirish
```

#### Agar "Port 8080 already in use" xatosi chiqsa:

```bash
# Port 8080 da ishlaydigan processni topish
lsof -i :8080

# Processni to'xtatish
kill -9 <PID>
```

#### Agar "Error 409: Conflict" xatosi chiqsa:

```bash
# Barcha Java processlarni to'xtatish
pkill -9 java

# 30 sekund kutish (Telegram API tozalanishi uchun)
sleep 30

# Application ni qayta ishga tushirish
```

## Systemd Service (Tavsiya Etiladi)

Applicationni systemd service sifatida sozlash:

### 1. Service File Yaratish

```bash
sudo nano /etc/systemd/system/kupon-bot.service
```

### 2. Service Configuration

```ini
[Unit]
Description=Kupon Bot Service
After=network.target postgresql.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/aysibot
Environment="SPRING_PROFILES_ACTIVE=prod"
ExecStart=/usr/bin/java -jar /opt/aysibot/kupon-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=append:/opt/aysibot/app.log
StandardError=append:/opt/aysibot/error.log

# Lock file cleanup on stop
ExecStopPost=/bin/rm -f /tmp/kupon-bot.lock

[Install]
WantedBy=multi-user.target
```

### 3. Service'ni Yoqish

```bash
# Service'ni reload qilish
sudo systemctl daemon-reload

# Service'ni yoqish
sudo systemctl enable kupon-bot

# Service'ni ishga tushirish
sudo systemctl start kupon-bot

# Status tekshirish
sudo systemctl status kupon-bot

# Loglarni ko'rish
sudo journalctl -u kupon-bot -f
```

### 4. Service Boshqaruvi

```bash
# To'xtatish
sudo systemctl stop kupon-bot

# Qayta ishga tushirish
sudo systemctl restart kupon-bot

# Status
sudo systemctl status kupon-bot

# Loglar
sudo journalctl -u kupon-bot -n 100 --no-pager
```

## Monitoring

### 1. Health Check

```bash
# Application health
curl http://localhost:8080/actuator/health

# Bot ishlayotganini tekshirish
ps aux | grep kupon

# Lock file
ls -la /tmp/kupon-bot.lock
```

### 2. Loglarni Kuzatish

```bash
# Real-time logs
tail -f /opt/aysibot/app.log

# Error logs
tail -f /opt/aysibot/error.log

# Systemd logs
sudo journalctl -u kupon-bot -f
```

## Xavfsizlik

### 1. Lock File Permissions

```bash
# Lock file faqat root user tomonidan o'qilishi va yozilishi mumkin
chmod 600 /tmp/kupon-bot.lock
```

### 2. Application Permissions

```bash
# Application fayllariga faqat root user kirishi mumkin
chown root:root /opt/aysibot/kupon-0.0.1-SNAPSHOT.jar
chmod 700 /opt/aysibot/kupon-0.0.1-SNAPSHOT.jar
```

## Backup va Recovery

### 1. Database Backup

```bash
# PostgreSQL backup
pg_dump -U kuponuser kupondb > /opt/aysibot/backups/kupondb_$(date +%Y%m%d_%H%M%S).sql
```

### 2. Application Backup

```bash
# JAR file backup
cp /opt/aysibot/kupon-0.0.1-SNAPSHOT.jar /opt/aysibot/backups/kupon-0.0.1-SNAPSHOT_$(date +%Y%m%d_%H%M%S).jar
```

## Xulosa

Ushbu o'zgarishlar quyidagi muammolarni hal qiladi:

1. ✅ Bir nechta bot instance yaratilishini oldini oladi
2. ✅ Port 8080 conflict muammosini hal qiladi
3. ✅ Telegram Bot Error 409 muammosini hal qiladi
4. ✅ Application faqat bir marta ishga tushishini ta'minlaydi
5. ✅ Automatic cleanup va error handling

**Muhim:** Har doim faqat BITTA application instance ishga tushiring!

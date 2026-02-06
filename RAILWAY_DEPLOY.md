# Railway.app ga Deploy Qilish Qo'llanmasi

## Railway.app - Bepul Test Uchun

Railway.app test uchun juda yaxshi variant:
- ✅ $5 credit har oy (bepul)
- ✅ PostgreSQL database bepul
- ✅ GitHub bilan avtomatik deploy
- ✅ HTTPS domain bepul
- ✅ Karta kerak emas (bepul plan uchun)

## 1. Railway.app ga Ro'yxatdan O'tish

1. **railway.app** ga kiring
2. **"Start a New Project"** tugmasini bosing
3. **GitHub** bilan login qiling
4. Railway sizning GitHub repositoriyalaringizga kirish so'raydi - ruxsat bering

## 2. Yangi Project Yaratish

1. **"New Project"** tugmasini bosing
2. **"Deploy from GitHub repo"** ni tanlang
3. **AysiOptics** repositoriyasini tanlang
4. Railway avtomatik build boshlaydi

## 3. PostgreSQL Database Qo'shish

1. Projectingizda **"New"** tugmasini bosing
2. **"Database"** → **"Add PostgreSQL"** ni tanlang
3. Railway avtomatik database yaratadi va `DATABASE_URL` o'rnatadi

## 4. Environment Variables Sozlash

Projectingizda **"Variables"** tabiga o'ting va quyidagilarni qo'shing:

```
TELEGRAM_BOT_TOKEN=7309585622:AAH8ZoVJOI5ySljZcKt_icS57219m1EZ3QQ
TELEGRAM_BOT_USERNAME=aysi_optic_bot
TELEGRAM_CHANNEL_USERNAME=aysi_optic
TELEGRAM_CHANNEL_ID=-1002127468736
PORT=8080
```

**Eslatma:** `DATABASE_URL` avtomatik qo'shiladi, qo'lda qo'shish shart emas.

## 5. Deploy Qilish

Railway avtomatik deploy qiladi:
1. GitHub ga push qilganingizda avtomatik yangilanadi
2. Yoki Railway dashboardda **"Deploy"** tugmasini bosing

## 6. Domain Olish

1. Projectingizda **"Settings"** → **"Networking"** ga o'ting
2. **"Generate Domain"** tugmasini bosing
3. Railway sizga bepul domain beradi: `your-app.up.railway.app`

## 7. Admin Panel va Shop ga Kirish

Railway domain berganidan keyin:

- **Admin Panel**: `https://your-app.up.railway.app/admin.html`
- **Shop**: `https://your-app.up.railway.app/shop.html`
- **Login**: `https://your-app.up.railway.app/login.html`

## 8. Loglarni Ko'rish

Railway dashboardda:
1. Projectingizni oching
2. **"Deployments"** tabiga o'ting
3. Oxirgi deploymentni bosing
4. **"View Logs"** tugmasini bosing

## 9. Database ga Kirish

Railway dashboardda:
1. PostgreSQL serviceni oching
2. **"Data"** tabida database ko'rishingiz mumkin
3. Yoki **"Connect"** tabida connection string olishingiz mumkin

## 10. Yangilash (Update)

Juda oson:
```bash
git add .
git commit -m "Yangilanish"
git push
```

Railway avtomatik yangi versiyani deploy qiladi!

## Muhim Eslatmalar:

### Bepul Plan Cheklovlari:
- **$5/oy credit** - taxminan 500 soat (kichik bot uchun yetarli)
- Agar credit tugasa, service to'xtaydi
- Keyingi oyda yana $5 credit beriladi

### Credit Tejash:
- Faqat kerakli vaqtda ishlatish
- Yoki Hobby Plan ($5/oy) sotib olish - unlimited

### Database Backup:
Railway dashboardda PostgreSQL serviceda:
1. **"Data"** tabiga o'ting
2. **"Backups"** bo'limida avtomatik backup bor

### Custom Domain (Ixtiyoriy):
1. **"Settings"** → **"Networking"** ga o'ting
2. **"Custom Domain"** qo'shing
3. DNS sozlamalarini o'zgartiring

## Muammolarni Hal Qilish:

### Build muvaffaqiyatsiz:
```bash
# Lokal build qilib ko'ring:
./mvnw clean package -DskipTests
```

### Bot ishlamayapti:
1. Loglarni tekshiring
2. Environment variables to'g'ri ekanligini tekshiring
3. Database ulanganligini tekshiring

### Database ulanmayapti:
1. PostgreSQL service ishlab turganini tekshiring
2. `DATABASE_URL` o'rnatilganini tekshiring

## Railway vs Hetzner:

| Xususiyat | Railway (Bepul) | Hetzner |
|-----------|----------------|---------|
| Narx | $0 (500 soat/oy) | €4.51/oy |
| Database | Bepul PostgreSQL | O'zingiz o'rnatish |
| Deploy | Avtomatik (GitHub) | Qo'lda |
| Domain | Bepul HTTPS | Alohida sotib olish |
| Sozlash | Oson | Qiyinroq |
| Nazorat | Cheklangan | To'liq |

**Tavsiya:** Test uchun Railway, production uchun Hetzner!

## Qo'shimcha Resurslar:

- Railway Docs: https://docs.railway.app
- Railway Discord: https://discord.gg/railway
- Railway Status: https://status.railway.app

-- Mahsulot rasmlarini ko'p rasmga o'zgartirish
-- Eski image_url ni image_urls ga o'zgartirish va JSON array formatiga o'tkazish

-- Yangi ustun qo'shish
ALTER TABLE products ADD COLUMN IF NOT EXISTS image_urls TEXT;

-- Eski ma'lumotlarni yangi formatga ko'chirish
UPDATE products 
SET image_urls = CASE 
    WHEN image_url IS NOT NULL AND image_url != '' 
    THEN '["' || image_url || '"]'
    ELSE '[]'
END
WHERE image_urls IS NULL;

-- Eski ustunni o'chirish (ixtiyoriy - agar kerak bo'lsa)
-- ALTER TABLE products DROP COLUMN IF EXISTS image_url;

-- Add cashback_balance column to users table if not exists (allow NULL first)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'users' AND column_name = 'cashback_balance'
    ) THEN
        ALTER TABLE users ADD COLUMN cashback_balance INTEGER;
        
        -- Update existing users to have 0 cashback
        UPDATE users SET cashback_balance = 0 WHERE cashback_balance IS NULL;
        
        -- Now make it NOT NULL with default
        ALTER TABLE users ALTER COLUMN cashback_balance SET NOT NULL;
        ALTER TABLE users ALTER COLUMN cashback_balance SET DEFAULT 0;
    END IF;
END $$;

-- Create cashbacks table if not exists
CREATE TABLE IF NOT EXISTS cashbacks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purchase_amount INTEGER NOT NULL,
    cashback_amount INTEGER NOT NULL,
    cashback_percentage DOUBLE PRECISION NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Create index for faster queries
CREATE INDEX IF NOT EXISTS idx_cashbacks_user_id ON cashbacks(user_id);
CREATE INDEX IF NOT EXISTS idx_cashbacks_status ON cashbacks(status);
CREATE INDEX IF NOT EXISTS idx_cashbacks_created_at ON cashbacks(created_at DESC);

-- Add cashback_balance column to users table if not exists
ALTER TABLE users ADD COLUMN IF NOT EXISTS cashback_balance INTEGER DEFAULT 0;

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

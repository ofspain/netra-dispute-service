CREATE TABLE customer_users (
   id BIGSERIAL PRIMARY KEY,
   created_at TIMESTAMP NOT NULL DEFAULT NOW(),
   updated_at TIMESTAMP DEFAULT NOW(),
   name VARCHAR(255) NOT NULL,
   disabled BOOLEAN NOT NULL DEFAULT FALSE,
   user_phone VARCHAR(20) NOT NULL UNIQUE,
   user_email VARCHAR(255)
);

CREATE INDEX idx_customer_user_created_at ON customer_users(created_at);

-- Optional: if filtering often by phone
CREATE INDEX idx_customer_user_phone ON customer_users(user_phone);
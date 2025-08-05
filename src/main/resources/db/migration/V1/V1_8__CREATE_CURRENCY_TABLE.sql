CREATE TABLE currencies (
   id BIGSERIAL PRIMARY KEY,
   created_at TIMESTAMP NOT NULL DEFAULT NOW(),
   updated_at TIMESTAMP DEFAULT NOW(),
   code VARCHAR(10) NOT NULL UNIQUE,        -- e.g., "NGN", "USD", "BTC"
   numeric_code VARCHAR(10) UNIQUE,        -- e.g., 566
   name VARCHAR(100) NOT NULL,              -- e.g., "Naira"
   symbol VARCHAR(10),                      -- e.g., "₦"
   type VARCHAR(20) NOT NULL CHECK (
       type IN ('FIAT', 'CRYPTO', 'CBDC', 'STABLE_COIN', 'TOKEN')
   ),
   decimal_precision INT NOT NULL CHECK (decimal_precision >= 0 AND decimal_precision <= 20),
   disabled BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_currency_created_at ON currencies(created_at);
CREATE INDEX idx_currency_disabled ON currencies(disabled);
CREATE INDEX idx_currency_code ON currencies(code);
CREATE INDEX idx_currency_numeric_code ON currencies(numeric_code);

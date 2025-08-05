CREATE TABLE institution_users (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    name VARCHAR(255) NOT NULL,
    disabled BOOLEAN DEFAULT FALSE,
    institution_id BIGINT NOT NULL REFERENCES financial_institutions(id) ON DELETE CASCADE,
    email VARCHAR(255),
);

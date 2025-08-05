-- Create the main transaction_type table
CREATE TABLE transaction_types (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    name VARCHAR(255) NOT NULL,
    disabled BOOLEAN DEFAULT FALSE,
    description TEXT,
    code VARCHAR(100) NOT NULL,

    UNIQUE (name),
    UNIQUE (code)
);

-- Create index on created_at for date-based queries
CREATE INDEX idx_transaction_type_created_at ON transaction_type(created_at);

-- Create the join table for channels
CREATE TABLE transaction_type_channels (
     transaction_type_id BIGINT NOT NULL REFERENCES transaction_types(id) ON DELETE CASCADE,
     channel VARCHAR(50) NOT NULL,

     -- this ensures multiple combination transaction_type and channel is not allowed
     PRIMARY KEY (transaction_type_id, channel),

     -- Add check constraint for allowed channel values
     CONSTRAINT chk_channel_allowed CHECK (
        channel IN (
             'NIP',
             'POS_SWITCH',
             'USSD_GATEWAY',
             'WALLET_PROCESSOR',
             'CARD_SCHEME',
             'OFFLINE'
        )
     )
);

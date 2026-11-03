-- ============================================================================
-- V5__create_seat_holds_schema.sql
-- Milestone M4: High-Concurrency Seat Holds & Invariant Verification
-- ============================================================================

-- 1. Seat Hold Session
CREATE TABLE seat_hold (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    show_id UUID NOT NULL REFERENCES show(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'HELD',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_seat_hold_status CHECK (status IN ('HELD', 'RELEASED', 'EXPIRED', 'CONVERTED'))
);

-- Invariant: One active hold per customer globally (anti-scalping / cart hoarding defense)
CREATE UNIQUE INDEX uk_active_customer_hold ON seat_hold (customer_id) WHERE (status = 'HELD');

-- Fast index for background cleanup of expired holds
CREATE INDEX idx_seat_hold_expiry ON seat_hold (status, expires_at) WHERE (status = 'HELD');
CREATE INDEX idx_seat_hold_show_customer ON seat_hold (show_id, customer_id);

-- 2. Seat Hold Items (Junction between Hold and ShowSeat)
CREATE TABLE seat_hold_item (
    id UUID PRIMARY KEY,
    hold_id UUID NOT NULL REFERENCES seat_hold(id) ON DELETE CASCADE,
    show_seat_id UUID NOT NULL REFERENCES show_seat(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_hold_show_seat UNIQUE (hold_id, show_seat_id)
);

CREATE INDEX idx_seat_hold_item_hold_id ON seat_hold_item (hold_id);
CREATE INDEX idx_seat_hold_item_show_seat ON seat_hold_item (show_seat_id);

-- 3. Wire foreign key from show_seat.active_hold_id to seat_hold(id)
ALTER TABLE show_seat
    ADD CONSTRAINT fk_show_seat_active_hold 
    FOREIGN KEY (active_hold_id) REFERENCES seat_hold(id) ON DELETE SET NULL;

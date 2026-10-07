-- =====================================================================
-- Migration: V4__create_shows_and_inventory_schema.sql
-- Description: Creates shows, pricing configurations, charge rules,
--              and materialized show seat inventory with PostgreSQL
--              spatio-temporal exclusion constraints (btree_gist).
-- =====================================================================

-- 0. Enable PostgreSQL btree_gist extension for spatio-temporal exclusion
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 1. Shows (Individual movie screening on a screen at a specific time)
CREATE TABLE show (
    id UUID PRIMARY KEY,
    movie_id UUID NOT NULL REFERENCES movie(id) ON DELETE RESTRICT,
    screen_id UUID NOT NULL REFERENCES screen(id) ON DELETE RESTRICT,
    seat_map_version_id UUID NOT NULL REFERENCES seat_map_version(id) ON DELETE RESTRICT,
    format_id UUID NOT NULL REFERENCES movie_format(id) ON DELETE RESTRICT,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    booking_opens_at TIMESTAMP WITH TIME ZONE NOT NULL,
    booking_closes_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_show_time_range CHECK (ends_at > starts_at),
    CONSTRAINT chk_show_booking_window CHECK (booking_closes_at > booking_opens_at AND booking_closes_at <= ends_at),
    CONSTRAINT chk_show_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'BOOKING_CLOSED', 'CANCELLED')),
    -- PostgreSQL Exclusion Constraint:
    -- Mathematically guarantees no two active shows can overlap on the same physical screen.
    CONSTRAINT no_overlapping_screen_shows EXCLUDE USING gist (
        screen_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status != 'CANCELLED')
);

CREATE INDEX idx_show_screen_id ON show(screen_id);
CREATE INDEX idx_show_movie_id ON show(movie_id);
CREATE INDEX idx_show_starts_at ON show(starts_at);
CREATE INDEX idx_show_status ON show(status);

-- 2. Show Price Configurations (Versioned pricing schema for a show)
CREATE TABLE show_price_configuration (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES show(id) ON DELETE CASCADE,
    version_number INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_show_price_config_version UNIQUE (show_id, version_number),
    CONSTRAINT chk_price_config_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_show_price_config_show_id ON show_price_configuration(show_id);

-- 3. Show Seat Prices (Per-category pricing, e.g. RECLINER = 450, PRIME = 300)
CREATE TABLE show_seat_price (
    id UUID PRIMARY KEY,
    price_configuration_id UUID NOT NULL REFERENCES show_price_configuration(id) ON DELETE CASCADE,
    section_category_code VARCHAR(20) NOT NULL,
    price_amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_price_config_category UNIQUE (price_configuration_id, section_category_code),
    CONSTRAINT chk_seat_price_positive CHECK (price_amount >= 0)
);

CREATE INDEX idx_seat_price_config_id ON show_seat_price(price_configuration_id);

-- 4. Show Charge Rules (Convenience fees, taxes, integrated GST)
CREATE TABLE show_charge_rule (
    id UUID PRIMARY KEY,
    price_configuration_id UUID NOT NULL REFERENCES show_price_configuration(id) ON DELETE CASCADE,
    charge_type VARCHAR(50) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    rate_percentage DECIMAL(5, 2) DEFAULT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_price_config_charge UNIQUE (price_configuration_id, charge_type),
    CONSTRAINT chk_charge_rule_values CHECK (amount >= 0 AND (rate_percentage IS NULL OR rate_percentage >= 0))
);

CREATE INDEX idx_charge_rule_config_id ON show_charge_rule(price_configuration_id);

-- 5. Show Seats (Atomic materialized inventory rows for high-concurrency booking)
CREATE TABLE show_seat (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES show(id) ON DELETE CASCADE,
    physical_seat_id UUID NOT NULL REFERENCES physical_seat(id) ON DELETE RESTRICT,
    seat_section_id UUID NOT NULL REFERENCES seat_section(id) ON DELETE RESTRICT,
    row_label VARCHAR(10) NOT NULL,
    seat_number INT NOT NULL,
    seat_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR',
    state VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    version BIGINT NOT NULL DEFAULT 0,
    active_hold_id UUID DEFAULT NULL,
    sold_booking_id UUID DEFAULT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_show_physical_seat UNIQUE (show_id, physical_seat_id),
    CONSTRAINT chk_show_seat_state CHECK (state IN ('AVAILABLE', 'HELD', 'SOLD', 'BLOCKED'))
);

CREATE INDEX idx_show_seat_show_state ON show_seat(show_id, state);
CREATE INDEX idx_show_seat_active_hold ON show_seat(active_hold_id) WHERE active_hold_id IS NOT NULL;
CREATE INDEX idx_show_seat_sold_booking ON show_seat(sold_booking_id) WHERE sold_booking_id IS NOT NULL;

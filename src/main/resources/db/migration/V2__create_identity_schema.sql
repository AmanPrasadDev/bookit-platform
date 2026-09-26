-- ============================================================================
-- V2__create_identity_schema.sql
-- Milestone M1: Identity and Access Management (Users, Roles, External Identity)
-- ============================================================================

-- 1. App User table (Internal core user entity)
CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    phone_number VARCHAR(32) UNIQUE,
    full_name VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Invariant: User must have at least an email (Google) or a phone number (OTP)
    CONSTRAINT chk_user_contact_present CHECK (email IS NOT NULL OR phone_number IS NOT NULL)
);

-- Index for fast user lookup during auth
CREATE INDEX idx_app_user_email ON app_user(email) WHERE email IS NOT NULL;
CREATE INDEX idx_app_user_phone ON app_user(phone_number) WHERE phone_number IS NOT NULL;

-- 2. External Identity table (Account Linker for Google, OTP, Apple, etc.)
CREATE TABLE external_identity (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL, -- e.g., 'GOOGLE', 'PHONE'
    provider_user_id VARCHAR(255) NOT NULL, -- Google's 'sub' claim or verified phone number
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Invariant: One external ID cannot belong to two different internal users
    CONSTRAINT uq_provider_user_id UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_external_identity_lookup ON external_identity(provider, provider_user_id);
CREATE INDEX idx_external_identity_user_id ON external_identity(user_id);

-- 3. Role Assignment table (Fine-grained RBAC with Cinema/Org scoping)
CREATE TABLE role_assignment (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role VARCHAR(64) NOT NULL, -- 'PLATFORM_ADMIN', 'ORG_ADMIN', 'CINEMA_STAFF', 'CUSTOMER'
    organization_id UUID,      -- Scoped to cinema chain (e.g., PVR org)
    cinema_id UUID,            -- Scoped to physical venue (for entrance scanners)
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Prevent duplicate role assignments for the same scope
    CONSTRAINT uq_user_role_scope UNIQUE NULLS NOT DISTINCT (user_id, role, organization_id, cinema_id)
);

CREATE INDEX idx_role_assignment_user_id ON role_assignment(user_id);

-- 4. Security Audit Event table (Tamper-evident security trail)
CREATE TABLE security_audit_event (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    event_type VARCHAR(64) NOT NULL, -- 'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'TOKEN_REUSE_DETECTED', etc.
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_security_audit_user_id ON security_audit_event(user_id);
CREATE INDEX idx_security_audit_event_type ON security_audit_event(event_type);
CREATE INDEX idx_security_audit_created_at ON security_audit_event(created_at);
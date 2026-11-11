-- StarX Mail Production Database Schema
-- Migration: 20261111000000_starx_mail_schema.sql
-- Database: Supabase PostgreSQL (Postgres 17)

-- Enable UUID extension if not enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Connected Accounts Table
CREATE TABLE IF NOT EXISTS public.mail_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT,
    resend_api_key_encrypted TEXT NOT NULL,
    resend_api_key_iv TEXT NOT NULL,
    resend_api_key_tag TEXT NOT NULL,
    resend_key_masked TEXT NOT NULL,
    default_from_address TEXT NOT NULL,
    webhook_id TEXT,
    webhook_signing_secret TEXT,
    webhook_configured BOOLEAN DEFAULT FALSE,
    webhook_url TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Inboxes Table
CREATE TABLE IF NOT EXISTS public.mail_inboxes (
    id TEXT PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES public.mail_accounts(id) ON DELETE CASCADE,
    email_address TEXT NOT NULL,
    name TEXT NOT NULL,
    from_name TEXT,
    forwarding BOOLEAN DEFAULT FALSE,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Threads Table
CREATE TABLE IF NOT EXISTS public.mail_threads (
    id TEXT PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES public.mail_accounts(id) ON DELETE CASCADE,
    subject TEXT NOT NULL,
    snippet TEXT,
    message_count INTEGER NOT NULL DEFAULT 1,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    is_starred BOOLEAN NOT NULL DEFAULT FALSE,
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Messages Table
CREATE TABLE IF NOT EXISTS public.mail_messages (
    id TEXT PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES public.mail_accounts(id) ON DELETE CASCADE,
    inbox_id TEXT,
    thread_id TEXT NOT NULL,
    from_address TEXT NOT NULL,
    to_addresses JSONB NOT NULL DEFAULT '[]'::jsonb,
    cc_addresses JSONB NOT NULL DEFAULT '[]'::jsonb,
    bcc_addresses JSONB NOT NULL DEFAULT '[]'::jsonb,
    reply_to TEXT,
    subject TEXT NOT NULL DEFAULT '',
    text_body TEXT NOT NULL DEFAULT '',
    html_body TEXT NOT NULL DEFAULT '',
    snippet TEXT NOT NULL DEFAULT '',
    folder TEXT NOT NULL DEFAULT 'inbox',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    is_starred BOOLEAN NOT NULL DEFAULT FALSE,
    has_attachments BOOLEAN NOT NULL DEFAULT FALSE,
    delivery_status TEXT NOT NULL DEFAULT 'received',
    headers JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Attachments Table
CREATE TABLE IF NOT EXISTS public.mail_attachments (
    id TEXT PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES public.mail_accounts(id) ON DELETE CASCADE,
    message_id TEXT NOT NULL REFERENCES public.mail_messages(id) ON DELETE CASCADE,
    filename TEXT NOT NULL,
    content_type TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_path TEXT,
    content_base64 TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. Drafts Table
CREATE TABLE IF NOT EXISTS public.mail_drafts (
    id TEXT PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES public.mail_accounts(id) ON DELETE CASCADE,
    inbox_id TEXT,
    to_addresses JSONB NOT NULL DEFAULT '[]'::jsonb,
    cc_addresses JSONB NOT NULL DEFAULT '[]'::jsonb,
    bcc_addresses JSONB NOT NULL DEFAULT '[]'::jsonb,
    subject TEXT NOT NULL DEFAULT '',
    text_body TEXT NOT NULL DEFAULT '',
    html_body TEXT NOT NULL DEFAULT '',
    attachments JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. Webhook Events Table (Idempotency)
CREATE TABLE IF NOT EXISTS public.mail_webhook_events (
    id TEXT PRIMARY KEY,
    account_id UUID REFERENCES public.mail_accounts(id) ON DELETE SET NULL,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'processed',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 8. Sync State Table
CREATE TABLE IF NOT EXISTS public.mail_sync_state (
    account_id UUID NOT NULL REFERENCES public.mail_accounts(id) ON DELETE CASCADE,
    cursor_key TEXT NOT NULL,
    last_sync_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_id, cursor_key)
);

-- 9. Push Notification Tokens Table (FCM)
CREATE TABLE IF NOT EXISTS public.mail_push_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES public.mail_accounts(id) ON DELETE CASCADE,
    fcm_token TEXT NOT NULL UNIQUE,
    device_name TEXT,
    platform TEXT DEFAULT 'android',
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 10. Sessions Table
CREATE TABLE IF NOT EXISTS public.mail_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES public.mail_accounts(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    device_info TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 11. Audit Logs Table
CREATE TABLE IF NOT EXISTS public.mail_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID REFERENCES public.mail_accounts(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    details JSONB,
    ip_address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for high performance querying
CREATE INDEX IF NOT EXISTS idx_mail_messages_account_folder ON public.mail_messages (account_id, folder, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mail_messages_thread_id ON public.mail_messages (thread_id, created_at);
CREATE INDEX IF NOT EXISTS idx_mail_messages_unread ON public.mail_messages (account_id, is_read) WHERE is_read = false;
CREATE INDEX IF NOT EXISTS idx_mail_messages_starred ON public.mail_messages (account_id, is_starred) WHERE is_starred = true;
CREATE INDEX IF NOT EXISTS idx_mail_threads_account ON public.mail_threads (account_id, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_mail_attachments_message ON public.mail_attachments (message_id);
CREATE INDEX IF NOT EXISTS idx_mail_webhook_events_created ON public.mail_webhook_events (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mail_push_tokens_account ON public.mail_push_tokens (account_id);
CREATE INDEX IF NOT EXISTS idx_mail_sessions_token ON public.mail_sessions (token_hash);

-- Enable Row Level Security (RLS) on all tables
ALTER TABLE public.mail_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mail_inboxes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mail_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mail_threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mail_drafts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mail_attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mail_webhook_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mail_sync_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mail_push_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mail_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mail_audit_logs ENABLE ROW LEVEL SECURITY;

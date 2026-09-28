-- 0012_payments: payment intent records for the Stripe Connect seam (API-071).
-- One intent per sitting booking (booking_id UNIQUE); status tracks the
-- PaymentIntent lifecycle once the real gateway is wired (webhooks later).
CREATE TABLE IF NOT EXISTS payment_intents (
    id TEXT PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE REFERENCES sitting_requests(id) ON DELETE CASCADE,
    amount_cents INT NOT NULL CHECK (amount_cents >= 0),
    fee_cents INT NOT NULL CHECK (fee_cents >= 0),
    client_secret TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'created',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS payment_intents_booking_idx ON payment_intents (booking_id);

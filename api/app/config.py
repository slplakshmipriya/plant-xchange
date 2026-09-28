"""Central configuration (SEC-001).

Every setting comes from the environment. Nothing is read from files, and no
default in this module is a real credential. ``get_settings()`` reads the
environment fresh on each call so tests can monkeypatch env vars freely.
"""

from __future__ import annotations

import os
from dataclasses import dataclass


def _int(name: str, default: int) -> int:
    try:
        return max(1, int(os.environ.get(name, default)))
    except (ValueError, TypeError):
        return default


@dataclass(frozen=True)
class Settings:
    database_url: str | None       # Neon Postgres connection string
    firebase_project_id: str | None
    rate_limit_per_min: int        # per-instance token bucket (approximate)
    log_level: str
    port: int                      # honored from $PORT on Cloud Run
    # --- Wave 1 additions (all optional; None-safe defaults, no real creds) ---
    sweep_secret: str | None       # SWEEP_SECRET: shared secret for /v1/internal/sweep
    idv_provider: str              # IDV_PROVIDER: "stub" (dev) or real provider name
    idv_webhook_secret: str | None  # IDV_WEBHOOK_SECRET: HMAC secret for IDV webhooks
    storage_backend: str           # STORAGE_BACKEND: "local" stub or "gcs"
    uploads_dir: str               # UPLOADS_DIR: local stub upload root
    user_tz: str                   # USER_TZ: IANA tz for quiet-hours evaluation
    notify_daily_cap: int          # NOTIFY_DAILY_CAP: max notifications per user per day
    # --- Stripe Connect seam (API-071; stub until wired) ---
    payment_provider: str          # PAYMENT_PROVIDER: "stub" (dev) or "stripe"
    stripe_secret_key: str | None  # STRIPE_SECRET_KEY: platform secret key (sk_...)
    stripe_webhook_secret: str | None  # STRIPE_WEBHOOK_SECRET: whsec_... for webhooks


def get_settings() -> Settings:
    return Settings(
        database_url=os.environ.get("DATABASE_URL"),
        firebase_project_id=os.environ.get("FIREBASE_PROJECT_ID"),
        rate_limit_per_min=_int("RATE_LIMIT_PER_MIN", 120),
        log_level=os.environ.get("LOG_LEVEL", "INFO").upper(),
        port=_int("PORT", 8080),
        sweep_secret=os.environ.get("SWEEP_SECRET"),
        idv_provider=os.environ.get("IDV_PROVIDER", "stub"),
        idv_webhook_secret=os.environ.get("IDV_WEBHOOK_SECRET"),
        storage_backend=os.environ.get("STORAGE_BACKEND", "local"),
        uploads_dir=os.environ.get("UPLOADS_DIR", "var/uploads"),
        user_tz=os.environ.get("USER_TZ", "America/New_York"),
        notify_daily_cap=_int("NOTIFY_DAILY_CAP", 5),
        payment_provider=os.environ.get("PAYMENT_PROVIDER", "stub"),
        stripe_secret_key=os.environ.get("STRIPE_SECRET_KEY"),
        stripe_webhook_secret=os.environ.get("STRIPE_WEBHOOK_SECRET"),
    )

"""Cross-cutting middleware (API-003).

Stack order, outermost -> innermost (see create_app wiring):
  RequestIDMiddleware -> AccessLogMiddleware -> RateLimitMiddleware
      -> FirebaseAuthMiddleware -> ExceptionMiddleware -> router

- Request IDs propagate via header ``X-Request-ID`` (generated when absent)
  and a contextvar so the JSON log formatter can attach them anywhere.
- Structured JSON access/error logs via stdlib logging (no extra deps).
- Token-bucket rate limiter keyed on (client IP, credential fingerprint).
  In-memory and per-instance: under Cloud Run scale-to-zero each instance
  enforces its own bucket, so the effective limit is *approximately*
  ``RATE_LIMIT_PER_MIN * instance_count``. Documented as approximate;
  abuse-grade limiting belongs in Wave 5 (SEC-101, e.g. Cloud Armor).
"""

from __future__ import annotations

import contextvars
import hashlib
import json
import logging
import threading
import time
from datetime import datetime, timezone

from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware

from .config import get_settings
from .errors import error_response, new_request_id

logger = logging.getLogger(__name__)

request_id_var: contextvars.ContextVar[str | None] = contextvars.ContextVar(
    "request_id", default=None
)


# ---------------------------------------------------------------- logging ---


class JsonFormatter(logging.Formatter):
    """Single-line JSON logs. Merges ``extra={"fields": {...}}`` into the payload."""

    def format(self, record: logging.LogRecord) -> str:
        payload: dict = {
            "ts": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "msg": record.getMessage(),
            "request_id": request_id_var.get(),
        }
        fields = getattr(record, "fields", None)
        if isinstance(fields, dict):
            payload.update(fields)
        # Server-side only: exception text stays in logs, never in responses.
        if record.exc_info:
            payload["exc"] = self.formatException(record.exc_info)
        return json.dumps(payload, default=str)


def configure_logging() -> None:
    """Attach the JSON formatter to the root logger once (LOG_LEVEL env)."""
    root = logging.getLogger()
    if any(isinstance(h.formatter, JsonFormatter) for h in root.handlers):
        return
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    root.addHandler(handler)
    root.setLevel(get_settings().log_level)


# ------------------------------------------------------------ request id ---


class RequestIDMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        rid = request.headers.get("x-request-id") or new_request_id()
        request.state.request_id = rid
        token = request_id_var.set(rid)
        try:
            response = await call_next(request)
        finally:
            request_id_var.reset(token)
        response.headers["X-Request-ID"] = rid
        return response


# ------------------------------------------------------------ access log ---


class AccessLogMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        start = time.perf_counter()
        response = await call_next(request)
        duration_ms = round((time.perf_counter() - start) * 1000, 2)
        logger.info(
            "request completed",
            extra={
                "fields": {
                    "method": request.method,
                    "path": request.url.path,
                    "status": response.status_code,
                    "duration_ms": duration_ms,
                }
            },
        )
        return response


# ---------------------------------------------------------- rate limit ---


def _rate_limit_per_min() -> int:
    return get_settings().rate_limit_per_min


class TokenBucketLimiter:
    """In-memory token bucket. NOT shared across Cloud Run instances (see docstring)."""

    def __init__(self, per_minute: int):
        self.capacity = max(1, per_minute)
        self.refill_per_sec = self.capacity / 60.0
        self._buckets: dict[str, tuple[float, float]] = {}
        self._lock = threading.Lock()

    @staticmethod
    def bucket_key(request: Request) -> str:
        # Prefer the leftmost X-Forwarded-For entry: behind Cloud Run's
        # load balancer request.client.host would otherwise be the LB's IP
        # and every anonymous caller would share one bucket. (uvicorn also
        # runs with --proxy-headers, which rewrites scope["client"]; reading
        # XFF explicitly keeps this correct regardless of proxy config.)
        xff = request.headers.get("x-forwarded-for", "")
        client = xff.split(",")[0].strip() or (
            request.client.host if request.client else "unknown"
        )
        auth = request.headers.get("authorization", "")
        cred = hashlib.sha256(auth.encode()).hexdigest()[:16] if auth else "anon"
        return f"{client}:{cred}"

    def allow(self, key: str) -> tuple[bool, float]:
        """Return (allowed, retry_after_seconds)."""
        now = time.monotonic()
        with self._lock:
            tokens, last = self._buckets.get(key, (float(self.capacity), now))
            tokens = min(float(self.capacity), tokens + (now - last) * self.refill_per_sec)
            if tokens >= 1.0:
                self._buckets[key] = (tokens - 1.0, now)
                return True, 0.0
            retry_after = (1.0 - tokens) / self.refill_per_sec
            self._buckets[key] = (tokens, now)
            if len(self._buckets) > 10_000:  # lazy prune of stale buckets
                self._buckets = {
                    k: v for k, v in self._buckets.items() if now - v[1] < 3600
                }
            return False, retry_after


class RateLimitMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, per_minute: int | None = None):
        super().__init__(app)
        self.limiter = TokenBucketLimiter(
            per_minute if per_minute is not None else _rate_limit_per_min()
        )

    async def dispatch(self, request: Request, call_next):
        ok, retry_after = self.limiter.allow(TokenBucketLimiter.bucket_key(request))
        if not ok:
            return error_response(
                request,
                429,
                "rate_limited",
                "Too many requests — please slow down.",
                headers={"Retry-After": str(max(1, int(retry_after + 0.5)))},
            )
        return await call_next(request)

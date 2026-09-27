"""Uniform error envelope shared by all middleware and handlers.

Shape: {"code": "<machine_code>", "message": "<human message>", "request_id": "<hex>"}.
Every error response carries a request_id so a client report can be traced in
the JSON logs. Never include stack traces or exception internals.
"""

from __future__ import annotations

import uuid
from typing import Any

from fastapi import Request
from fastapi.responses import JSONResponse


def new_request_id() -> str:
    return uuid.uuid4().hex


def request_id_of(request: Request | None) -> str:
    if request is not None:
        rid = getattr(getattr(request, "state", None), "request_id", None)
        if rid:
            return rid
    return new_request_id()


def error_response(
    request: Request | None,
    status_code: int,
    code: str,
    message: str,
    headers: dict[str, str] | None = None,
) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={
            "code": code,
            "message": message,
            "request_id": request_id_of(request),
        },
        headers=headers,
    )


def http_exception_detail(status_code: int, detail: Any) -> tuple[str, str]:
    """Normalize an HTTPException detail into (code, message)."""
    if isinstance(detail, dict):
        return str(detail.get("code", "error")), str(detail.get("message", detail))
    fallback = {
        400: "bad_request",
        401: "unauthorized",
        403: "forbidden",
        404: "not_found",
        429: "rate_limited",
    }
    return fallback.get(status_code, "error"), str(detail)

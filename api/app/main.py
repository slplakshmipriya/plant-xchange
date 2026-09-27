"""GardenSwap API — FastAPI application factory."""

from contextlib import asynccontextmanager
from pathlib import Path

import logging

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import FileResponse

from .auth import FirebaseAuthMiddleware, get_current_uid, init_firebase
from .db import run_migrations
from .errors import error_response, http_exception_detail
from .middleware import (
    AccessLogMiddleware,
    RateLimitMiddleware,
    RequestIDMiddleware,
    configure_logging,
)

API_DIR = Path(__file__).resolve().parent.parent
OPENAPI_PATH = API_DIR / "openapi.yaml"

APP_VERSION = "0.1.0"


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Apply pending DB migrations on startup. Skips gracefully when
    # DATABASE_URL is unset (e.g. local dev / CI without Postgres).
    run_migrations()
    init_firebase()
    yield


def create_app() -> FastAPI:
    configure_logging()
    app = FastAPI(
        title="GardenSwap API",
        version=APP_VERSION,
        description=(
            "Hyperlocal gardening exchange: seedling swaps, harvest swaps, "
            "pick-your-own slots, plant sitting, and a fungible credit economy."
        ),
        lifespan=lifespan,
    )

    # Middleware: added innermost-first; last added runs outermost.
    # Final order: RequestID -> AccessLog -> RateLimit -> FirebaseAuth.
    app.add_middleware(FirebaseAuthMiddleware)
    app.add_middleware(RateLimitMiddleware)
    app.add_middleware(AccessLogMiddleware)
    app.add_middleware(RequestIDMiddleware)

    @app.exception_handler(HTTPException)
    async def http_exception_handler(request: Request, exc: HTTPException):
        code, message = http_exception_detail(exc.status_code, exc.detail)
        return error_response(request, exc.status_code, code, message)

    @app.exception_handler(Exception)
    async def unhandled_exception_handler(request: Request, exc: Exception):
        # Traceback goes to the JSON logs only — never to the client.
        logging.getLogger(__name__).exception("unhandled exception")
        return error_response(request, 500, "internal_error", "Internal server error.")

    async def not_found_default(scope, receive, send):
        """Router fallback: unmatched paths also get the error envelope."""
        request = Request(scope)
        response = error_response(
            request, 404, "not_found",
            f"No route for {request.method} {request.url.path}",
        )
        await response(scope, receive, send)

    app.router.default = not_found_default

    @app.get("/healthz", tags=["ops"])
    def healthz() -> dict:
        """Liveness probe. Auth-exempt; must stay cheap and dependency-free."""
        return {"status": "ok", "version": APP_VERSION}

    @app.get("/me", tags=["auth"])
    def me(uid: str = Depends(get_current_uid)) -> dict:
        """Whoami: proves the caller's ID token verified. Used by clients at login."""
        return {"uid": uid}

    @app.get("/openapi.yaml", include_in_schema=False)
    def openapi_yaml() -> FileResponse:
        """Serve the checked-in contract spec (see API-004). Auth-exempt."""
        return FileResponse(OPENAPI_PATH, media_type="text/yaml")

    return app


app = create_app()

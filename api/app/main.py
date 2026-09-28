"""GardenSwap API — FastAPI application factory."""

from contextlib import asynccontextmanager
from pathlib import Path

import logging

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.openapi.utils import get_openapi
from fastapi.responses import FileResponse

from .auth import (
    EXEMPT_PATHS,
    EXEMPT_PREFIXES,
    FirebaseAuthMiddleware,
    get_current_uid,
    init_firebase,
)
from .config import get_settings, validate_idv_config
from .db import run_migrations
from .errors import error_response, http_exception_detail
from .middleware import (
    AccessLogMiddleware,
    RateLimitMiddleware,
    RequestIDMiddleware,
    configure_logging,
)
from . import users as users_module
from . import credits as credits_module
from . import verify as verify_module
from . import idv as idv_module
from . import listings as listings_module
from . import uploads as uploads_module
from . import notify as notify_module
from . import feed as feed_module
from . import wantlist as wantlist_module
from . import exchange as exchange_module
from . import claims as claims_module
from . import sitter as sitter_module
from . import msg as msg_module
from . import slots as slots_module
from . import payments as payments_module
from . import moderation as moderation_module

API_DIR = Path(__file__).resolve().parent.parent
OPENAPI_PATH = API_DIR / "openapi.yaml"

APP_VERSION = "0.1.0"


@asynccontextmanager
async def lifespan(app: FastAPI):
    # C1: fail closed at boot when the stub IDV provider is selected without
    # the explicit ENABLE_IDV_STUB=1 opt-in — a deploy that forgot
    # IDV_PROVIDER must refuse to boot instead of shipping the backdoor.
    validate_idv_config(get_settings())
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

    # Domain routers (each owns its /v1/* routes and repo factory).
    app.include_router(users_module.router)
    app.include_router(credits_module.router)
    app.include_router(verify_module.router)
    app.include_router(idv_module.router)
    app.include_router(listings_module.router)
    app.include_router(listings_module.internal_router)
    app.include_router(uploads_module.router)
    app.include_router(notify_module.router)
    app.include_router(feed_module.router)
    app.include_router(wantlist_module.router)
    app.include_router(exchange_module.router)
    app.include_router(claims_module.router)
    app.include_router(sitter_module.router)
    app.include_router(msg_module.router)
    app.include_router(slots_module.router)
    app.include_router(payments_module.router)
    app.include_router(moderation_module.router)

    @app.get("/me", tags=["auth"])
    def me(uid: str = Depends(get_current_uid)) -> dict:
        """Whoami: proves the caller's ID token verified. Used by clients at login."""
        return {"uid": uid}

    @app.get("/openapi.yaml", include_in_schema=False)
    def openapi_yaml() -> FileResponse:
        """Serve the checked-in contract spec (see API-004). Auth-exempt."""
        return FileResponse(OPENAPI_PATH, media_type="text/yaml")

    def custom_openapi():
        """OpenAPI with the Firebase bearer scheme documented for FE mock generation."""
        if app.openapi_schema:
            return app.openapi_schema
        spec = get_openapi(
            title=app.title,
            version=app.version,
            description=app.description,
            routes=app.routes,
        )
        spec.setdefault("components", {}).setdefault("securitySchemes", {})[
            "bearerAuth"
        ] = {
            "type": "http",
            "scheme": "bearer",
            "bearerFormat": "JWT",
            "description": (
                "Firebase ID token. Required on every route except /healthz "
                "and /openapi.yaml."
            ),
        }
        # Mark authenticated operations so generated mocks know the contract.
        # Auth-exempt paths come from the same sets the middleware enforces.
        for path, methods in spec.get("paths", {}).items():
            if path in EXEMPT_PATHS or path.startswith(EXEMPT_PREFIXES):
                continue
            for op in methods.values():
                if isinstance(op, dict):
                    op.setdefault("security", [{"bearerAuth": []}])
        app.openapi_schema = spec
        return spec

    app.openapi = custom_openapi  # type: ignore[method-assign]

    return app


app = create_app()

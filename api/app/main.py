"""GardenSwap API — FastAPI application factory."""

from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import FileResponse

from .db import run_migrations

API_DIR = Path(__file__).resolve().parent.parent
OPENAPI_PATH = API_DIR / "openapi.yaml"

APP_VERSION = "0.1.0"


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Apply pending DB migrations on startup. Skips gracefully when
    # DATABASE_URL is unset (e.g. local dev / CI without Postgres).
    run_migrations()
    yield


def create_app() -> FastAPI:
    app = FastAPI(
        title="GardenSwap API",
        version=APP_VERSION,
        description=(
            "Hyperlocal gardening exchange: seedling swaps, harvest swaps, "
            "pick-your-own slots, plant sitting, and a fungible credit economy."
        ),
        lifespan=lifespan,
    )

    @app.get("/healthz", tags=["ops"])
    def healthz() -> dict:
        """Liveness probe. Auth-exempt; must stay cheap and dependency-free."""
        return {"status": "ok", "version": APP_VERSION}

    @app.get("/openapi.yaml", include_in_schema=False)
    def openapi_yaml() -> FileResponse:
        """Serve the checked-in contract spec (see API-004). Auth-exempt."""
        return FileResponse(OPENAPI_PATH, media_type="text/yaml")

    return app


app = create_app()

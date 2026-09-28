"""API-004: the checked-in openapi.yaml must match the app. Drift fails loudly."""
from __future__ import annotations

from pathlib import Path

import yaml

SPEC_PATH = Path(__file__).resolve().parent.parent / "openapi.yaml"


def _current_spec() -> dict:
    from app.main import create_app

    return create_app().openapi()


def test_spec_file_exists():
    assert SPEC_PATH.is_file(), "run: ../.venv/bin/python export_openapi.py"


def test_spec_matches_app():
    checked_in = yaml.safe_load(SPEC_PATH.read_text(encoding="utf-8"))
    assert checked_in == _current_spec(), (
        "openapi.yaml drifted from the app — regenerate with "
        "../.venv/bin/python export_openapi.py and commit the result"
    )


def test_spec_covers_core_contract():
    spec = yaml.safe_load(SPEC_PATH.read_text(encoding="utf-8"))
    paths = spec["paths"]
    assert "/healthz" in paths
    assert "/me" in paths
    assert spec["info"]["title"] == "GardenSwap API"
    # Security scheme documented for FE mock generation.
    schemes = spec.get("components", {}).get("securitySchemes", {})
    assert "bearerAuth" in schemes

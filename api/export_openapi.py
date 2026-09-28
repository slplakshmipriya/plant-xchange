#!/usr/bin/env python3
"""Regenerate api/openapi.yaml from the FastAPI app.

This is the contract FE builds its mocks against (see DEPENDENCY_TREE.md).
Run from the api/ directory:  ../.venv/bin/python export_openapi.py
CI does NOT regenerate — the drift test fails instead, forcing an explicit
commit of the updated spec.
"""

from __future__ import annotations

import sys
from pathlib import Path

import yaml

API_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(API_DIR))

from app.main import create_app  # noqa: E402

SPEC_PATH = API_DIR / "openapi.yaml"


def export() -> Path:
    spec = create_app().openapi()
    SPEC_PATH.write_text(
        yaml.safe_dump(spec, sort_keys=False, allow_unicode=True),
        encoding="utf-8",
    )
    return SPEC_PATH


if __name__ == "__main__":
    print(f"wrote {export()}")

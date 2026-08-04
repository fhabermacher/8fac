"""Pairing config: ~/.config/8fac/pairing.json (see PROTOCOL.md §1)."""
import base64
import json
import os
import secrets
from pathlib import Path

CONF_DIR = Path(os.environ.get("EIGHTFAC_CONF", "~/.config/8fac")).expanduser()
PAIRING = CONF_DIR / "pairing.json"


def create_pairing(relay_url: str, invite: str | None = None) -> dict:
    pairing = {
        "v": 0,
        "pair_id": secrets.token_hex(16),
        "key": base64.b64encode(secrets.token_bytes(32)).decode(),
        "relay": relay_url,
    }
    if invite:
        pairing["invite"] = invite
    CONF_DIR.mkdir(parents=True, exist_ok=True)
    PAIRING.write_text(json.dumps(pairing, indent=2))
    PAIRING.chmod(0o600)
    return pairing


def load_pairing() -> dict:
    if not PAIRING.exists():
        raise SystemExit(f"no pairing at {PAIRING} — run pair.py first")
    return json.loads(PAIRING.read_text())


def key_bytes(pairing: dict) -> bytes:
    return base64.b64decode(pairing["key"])


def hello(pairing: dict, role: str) -> dict:
    h = {"role": role, "pair_id": pairing["pair_id"]}
    if "invite" in pairing:
        h["invite"] = pairing["invite"]
    return h

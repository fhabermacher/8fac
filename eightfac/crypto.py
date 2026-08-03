"""E2E envelope: NaCl secretbox under the pairing key.

Wire form (the relay's "blob"): base64( nonce[24] || ciphertext ).
Payloads are JSON dicts and must carry "ts"; unseal() enforces freshness.
"""
import base64
import json
import time

import nacl.secret
import nacl.utils

MAX_SKEW = 60  # seconds


def seal(key: bytes, payload: dict) -> str:
    payload = {**payload, "ts": int(time.time())}
    box = nacl.secret.SecretBox(key)
    nonce = nacl.utils.random(nacl.secret.SecretBox.NONCE_SIZE)
    ct = box.encrypt(json.dumps(payload).encode(), nonce)
    return base64.b64encode(bytes(ct)).decode()  # nonce||ciphertext


def unseal(key: bytes, blob_b64: str, max_age: float | None = MAX_SKEW) -> dict:
    """Decrypt and freshness-check. Raises on tamper or staleness.

    max_age=None skips the staleness check — only for payload types where
    replay is harmless (e.g. "endpoint" mailbox deposits, which may sit at
    the relay far longer than MAX_SKEW)."""
    raw = base64.b64decode(blob_b64)
    payload = json.loads(nacl.secret.SecretBox(key).decrypt(raw).decode())
    if max_age is not None and abs(time.time() - payload["ts"]) > max_age:
        raise ValueError("stale payload")
    return payload

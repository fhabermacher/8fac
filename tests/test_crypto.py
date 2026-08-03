import time

import pytest

from eightfac import crypto

KEY = bytes(range(32))


def test_roundtrip():
    blob = crypto.seal(KEY, {"t": "req", "id": "x", "service": "github"})
    out = crypto.unseal(KEY, blob)
    assert out["service"] == "github" and "ts" in out


def test_tamper_detected():
    blob = crypto.seal(KEY, {"t": "req"})
    corrupted = blob[:-8] + ("A" * 8 if not blob.endswith("A" * 8) else "B" * 8)
    with pytest.raises(Exception):
        crypto.unseal(KEY, corrupted)


def test_wrong_key_rejected():
    blob = crypto.seal(KEY, {"t": "req"})
    with pytest.raises(Exception):
        crypto.unseal(bytes(32), blob)


def test_staleness_rejected(monkeypatch):
    blob = crypto.seal(KEY, {"t": "req"})
    real = time.time
    monkeypatch.setattr(time, "time", lambda: real() + 120)
    with pytest.raises(ValueError):
        crypto.unseal(KEY, blob)


def test_nonces_unique():
    p = {"t": "req"}
    assert crypto.seal(KEY, p) != crypto.seal(KEY, p)

#!/usr/bin/env python3
"""Request a TOTP code from the paired phone. Bind this to a hotkey.

  request.py github            print the code (and copy to clipboard if possible)
  request.py github --type     type it into the focused window (xdotool/wtype)
"""
import argparse
import asyncio
import json
import sys
import time
import urllib.request
import uuid

import websockets

from eightfac import config, crypto, typer

TIMEOUT = 45  # phone gets this long to approve


def wake_phone(pairing: dict):
    """POST to the phone's push endpoint (learned via the relay mailbox) so
    a Dozing phone wakes and connects. No payload beyond 'wake up'."""
    url = pairing.get("wake")
    if not url:
        return
    try:
        req = urllib.request.Request(
            url, data=b"8fac", method="POST",
            headers={"Title": "8fac", "Priority": "urgent",
                     "Tags": "closed_lock_with_key"})
        urllib.request.urlopen(req, timeout=5)
    except Exception as e:
        print(f"(wake push failed: {e})", file=sys.stderr)


def learn_endpoint(pairing: dict, url: str):
    if pairing.get("wake") == url:
        return
    pairing["wake"] = url
    config.PAIRING.write_text(json.dumps(pairing, indent=2))
    print("(learned phone push endpoint)", file=sys.stderr)


async def _await_reply(ws, key: bytes, req_id: str, pairing: dict) -> str:
    async for raw in ws:
        msg = json.loads(raw)
        if msg.get("type") == "peer_offline":
            print("phone offline — request queued 60s", file=sys.stderr)
            continue
        if "blob" not in msg:
            continue
        try:
            payload = crypto.unseal(key, msg["blob"], max_age=None)
        except Exception:
            continue  # tampered — ignore
        if payload.get("t") == "endpoint":
            learn_endpoint(pairing, payload["url"])
            continue
        # non-endpoint payloads keep the strict freshness bound
        if abs(time.time() - payload.get("ts", 0)) > crypto.MAX_SKEW:
            continue
        if payload.get("id") != req_id:
            continue
        if payload["t"] == "code":
            return payload["code"]
        if payload["t"] == "deny":
            raise SystemExit("denied on phone")
    raise SystemExit("relay closed connection")


async def fetch_code(service: str) -> str:
    pairing = config.load_pairing()
    key = config.key_bytes(pairing)
    req_id = str(uuid.uuid4())

    wake_phone(pairing)
    async with websockets.connect(pairing["relay"]) as ws:
        await ws.send(json.dumps(config.hello(pairing, "pc")))
        await ws.send(json.dumps(
            {"blob": crypto.seal(key, {"t": "req", "id": req_id,
                                       "service": service})}))
        print(f"waiting for phone approval ({service})...", file=sys.stderr)
        try:
            return await asyncio.wait_for(
                _await_reply(ws, key, req_id, pairing), TIMEOUT)
        except asyncio.TimeoutError:
            raise SystemExit("timed out")


def deliver(code: str, type_it: bool):
    print(code)
    if type_it and typer.type_text(code):
        print("(typed into focused field)", file=sys.stderr)
        return
    if typer.copy_text(code):
        print("(copied to clipboard)", file=sys.stderr)


def remember_service(service: str):
    """Track requested services, most-recent first, for the hotkey picker."""
    f = config.CONF_DIR / "services.txt"
    known = f.read_text().split() if f.exists() else []
    if known[:1] != [service]:
        known = [service] + [s for s in known if s != service]
        f.write_text("\n".join(known[:20]) + "\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("service", help="service name shown on the phone prompt")
    ap.add_argument("--type", action="store_true",
                    help="type the code into the focused window")
    args = ap.parse_args()
    try:
        code = asyncio.run(fetch_code(args.service))
    except (OSError, websockets.WebSocketException) as e:
        raise SystemExit(f"relay unreachable: {e}")
    remember_service(args.service)
    deliver(code, args.type)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Request a TOTP code from the paired phone. Bind this to a hotkey.

  request.py github            print the code (and copy to clipboard if possible)
  request.py github --type     type it into the focused window (xdotool/wtype)
"""
import argparse
import asyncio
import json
import shutil
import subprocess
import sys
import time
import urllib.request
import uuid

import websockets

from eightfac import config, crypto

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
        await ws.send(json.dumps({"role": "pc", "pair_id": pairing["pair_id"]}))
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
    if type_it:
        for tool, argv in [("xdotool", ["xdotool", "type", "--", code]),
                           ("wtype", ["wtype", code])]:
            if shutil.which(tool):
                subprocess.run(argv, check=False)
                return
    for tool, argv in [("wl-copy", ["wl-copy"]), ("xclip", ["xclip", "-sel", "c"])]:
        if shutil.which(tool):
            # clipboard tools fork a resident child; detach its stdio so
            # callers capturing our output ($(...)) see EOF and don't hang
            subprocess.run(argv, input=code.encode(), check=False,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print("(copied to clipboard)", file=sys.stderr)
            return


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
    deliver(code, args.type)


if __name__ == "__main__":
    main()

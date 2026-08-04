#!/usr/bin/env python3
"""Phone stub: emulates the 8fac Android app for end-to-end testing.

Holds TOTP secrets in ~/.config/8fac/secrets.json ({"github": "BASE32", ...}),
connects to the relay as role "phone", and prompts on each request:

    y = approve once        n/enter = deny
    a = auto-accept THIS service for 5 min
    A = auto-accept ALL services for 5 min

--yes approves everything without prompting (for automated tests).
"""
import argparse
import asyncio
import json
import sys
import time
from pathlib import Path

import websockets

from eightfac import config, crypto, totp

AUTO_WINDOW = 5 * 60
SECRETS = config.CONF_DIR / "secrets.json"


class AutoAccept:
    """Time-boxed auto-approval, per service or global (PROTOCOL.md §4)."""

    def __init__(self):
        self.until: dict[str, float] = {}  # service ("*" = all) -> expiry
        self.log: list[str] = []

    def arm(self, scope: str, seconds: int = AUTO_WINDOW):
        self.until[scope] = time.monotonic() + seconds
        print(f"[auto-accept armed: {scope!r} for {seconds // 60} min]")

    def check(self, service: str) -> bool:
        now = time.monotonic()
        for scope in (service, "*"):
            if self.until.get(scope, 0) > now:
                self.log.append(f"{time.strftime('%H:%M:%S')} {service}")
                return True
        return False


def load_secrets() -> dict:
    if not SECRETS.exists():
        raise SystemExit(
            f"no secrets at {SECRETS} — create it, e.g. "
            '{"github": "JBSWY3DPEHPK3PXP"}')
    return json.loads(SECRETS.read_text())


async def ask(prompt: str) -> str:
    print(prompt, end="", flush=True)
    return (await asyncio.get_event_loop()
            .run_in_executor(None, sys.stdin.readline)).strip()


async def handle_request(ws, key: bytes, payload: dict, secrets: dict,
                         auto: AutoAccept, always_yes: bool):
    service, req_id = payload.get("service", "?"), payload["id"]

    async def reply(p):
        await ws.send(json.dumps({"blob": crypto.seal(key, p)}))

    if service not in secrets:
        print(f"[request for unknown service {service!r} — denying]")
        await reply({"t": "deny", "id": req_id})
        return

    if always_yes or auto.check(service):
        print(f"[auto-approved {service}]")
    else:
        ans = await ask(f"\n>> code request for {service.upper()} — "
                        f"approve? [y/n/a/A] ")
        if ans == "a":
            auto.arm(service)
        elif ans == "A":
            auto.arm("*")
        elif ans != "y":
            await reply({"t": "deny", "id": req_id})
            print("[denied]")
            return
        print("[fingerprint OK 👍 (imagine)]")

    await reply({"t": "code", "id": req_id, "code": totp.totp(secrets[service])})
    print(f"[code sent for {service}]")


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--yes", action="store_true", help="approve all (testing)")
    args = ap.parse_args()

    pairing = config.load_pairing()
    key = config.key_bytes(pairing)
    secrets = load_secrets()
    auto = AutoAccept()
    print(f"phone stub: {len(secrets)} secret(s), relay {pairing['relay']}")

    async for ws in websockets.connect(pairing["relay"]):
        try:
            await ws.send(json.dumps(config.hello(pairing, "phone")))
            async for raw in ws:
                msg = json.loads(raw)
                if "blob" not in msg:
                    continue
                try:
                    payload = crypto.unseal(key, msg["blob"])
                except Exception:
                    continue
                if payload.get("t") == "req":
                    await handle_request(ws, key, payload, secrets, auto,
                                         args.yes)
        except websockets.ConnectionClosed:
            print("[relay connection lost — reconnecting]")
            continue


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass

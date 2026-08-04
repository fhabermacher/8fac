#!/usr/bin/env python3
"""8fac relay: a blind pipe between a paired PC and phone.

Routes opaque {"blob": ...} frames between the two roles of a pair_id.
Holds no keys, decrypts nothing. Queues frames up to QUEUE_TTL seconds
for an offline peer (so a push-woken phone can connect and catch up).

Run:  python relay.py [--host 0.0.0.0] [--port 8443]
"""
import argparse
import asyncio
import json
import logging
import time

import websockets

QUEUE_TTL = 60  # seconds a frame waits for an offline peer
MAX_QUEUE = 32  # frames per (pair, role)

log = logging.getLogger("8fac.relay")

# pair_id -> {"pc": ws|None, "phone": ws|None}
peers: dict[str, dict] = {}
# (pair_id, target_role) -> list[(expiry_ts, frame_str)]
queues: dict[tuple, list] = {}
# pair_id -> last blob the phone deposited (its encrypted push endpoint);
# delivered to the PC on every connect. Opaque to the relay, memory-only —
# the phone re-deposits on each of its connects.
mailbox: dict[str, str] = {}

OTHER = {"pc": "phone", "phone": "pc"}


def _flush_queue(pair_id: str, role: str):
    """Return still-fresh queued frames for (pair_id, role) and clear them."""
    now = time.monotonic()
    fresh = [f for exp, f in queues.pop((pair_id, role), []) if exp > now]
    return fresh


invites: set | None = None  # None = open relay; set = required tokens


async def handle(ws):
    pair_id = role = None
    try:
        hello = json.loads(await asyncio.wait_for(ws.recv(), timeout=10))
        pair_id, role = hello["pair_id"], hello["role"]
        if role not in OTHER or not isinstance(pair_id, str) or len(pair_id) > 64:
            await ws.close(1008, "bad hello")
            return
        if invites is not None and hello.get("invite") not in invites:
            await ws.close(1008, "invite required")
            return
        slot = peers.setdefault(pair_id, {"pc": None, "phone": None})
        if slot[role] is not None:
            await slot[role].close(1000, "replaced")
        slot[role] = ws
        log.info("%s connected (%s)", role, pair_id[:8])

        if role == "pc" and pair_id in mailbox:
            await ws.send(json.dumps({"blob": mailbox[pair_id]}))
        for frame in _flush_queue(pair_id, role):
            await ws.send(frame)

        async for raw in ws:
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                continue
            if len(raw) > 65536:
                continue
            if role == "phone" and "deposit" in msg:
                if isinstance(msg["deposit"], str):
                    mailbox[pair_id] = msg["deposit"]
                continue
            if "blob" not in msg:
                continue
            frame = json.dumps({"blob": msg["blob"]})
            peer = peers.get(pair_id, {}).get(OTHER[role])
            if peer is not None:
                try:
                    await peer.send(frame)
                    continue
                except websockets.ConnectionClosed:
                    pass
            q = queues.setdefault((pair_id, OTHER[role]), [])
            q.append((time.monotonic() + QUEUE_TTL, frame))
            del q[:-MAX_QUEUE]
            await ws.send(json.dumps({"type": "peer_offline"}))
    except (asyncio.TimeoutError, websockets.ConnectionClosed, KeyError,
            json.JSONDecodeError):
        pass
    finally:
        if pair_id and peers.get(pair_id, {}).get(role) is ws:
            peers[pair_id][role] = None
            if not any(peers[pair_id].values()):
                del peers[pair_id]
            log.info("%s disconnected (%s)", role, pair_id[:8])


async def main():
    global invites
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8443)
    ap.add_argument("--invites", metavar="FILE",
                    help="one invite token per line; if set, hellos must "
                         "carry a listed token (friends-phase gate)")
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s")
    if args.invites:
        with open(args.invites) as f:
            invites = {ln.strip() for ln in f if ln.strip()}
        log.info("invite gating: %d token(s)", len(invites))
    async with websockets.serve(handle, args.host, args.port):
        log.info("relay listening on ws://%s:%d", args.host, args.port)
        await asyncio.Future()


if __name__ == "__main__":
    asyncio.run(main())

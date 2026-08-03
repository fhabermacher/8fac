# 8fac protocol v0

Three parties:

- **PC client** — initiates a code request (hotkey/CLI).
- **Phone** — holds TOTP secrets, approves with biometric, computes the code.
- **Relay** — a blind pipe. It routes opaque encrypted blobs between the two
  peers of a pairing and can never read secrets, codes, or service names.

## 1. Pairing

The PC generates and stores (`~/.config/8fac/pairing.json`), and shows as a QR
for the phone to scan:

```json
{
  "v": 0,
  "pair_id": "<16 random bytes, hex>",
  "key": "<32 random bytes, base64>",
  "relay": "wss://relay.8fac.com/ws"
}
```

- `pair_id` is the routing channel name. The relay sees it (it must route),
  but it carries no meaning beyond "these two belong together".
- `key` is the shared symmetric key. **The relay never sees it.** All payloads
  are NaCl `secretbox` (XSalsa20-Poly1305) under this key.

One pairing = one (PC, phone) relationship. Multiple PCs → multiple pairings.

## 2. Transport

Both peers connect to the relay by WebSocket and send a plaintext hello:

```json
{"role": "pc" | "phone", "pair_id": "..."}
```

Every later frame is:

```json
{"blob": "<base64( nonce[24] || secretbox_ciphertext )>"}
```

The relay forwards `blob` frames verbatim to the opposite role of the same
`pair_id`. If the peer is offline it queues the frame for up to 60 s
(so a phone woken by push can connect and still receive the request).
Control frames from the relay: `{"type": "peer_offline"}` sent to a peer whose
counterpart is absent and whose frame has been queued.

### Wake-up push + endpoint mailbox

An always-on phone socket dies to Doze and network changes, so the real
transport is wake-on-demand:

1. The phone registers with a UnifiedPush distributor (e.g. the ntfy app)
   and obtains a push endpoint URL.
2. On every relay connect, the phone *deposits* that endpoint, sealed like
   any payload (`{"t": "endpoint", "url": ...}`), as
   `{"deposit": "<blob>"}`. The relay stores the latest deposit per pair
   (opaque, memory-only) and hands it to the PC on each PC connect.
   Deposits are exempt from the freshness bound (replay is harmless —
   worst case an obsolete endpoint gets a spurious wake).
3. `request.py` learns the endpoint into `pairing.json` (`"wake"`) and
   thereafter POSTs a contentless wake to it before each request. The
   woken phone connects, receives the queued request, proceeds as normal.

The push channel carries no secrets — an attacker with the endpoint URL can
only make the phone connect to the relay. The prototype phone-stub skips all
of this and just stays connected.

## 3. Payloads (inside secretbox)

PC → phone:

```json
{"t": "req", "id": "<uuid4>", "service": "github", "ts": 1722690000}
```

Phone → PC:

```json
{"t": "code", "id": "<same uuid>", "code": "123456", "ts": ...}
{"t": "deny", "id": "<same uuid>", "ts": ...}
```

Rules:

- Receiver drops any payload with `|now - ts| > 60 s` (replay bound; the
  random 24-byte nonce prevents ciphertext reuse, `ts` bounds replays).
- `id` correlates response to request; a PC ignores unknown `id`s.
- The phone displays `service` in the approval prompt — never approve blind.

## 4. Approval on the phone

- TOTP secrets are imported into the Android Keystore as HMAC-SHA1 keys with
  `setUserAuthenticationRequired(true)` — the key is unusable until
  BiometricPrompt succeeds. The raw secret is not readable back.
- Default flow: notification → tap → fingerprint → compute → reply.

### Auto-accept window (short-circuit)

The user may arm a time-boxed auto-accept, from the app or a widget:

- Arming always costs one fingerprint.
- Scope: one service (default) or all services (loud, explicit option).
- Duration: 5 min default, hard max 15 min.
- While armed: a persistent countdown notification with a "stop" action;
  every auto-approved request is logged and shown when the window ends.
- Keystore note: auto-accept uses `setUserAuthenticationValidityDurationSeconds`
  (auth-bound key window) rather than exporting secrets.

## 5. Threat model (v0, honest)

- Relay compromise: attacker sees pair_ids, timing, blob sizes. No secrets,
  no codes, no service names. Can drop/delay traffic (DoS only).
- Stolen pairing QR / pairing.json: attacker can *request* codes; the phone
  still gates every code behind biometric + a visible prompt. Re-pair to evict.
- Compromised PC: attacker gets codes the user approves — equivalent to any
  2FA entered on that PC. Auto-accept widens this to the armed window.
- Not phishing-resistant (inherent to TOTP). For passkey-enabled sites,
  prefer passkeys.

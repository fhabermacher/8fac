# 8fac — push-style 2FA on your own terms

One keyboard shortcut on the PC, one tap + one fingerprint on the phone, and
the TOTP code lands in your clipboard (or gets typed for you). TOTP secrets
never leave the phone; the relay is a blind pipe that sees only encrypted
blobs. See [PROTOCOL.md](PROTOCOL.md) for the design and threat model.

```
PC (hotkey: request.py) ──E2E blob──▶ relay.py ──E2E blob──▶ phone
        ◀────── 6-digit code, valid 30 s ──────  (fingerprint gates the key)
```

## Status

- **Working today** (tested end-to-end): `relay.py`, `pair.py`, `request.py`,
  and `stub.py` — a phone *emulator* so the whole loop runs without the app.
- **Skeleton**: `android/` — Kotlin app with the real security architecture:
  Keystore-imported HMAC keys (per-use biometric + OS-capped auth-window
  aliases for auto-accept), BiometricPrompt CryptoObject approval, E2E
  secretbox, otpauth:// QR import, manual fallback codes screen, reconnecting
  OkHttp relay client. Not yet device-tested — open in Android Studio.

## Install

**PC (Linux):** `./install.sh` — sets up Python, asks which global hotkey
you want (default `<Super>F9`), registers it (GNOME; prints instructions
for other DEs). The hotkey opens a picker (type-to-filter, recent services
as buttons), then the approved code is typed into the focused field
(X11/XTEST, xdotool/wtype fallback) or copied to the clipboard.

**PC (Windows):** `powershell -ExecutionPolicy Bypass -File install.ps1` —
same flow; the hotkey (default `Ctrl+Alt+8`) lives on a Start-Menu
shortcut, codes are typed via SendInput. macOS works too (osascript
typing); bind `pc/8fac-hotkey.sh` with your tool of choice.

**Phone (Android):** sideload the APK from
[Releases](https://github.com/fhabermacher/8fac/releases). For automatic
updates, add this repo to [Obtainium](https://github.com/ImranR98/Obtainium).
Not on the Play Store (closed-testing requirements make it disproportionate
for a personal project); F-Droid inclusion is possible later if demand
appears. Also install [ntfy](https://ntfy.sh) so a sleeping phone can be
woken.

## Quickstart (test everything on one machine)

```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt

.venv/bin/python relay.py &                       # 1. the blind pipe
.venv/bin/python pair.py --relay ws://127.0.0.1:8443   # 2. create pairing (+ QR)
echo '{"github": "JBSWY3DPEHPK3PXP"}' > ~/.config/8fac/secrets.json
.venv/bin/python stub.py                          # 3. "phone" (separate terminal)
.venv/bin/python request.py github                # 4. bind this to your hotkey
```

`stub.py` prompts per request: `y` approve, `n` deny, `a` auto-accept this
service 5 min, `A` auto-accept everything 5 min. `request.py --type` types the
code into the focused window (xdotool/wtype) instead of clipboard.

## Files

| Path | What |
|---|---|
| `PROTOCOL.md` | pairing, E2E crypto, frames, auto-accept, threat model |
| `relay.py` | blind WebSocket relay (~100 lines, holds no keys) |
| `eightfac/` | shared lib: RFC 6238 TOTP, secretbox envelope, config |
| `pair.py` / `request.py` | PC side: create pairing QR / request a code |
| `stub.py` | phone emulator for end-to-end testing |
| `testsite.py` | pretend third-party service: otpauth QR enrollment + code verification, so the full chain can be rehearsed with zero real accounts |
| `android/` | Kotlin app skeleton (Android Studio project) |

## Non-negotiables before real use

1. **Manual fallback on the phone.** The app must always display codes
   locally (like any authenticator) so a dead relay is an inconvenience,
   never a lockout. Not built yet — build it before migrating any account.
2. **TLS on the relay** (`wss://`) once it leaves localhost. E2E encryption
   protects the payloads regardless, but don't leak pair_ids/timing plainly.
3. **Keep backup codes** for every account you move onto 8fac.
4. Production push = UnifiedPush/FCM wake-up, not the always-on socket the
   prototype uses.

## Tests

```bash
.venv/bin/python -c "from eightfac.totp import totp; \
  assert totp('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ', at=59, digits=8) == '94287082'; \
  print('RFC 6238 ok')"
```

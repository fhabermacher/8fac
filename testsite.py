#!/usr/bin/env python3
"""A pretend third-party service for testing 8fac without any real account.

Acts like a site's 2FA enrollment + login:

1. Generates a fresh random secret and shows it as a standard
   otpauth:// QR — scan it with the 8fac app ("Add secret"), exactly as
   you would GitHub's QR.
2. Then behaves like the site's login form: paste/type codes and it
   verifies them (±1 time-step drift window, like real servers).

So the full rehearsal is:
    testsite.py            → scan QR with phone
    request.py testsite    → approve on phone, code lands on PC
    paste code here        → "ACCEPTED" proves the whole chain
"""
import base64
import secrets
import sys
import time

from eightfac.totp import totp, seconds_remaining

SERVICE = "testsite"


def main():
    secret = base64.b32encode(secrets.token_bytes(20)).decode().rstrip("=")
    uri = (f"otpauth://totp/{SERVICE}:florian?secret={secret}"
           f"&issuer={SERVICE}&algorithm=SHA1&digits=6&period=30")
    try:
        import qrcode
        qr = qrcode.QRCode(border=1)
        qr.add_data(uri)
        qr.print_ascii(invert=True)
    except ImportError:
        pass
    print(f"enrollment URI: {uri}\n")
    print("Scan with the 8fac app, then enter codes to verify "
          "(empty line quits).\n")

    while True:
        try:
            entered = input(f"[{seconds_remaining():2d}s left] code: ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if not entered:
            break
        now = time.time()
        ok = any(totp(secret, at=now + drift * 30) == entered
                 for drift in (-1, 0, 1))
        print("ACCEPTED ✓\n" if ok else "REJECTED ✗\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())

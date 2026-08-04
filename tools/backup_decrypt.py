#!/usr/bin/env python3
"""Decrypt an 8fac phone backup (Downloads/8fac-backup.json) and print the
otpauth:// URIs — optionally as QRs to re-enroll a replacement phone.

  tools/backup_decrypt.py 8fac-backup.json [--qr]
"""
import argparse
import base64
import getpass
import json
import sys

import nacl.pwhash
import nacl.secret


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("backup")
    ap.add_argument("--qr", action="store_true", help="print enrollment QRs")
    args = ap.parse_args()

    o = json.load(open(args.backup))
    assert o["v"] == 0 and o["kdf"] == "argon2id13", "unknown backup format"
    salt = base64.b64decode(o["salt"])
    raw = base64.b64decode(o["blob"])

    pw = getpass.getpass("backup passphrase: ").encode()
    key = nacl.pwhash.argon2id.kdf(
        nacl.secret.SecretBox.KEY_SIZE, pw, salt,
        opslimit=o["ops"], memlimit=o["mem"])
    try:
        plain = nacl.secret.SecretBox(key).decrypt(raw)
    except Exception:
        raise SystemExit("wrong passphrase (or corrupted backup)")

    uris = json.loads(plain)
    for uri in uris:
        print(uri)
        if args.qr:
            try:
                import qrcode
                q = qrcode.QRCode(border=1)
                q.add_data(uri)
                q.print_ascii(invert=True)
            except ImportError:
                print("(pip install qrcode for QRs)", file=sys.stderr)
    print(f"\n{len(uris)} secret(s) recovered", file=sys.stderr)


if __name__ == "__main__":
    main()

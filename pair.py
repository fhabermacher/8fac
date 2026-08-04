#!/usr/bin/env python3
"""Create a PC↔phone pairing and show it as a QR for the phone to scan."""
import argparse
import json

from eightfac import config


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--relay", default="ws://127.0.0.1:8443",
                    help="relay WebSocket URL the phone should use")
    ap.add_argument("--invite", help="invite token, if the relay requires one")
    args = ap.parse_args()

    pairing = config.create_pairing(args.relay, args.invite)
    print(f"pairing written to {config.PAIRING}\n")
    payload = json.dumps(pairing, separators=(",", ":"))
    try:
        import qrcode
        qr = qrcode.QRCode(border=1)
        qr.add_data(payload)
        qr.print_ascii(invert=True)
        print("\nScan with the 8fac phone app (or copy the JSON below).")
    except ImportError:
        print("(pip install qrcode for a scannable QR)")
    print(payload)


if __name__ == "__main__":
    main()

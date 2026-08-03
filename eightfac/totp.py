"""RFC 6238 TOTP — the whole algorithm, no dependencies."""
import base64
import hmac
import struct
import time


def totp(secret_b32: str, at: float | None = None, digits: int = 6,
         period: int = 30, algo: str = "sha1") -> str:
    s = secret_b32.strip().replace(" ", "").upper()
    key = base64.b32decode(s + "=" * (-len(s) % 8))
    counter = int((time.time() if at is None else at) // period)
    mac = hmac.new(key, struct.pack(">Q", counter), algo).digest()
    offset = mac[-1] & 0x0F
    code = struct.unpack(">I", mac[offset:offset + 4])[0] & 0x7FFFFFFF
    return str(code % 10 ** digits).zfill(digits)


def seconds_remaining(period: int = 30) -> int:
    return period - int(time.time()) % period

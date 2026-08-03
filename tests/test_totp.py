from eightfac.totp import totp

# RFC 6238 Appendix B (secret "12345678901234567890", SHA1, 8 digits)
B32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
VECTORS = [(59, "94287082"), (1111111109, "07081804"),
           (1234567890, "89005924"), (2000000000, "69279037"),
           (20000000000, "65353130")]


def test_rfc6238_vectors():
    for t, want in VECTORS:
        assert totp(B32, at=t, digits=8) == want


def test_six_digits_and_padding():
    code = totp("JBSWY3DPEHPK3PXP", at=59)
    assert len(code) == 6 and code.isdigit()


def test_unpadded_and_spaced_secrets():
    assert totp("JBSW Y3DP EHPK 3PXP", at=59) == totp("JBSWY3DPEHPK3PXP", at=59)

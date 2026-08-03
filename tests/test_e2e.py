"""Full-chain test: relay + phone stub (--yes) + request, all subprocesses."""
import json
import os
import socket
import subprocess
import sys
import time
from pathlib import Path

from eightfac.totp import totp

ROOT = Path(__file__).resolve().parent.parent
SECRET = "JBSWY3DPEHPK3PXP"


def _free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def _wait_port(port, timeout=10):
    deadline = time.time() + timeout
    while time.time() < deadline:
        with socket.socket() as s:
            if s.connect_ex(("127.0.0.1", port)) == 0:
                return
        time.sleep(0.1)
    raise TimeoutError(f"port {port} never opened")


def test_full_chain(tmp_path):
    port = _free_port()
    env = {**os.environ, "EIGHTFAC_CONF": str(tmp_path)}
    procs = []
    try:
        procs.append(subprocess.Popen(
            [sys.executable, ROOT / "relay.py", "--port", str(port)]))
        _wait_port(port)
        subprocess.run(
            [sys.executable, ROOT / "pair.py",
             "--relay", f"ws://127.0.0.1:{port}"],
            env=env, check=True, capture_output=True)
        (tmp_path / "secrets.json").write_text(json.dumps({"e2e": SECRET}))
        procs.append(subprocess.Popen(
            [sys.executable, ROOT / "stub.py", "--yes"],
            env=env, stdin=subprocess.DEVNULL))
        time.sleep(1)  # let the stub register with the relay
        out = subprocess.run(
            [sys.executable, ROOT / "request.py", "e2e"],
            env=env, capture_output=True, text=True, timeout=30)
        code = out.stdout.strip().splitlines()[-1]
        assert code == totp(SECRET), (out.stdout, out.stderr)
    finally:
        for p in procs:
            p.terminate()

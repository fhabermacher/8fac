# Deploying the relay

The relay is one Python process, no database, no state worth backing up.
It binds localhost; TLS comes from your reverse proxy / Cloudflare.

## Server setup (as root, once)

```bash
useradd -r -m -d /opt/8fac 8fac
git clone https://github.com/fhabermacher/8fac /opt/8fac/src
ln -s /opt/8fac/src/relay.py /opt/8fac/relay.py
python3 -m venv /opt/8fac/.venv
/opt/8fac/.venv/bin/pip install websockets
cp /opt/8fac/src/deploy/8fac-relay.service /etc/systemd/system/
systemctl enable --now 8fac-relay
```

## Reverse proxy

Caddy (TLS automatic; if Cloudflare terminates TLS, plain :80 works too):

```
8fac.example.com {
    reverse_proxy 127.0.0.1:8443
}
```

nginx:

```
location / {
    proxy_pass http://127.0.0.1:8443;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 120s;
}
```

## Cloudflare

- Add a proxied (orange-cloud) A/CNAME record for the subdomain.
- WebSockets are proxied by default on all plans; no setting needed.
- Note: Cloudflare closes idle WebSockets after ~100 s; the phone client
  pings every 30 s, which keeps it open.

## Re-pair

On the PC:

```bash
python pair.py --relay wss://8fac.example.com
```

and re-scan the QR on the phone (Pair with PC). `wss://` — never `ws://` —
once off localhost.

#!/usr/bin/env bash
# A user-owned HAProxy listener accepts HTTP and TLS on the same public port.
set -euo pipefail
umask 077

DIR="${DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
if [[ -f "$DIR/.env" ]]; then
  set -a
  source "$DIR/.env"
  set +a
fi
SOA_PROXY_PORT="${SOA_PROXY_PORT:?Set SOA_PROXY_PORT to enable the shared listener}"
SOA_PROXY_REDIRECT_PORT="${SOA_PROXY_REDIRECT_PORT:-61814}"
SOA_HTTPS_PORT="${SOA_HTTPS_PORT:-61813}"
HAPROXY_BIN="${HAPROXY_BIN:-$HOME/.local/bin/haproxy}"
for port in "$SOA_PROXY_PORT" "$SOA_PROXY_REDIRECT_PORT" "$SOA_HTTPS_PORT"; do
  [[ "$port" =~ ^[0-9]+$ ]] && ((port > 1024 && port < 65536)) || {
    echo "Invalid proxy/backend port: $port" >&2; exit 1;
  }
done
[[ "$SOA_PROXY_PORT" != "$SOA_HTTPS_PORT" && "$SOA_PROXY_PORT" != "$SOA_PROXY_REDIRECT_PORT" && "$SOA_HTTPS_PORT" != "$SOA_PROXY_REDIRECT_PORT" ]] || {
  echo 'Proxy and backend ports must be distinct' >&2; exit 1;
}
[[ -x "$HAPROXY_BIN" ]] || { echo "HAProxy is missing: $HAPROXY_BIN" >&2; exit 1; }

mkdir -p "$DIR/logs"
config="$DIR/haproxy.cfg"
cat > "$config" <<CFG
global
    maxconn 256
    nbthread 1

defaults
    mode tcp
    timeout connect 5s
    timeout client 60s
    timeout server 60s

frontend shared_port
    bind 0.0.0.0:${SOA_PROXY_PORT}
    tcp-request inspect-delay 5s
    tcp-request content accept if { req.ssl_hello_type 1 }
    tcp-request content accept if HTTP
    tcp-request content reject if WAIT_END
    use_backend wildfly_tls if { req.ssl_hello_type 1 }
    default_backend http_redirect

backend wildfly_tls
    server wildfly 127.0.0.1:${SOA_HTTPS_PORT}

backend http_redirect
    server redirect 127.0.0.1:${SOA_PROXY_REDIRECT_PORT}

frontend redirect_handler
    bind 127.0.0.1:${SOA_PROXY_REDIRECT_PORT}
    mode http
    # Keep Host (including its port), path and query for SSH tunnels as well.
    http-request redirect scheme https code 308
CFG

"$HAPROXY_BIN" -c -f "$config"
[[ "${1:-start}" != check ]] || exit 0
old_pid=""
if [[ -f "$DIR/haproxy.pid" ]]; then
  read -r old_pid < "$DIR/haproxy.pid"
  if [[ ! "$old_pid" =~ ^[0-9]+$ ]] || [[ "$(ps -p "$old_pid" -o comm= 2>/dev/null || true)" != *haproxy ]]; then
    old_pid=""
  fi
fi
args=(-D -f "$config" -p "$DIR/haproxy.pid")
[[ -z "$old_pid" ]] || args+=(-sf "$old_pid")
"$HAPROXY_BIN" "${args[@]}" >> "$DIR/logs/haproxy.log" 2>&1
echo "HAProxy serves HTTP redirects and TLS on port $SOA_PROXY_PORT"

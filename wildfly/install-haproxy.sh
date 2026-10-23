#!/usr/bin/env bash
# Install a private HAProxy binary; TLS remains terminated by WildFly.
set -euo pipefail
version=3.4.7
prefix="${SOA_PROXY_PREFIX:-$HOME/.local}"
case "$(uname -s)" in
  FreeBSD) builder=gmake; target=freebsd ;;
  Linux) builder=make; target=linux-glibc ;;
  *) echo 'Build HAProxy for this OS and set HAPROXY_BIN to its path.' >&2; exit 1 ;;
esac
mkdir -p "$prefix/src" "$prefix/bin"
cd "$prefix/src"
curl --fail --location --output "haproxy-$version.tar.gz" \
  "https://www.haproxy.org/download/3.4/src/haproxy-$version.tar.gz"
tar -xzf "haproxy-$version.tar.gz"
cd "haproxy-$version"
"$builder" -j2 TARGET="$target"
install -m 755 haproxy "$prefix/bin/haproxy"
"$prefix/bin/haproxy" -v

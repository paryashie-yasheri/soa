#!/usr/bin/env bash
# Build the private HAProxy with TLS for the Lab 3 gateway.
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
"$builder" clean
"$builder" -j2 TARGET="$target" USE_OPENSSL=1
./haproxy -vv | grep -F ' +SSL ' >/dev/null
staged=$(mktemp "$prefix/bin/haproxy.XXXXXX")
trap 'rm -f "$staged"' EXIT
install -m 755 haproxy "$staged"
mv -f "$staged" "$prefix/bin/haproxy"
"$prefix/bin/haproxy" -v

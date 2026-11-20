#!/usr/bin/env bash
set -euo pipefail
host=${1:-helios}
echo "Connecting to $host to identify the platform..."
platform_name=$(ssh -o ConnectTimeout=20 "$host" uname -sm)
case "$platform_name" in
  'FreeBSD amd64') platform=freebsd_amd64 ;;
  'Linux x86_64') platform=linux_amd64 ;;
  'Linux aarch64') platform=linux_arm64 ;;
  *) echo "Could not identify a supported platform on $host" >&2; exit 1 ;;
esac
file="$(dirname "$0")/build/consul/$platform/consul"
[[ -x "$file" ]] || { echo "Run ./download-consul.sh $platform first" >&2; exit 1; }
echo "Preparing $host:.local/bin..."
ssh -o ConnectTimeout=20 "$host" 'mkdir -p "$HOME/.local/bin"'
echo "Sending Consul to $host..."
rsync -e "ssh -o ConnectTimeout=20" --progress -pt --checksum -- "$file" "$host:.local/bin/consul"
echo "Checking the installed Consul version..."
ssh -o ConnectTimeout=20 "$host" '"$HOME/.local/bin/consul" version'

#!/usr/bin/env bash
set -euo pipefail
version=1.21.5
platform=${1:-freebsd_amd64}
case "$platform" in freebsd_amd64|linux_amd64|linux_arm64) ;; *) echo "Unsupported platform: $platform" >&2; exit 1 ;; esac
dir="$(dirname "$0")/build/consul/$platform"
mkdir -p "$dir"
cd "$dir"
work=$(mktemp -d ./download.XXXXXX)
trap 'rm -rf "$work"' EXIT
archive="consul_${version}_${platform}.zip"
base="https://releases.hashicorp.com/consul/$version"
(
  cd "$work"
  for file in "$archive" "consul_${version}_SHA256SUMS"; do
    echo "Downloading $base/$file"
    curl -fSL --retry 2 "$base/$file" -o "$file"
  done
  awk -v name="$archive" '$2 == name {print; found=1} END {exit !found}' "consul_${version}_SHA256SUMS" > checksum
  sha256sum -c checksum
  unzip -q "$archive" consul
  chmod 755 consul
  mv consul ../consul
)
echo "Ready: $PWD/consul"

#!/usr/bin/env bash
# Resolve a pinned JDK and Maven without changing the user's global toolchain.
set -euo pipefail
case "${1:-}" in java|javac|jcmd|jfr|mvn|github-env) tool=$1; shift;; *) echo "Use java, javac, jcmd, jfr, mvn or github-env" >&2; exit 2;; esac
case "$(uname -s)" in Darwin) os=mac;; Linux) os=linux;; *) echo "Use macOS or Linux" >&2; exit 2;; esac
case "$(uname -m)" in arm64|aarch64) arch=aarch64;; x86_64) arch=x64;; *) echo "Unsupported architecture" >&2; exit 2;; esac
cache="${XDG_CACHE_HOME:-$HOME/.cache}/parcel-study"
mkdir -p "$cache"
stage=$(mktemp -d "$cache/.download.XXXXXX")
trap 'rm -rf "$stage"' EXIT

install_archive() {
  local dir=$1 probe=$2 url=$3 checksum_url=$4 algorithm=$5
  [[ -x "$cache/$dir/$probe" ]] && return 0
  local archive="$stage/archive" checksum="$stage/checksum"
  curl -fsSL --connect-timeout 20 --retry 3 "$url" -o "$archive"
  curl -fsSL --connect-timeout 20 --retry 3 "$checksum_url" -o "$checksum"
  local expected actual
  expected=$(awk 'NR==1 {print $1}' "$checksum")
  actual=$(shasum -a "$algorithm" "$archive" | awk '{print $1}')
  [[ "$expected" == "$actual" ]] || { echo "Checksum mismatch for $dir" >&2; exit 1; }
  mkdir "$stage/$dir"
  tar -xzf "$archive" -C "$stage/$dir" --strip-components=1
  [[ -x "$stage/$dir/$probe" ]]
  # A concurrent launcher can finish installing the same verified archive first.
  mv "$stage/$dir" "$cache/" 2>/dev/null || [[ -x "$cache/$dir/$probe" ]]
}

jdk="temurin-25.0.5.1+2-$os-$arch"
release="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5.1%2B2"
url="$release/OpenJDK25U-jdk_${arch}_${os}_hotspot_25.0.5.1_2.tar.gz"
jdk_home="$cache/$jdk"
probe=bin/java
if [[ "$os" == mac ]]; then jdk_home="$jdk_home/Contents/Home"; probe=Contents/Home/bin/java; fi
install_archive "$jdk" "$probe" "$url" "$url.sha256.txt" 256
export JAVA_HOME="$jdk_home" PATH="$jdk_home/bin:$PATH"

if [[ "$tool" == mvn || "$tool" == github-env ]]; then
  maven=apache-maven-3.10.2
  url="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.2/$maven-bin.tar.gz"
  install_archive "$maven" bin/mvn "$url" "$url.sha512" 512
  if [[ "$tool" == github-env ]]; then
    printf 'JAVA_HOME=%s\n' "$JAVA_HOME" >> "${GITHUB_ENV:?Run github-env inside GitHub Actions}"
    printf '%s\n' "$JAVA_HOME/bin" "$cache/$maven/bin" >> "${GITHUB_PATH:?}"
    exit 0
  fi
  executable="$cache/$maven/bin/mvn"
else
  executable="$jdk_home/bin/$tool"
fi
rm -rf "$stage"
trap - EXIT
exec "$executable" "$@"

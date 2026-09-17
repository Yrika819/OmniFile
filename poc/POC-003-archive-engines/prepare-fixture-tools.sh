#!/bin/zsh
set -euo pipefail
# POC-ONLY — NOT PRODUCTION AUTHORITY
ROOT="${0:A:h}"
mkdir -p "$ROOT/vendor/7zip" "$ROOT/vendor"
if [[ ! -x "$ROOT/vendor/7zip/7zz" ]]; then
  curl -fL --retry 3 https://www.7-zip.org/a/7z2603-mac.tar.xz -o "$ROOT/vendor/7zip/7z2603-mac.tar.xz"
  tar -xJf "$ROOT/vendor/7zip/7z2603-mac.tar.xz" -C "$ROOT/vendor/7zip"
fi
if [[ ! -d "$ROOT/vendor/junrar-src/.git" ]]; then
  git clone --depth 1 --branch v8.1.1 https://github.com/junrar/junrar.git "$ROOT/vendor/junrar-src"
fi
printf 'Fixture tools prepared. They are PoC inputs only and are ignored by Git.\n'
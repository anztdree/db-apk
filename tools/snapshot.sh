#!/usr/bin/env bash
# Ambil ulang 2 zip besar mirror server asli (di-gitignore — tidak dibawa git).
# SHA256 & ukuran TER-PIN dari probe 2026-10-31 (README §2.9) — diverifikasi saat unduh.
set -euo pipefail
DEST="$(dirname "$0")/../local-server/assets/server/www/bs/upgrade"
mkdir -p "$DEST"
fetch() { # $1=nama $2=size $3=sha256
  local f="$DEST/$1"
  if [ -f "$f" ] && [ "$(stat -c%s "$f")" = "$2" ] && [ "$(sha256sum "$f" | cut -d' ' -f1)" = "$3" ]; then
    echo "OK (sudah ada): $1"; return 0
  fi
  echo "unduh $1 ..."
  curl -sL --retry 3 -o "$f" "https://dragonh5cdn.popoh5.com/bs/upgrade/$1"
  local sz; sz=$(stat -c%s "$f"); local sh; sh=$(sha256sum "$f" | cut -d' ' -f1)
  if [ "$sz" != "$2" ] || [ "$sh" != "$3" ]; then
    echo "GAGAL verifikasi $1 (size=$sz sha=$sh)"; rm -f "$f"; exit 1
  fi
  echo "OK: $1 ($sz B, sha $sh)"
}
fetch base.zip 31015076 31dedf27481bf59f4ddac3c67748e45371195b47ac2a822cc1e121fba8c6259a
fetch all.zip  19385098 6b838206bfa148ed85c1e8529b20facca3518f84bcd5e462512ebc7667ff85cf

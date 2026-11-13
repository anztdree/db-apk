#!/usr/bin/env bash
# =============================================================
# RESTORE db-apk dari backup GitHub — jalankan setiap kali sandbox di-reset.
#
# Pakai:
#   bash tools/restore.sh [TOKEN_GITHUB]
#
# Sumber token (urutan):
#   1. argumen ke-1
#   2. env DB_APK_TOKEN
#   3. file /home/z/my-project/db-apk-backup/token (chmod 600 — TIDAK ikut repo)
#
# Catatan:
#   - Clone berisi pointer LFS (cepat). Objek LFS diambil on-demand.
#   - Perlu APK dasar untuk build? jalankan di dalam repo:
#       git lfs pull --include "releases/*"
#   - Versi sebelumnya diselamatkan di docs/build-scripts/ (0.5.7, 0.5.8).
# =============================================================
set -euo pipefail

TOKEN="${1:-${DB_APK_TOKEN:-}}"
if [ -z "$TOKEN" ] && [ -f /home/z/my-project/db-apk-backup/token ]; then
  TOKEN="$(tr -d '[:space:]' < /home/z/my-project/db-apk-backup/token)"
fi
if [ -z "$TOKEN" ]; then
  echo "token tidak ada — sediakan argumen / env DB_APK_TOKEN / file /home/z/my-project/db-apk-backup/token"
  exit 1
fi

DEST="${DEST:-/home/z/db-apk}"
if [ -d "$DEST/.git" ]; then
  echo "$DEST sudah ada — restore dihentikan (hapus manual bila ingin ulang)"
  exit 0
fi

GIT_LFS_SKIP_SMUDGE=1 git clone "https://x-access-token:${TOKEN}@github.com/anztdree/db-apk.git" "$DEST"
echo "restore selesai: $DEST"
echo " — pointer LFS saja (cepat); APK dasar bila perlu: git lfs pull --include 'releases/*'"

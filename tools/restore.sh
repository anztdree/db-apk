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

# git-lfs belum tentu ada di sandbox — pakai salinan di folder backup bila ada
if ! command -v git-lfs >/dev/null 2>&1; then
  if [ -x /home/z/my-project/db-apk-backup/git-lfs ]; then
    cp /home/z/my-project/db-apk-backup/git-lfs /usr/local/bin/git-lfs 2>/dev/null \
      || cp /home/z/my-project/db-apk-backup/git-lfs "$HOME/.local/bin/git-lfs" 2>/dev/null \
      || true
  fi
fi
if command -v git-lfs >/dev/null 2>&1; then
  git -C "$DEST" lfs install --local
  git -C "$DEST" config --local lfs.url "https://x-access-token:${TOKEN}@github.com/anztdree/db-apk.git/info/lfs"
  echo " — LFS siap; objek besar bila perlu: cd $DEST && git lfs pull --include 'releases/*'"
else
  echo " — PERINGATAN: git-lfs tidak tersedia; commit yang menyentuh file LFS butuh binary"
  echo "   (salinan ada di /home/z/my-project/db-apk-backup/git-lfs — letakkan di PATH lalu jalankan)"
fi
echo "restore selesai: $DEST"

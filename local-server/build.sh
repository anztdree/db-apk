#!/usr/bin/env bash
# =============================================================
# Build APK-LSF 0.3.0 — MIRROR PENUH SERVER ASLI + FLOATING BISA DIGESER
#
# Dasar: releases/APK-LSF-0.1.0.apk (sudah memuat manifest provider +
# endpoint 127.0.0.1:8080 + dex game asli). Patch yang diterapkan
# (TANPA apktool — dex game & resource tidak disentuh):
#   1) javac(ECJ) + d8  -> classes2.dex baru (kode server lokal)
#   2) ganti assets/server/dashboard.html
#   3) HAPUS assets/server/payload/** versi lama (4 file minimal)
#   4) tambah assets/server/www/**  — MIRROR PENUH server asli (31 file,
#      ±56 MB: setting bin, H5 Egret + 15 JS, resource properties,
#      base.version/resource.version/upgrade.json/size.json, base.zip,
#      all.zip, 4 zip incremental, setting bin lzceshi) yang diekstrak
#      OTOMATIS ke Android/data/<paket>/server/www/ saat aplikasi hidup
#   5) buang tanda tangan lama -> zipalign + sign (keystore tetap)
#
# Prasyarat: tools/snapshot.sh sudah jalan (zip besar tersedia).
# =============================================================
set -euo pipefail

REPO="${REPO:-/home/z/db-apk}"
TOOLS="${TOOLS:-/home/z/tools2}"                       # android.jar + r8.jar
UBER="${UBER:-$REPO/tools/uber-apk-signer-1.3.0.jar}"
ECJ="${ECJ:-$REPO/tools/ecj-3.33.0.jar}"               # compiler Java 8 (sandbox tanpa JDK)
KS="${KS:-$REPO/keystore/lsf.keystore}"
KS_ALIAS="${KS_ALIAS:-lsf}"
KS_PASS="${KS_PASS:-lsf-local-2025}"
BASE="${BASE:-$REPO/releases/APK-LSF-0.1.0.apk}"       # APK dasar (release 0.1.0)
SRC="$REPO/local-server/src/main/java"
ASSETS="$REPO/local-server/assets"
BUILD="$REPO/build"
DIST="$REPO/dist"
OUT="$REPO/releases/APK-LSF-0.3.0.apk"
MIN_API="${MIN_API:-21}"

WWW="$ASSETS/server/www"
if [ ! -f "$WWW/bs/upgrade/all.zip" ] || [ ! -f "$WWW/bs/upgrade/base.zip" ]; then
  echo "mirror www/ belum lengkap — jalankan: bash tools/snapshot.sh"; exit 1
fi

echo "[1/8] ECJ compile (sandbox tanpa javac) ..."
rm -rf "$BUILD/classes" "$BUILD/dex" "$BUILD/stage"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/stage"
find "$SRC" -name '*.java' | sort > "$BUILD/sources.txt"
java -jar "$ECJ" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$TOOLS/android.jar" -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "[2/8] d8 -> classes2.dex ..."
mapfile -t CLASS_FILES < <(find "$BUILD/classes" -name '*.class')
java -cp "$TOOLS/r8.jar" com.android.tools.r8.D8 --release \
  --min-api "$MIN_API" --lib "$TOOLS/android.jar" \
  --output "$BUILD/dex" "${CLASS_FILES[@]}"
cp "$BUILD/dex/classes.dex" "$BUILD/stage/classes2.dex"

echo "[3/8] siapkan aset server (dashboard + mirror www/) ..."
mkdir -p "$BUILD/stage/assets/server"
cp "$ASSETS/server/dashboard.html" "$BUILD/stage/assets/server/dashboard.html"
cp -R "$WWW" "$BUILD/stage/assets/server/www"

echo "[4/8] salin APK dasar + buang tanda tangan lama + hapus payload lama ..."
cp "$BASE" "$BUILD/work.apk"
zip -d "$BUILD/work.apk" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/MANIFEST.MF' >/dev/null 2>&1 || true
zip -d "$BUILD/work.apk" 'assets/server/payload/*' >/dev/null 2>&1 || true

echo "[5/8] sisipkan classes2.dex + dashboard + mirror www/ ..."
(
  cd "$BUILD/stage"
  # shellcheck disable=SC2046
  zip -X -0 ../work.apk classes2.dex assets/server/dashboard.html \
    $(cd "$BUILD/stage" && find assets/server/www -type f | sort) >/dev/null
)

echo "[6/8] zipalign + sign (keystore tetap) ..."
rm -rf "$DIST"; mkdir -p "$DIST"
java -jar "$UBER" -a "$BUILD/work.apk" \
  --ks "$KS" --ksAlias "$KS_ALIAS" --ksPass "$KS_PASS" --ksKeyPass "$KS_PASS" \
  --out "$DIST"

echo "[7/8] verifikasi (dex game & config endpoint WAJIB identik dengan dasar) ..."
NEWAPK="$DIST/work-aligned-signed.apk"
echo "-- CRC di APK DASAR:"
unzip -v "$BASE" | grep -E "classes\.dex |config_BS\.properties|config\.properties" || true
echo "-- CRC di APK BARU (classes.dex/config harus sama; classes2.dex/dashboard/www = baru; payload lama = hilang):"
unzip -v "$NEWAPK" | grep -E "classes\.dex |config_BS\.properties|config\.properties|classes2\.dex|server/payload|server/www|dashboard" | head -45 || true
echo "-- payload lama masih ada? (harus kosong):"
unzip -l "$NEWAPK" | grep "server/payload" || echo "(bersih — payload lama sudah dihapus)"

echo "[8/8] terbitkan ..."
cp "$NEWAPK" "$OUT"
echo "SELESAI: $OUT"
ls -la "$OUT"
sha256sum "$OUT"

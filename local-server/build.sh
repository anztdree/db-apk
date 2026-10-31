#!/usr/bin/env bash
# =============================================================
# Build APK-LSF 0.1.0 (v2.8 — payload config asli diekstrak otomatis)
#
# Dasar: APK release sebelumnya (releases/APK-LSF-0.1.0.apk) yang SUDAH
# memuat manifest provider + endpoint 127.0.0.1:8080 + dex game asli.
# Patch yang diterapkan (TANPA apktool — dex game & resource tidak disentuh):
#   1) javac + d8  -> classes2.dex baru (kode server lokal)
#   2) ganti assets/server/dashboard.html
#   3) tambah assets/server/payload/**  — payload config asli yang diekstrak
#      OTOMATIS ke www/ saat aplikasi pertama jalan (koreksi pemilik v2.8)
#   4) buang tanda tangan lama -> zipalign + sign (keystore tetap)
# =============================================================
set -euo pipefail

REPO="${REPO:-/home/z/db-apk}"
TOOLS="${TOOLS:-/home/z/tools2}"                       # android.jar + r8.jar
UBER="${UBER:-$REPO/tools/uber-apk-signer-1.3.0.jar}"
ECJ="${ECJ:-$REPO/tools/ecj-3.33.0.jar}"               # compiler Java 8 (sandbox tanpa JDK)
KS="${KS:-$REPO/keystore/lsf.keystore}"
KS_ALIAS="${KS_ALIAS:-lsf}"
KS_PASS="${KS_PASS:-lsf-local-2025}"
BASE="${BASE:-$REPO/releases/APK-LSF-0.1.0.apk}"       # APK dasar (release sebelumnya)
SRC="$REPO/local-server/src/main/java"
ASSETS="$REPO/local-server/assets"
BUILD="$REPO/build"
DIST="$REPO/dist"
OUT="$REPO/releases/APK-LSF-0.1.0.apk"
MIN_API="${MIN_API:-21}"

echo "[1/7] ECJ compile (sandbox tanpa javac) ..."
rm -rf "$BUILD/classes" "$BUILD/dex" "$BUILD/stage"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/stage"
find "$SRC" -name '*.java' | sort > "$BUILD/sources.txt"
java -jar "$ECJ" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$TOOLS/android.jar" -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "[2/7] d8 -> classes2.dex ..."
mapfile -t CLASS_FILES < <(find "$BUILD/classes" -name '*.class')
java -cp "$TOOLS/r8.jar" com.android.tools.r8.D8 --release \
  --min-api "$MIN_API" --lib "$TOOLS/android.jar" \
  --output "$BUILD/dex" "${CLASS_FILES[@]}"
cp "$BUILD/dex/classes.dex" "$BUILD/stage/classes2.dex"

echo "[3/7] siapkan aset server (dashboard + payload) ..."
mkdir -p "$BUILD/stage/assets/server"
cp "$ASSETS/server/dashboard.html" "$BUILD/stage/assets/server/dashboard.html"
cp -R "$ASSETS/server/payload" "$BUILD/stage/assets/server/payload"

echo "[4/7] salin APK dasar + buang tanda tangan lama ..."
cp "$BASE" "$BUILD/work.apk"
zip -d "$BUILD/work.apk" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/MANIFEST.MF' >/dev/null 2>&1 || true

echo "[5/7] sisipkan classes2.dex + dashboard + payload ..."
(
  cd "$BUILD/stage"
  # shellcheck disable=SC2046
  zip -X ../work.apk classes2.dex assets/server/dashboard.html \
    $(find assets/server/payload -type f | sort) >/dev/null
)

echo "[6/7] zipalign + sign (keystore tetap) ..."
rm -rf "$DIST"; mkdir -p "$DIST"
java -jar "$UBER" -a "$BUILD/work.apk" \
  --ks "$KS" --ksAlias "$KS_ALIAS" --ksPass "$KS_PASS" --ksKeyPass "$KS_PASS" \
  --out "$DIST"

echo "[7/7] verifikasi (dex game & config endpoint WAJIB identik dengan dasar) ..."
NEWAPK="$DIST/work-aligned-signed.apk"
echo "-- CRC di APK DASAR:"
unzip -v "$BASE" | grep -E "classes\.dex |config_BS\.properties|config\.properties|classes2\.dex" || true
echo "-- CRC di APK BARU (classes.dex/config harus sama; classes2.dex/dashboard/payload = baru):"
unzip -v "$NEWAPK" | grep -E "classes\.dex |config_BS\.properties|config\.properties|classes2\.dex|server/payload|dashboard" || true

cp "$NEWAPK" "$OUT"
echo "SELESAI: $OUT"
sha256sum "$OUT"

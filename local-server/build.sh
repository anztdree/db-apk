#!/usr/bin/env bash
# =============================================================
# Build APK-LSF 0.3.2 — WEBSITE TANPA SWITCH MODE
#
# Dasar: releases/APK-LSF-0.3.1.apk (sudah memuat manifest provider +
# endpoint 127.0.0.1:8080 + dex game asli). Bila file dasar belum ada:
#   unduh https://github.com/anztdree/db-apk/releases/download/v0.3.1/APK-LSF-0.3.1.apk
#   (sha256 252984ce190d4e2ebbb9dd9693c9ca0d48b3521c48a02d98cac5d07f7602436d)
# Patch yang diterapkan (TANPA apktool — dex game & resource tidak disentuh):
#   1) javac(ECJ) + d8  -> classes2.dex baru (kode server lokal)
#   2) ganti assets/server/dashboard.html
#   3) tambah assets/server/www/** — MIRROR PENUH server asli (31 file, ±56 MB)
#      yang diekstrak OTOMATIS ke Android/data/<paket>/server/www/
#   4) buang tanda tangan lama -> zipalign + sign (keystore tetap)
#
# Koreksi 0.3.2 (umpan balik owner: "website pindah mode itu salah cuma
# menuhin tempat" + bukti baris "memuar…" tersisa di kotak log):
#   a) switch mode DIHAPUS dari SEMUA halaman web — dasbor 4000 TANPA kartu
#      mode besar, halaman root port game 8080 jadi status mini tanpa tombol.
#      Ganti mode cukup lewat tombol melayang di layar game (satu tempat).
#   b) endpoint POST /api/mode dihapus dari kedua port (tidak ada lagi
#      pemakainya — kontrak API bersih)
#   c) dasbor: mode tampil sebagai badge read-only di header; baris placeholder
#      "memuat…" di kotak log dibuang saat jawaban pertama masuk; bila dasbor
#      gagal dihubungi muncul pesan jelas, bukan "memuat…" menggantung
#   d) narasi mode di log dirampingkan: baris boot lebih pendek, tanpa iklan
#      "halaman 8080 berisi tombol ganti mode", baris tombol melayang 1 kalimat
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
BASE="${BASE:-$REPO/releases/APK-LSF-0.3.1.apk}"       # APK dasar (rilis 0.3.1)
SRC="$REPO/local-server/src/main/java"
ASSETS="$REPO/local-server/assets"
BUILD="$REPO/build"
DIST="$REPO/dist"
OUT="$REPO/releases/APK-LSF-0.3.2.apk"
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

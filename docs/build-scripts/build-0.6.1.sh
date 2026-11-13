#!/usr/bin/env bash
# =============================================================
# Build APK-LSF 0.6.1 — SUB JUDUL JALUR PER BARIS + JEJAK ASLI
# HTTPCANARY (nilai asli utuh, routing terpisah)
#
# Perintah owner (2026-11-13):
#   "sub judul pada log (Online / local) gk ada jelas bikin bingung"
#   "Log Emang saya campur Biar jelas current state yg jalan apa ..
#    jelas jelas isi log anda yg ngawur .."
#   "bkan masalah rekam aslinya dulu, kalau anda paham konsep
#    httpcanary capturre atau log akan jelas"
#   "log dari online nanti sebagai bahan diskusi untuk memastikan
#    apalah server local kita cacat dan tau harus kemana ..
#    server local kita gak asal bikin nama : local, alamat; local"
#
# Baru 0.6.1 (timeline campur TETAP SATU LAYAR — desain owner):
#   1) SUB JUDUL JALUR PER BARIS — field "m" (lokal/online) sudah ada di
#      JSON sejak 0.5.4 tapi renderRows dasbor MEMBUANGNYA. Kini: badge
#      [LOCAL] hijau / [ONLINE] kuning di depan kalimat (warna = badge
#      kepala), baris PERTAMA rincian = "Jalur : …", dan hasil Copy ikut
#      format human.log [ICON|JALUR].
#   2) human.log txt membawa tag jalur di kepala tiap baris:
#      [✅|LOCAL] / [📡·|ONLINE] (dulu hanya JSON yang bawa "m").
#   3) JEJAK ASLI HTTPCANARY — dulu 0.6.0: Capture + blok ISI baris
#      daftar server memakai teks HASIL tulis-ulang → nilai ASLI hilang
#      dari jejak (Gerbang tampil 127.0.0.1:8080 dua kali — komentar kode
#      tidak cocok perilaku; temuan owner "isi log anda yg ngawur").
#      Kini: teks ASLI SEBELUM tulis-ulang disimpan khusus (respAsli) —
#      REKAM (arah ←) + blok ISI menampilkan respons ASLI utuh dari
#      login.popoh5.com:610 (nilai asli apa adanya = bahan diskusi/
#      diagnosis); field Routing TERPISAH menyatakan apa yang diterima
#      klien (url = relay lokal, diteruskan ke url ASLI per entri) dan
#      menegaskan: tulis-ulang = routing, BUKAN sumber config.
#   4) BARIS RELAY NYEBUT JALUR — 📡 tersambung, ✅ loginGame, 🔌 tutup
#      sesi, 🎮 Play relay, ⚠️ upstream tidak terjangkau, ❌ Play berhenti
#      (pesan beda per jalur: LOCAL = isian manual kosong + Solusi isi
#      config/game-upstream.txt; ONLINE = tabel daftar server ASLI kosong)
#      — semuanya kini membawa field "Jalur : LOCAL — … / ONLINE — …".
#   5) Versi dasbor (StatusStore) naik 0.5.6 → 0.6.1 (dulu tertinggal).
#
# Dasar: releases/APK-LSF-0.5.9.apk (sha256 3e1bdcb7… — terbit).
#
# Patch yang diterapkan (TANPA apktool — dex game & resource tidak disentuh):
#   1) javac(ECJ) + d8  -> classes2.dex baru (kode server lokal)
#   2) ganti assets/server/dashboard.html (badge jalur + Jalur rincian + Copy)
#   3) mirror www/ + config-offline/ + QHinfo di-stage ulang (byte sama)
#   4) buang tanda tangan lama -> zipalign + sign (keystore tetap)
# =============================================================
set -euo pipefail

REPO="${REPO:-/home/z/db-apk}"
TOOLS="${TOOLS:-$REPO/tools}"
UBER="${UBER:-$REPO/tools/uber-apk-signer.jar}"
ECJ="${ECJ:-$REPO/tools/ecj-3.33.0.jar}"
KS="${KS:-$REPO/keystore/lsf.keystore}"
KS_ALIAS="${KS_ALIAS:-lsf}"
KS_PASS="${KS_PASS:-lsf-local-2025}"
BASE="${BASE:-$REPO/releases/APK-LSF-0.5.9.apk}"
LOGIN_PORT_QH="${LOGIN_PORT_QH:-8610}"
SRC="$REPO/local-server/src/main/java"
ASSETS="$REPO/local-server/assets"
BUILD="$REPO/build"
DIST="$REPO/dist"
OUT="$REPO/releases/APK-LSF-0.6.1.apk"
MIN_API="${MIN_API:-21}"
WWW="$ASSETS/server/www"

echo "[1/8] ECJ compile ..."
rm -rf "$BUILD/classes" "$BUILD/dex" "$BUILD/stage" "$BUILD/harness"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/stage"
find "$SRC" -name '*.java' | sort > "$BUILD/sources.txt"
ECJ_CP="$TOOLS/android.jar"
if [ -f "$TOOLS/sqlite-jdbc.jar" ]; then
  ECJ_CP="$TOOLS/android.jar:$TOOLS/sqlite-jdbc.jar"
fi
java -jar "$ECJ" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$ECJ_CP" -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "[1b] compile + jalankan harness JVM (bukti perilaku, tidak masuk APK) ..."
java -jar "$ECJ" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$BUILD/classes:$TOOLS/sqlite-jdbc.jar" -d "$BUILD/harness" \
  "$REPO/local-server/harness/HarnessSdk.java"
HR="$BUILD/harness-root-061"
rm -rf "$HR"
java -cp "$BUILD/classes:$BUILD/harness:$TOOLS/sqlite-jdbc.jar" HarnessSdk "$HR" | tail -3 || true

echo "[2/8] d8 -> classes2.dex (DbJdbc DISINGKIRKAN — hanya harness desktop) ..."
mapfile -t CLASS_FILES < <(find "$BUILD/classes" -name '*.class' ! -name 'DbJdbc.class' ! -path '*DbJdbc*')
java -cp "$TOOLS/r8.jar" com.android.tools.r8.D8 --release \
  --min-api "$MIN_API" --lib "$TOOLS/android.jar" \
  --output "$BUILD/dex" "${CLASS_FILES[@]}"
cp "$BUILD/dex/classes.dex" "$BUILD/stage/classes2.dex"

echo "[3/8] siapkan aset server ..."
mkdir -p "$BUILD/stage/assets/server"
cp "$ASSETS/server/dashboard.html" "$BUILD/stage/assets/server/dashboard.html"
cp -R "$WWW" "$BUILD/stage/assets/server/www"
if [ -d "$ASSETS/server/config-offline" ]; then
  cp -R "$ASSETS/server/config-offline" "$BUILD/stage/assets/server/config-offline"
else
  echo "PERINGATAN: paket config-offline/ tidak ada"
fi
printf 'http://127.0.0.1:%s' "$LOGIN_PORT_QH" > "$BUILD/stage/assets/QHinfo"

echo "[4/8] salin APK dasar + buang tanda tangan lama + payload lama + QHinfo lama ..."
cp "$BASE" "$BUILD/work.apk"
zip -d "$BUILD/work.apk" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/MANIFEST.MF' >/dev/null 2>&1 || true
zip -d "$BUILD/work.apk" 'assets/server/payload/*' >/dev/null 2>&1 || true
zip -d "$BUILD/work.apk" 'assets/QHinfo' >/dev/null 2>&1 || true
zip -d "$BUILD/work.apk" \
  'assets/server/www/bs/db/android/setting_BS_Android.bin' \
  'assets/server/www/lzceshi/native_setting/Android/setting_BS_Android.bin' \
  'assets/server/www/lzceshi/native_setting/Android/serversetting.json' \
  'assets/server/www/bs/resource/properties/serversetting.json' >/dev/null 2>&1 || true

echo "[5/8] sisipkan classes2.dex + dashboard + mirror www/ + config-offline + QHinfo ..."
(
  cd "$BUILD/stage"
  # shellcheck disable=SC2046
  zip -X -0 ../work.apk classes2.dex assets/QHinfo assets/server/dashboard.html \
    $(cd "$BUILD/stage" && find assets/server/www assets/server/config-offline -type f | sort) >/dev/null
)

echo "[6/8] zipalign + sign (keystore tetap) ..."
rm -f "$DIST/work-aligned-signed.apk"; mkdir -p "$DIST"
java -jar "$UBER" -a "$BUILD/work.apk" \
  --ks "$KS" --ksAlias "$KS_ALIAS" --ksPass "$KS_PASS" --ksKeyPass "$KS_PASS" \
  --out "$DIST"

echo "[7/8] verifikasi ..."
NEWAPK="$DIST/work-aligned-signed.apk"
echo "-- bukti 0.6.1 di dex (python byte-count — strings memangkas baris em-dash; harus >0):"
python3 - "$BUILD/dex/classes.dex" <<'PYEOF'
import sys
data = open(sys.argv[1], 'rb').read()
baru = [
    'jalur ONLINE',
    'server login lokal — jalur LOCAL',
    'respons ASLI (sebelum tulis-ulang)',
    'tulis-ulang = routing, BUKAN sumber config',
    'respons ASLI dari login.popoh5.com:610 (utuh — jejak capture)',
    'Play berhenti — jalur LOCAL',
    'Play berhenti — jalur ONLINE',
]
lama = [
    'respons daftar server (utuh — persis yang diterima klien)',
    'Rewrite  : ',
    'loginServer hasil rewrite setting bin',
    ' (passthrough → relay lokal)',
]
gagal = 0
for p in baru:
    n = data.count(p.encode('utf-8'))
    print('   BARU  [harus >0]  %3d  %s' % (n, p))
    if n == 0:
        gagal += 1
for p in lama:
    n = data.count(p.encode('utf-8'))
    print('   LAMA  [harus =0]  %3d  %s' % (n, p))
    if n != 0:
        gagal += 1
sys.exit(1 if gagal else 0)
PYEOF
# set -e: python keluar 1 = build GAGAL di sini (marker baru tidak lengkap /
# narasi lama masih ada) — APK tidak diterbitkan. Disiplin hati-hati manual.
echo "-- CRC penting (classes.dex game & config endpoint WAJIB identik dengan dasar):"
unzip -v "$BASE" | grep -E "classes\.dex |config_BS\.properties|config\.properties|QHinfo" || true
echo "-- CRC APK BARU:"
unzip -v "$NEWAPK" | grep -E "classes\.dex |config_BS\.properties|config\.properties|classes2\.dex|server/payload|dashboard|QHinfo" | head -20 || true
echo "-- isi QHinfo baru (harus http://127.0.0.1:8610):"
unzip -p "$NEWAPK" assets/QHinfo; echo
echo "-- badge jalur di dashboard baru (harus >0):"
unzip -p "$NEWAPK" assets/server/dashboard.html | grep -c "jtag" || true

echo "[8/8] terbitkan ..."
cp "$NEWAPK" "$OUT"
echo "SELESAI: $OUT"
ls -la "$OUT"
sha256sum "$OUT"

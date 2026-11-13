#!/usr/bin/env bash
# =============================================================
# Build APK-LSF 0.6.2 — FIX SERVERLIST KOSONG (FRAMING ENGINE.IO)
#
# Laporan owner (2026-11-13, log perangkat dilampirkan ke GitHub):
#   "Aplikasi jelas bermasalah.. SERVERLIST tidak muncul dan ini
#    seharusnya gak mgkin terjadi di versi online ..."
#   "saya coba pakai versi originalnya aplikasi serverlist muncul
#    dengan baik .."
#
# AKAR MASALAH (terbukti 3 level dari log perangkat 0.6.1,
# REKAM events.jsonl id 33 · 19:33:56):
#   1) KODE  — proxyOnline menulis-ulang url 2111 entri daftar server
#      (https://sXXXX-bs.popoh5.com:NNNN → http://127.0.0.1:8080) TAPI
#      prefix panjang engine.io TIDAK dihitung ulang.
#   2) ANGKA — respons ASLI upstream = satu frame "618016:430[...]"
#      (prefix 618016 = panjang paket, selisih 0 — framing upstream
#      sempurna); paket hasil tulis-ulang = 597094 (−20.922 char).
#      Klien diminta baca 618016 byte, body hanya 597094 byte.
#   3) PARSER — engine.io decodePayload di socket.io.min.js bawaan game:
#      "s = t.substr(c+1,i); a != s.length → return r(w,0,1)" →
#      SELURUH payload ditolak → ACK GetServerList tidak pernah sampai
#      ke UI game → SERVERLIST kosong. Versi original tidak menyentuh
#      payload → daftar muncul. Log berakhir di 📋 — konsisten.
#
# Baru 0.6.2:
#   1) rewriteServerListFramed — urai body frame-per-frame (logika sama
#      dgn parseFrames), tulis-ulang url di dalam paket, susun ulang
#      dengan prefix panjang BARU (semantik UTF-16 char = substr/length
#      parser JS). Frame tanpa url byte-identik. Framing rusak/tidak
#      ada perubahan → null → passthrough respons ASLI (tetap aman).
#   2) LOG JUJUR (HttpCanary) — baris 📋 kini menyatakan: prefix dihitung
#      ulang (nilai lama → baru), Ukuran ASLI vs ke klien. Jejak ASLI
#      tetap UTUH di REKAM + blok ISI (tidak berubah dari 0.6.1).
#   3) Harness TAHAP 21 — bukti perilaku: prefix frame == panjang paket
#      (semantik substr parser klien), kasus tepi (terpotong/rusak/tanpa
#      url → null), dan bukti cacat 0.6.1 (rewrite lama = prefix mati).
#   4) StatusStore.VERSION → 0.6.2.
#
# Dasar: releases/APK-LSF-0.6.1.apk (sha256 7dbe004c… — terbit).
#
# Patch yang diterapkan (TANPA apktool — dex game & resource tidak disentuh):
#   1) javac(ECJ) + d8  -> classes2.dex baru (kode server lokal)
#   2) mirror www/ + config-offline/ + QHinfo di-stage ulang (byte sama)
#   3) buang tanda tangan lama -> zipalign + sign (keystore tetap)
# =============================================================
set -euo pipefail

REPO="${REPO:-/home/z/db-apk}"
TOOLS="${TOOLS:-$REPO/tools}"
UBER="${UBER:-$REPO/tools/uber-apk-signer.jar}"
ECJ="${ECJ:-$REPO/tools/ecj-3.33.0.jar}"
KS="${KS:-$REPO/keystore/lsf.keystore}"
KS_ALIAS="${KS_ALIAS:-lsf}"
KS_PASS="${KS_PASS:-lsf-local-2025}"
BASE="${BASE:-$REPO/releases/APK-LSF-0.6.1.apk}"
LOGIN_PORT_QH="${LOGIN_PORT_QH:-8610}"
SRC="$REPO/local-server/src/main/java"
ASSETS="$REPO/local-server/assets"
BUILD="$REPO/build"
DIST="$REPO/dist"
OUT="$REPO/releases/APK-LSF-0.6.2.apk"
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
HR="$BUILD/harness-root-062"
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
    $(cd "$BUILD/stage" && find assets/server/www assets/server/config-offline -type f | sort) \
    > /tmp/zip-stage-062.log 2>&1
)
# 0.6.2 — hard-fail bila classes2.dex gagal disisip (zip diam-diam melewati
# file yang tidak ada — cacat senyap yang nyaris menerbitkan APK tanpa kode
# server baru pada build manual 2026-11-13):
if ! grep -q "adding: classes2.dex\|updating: classes2.dex" /tmp/zip-stage-062.log; then
  echo "FATAL: classes2.dex TIDAK tersisip ke work.apk — periksa build/stage" >&2
  exit 1
fi
unzip -p "$BUILD/work.apk" classes2.dex | cmp -s - "$BUILD/dex/classes.dex" \
  || { echo "FATAL: classes2.dex di work.apk ≠ build/dex/classes.dex" >&2; exit 1; }

echo "[6/8] zipalign + sign (keystore tetap) ..."
rm -f "$DIST/work-aligned-signed.apk"; mkdir -p "$DIST"
java -jar "$UBER" -a "$BUILD/work.apk" \
  --ks "$KS" --ksAlias "$KS_ALIAS" --ksPass "$KS_PASS" --ksKeyPass "$KS_PASS" \
  --out "$DIST"

echo "[7/8] verifikasi ..."
NEWAPK="$DIST/work-aligned-signed.apk"
echo "-- bukti 0.6.2 di dex (python byte-count — strings memangkas baris em-dash; harus >0):"
python3 - "$BUILD/dex/classes.dex" <<'PYEOF'
import sys
data = open(sys.argv[1], 'rb').read()
baru = [
    'rewriteServerListFramed',
    'framing engine.io UTUH',
    'prefix panjang dihitung ulang per frame',
    'dulu 0.6.1 prefix tertinggal',
    'payload ditolak parser klien → SERVERLIST kosong',
    'Ukuran   : ASLI ',
    ' · ke klien ',
    'respons ASLI (sebelum tulis-ulang)',
]
lama = [
    'url ASLI per entri · tulis-ulang = routing',
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
echo "-- versi dasbor + marker di classes2.dex DALAM APK (hard-fail):"
python3 - "$NEWAPK" <<'PYEOF2'
import zipfile, sys
z = zipfile.ZipFile(sys.argv[1])
d = z.read('classes2.dex')
if d.count(b'0.6.2') == 0:
    print('FATAL: versi 0.6.2 tidak ada di classes2.dex APK (dex basi?)'); sys.exit(1)
if d.count('framing engine.io UTUH'.encode('utf-8')) == 0:
    print('FATAL: marker framing tidak ada di APK'); sys.exit(1)
print('   OK: 0.6.2 + marker framing ada di classes2.dex dalam APK')
PYEOF2

echo "[8/8] terbitkan ..."
cp "$NEWAPK" "$OUT"
echo "SELESAI: $OUT"
ls -la "$OUT"
sha256sum "$OUT"

#!/usr/bin/env bash
# =============================================================
# Build APK-LSF 0.6.3 — FIX PLAY BERHENTI MODE LOCAL (UPSTREAM DEFAULT)
#
# Laporan owner (2026-11-14, log perangkat logs/local/ di GitHub):
#   "Github sudah saya update di bagian local terhenti saat klik play
#    setelah muncul nama server local"
#
# DIAGNOSIS (dari log perangkat owner sendiri — logs/local/human.log):
#   SDK milestone 0.3.0 TERBUKTI PENUH di perangkat:
#     - DAFTAR  registerUser → 200 · 1,0 KB (04:23:02, uid tes@mail.com)
#     - AUTO    autoLogin    → 200 (04:24:00, 04:24:18)
#     - LOGIN   loginByName ditolak benar saat salah → 40101 (04:25:11),
#               sukses → 200 · 1,0 KB (04:25:19) + loginchecknative code 0
#     - TAMU    registerVisitor (log sebelumnya 7d91d09)
#   Lalu: "📋 Daftar server terkirim — 1 server (paket MANUAL)" →
#         "❌ Play berhenti — jalur LOCAL belum punya upstream relay"
#         "io.connect : http://127.0.0.1:8080 · upstream: (kosong)"
#   AKAR: field "upstream" config-offline/servers.json = "" →
#         resolveGameUpstream(mode LOCAL) = null → relay tanpa tujuan.
#
# Baru 0.6.3:
#   1) Paket manual config-offline/servers.json — field "upstream" TERISI
#      DEFAULT: https://s2111-bs.popoh5.com:8141 (entri pertama daftar
#      server ASLI dari jejak capture 2026-11-13 — TERBUKTI jalan di log
#      ONLINE: Play → enterGame → gameplay mengalir; nilai nyata dari
#      peta, bukan karangan). manifest.txt sha256 ikut diperbarui.
#   2) Teks boot LOCAL menyebut SUMBER upstream (isian file pemilik vs
#      paket manual) — dasbor & log tetap jujur satu jalur.
#   3) StatusStore.VERSION → 0.6.3.
#
# Dasar: releases/APK-LSF-0.6.2.apk (164.003.964 B — terbit).
#
# Patch yang diterapkan (TANPA apktool — dex game & resource tidak disentuh):
#   1) javac(ECJ) + d8  -> classes2.dex baru (kode server lokal)
#   2) mirror www/ + config-offline/ + QHinfo di-stage ulang
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
BASE="${BASE:-$REPO/releases/APK-LSF-0.6.2.apk}"
LOGIN_PORT_QH="${LOGIN_PORT_QH:-8610}"
SRC="$REPO/local-server/src/main/java"
ASSETS="$REPO/local-server/assets"
BUILD="$REPO/build"
DIST="$REPO/dist"
OUT="$REPO/releases/APK-LSF-0.6.3.apk"
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
HR="$BUILD/harness-root-063"
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

echo "[3b] verifikasi paket manual 0.6.3 (upstream default TERISI — hard-fail):"
python3 - "$ASSETS/server/config-offline/servers.json" <<'PYUP'
import json, sys
raw = open(sys.argv[1], encoding='utf-8').read()
j = json.loads(raw)
up = (j.get('serverList') or [{}])[0].get('upstream', '')
if not up.startswith('https://') and not up.startswith('http://'):
    print('FATAL: field "upstream" paket manual KOSONG — Play LOCAL akan berhenti lagi')
    sys.exit(1)
print('   OK: upstream default = %s' % up)
PYUP

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
    > /tmp/zip-stage-063.log 2>&1
)
# 0.6.2 — hard-fail bila classes2.dex gagal disisip (zip diam-diam melewati
# file yang tidak ada — cacat senyap yang nyaris menerbitkan APK tanpa kode
# server baru pada build manual 2026-11-13):
if ! grep -q "adding: classes2.dex\|updating: classes2.dex" /tmp/zip-stage-063.log; then
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
echo "-- bukti 0.6.3 di dex (python byte-count — harus >0):"
python3 - "$BUILD/dex/classes.dex" <<'PYEOF'
import sys
data = open(sys.argv[1], 'rb').read()
baru = [
    'paket manual config-offline/servers.json',
    'config/game-upstream.txt — isian pemilik',
    '(sumber: ',
    'rewriteServerListFramed',
    'framing engine.io UTUH',
]
lama = [
    'url ASLI per entri · tulis-ulang = routing',
]
gagal = 0
for p in baru:
    n = data.count(p.encode('utf-8'))
    print('   BARU  wajib >0: %3d  %s' % (n, p))
    if n == 0:
        gagal += 1
for p in lama:
    n = data.count(p.encode('utf-8'))
    print('   LAMA  wajib =0: %3d  %s' % (n, p))
    if n != 0:
        gagal += 1
sys.exit(1 if gagal else 0)
PYEOF
echo "-- CRC penting (classes.dex game & config endpoint WAJIB identik dengan dasar):"
unzip -v "$BASE" | grep -E "classes\.dex |config_BS\.properties|config\.properties|QHinfo" || true
echo "-- CRC APK BARU:"
unzip -v "$NEWAPK" | grep -E "classes\.dex |config_BS\.properties|config\.properties|classes2\.dex|server/payload|dashboard|QHinfo" | head -20 || true
echo "-- isi QHinfo baru (harus http://127.0.0.1:8610):"
unzip -p "$NEWAPK" assets/QHinfo; echo
echo "-- upstream default DALAM APK (hard-fail):"
python3 - "$NEWAPK" <<'PYEOF2'
import zipfile, sys, json
z = zipfile.ZipFile(sys.argv[1])
d = z.read('classes2.dex')
if d.count(b'0.6.3') == 0:
    print('FATAL: versi 0.6.3 tidak ada di classes2.dex APK (dex basi?)'); sys.exit(1)
j = json.loads(z.read('assets/server/config-offline/servers.json').decode('utf-8'))
up = (j.get('serverList') or [{}])[0].get('upstream', '')
if up != 'https://s2111-bs.popoh5.com:8141':
    print('FATAL: upstream dalam APK salah/koosng: %r' % up); sys.exit(1)
print('   OK: 0.6.3 di classes2.dex + upstream default %s ada di APK' % up)
PYEOF2

echo "[8/8] terbitkan ..."
cp "$NEWAPK" "$OUT"
echo "SELESAI: $OUT"
ls -la "$OUT"
sha256sum "$OUT"

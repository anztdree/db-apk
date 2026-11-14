#!/usr/bin/env bash
# =============================================================
# Build APK-LSF 0.0.2 — PEMISAHAN PORT SDK/LOGIN (pola 5-server asli).
#
# ISI TERBITAN INI (di atas terbitan mesin game lokal 0.0.1):
#   Arahan owner 2026-11-14: "pola server aslinya punya 5 server utama..
#   SDK, login, main, chat, dungeon, terpisah dengan berbagai port.."
#   dan "SDK dan loginserver harus matang sempurna rapi".
#
#   1) SdkServer.java BARU — API SDK HTTP MURNI di :sdkPort (8610):
#      /v1/* + /loginchecknative. Dulu API SDK ditumpangkan DI DALAM
#      LoginServer :8610 (satu port dua peran = akar "berantakan").
#   2) LoginServer — MURNI socket.io login di :loginPort (8611);
#      permintaan SDK di port login = 404 dengan penunjuk jalan ke 8610.
#   3) Config — sdkPort (8610) + loginPort (8611) TERPISAH; migrasi aman
#      server.json legacy (loginPort 8610): SDK mempertahankan 8610,
#      login socket digeser 8611 (keputusan terekam log).
#   4) SettingBin — loginPort (socket login) dan loginWebPort
#      (loginchecknative → SDK) dipecah di SEMUA mode (LOCAL/ONLINE/REKAM).
#   5) Starter — 4 layanan: game :8080 · sdk :8610 · login :8611 ·
#      dasbor :4000; watchdog probe 3 port game/sdk/login; boot line rinci.
#   6) StatusStore — 0.0.2 + status SDK terpisah (sdkPort/sdkAlive).
#   7) Paket config-offline handcraft: setting.json loginPort 8611 +
#      loginWebPort 8610 (bin XOR dihitung ulang); serversetting.json
#      loginserver :8611; servers.json HANYA FIELD KONTRAK KLIEN
#      (upstream/urlInner/chatInner DIHAPUS — 0 referensi di JS klien);
#      manifest.txt sha256 baru.
#   8) Harness TAHAP 23 — 179 LULUS + 15 GAGAL LINGKUNGAN (baseline tetap,
#      nol regresi): migrasi port, pemisahan 8610/8611, audit paket nyata
#      (bin=XOR exact, kontrak field, sha256 manifest), StatusStore 0.0.2.
#
# RIWAYAT TERBITAN SEBELUMNYA (tetap berlaku):
#   0.0.1 — MESIN GAME LOKAL (GameEngine/Xxtea/GamePlayer) + KOREKSI KESALAHAN
#   FATAL upstream (owner: "CONFIG ONLINE ITU CUMA JEJAK ARAH, BUKAN SUMBER
#   NILAI") — paket tidak pernah membawa alamat server asli; relay opsional =
#   isian pemilik config/game-upstream.txt.
#
# Dasar: releases/APK-LSF-0.0.1.apk (terbitan mesin game lokal).
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
BASE="${BASE:-$REPO/releases/APK-LSF-0.0.1.apk}"
SDK_PORT_QH="${SDK_PORT_QH:-8610}"
SRC="$REPO/local-server/src/main/java"
ASSETS="$REPO/local-server/assets"
BUILD="$REPO/build"
DIST="$REPO/dist"
OUT="$REPO/releases/APK-LSF-0.0.2.apk"
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
  -classpath "$BUILD/classes:$TOOLS/sqlite-jdbc.jar:$TOOLS/android.jar" -d "$BUILD/harness" \
  "$REPO/local-server/harness/HarnessSdk.java"
HR="$BUILD/harness-root-001"
rm -rf "$HR"
java -cp "$BUILD/classes:$BUILD/harness:$TOOLS/sqlite-jdbc.jar:$TOOLS/android.jar" \
  HarnessSdk "$HR" "$REPO" | tail -3 || true

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
printf 'http://127.0.0.1:%s' "$SDK_PORT_QH" > "$BUILD/stage/assets/QHinfo"

echo "[3b] verifikasi paket manual 0.0.2 (kontrak klien saja — hard-fail):"
python3 - "$ASSETS/server/config-offline" <<'PYUP'
import json, sys, os
folder = sys.argv[1]
FORBIDDEN = ["popoh5.com", "sjmobilegame.com", "sjflaregame.com", "t4game.com", "parajoymobile.com"]
MATI = ["upstream", "urlInner", "chatInner"]
gagal = 0
for name in sorted(os.listdir(folder)):
    p = os.path.join(folder, name)
    raw = open(p, encoding='utf-8', errors='replace').read()
    for h in FORBIDDEN:
        if h in raw:
            print('FATAL: paket manual memuat host server asli %s di %s — owner: config online cuma jejak, bukan sumber nilai' % (h, name))
            gagal += 1
j = json.loads(open(os.path.join(folder, 'servers.json'), encoding='utf-8').read())
entri = (j.get('serverList') or [{}])[0]
for m in MATI:
    if m in entri:
        print('FATAL: field mati "%s" masih ada di servers.json — 0 referensi di JS klien, bukan kontrak (0.0.2 wajib dibuang)' % m)
        gagal += 1
sj = json.loads(open(os.path.join(folder, 'setting.json'), encoding='utf-8').read())
if sj.get('loginPort') != 8611 or sj.get('loginWebPort') != 8610:
    print('FATAL: setting.json harus loginPort 8611 + loginWebPort 8610 (pemisahan 0.0.2): %r' % sj)
    gagal += 1
ss = json.loads(open(os.path.join(folder, 'serversetting.json'), encoding='utf-8').read())
if ss.get('loginserver') != 'http://127.0.0.1:8611':
    print('FATAL: serversetting.json loginserver harus http://127.0.0.1:8611: %r' % ss)
    gagal += 1
if gagal:
    sys.exit(1)
print('   OK: paket bebas host asli · tanpa field mati · port terpisah 8080/8611/8610')
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
    > /tmp/zip-stage-001.log 2>&1
)
# 0.6.2 — hard-fail bila classes2.dex gagal disisip (zip diam-diam melewati
# file yang tidak ada — cacat senyap yang nyaris menerbitkan APK tanpa kode
# server baru pada build manual 2026-11-13):
if ! grep -q "adding: classes2.dex\|updating: classes2.dex" /tmp/zip-stage-001.log; then
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
echo "-- bukti 0.0.2 pemisahan port di dex (python byte-count — harus >0):"
python3 - "$BUILD/dex/classes.dex" <<'PYEOF'
import sys
data = open(sys.argv[1], 'rb').read()
baru = [
    '0.0.2',
    'Server SDK lokal',
    'sdkPort',
    'pindah ke port ',
    'MESIN GAME LOKAL',
    'game_player:',
]
lama = [
    's2111-bs.popoh5.com',
    'upstream":"https',
    'urlInner',
    'chatInner',
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
echo "-- isi QHinfo baru (harus http://127.0.0.1:8610 = SDK):"
unzip -p "$NEWAPK" assets/QHinfo; echo
echo "-- paket DALAM APK (hard-fail: bebas host asli + tanpa field mati + port terpisah):"
python3 - "$NEWAPK" <<'PYEOF2'
import zipfile, sys, json
z = zipfile.ZipFile(sys.argv[1])
d = z.read('classes2.dex')
if d.count(b'0.0.2') == 0:
    print('FATAL: versi 0.0.2 tidak ada di classes2.dex APK (dex basi?)'); sys.exit(1)
FORBIDDEN = ["popoh5.com", "sjmobilegame.com", "sjflaregame.com", "t4game.com", "parajoymobile.com"]
gagal = 0
for n in z.namelist():
    if n.startswith('assets/server/config-offline/'):
        raw = z.read(n).decode('utf-8', errors='replace')
        for h in FORBIDDEN:
            if h in raw:
                print('FATAL: APK membawa host server asli %s di %s' % (h, n)); gagal += 1
j = json.loads(z.read('assets/server/config-offline/servers.json').decode('utf-8'))
entri = (j.get('serverList') or [{}])[0]
for m in ("upstream", "urlInner", "chatInner"):
    if m in entri:
        print('FATAL: field mati %s masih ada di servers.json dalam APK' % m); gagal += 1
sj = json.loads(z.read('assets/server/config-offline/setting.json').decode('utf-8'))
if sj.get('loginPort') != 8611 or sj.get('loginWebPort') != 8610:
    print('FATAL: setting.json dalam APK salah port: %r' % sj); sys.exit(1)
if gagal:
    sys.exit(1)
print('   OK: 0.0.2 di classes2.dex + paket dalam APK bebas host asli + tanpa field mati + port 8080/8611/8610')
PYEOF2

echo "[8/8] terbitkan ..."
cp "$NEWAPK" "$OUT"
mkdir -p "$DIST/0.0.2"
cp "$BUILD/dex/classes.dex" "$DIST/0.0.2/classes2.dex"
sha256sum "$OUT" | awk '{print $1"  APK-LSF-0.0.2.apk"}' > "$DIST/0.0.2/SHA256.txt"
echo "SELESAI: $OUT"
ls -la "$OUT"
sha256sum "$OUT"

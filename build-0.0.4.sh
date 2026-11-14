#!/usr/bin/env bash
# =============================================================
# Build APK-LSF 0.0.4 — FOLDER LAYANAN + .env (arahan owner langsung).
#
# ISI TERBITAN INI (di atas struktur 0.0.3):
#   Arahan owner 2026-11-15: "server json bisa di gantikah jadi .env .
#   lalu SDK-SERVER, LOGIN-SERVER, MAIN-SERVER, CHAT-SERVER,
#   DUNGEON-SERVER dan LOG !!  nama nya jelas" + kendala sebelumnya
#   "banyak folder sampah yg gak jelas.. PETA.TXT GK PENTING.. TMP GK PENTING".
#
#   1) .env — SATU FILE PENGATURAN di root (pengganti config/server.json +
#      config/game-upstream.txt): MODE, SDK_PORT 8610, LOGIN_PORT 8611,
#      MAIN_PORT 8080, CHAT_PORT 8612, DUNGEON_PORT 8613, DASBOR_PORT 4000,
#      GAME_UPSTREAM, USE_SDK, BIND, MONITOR_SEC, TOGGLE_X/Y, LOG_TECH, REKAM.
#      Komentar menjelaskan tiap kunci; bisa disunting di file manager.
#   2) ROOT = 3 file + 6 folder layanan bernama JELAS (nama = layanannya):
#      .env · dragonball.db (DATA PEMAIN, dari db/ — root) · status.json
#      (dari state/) · SDK-SERVER/ · LOGIN-SERVER/ · MAIN-SERVER/
#      (bs/ + config-offline/ + www.stamp — semua yang dilayani :8080) ·
#      CHAT-SERVER/ · DUNGEON-SERVER/ · LOG/ (dari logs/).
#      PETA.txt + tmp/ DIHAPUS (owner: tidak penting); config/db/state/www/
#      config-offline/logs di root hilang — isi pindah ke folder layanannya.
#   3) MIGRASI otomatis perangkat 0.0.3 ke bawah (Paths.migrateToServiceLayout
#      + Config.migrateLegacyValues — SEBELUM Db.boot & Cleaner, terekam log 📂;
#      TIDAK PERNAH menimpa target yang sudah ada): db/dragonball.db* → root ·
#      state/status.json → root · state/www.stamp → MAIN-SERVER/ · www/bs →
#      MAIN-SERVER/bs · config-offline → MAIN-SERVER/config-offline · logs →
#      LOG · NILAI server.json + game-upstream.txt → .env (pilihan pemilik
#      tidak hilang; file lama disapu Cleaner dengan nama+ukuran di log).
#   4) Cleaner 0.0.4 — whitelist per-folder layanan: root (3 file + 6 folder) ·
#      MAIN-SERVER (bs, config-offline, www.stamp) · MAIN-SERVER/config-offline
#      (5 file paket) · LOG (human.log/events.jsonl/crash.log + rotasi) ·
#      SDK/LOGIN/CHAT/DUNGEON-SERVER dijaga kosong (state layanan di
#      dragonball.db); berkas nyasar disapu dengan nama di log.
#   5) tmp/ DIHAPUS — kerja sementara pindah ke cache INTERNAL aplikasi
#      (java.io.tmpdir = context.getCacheDir(); di luar folder server).
#   6) Port CHAT 8612 + DUNGEON 8613 dideklarasikan .env + StatusStore —
#      folder layanan siap utk tahap CHAT/DUNGEON (urutan kerja owner).
#   7) Harness 0.0.4 — +17 tes: migrasi folder layanan (pindah + jangan
#      timpa + ringkasan), .env (bawaan + nilai legacy + GAME_UPSTREAM +
#      tahan reboot), whitelist 0.0.4 (PETA/tmp/config lama disapu;
#      .env/db/status/MAIN-SERVER/LOG dilindungi; nyasar di layanan disapu).
#      HASIL: 224 LULUS + 0 GAGAL (slf4j dilengkapi — SQLite JDBC nyata
#      teruji; baseline lama 192 LULUS + 15 GAGAL LINGKUNGAN).
#
# Dasar: releases/APK-LSF-0.0.3.apk (struktur folder jelas).
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
BASE="${BASE:-$REPO/releases/APK-LSF-0.0.3.apk}"
SDK_PORT_QH="${SDK_PORT_QH:-8610}"
SRC="$REPO/local-server/src/main/java"
ASSETS="$REPO/local-server/assets"
BUILD="$REPO/build"
DIST="$REPO/dist"
OUT="$REPO/releases/APK-LSF-0.0.4.apk"
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
SLF4J=""
[ -f "$TOOLS/slf4j-api.jar" ] && SLF4J=":$TOOLS/slf4j-api.jar"
[ -f "$TOOLS/slf4j-nop.jar" ] && SLF4J="$SLF4J:$TOOLS/slf4j-nop.jar"
java -jar "$ECJ" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$BUILD/classes:$TOOLS/sqlite-jdbc.jar:$TOOLS/android.jar" -d "$BUILD/harness" \
  "$REPO/local-server/harness/HarnessSdk.java"
HR="$BUILD/harness-root-001"
rm -rf "$HR"
java -cp "$BUILD/classes:$BUILD/harness:$TOOLS/sqlite-jdbc.jar$SLF4J:$TOOLS/android.jar" \
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

echo "[3b] verifikasi paket manual 0.0.3 (kontrak klien saja — hard-fail):"
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
echo "-- bukti 0.0.4 folder layanan + .env di dex (python byte-count — harus >0):"
python3 - "$BUILD/dex/classes.dex" <<'PYEOF'
import sys
data = open(sys.argv[1], 'rb').read()
baru = [
    '0.0.4',
    'SDK-SERVER',
    'LOGIN-SERVER',
    'MAIN-SERVER',
    'CHAT-SERVER',
    'DUNGEON-SERVER',
    'GAME_UPSTREAM',
    'migrateToServiceLayout',
    'migrateLegacyValues',
    'MESIN GAME LOKAL',
    'game_player:',
]
# CATATAN: 'server.json'/'game-upstream.txt' MASIH SAH — hanya di
# Config.migrateLegacyValues (membaca file lama perangkat 0.0.3 ke bawah).
# Yang wajib HILANG = sistem config LAMA: parser grepBool + PETA.txt.
lama = [
    'grepBool',
    'PETA.txt',
    'Peta versi ',
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
if d.count(b'0.0.4') == 0:
    print('FATAL: versi 0.0.4 tidak ada di classes2.dex APK (dex basi?)'); sys.exit(1)
for m in (b'SDK-SERVER', b'MAIN-SERVER', b'GAME_UPSTREAM'):
    if d.count(m) == 0:
        print('FATAL: marker folder layanan/.env (%s) tidak ada di classes2.dex APK (dex basi?)' % m.decode()); sys.exit(1)
if d.count(b'PETA.txt') != 0 or d.count(b'grepBool') != 0:
    print('FATAL: marker lama (PETA.txt / grepBool) masih ada di classes2.dex APK'); sys.exit(1)
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
print('   OK: 0.0.4 + folder layanan + GAME_UPSTREAM di classes2.dex + paket bebas host asli + tanpa field mati + port 8080/8611/8610')
PYEOF2

echo "[8/8] terbitkan ..."
cp "$NEWAPK" "$OUT"
mkdir -p "$DIST/0.0.4"
cp "$BUILD/dex/classes.dex" "$DIST/0.0.4/classes2.dex"
sha256sum "$OUT" | awk '{print $1"  APK-LSF-0.0.4.apk"}' > "$DIST/0.0.4/SHA256.txt"
echo "SELESAI: $OUT"
ls -la "$OUT"
sha256sum "$OUT"

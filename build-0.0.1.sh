#!/usr/bin/env bash
# =============================================================
# Build APK-LSF 0.0.1 — TERBITAN PENGGANTI: MESIN GAME LOKAL (rebuild
# handcraft; owner: "local kita ini rebuild bukan hasil jiplak dari online").
#
# ISI TERBITAN INI (di atas terbitan koreksi sebelumnya):
#   - GameEngine.java — mesin jawaban protokol game di :8080 (verify XXTEA,
#     enterGame, aksi lanjutan, Notify) — kontrak dari KODE KLIEN SENDIRI,
#     nilai dari dragonball.db; Play LOKAL hidup TANPA relay & tanpa kontak
#     keluar. Isian pemilik game-upstream.txt = relay opsional yang tetap
#     dihormati (paket manual tetap tidak pernah membawa alamat server asli).
#   - Xxtea.java — klon persis TEA klien (vektor silang: kode klien dieksekusi
#     bun; cocok dua arah).
#   - GamePlayer.java — data pemain lokal (kv game_player: tahan reboot).
#   - Harness TAHAP 22 — 165 LULUS + 15 GAGAL LINGKUNGAN (baseline tetap).
#
# RIWAYAT TERBITAN SEBELUMNYA (tetap berlaku):
#   KOREKSI KESALAHAN FATAL (owner 2026-11-14:
#   "KESALAHAN FATAL TIDAK BISA DI TOLERANSI! ANDA AMBIL MENTAH CONFIG DARI
#    SERVER ASLINYA!! .. CONFIG ONLINE ITU CUMA JEJAK AGAR KITA GAK TERSESAT..
#    BUKAN MALAH MURNI DI PAKAI MENTAH-MENTAH")
#
# PELANGGARAN YANG DIKOREKSI (bukti log perangkat owner logs/local/ 19:41–19:42):
#   0.6.3/0.0.1 lama mengisi field "upstream" paket manual dengan alamat server
#   game ASLI (s2111-bs.popoh5.com:8141) mentah-mentah dari jejak online →
#   mode LOCAL jadi MITM relay ke server online: enterGame(user) diteruskan ke
#   server asli byte-per-byte, dan Play MATI saat upstream tak terjangkau
#   (SocketTimeoutException 19:42:23). LOCAL bukan server mandiri — itu
#   pelanggaran tegasan berulang owner: OFFLINE/ONLINE 100% terpisah.
#
# PERBAIKAN:
#   1) Paket manual config-offline/servers.json — "upstream" = "" (kosong);
#      manifest.txt ditulis ulang (paket TIDAK PERNAH membawa alamat server asli).
#   2) Kode: satu-satunya sumber tujuan relay = ISIAN PEMILIK
#      config/game-upstream.txt (dasbor seksi CONFIG). Field paket tidak dibaca
#      lagi (LoginData.gameUpstreamEx file-only; GameServer.resolveGameUpstream).
#      LOCAL tanpa isian = TIDAK ada kontak keluar; Play berhenti dengan log
#      jujur + payload klien UTUH (bahan handcraft berikutnya) — bukan relay
#      diam-diam ke server asli.
#   3) Guard build DIBALIK: hard-fail bila file paket mana pun memuat host
#      server asli (popoh5.com, sjmobilegame.com, sjflaregame.com, t4game.com,
#      parajoymobile.com) — paket mentah dari online tidak lolos terbit.
#
# Dasar: releases/APK-LSF-0.0.1.apk (terbitan 2026-11-14 pagi — berisi paket
#        pelanggaran; digantikan terbitan koreksi ini).
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
LOGIN_PORT_QH="${LOGIN_PORT_QH:-8610}"
SRC="$REPO/local-server/src/main/java"
ASSETS="$REPO/local-server/assets"
BUILD="$REPO/build"
DIST="$REPO/dist"
OUT="$REPO/releases/APK-LSF-0.0.1.apk"
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
printf 'http://127.0.0.1:%s' "$LOGIN_PORT_QH" > "$BUILD/stage/assets/QHinfo"

echo "[3b] verifikasi paket manual 0.0.1-koreksi (TANPA alamat server asli — hard-fail):"
python3 - "$ASSETS/server/config-offline" <<'PYUP'
import json, sys, os
folder = sys.argv[1]
FORBIDDEN = ["popoh5.com", "sjmobilegame.com", "sjflaregame.com", "t4game.com", "parajoymobile.com"]
gagal = 0
for name in sorted(os.listdir(folder)):
    p = os.path.join(folder, name)
    raw = open(p, encoding='utf-8', errors='replace').read()
    for h in FORBIDDEN:
        if h in raw:
            print('FATAL: paket manual memuat host server asli %s di %s — owner: config online cuma jejak, bukan sumber nilai' % (h, name))
            gagal += 1
j = json.loads(open(os.path.join(folder, 'servers.json'), encoding='utf-8').read())
up = (j.get('serverList') or [{}])[0].get('upstream', '')
if up != '':
    print('FATAL: field "upstream" paket manual harus "" (kosong) — tujuan relay hanya isian pemilik; sekarang: %r' % up)
    gagal += 1
if gagal:
    sys.exit(1)
print('   OK: paket manual bebas host server asli · upstream kosong (tujuan relay = isian pemilik saja)')
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
echo "-- bukti 0.0.1-koreksi di dex (python byte-count — harus >0):"
python3 - "$BUILD/dex/classes.dex" <<'PYEOF'
import sys
data = open(sys.argv[1], 'rb').read()
baru = [
    'MESIN GAME LOKAL',
    'battleMedalRefresh',
    'game_player:',
    'config/game-upstream.txt — isian pemilik',
    'rewriteServerListFramed',
    'framing engine.io UTUH',
]
lama = [
    's2111-bs.popoh5.com',
    'upstream isian manual (bukan daftar server)',
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
echo "-- upstream DALAM APK (hard-fail: wajib kosong + bebas host server asli):"
python3 - "$NEWAPK" <<'PYEOF2'
import zipfile, sys, json, os
z = zipfile.ZipFile(sys.argv[1])
d = z.read('classes2.dex')
if d.count(b'0.0.1') == 0:
    print('FATAL: versi 0.0.1 tidak ada di classes2.dex APK (dex basi?)'); sys.exit(1)
FORBIDDEN = ["popoh5.com", "sjmobilegame.com", "sjflaregame.com", "t4game.com", "parajoymobile.com"]
gagal = 0
for n in z.namelist():
    if n.startswith('assets/server/config-offline/'):
        raw = z.read(n).decode('utf-8', errors='replace')
        for h in FORBIDDEN:
            if h in raw:
                print('FATAL: APK membawa host server asli %s di %s' % (h, n)); gagal += 1
j = json.loads(z.read('assets/server/config-offline/servers.json').decode('utf-8'))
up = (j.get('serverList') or [{}])[0].get('upstream', '')
if up != '':
    print('FATAL: upstream dalam APK harus "" (kosong): %r' % up); sys.exit(1)
if gagal:
    sys.exit(1)
print('   OK: 0.0.1 di classes2.dex + paket dalam APK bebas host server asli + upstream kosong')
PYEOF2

echo "[8/8] terbitkan ..."
cp "$NEWAPK" "$OUT"
echo "SELESAI: $OUT"
ls -la "$OUT"
sha256sum "$OUT"

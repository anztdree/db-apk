#!/usr/bin/env bash
# =============================================================
# Build APK-LSF 0.6.0 — LOG ITU NYAWA: TANPA SINGKATAN, ISI UTUH,
# PLAY MODE ONLINE TERTANGKAP (relay semua mode)
#
# Perintah owner (2026-11-13):
#   "log itu adalah nyawa kita agar tidak tersesat!! dari awal saya tekankan
#    tidak boleh ada singkatan di log. kalau memang log itu panjang ya wajib
#    tampil dan juga log txt harus menyimpan"
#   + laporan: klik Play mode ONLINE "gak muncul" di log — tidak tertangkap.
#
# Baru 0.6.0:
#   1) ISI LOG SELALU UTUH — HumanLog.capIsi tidak memangkas apa pun (dulu
#      16 KB pertama + catatan). human.log + events.jsonl + dasbor menyimpan
#      isi penuh; sesi/sid/query/url tanpa elipsis (sid.substring(0,6)+"…",
#      shortId, ringkasQuery 80 char, joinParams 400 char — semua DIHAPUS).
#   2) PLAY MODE ONLINE TERTANGKAP — akar laporan owner DIBUKTIKAN dari log
#      device: daftar server ASLI passthrough UTUH → url entri = server game
#      asli → klien io.connect LANGSUNG ke sana → server lokal BUTA (tidak
#      ada satu baris pun). Kini: LoginServer.rewriteServerListUrls menulis-
#      ulang url SETIAP entri ke http://127.0.0.1:8080 (regex, tahan JSON
#      polos maupun ter-escape) → relay game meneruskan ke url ASLI per
#      entri (tabel serverId→url dari parseServerTable). Bila parse gagal →
#      passthrough UTUH tanpa rewrite (perilaku lama, tetap aman).
#   3) Relay game :8080 AKTIF DI SEMUA MODE — GameServer.resolveGameUpstream:
#      isian owner (config/game-upstream.txt / field "upstream") menang;
#      mode ONLINE lanjut ke tabel daftar server ASLI (serverId pilihan
#      pemain bila terbaca dari paket loginGame/SaveUserEnterInfo/enterGame,
#      bila tidak entri pertama). Monitor probe ikut resolusi yang sama.
#   4) [📋] Daftar server kini PENTING + blok ISI UTUH (respons persis yang
#      diterima klien — setelah rewrite) + Gerbang pertama/terakhir FULL +
#      jumlah url yang di-rewrite. Parser gerbang baru menangani bentuk
#      ter-escape (sebab lama "url tidak terbaca": pola "url":" gagal pada
#      respons ber-escape).
#   5) Payload relay game ber-ISI UTUH di blok ISI ([🎮] POST aksi + GET
#      event) — bukan lagi "Catatan: payload diteruskan UTUH" tanpa isi.
#   6) Delta 0.5.9 dipulihkan manual ke source (dari dex terbit + log device):
#      pesan ❌ upstream minimal, probe Monitor tanpa narasi "Lanjutan",
#      simpan upstream tanpa "Lanjutan", SWITCH MODE + tombol melayang
#      minimal, 🔑 passthrough 2 field (device-exact), pesan 🔌 tanpa cerita.
#
# Dasar: releases/APK-LSF-0.5.9.apk (sha256 3e1bdcb7… — terbit, berisi
#        dashboard 0.5.9 28,4 KB; dasbor 0.5.9 diganti dashboard 0.6.0 bila
#        ada perubahan — kali ini TIDAK ada perubahan dasbor, HTML tetap).
#
# Patch yang diterapkan (TANPA apktool — dex game & resource tidak disentuh):
#   1) javac(ECJ) + d8  -> classes2.dex baru (kode server lokal)
#   2) ganti assets/server/dashboard.html (dari repo — sama dengan 0.5.9)
#   3) mirror www/ + config-offline/ + QHinfo di-stage ulang (byte sama)
#   4) buang tanda tangan lama -> zipalign + sign (keystore tetap)
# =============================================================
set -euo pipefail

REPO="${REPO:-/home/z/db-apk}"
TOOLS="${TOOLS:-$REPO/tools}"                          # android.jar + r8.jar + ecj + sqlite-jdbc + uber-apk-signer
UBER="${UBER:-$REPO/tools/uber-apk-signer.jar}"
ECJ="${ECJ:-$REPO/tools/ecj-3.33.0.jar}"               # compiler Java 8
KS="${KS:-$REPO/keystore/lsf.keystore}"
KS_ALIAS="${KS_ALIAS:-lsf}"
KS_PASS="${KS_PASS:-lsf-local-2025}"
BASE="${BASE:-$REPO/releases/APK-LSF-0.5.9.apk}"        # APK dasar (terbit 0.5.9)
LOGIN_PORT_QH="${LOGIN_PORT_QH:-8610}"
SRC="$REPO/local-server/src/main/java"
ASSETS="$REPO/local-server/assets"
BUILD="$REPO/build"
DIST="$REPO/dist"
OUT="$REPO/releases/APK-LSF-0.6.0.apk"
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

echo "[1b] compile harness JVM (tidak masuk APK) ..."
java -jar "$ECJ" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$BUILD/classes:$TOOLS/sqlite-jdbc.jar" -d "$BUILD/harness" \
  "$REPO/local-server/harness/HarnessSdk.java" || echo "   (harness dilewati)"

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
echo "-- bukti 0.6.0 di dex (harus >0): relay semua mode + parser baru"
STRINGS=$(java -jar "$ECJ" 2>/dev/null >/dev/null; strings "$BUILD/dex/classes.dex")
echo "   'relay meneruskan ke url ASLI per entri' : $(echo "$STRINGS" | grep -c 'relay meneruskan ke url ASLI per entri' || true)"
echo "   'passthrough -> relay lokal'             : $(echo "$STRINGS" | grep -c 'passthrough → relay lokal' || true)"
echo "   'isi SELALU UTUH' (komentar tak masuk dex — cek perilaku lain): $(echo "$STRINGS" | grep -c 'jawaban upstream (utuh)' || true)"
echo "   'payload POST (utuh'                     : $(echo "$STRINGS" | grep -c 'payload POST (utuh' || true)"
echo "-- narasi lama yang HARUS hilang (harus 0):"
echo "   'tidak tahu harus meneruskan'            : $(echo "$STRINGS" | grep -c 'tidak tahu harus meneruskan' || true)"
echo "   'meneruskan Play ke sini'                : $(echo "$STRINGS" | grep -c 'meneruskan Play ke sini' || true)"
echo "-- CRC penting (classes.dex game & config endpoint WAJIB identik dengan dasar):"
unzip -v "$BASE" | grep -E "classes\.dex |config_BS\.properties|config\.properties|QHinfo" || true
echo "-- CRC APK BARU:"
unzip -v "$NEWAPK" | grep -E "classes\.dex |config_BS\.properties|config\.properties|classes2\.dex|server/payload|dashboard|QHinfo" | head -20 || true
echo "-- isi QHinfo baru (harus http://127.0.0.1:8610):"
unzip -p "$NEWAPK" assets/QHinfo; echo

echo "[8/8] terbitkan ..."
cp "$NEWAPK" "$OUT"
echo "SELESAI: $OUT"
ls -la "$OUT"
sha256sum "$OUT"

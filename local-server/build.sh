#!/usr/bin/env bash
# =============================================================
# Build APK-LSF 0.4.6 — MODEL FINAL LOG (grup batch + alur linear)
#
# Dasar: releases/APK-LSF-0.3.2.apk (sudah memuat manifest provider +
# endpoint 127.0.0.1:8080 + dex game asli + mirror www/ 31 file).
# Bila file dasar belum ada:
#   unduh https://github.com/anztdree/db-apk/releases/download/v0.3.2/APK-LSF-0.3.2.apk
#   (sha256 b64cba53bf96fd6de3bcb5b15c33f6bb9f84c1c170a7808fea7b897dc9ab96d2)
# Patch yang diterapkan (TANPA apktool — dex game & resource tidak disentuh):
#   1) javac(ECJ) + d8  -> classes2.dex baru (kode server lokal)
#   2) ganti assets/server/dashboard.html
#   3) tambah assets/server/www/** — MIRROR PENUH server asli (31 file, ±56 MB)
#   4) tambah assets/server/login/** — DEFAULT LOGIN LOKAL (servers.json)
#   5) assets/QHinfo → http://127.0.0.1:8610 (0.4.2: layar SDK asli → server SDK lokal)
#   6) buang tanda tangan lama -> zipalign + sign (keystore tetap)
#
# Baru 0.4.6 (model final log — kesepakatan owner:
#   "group itu kalau terdeteksi emang config/resource yg banyak. kalau alur ya
#   gk usah di group"):
#   a) LogGroups.java — GRUP BATCH: mirror CDN / request game / 404 fallback /
#      passthrough ONLINE tidak pernah jadi baris log linear; satu baris judul
#      dgn statistik hidup di dasbor (diklik = rincian per-file, cap 600 item),
#      human.log = ringkasan Σ satu baris per periode diam (45 dtk), gagal
#      NAIK OTOMATIS sebagai ⚠ linear (jeda 30 dtk), grup kosong tak tampil.
#   b) GameServer — per-file 200/404/seed/passthrough pindah ke grup; baris
#      PENTING tetap linear (setting bin hilang, 404 tak terpetakan, 502, 500).
#   c) API dasbor baru: GET /api/groups; /api/logs/clear ikut mengosongkan grup.
#   d) DASBOR dirombak sesuai model: panel status atas (game/login/sesi),
#      alur linear, grup lipat di bawahnya; bukti teknis dilipat di bawah.
#      Copy = cerita alur + ringkasan Σ per grup — rincian per-file di dasbor.
#   e) Dedupe "loginchecknative OK" beruntun (uid sama < 10 dtk = satu baris).
#   f) HumanLog.writeRawLine (Σ grup → human.log tanpa memori/jsonl).
#
# Baru 0.4.5 (laporan owner "jujur terus terang log anda bagi saya cuma sampah
# apalagi UI website banyak informasi yg tidak bermanfaat.. perlu susun rencana"):
#   a) LOG DUA TINGKAT (HumanLog): PENTING selalu tampil (boot dipadatkan ±5 baris,
#      mode, tamu/daftar/masuk/loginchecknative, daftar server, ⚠/500);
#      TEKNIS (request per-file 200/404/seed, probe DNS, denyut pantauan, geser
#      tombol) hanya saat mode RINCI — toggle dasbor, tersimpan server.json
#      "logTech". FILE logs/human.log TETAP LENGKAP (bukti tidak pernah hilang).
#   b) PENGHITUNG DASBOR (StatusStore): mirrorSeeded/mirrorSeedBytes/notFound —
#      aktivitas mirror terlihat TANPA membanjiri log (dulu 100+ baris GET JSON).
#   c) API DASBOR BARU: /api/logtech (on=0|1) + /api/logs/file?tail= (ekor
#      human.log penuh untuk "Copy penuh" — penting+teknis dari disk).
#   d) DASBOR DIROMBAK (dashboard.html): Log jadi pusat (filter Semua/⚠ saja +
#      toggle RINCI + Copy + Copy penuh + Bersihkan), strip status satu baris,
#      tab Data (dragonball.db + server asli) & Teknis (root/port/penghitung,
#      6 request terakhir, daftar www/ dilipat). Kartu besar berulang DIHAPUS.
#   e) harness LULUS penuh (+9 tes: dua tingkat log, file tetap lengkap,
#      tailText, logTech persisten).
#
# Baru 0.4.4 (laporan owner "login register gak work .. ini jelas PR"):
#   a) BEDAH DEX: tombol MASUK/DAFTAR layar SDK ASLI memakai EMAIL sebagai
#      nama akun (fragmen timidcompletely/navyshop + validator
#      feedtonight.singersmooth: regex email + sandi min 6) — server lokal
#      dulu menolak '@' → DAFTAR selalu 40102, tanpa akun email → MASUK
#      selalu 40101. FIX: SdkData.registerUser menerima email (huruf kecil,
#      [a-z0-9_.+-@], 3-64), loginByName tidak peka huruf besar/kecil.
#   b) SdkApi log verbose MASUK/DAFTAR (sukses/gagal + sebab) — dulu gagal diam.
#   c) Cleaner whitelist + servers.json (dulu dihapus-restor tiap boot —
#      suntingsan pemilik hilang; janji "boleh disunting" patah).
#   d) harness 63/63 LULUS (+15 tes: email register/login/case-insensitive
#      + whitelist Cleaner).
#
# Baru 0.4.3 (permintaan owner "ini database yg profesional lahh masak iyaaa
# database txt" — nama FINAL dari owner: dragonball.db):
#   a) DATABASE SQLITE — config/dragonball.db (engine bawaan Android, nol
#      tambahan APK): tabel accounts, sdk_guests, sdk_sessions, kv (Db.java +
#      DbAndroid.java). Menggantikan accounts.txt/local-user.txt/sdk-*.txt
#      yang rapuh (tulis-ulang penuh, tanpa lock, parsing tab, TIDAK tahan
#      reboot — dulu ikut terhapus Cleaner karena tidak masuk whitelist!)
#   b) MIGRASI OTOMATIS — boot pertama membaca txt warisan → diimpor ke
#      dragonball.db → file di-rename *.imported (data tidak hilang); dibuka
#      SEBELUM Cleaner (Starter 1a2)
#   c) WHITELIST — Cleaner kini melindungi dragonball.db (+jurnal) di config/
#   d) DbJdbc.java (engine JDBC untuk harness desktop) TIDAK dimasukkan ke
#      classes2.dex (disaring di bawah); DbMem = jaring pengaman terakhir
#   e) harness 50/50 LULUS (local-server/harness/HarnessSdk.java — di-commit):
#      migrasi, reboot-persistence, SQL nyata (JDBC), regresi SDK 0.4.2 penuh
#
# Koreksi 0.4.2 (umpan balik owner "SDK itu lebih dulu ..."): layar SDK asli
#   duluan — semua tahap SDK lokal skema salinan asli (README §2.11).
# Koreksi 0.4.1: FIX servers.json + loop accept tahan Throwable + watchdog port.
#
# Prasyarat: mirror www/ tersedia (assets/server/www — dari APK 0.4.1 atau
# tools/snapshot.sh). TOOLS memuat android.jar + r8.jar (+ sqlite-jdbc.jar
# opsional untuk harness; TIDAK dipakai d8).
# =============================================================
set -euo pipefail

REPO="${REPO:-/home/z/db-apk}"
TOOLS="${TOOLS:-/home/z/tools2}"                       # android.jar + r8.jar
UBER="${UBER:-$REPO/tools/uber-apk-signer-1.3.0.jar}"
ECJ="${ECJ:-$REPO/tools/ecj-3.33.0.jar}"               # compiler Java 8 (sandbox tanpa JDK)
KS="${KS:-$REPO/keystore/lsf.keystore}"
KS_ALIAS="${KS_ALIAS:-lsf}"
KS_PASS="${KS_PASS:-lsf-local-2025}"
BASE="${BASE:-$REPO/releases/APK-LSF-0.3.2.apk}"         # APK dasar (rilis 0.3.2)
LOGIN_PORT_QH="${LOGIN_PORT_QH:-8610}"                    # port server SDK lokal (QHinfo)
SRC="$REPO/local-server/src/main/java"
ASSETS="$REPO/local-server/assets"
BUILD="$REPO/build"
DIST="$REPO/dist"
OUT="$REPO/releases/APK-LSF-0.4.6.apk"
MIN_API="${MIN_API:-21}"

WWW="$ASSETS/server/www"
if [ ! -f "$WWW/bs/upgrade/all.zip" ] || [ ! -f "$WWW/bs/upgrade/base.zip" ]; then
  echo "mirror www/ belum lengkap — jalankan: bash tools/snapshot.sh"; exit 1
fi

echo "[1/8] ECJ compile (sandbox tanpa javac) ..."
rm -rf "$BUILD/classes" "$BUILD/dex" "$BUILD/stage" "$BUILD/harness"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/stage"
find "$SRC" -name '*.java' | sort > "$BUILD/sources.txt"
ECJ_CP="$TOOLS/android.jar"
if [ -f "$TOOLS/sqlite-jdbc.jar" ]; then
  ECJ_CP="$TOOLS/android.jar:$TOOLS/sqlite-jdbc.jar"   # DbJdbc (harness) butuh org.sqlite
fi
java -jar "$ECJ" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$ECJ_CP" -d "$BUILD/classes" @"$BUILD/sources.txt"

# harness JVM (uji SQLite nyata via JDBC) — di-compile tapi TIDAK masuk APK
java -jar "$ECJ" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$BUILD/classes:$TOOLS/sqlite-jdbc.jar" -d "$BUILD/harness" \
  "$REPO/local-server/harness/HarnessSdk.java" || echo "   (harness dilewati — sqlite-jdbc tidak ada)"

echo "[2/8] d8 -> classes2.dex (DbJdbc DISINGKIRKAN — hanya harness desktop) ..."
mapfile -t CLASS_FILES < <(find "$BUILD/classes" -name '*.class' ! -name 'DbJdbc.class' ! -path '*DbJdbc*')
java -cp "$TOOLS/r8.jar" com.android.tools.r8.D8 --release \
  --min-api "$MIN_API" --lib "$TOOLS/android.jar" \
  --output "$BUILD/dex" "${CLASS_FILES[@]}"
cp "$BUILD/dex/classes.dex" "$BUILD/stage/classes2.dex"

echo "[3/8] siapkan aset server (dashboard + mirror www/ + default login + QHinfo) ..."
mkdir -p "$BUILD/stage/assets/server"
cp "$ASSETS/server/dashboard.html" "$BUILD/stage/assets/server/dashboard.html"
cp -R "$WWW" "$BUILD/stage/assets/server/www"
if [ -d "$ASSETS/server/login" ]; then
  cp -R "$ASSETS/server/login" "$BUILD/stage/assets/server/login"
fi
# 0.4.2 ALUR SDK — QHinfo (host SDK quickgame) → server SDK lokal.
# Asli: http://qsdk.t4game.com (salinan di /tmp dari APK 0.4.1 untuk pembanding).
printf 'http://127.0.0.1:%s' "$LOGIN_PORT_QH" > "$BUILD/stage/assets/QHinfo"
if [ -f /tmp/qhinfo-asli.txt ]; then
  echo "   QHinfo asli: $(cat /tmp/qhinfo-asli.txt) → $(cat "$BUILD/stage/assets/QHinfo")"
fi

echo "[4/8] salin APK dasar + buang tanda tangan lama + hapus payload lama + QHinfo lama ..."
cp "$BASE" "$BUILD/work.apk"
zip -d "$BUILD/work.apk" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/MANIFEST.MF' >/dev/null 2>&1 || true
zip -d "$BUILD/work.apk" 'assets/server/payload/*' >/dev/null 2>&1 || true
zip -d "$BUILD/work.apk" 'assets/QHinfo' >/dev/null 2>&1 || true

echo "[5/8] sisipkan classes2.dex + dashboard + mirror www/ + login + QHinfo ..."
(
  cd "$BUILD/stage"
  # shellcheck disable=SC2046
  zip -X -0 ../work.apk classes2.dex assets/QHinfo assets/server/dashboard.html \
    $(cd "$BUILD/stage" && find assets/server/www assets/server/login -type f | sort) >/dev/null
)

echo "[6/8] zipalign + sign (keystore tetap) ..."
rm -rf "$DIST"; mkdir -p "$DIST"
java -jar "$UBER" -a "$BUILD/work.apk" \
  --ks "$KS" --ksAlias "$KS_ALIAS" --ksPass "$KS_PASS" --ksKeyPass "$KS_PASS" \
  --out "$DIST"

echo "[7/8] verifikasi (dex game & config endpoint WAJIB identik dengan dasar) ..."
NEWAPK="$DIST/work-aligned-signed.apk"
echo "-- CRC di APK DASAR:"
unzip -v "$BASE" | grep -E "classes\.dex |config_BS\.properties|config\.properties|QHinfo" || true
echo "-- CRC di APK BARU (classes.dex/config harus sama; classes2.dex/dashboard/www/QHinfo = baru; payload lama = hilang):"
unzip -v "$NEWAPK" | grep -E "classes\.dex |config_BS\.properties|config\.properties|classes2\.dex|server/payload|server/www|dashboard|QHinfo" | head -45 || true
echo "-- isi QHinfo baru (harus http://127.0.0.1:8610):"
unzip -p "$NEWAPK" assets/QHinfo; echo
echo "-- payload lama masih ada? (harus kosong):"
unzip -l "$NEWAPK" | grep "server/payload" || echo "(bersih — payload lama sudah dihapus)"

echo "[8/8] terbitkan ..."
cp "$NEWAPK" "$OUT"
echo "SELESAI: $OUT"
ls -la "$OUT"
sha256sum "$OUT"

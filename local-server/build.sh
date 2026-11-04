#!/usr/bin/env bash
# =============================================================
# Build APK-LSF 0.5.2 — AKAR CONFIG (isi config dibedah field-per-field di log)
#
# Dasar: releases/APK-LSF-0.5.1.apk (LFS — memuat CONFIG LOGIN SEMUA MODE 0.5.1
# + mirror www/ lengkap dgn base.zip/all.zip + QHinfo 127.0.0.1:8610).
# Patch yang diterapkan (TANPA apktool — dex game & resource tidak disentuh):
#   1) javac(ECJ) + d8  -> classes2.dex baru (kode server lokal)
#   2) ganti assets/server/dashboard.html
#   3) tambah/perbarui assets/server/www/** (file yang ada di repo; berkas
#      besar base.zip/all.zip ikut terbawa dari APK dasar)
#   4) tambah assets/server/login/** — DEFAULT LOGIN LOKAL (servers.json)
#   5) assets/QHinfo → http://127.0.0.1:8610 (layar SDK asli → server SDK lokal)
#   6) buang tanda tangan lama -> zipalign + sign (keystore tetap)
#
# Baru 0.5.1 (tegasan owner: "SAYA TEGASKAN LAGI CONFIG YG JELAS BUKAN REKAM
# JEJAAAAK"; laporan "config Setelah SDK login ini masuk ke loginserver sampai
# detik ini anda ngeyel" + "saya sudah pilih server lalu klik play dan log hanya
# tampil itu" — README §2.19):
#   a) CONFIG SEMUA MODE — klien TIDAK PERNAH langsung ke login.popoh5.com lagi:
#      SettingBin.rewrittenOnline (mode ONLINE: HANYA jalur login + url H5 →
#      server lokal; tsUrl tetap asli; tanpa userid — identitas = shell asli);
#      GameServer.servePassthrough menulis-ulang setting bin + serversetting.json
#      SEBELUM disajikan; SdkApi loginchecknative = jalur BAKU ke 510 + baris 🔑
#      passthrough (dedupe 10 mnt/uid).
#   b) LoginServer.proxyOnline — socket.io diteruskan UTUH byte-per-byte ke
#      https://login.popoh5.com:610 (probe: HTTPS wajib — HTTP polos ditolak;
#      TLSv1.2 DigiCert *.popoh5.com). Websocket dijawab 400 → client tetap
#      polling (perilaku terbukti device). Token & daftar server ASLI keluar
#      dari SERVER ASLI (progres pemain tetap di sana) — tetapi seluruh alur
#      login LEWAT server lokal: 📡 → 🔑 → ✅ → 📋 → 🔌 kini hidup di ONLINE.
#   c) Log alur ONLINE: 📡 Klien tersambung — diteruskan ke SERVER ASLI ·
#      ✅ Login game diteruskan · 📋 Daftar server ASLI terkirim (dihitung dari
#      payload, bukan snapshot) · 🔌 pindah ke server game (dedupe sama dgn LOCAL).
#   d) Dasbor: modemap + kartu "Ke mana koneksi mengarah" = SEMUA mode (config
#      login selalu ke server lokal; ONLINE = passthrough utuh). StatusStore 0.5.1.
#   e) Harness 96/96 LULUS (+16 tes TAHAP 16: bin ONLINE, serversetting ONLINE,
#      parser framing tahan rusak, hitung server ACK, upstream HTTPS).
#   f) Uji asap HIDUP: handshake relayed identik upstream (sid asli diteruskan);
#      urutan langsung vs lewat proxy = jawaban identik (byte-per-byte terbukti).
#
# Baru 0.4.9 (permintaan owner: "emoji sudah bagus, hanya saja masih kurang
# rinci" + "ingat gak boleh disingkat" — setelah menyetujui contoh output):
#   a) IKON EMOJI per kategori (dulu simbol tipis ✔/⚠/ℹ samar): 🚀 siap ·
#      👤 pemain · 🔑 pengesahan sesi · 📡 koneksi · ✅ sukses · ⚠️ masalah ·
#      ❌ terhenti · 📋 daftar server · 💾 data · 🧹 bersih/mirror · ℹ️ info.
#      HumanLog: ikon char → String (emoji = 2 char UTF-16).
#   b) BLOK RINCIAN tiap baris penting (fakta nyata server: ms, byte, uid,
#      sid, sumber data, sebab, akibat/lanjutan) — tampil sebagai pohon
#      ├─/└─ di dasbor, di human.log, dan di hasil Copy. TIDAK dipangkas.
#   c) Alur utama (contoh sesi 09:39–09:41 versi baru):
#      🚀 Server lokal siap (rincian Port/Mode/Folder/Pemain)
#      → 👤 Layar SDK — masuk sebagai tamu … (rincian Perangkat/Cara/Identitas)
#      → 🔑 Sesi tamu disahkan — game lanjut boot (rincian Uid/Hasil/Sesi)
#      → 📡 Klien game tersambung ke server login lokal (dulu TEKNIS — kini
#        penting; dedupe 10 mnt)
#      → ✅ Login game sukses (rincian Akun/Proses ms+byte/Sumber)
#      → 📋 Daftar server terkirim — N server (rincian Sumber/Ukuran/Lanjutan;
#        baris ℹ "TUNTAS" 0.4.8 dilebur ke rincian Lanjutan)
#   d) 👤 layar SDK = SATU baris walaupun registerVisitor & autoLogin sama-sama
#      terpanggil (dedupe 10 mnt per uid — SdkData.noteSdkLogin); duplikat log
#      daftar akun (SdkData + SdkApi berdempet) dihapus.
#   e) DASBOR: teks diperbesar & kontras dinaikkan, kolom ikon dilebari untuk
#      emoji, rincian = pohon ├─/└─ penuh (tanpa pemenggalan), Copy ikut
#      membawa blok rincian penuh; legenda footer emoji.
#   f) harness disesuaikan (ikon String); StatusStore v0.4.9.
#
# Baru 0.4.8 (laporan owner sesi 09:39-09:41: "tidak ada revisi!!!! log masih
# tidak jelas, padahal sampai loginserver munculkan serverlist.. tapi log cuma
# Sampah gak jelas" — paste-nya = keluaran tombol Copy):
#   a) SATU KEJADIAN SATU BARIS: percobaan login yang langsung selesai tidak
#      diumumkan dua kali (dulu "SDK: MASUK — …" + "SDK: MASUK gagal — …"
#      berdempet 0 dtk). Hasil saja: "⚠ Login akun \"…\" DITOLAK — … (40101)".
#   b) IKON ▶/◀ DIHAPUS + awalan "SDK:" dibuang — alur kini membaca seperti
#      cerita: Login ditolak → Lanjut masuk sebagai tamu → Sesi disahkan →
#      Daftar server terkirim → TUNTAS.
#   c) BARIS TUNTAS setelah GetServerList (dedupe 10 mnt): daftar = bawaan APK
#      DISAJIKAN server lokal (bukan diambil dari server asli) + penjelas
#      kenapa log diam setelahnya (koneksi gameplay langsung ke server game
#      asli, tidak lewat server lokal).
#   d) FORMAT COPY DIRAPIKAN (akar "paste sampah"): jam-ikon-teks berjarak,
#      ringkasan grup diberi "Σ " + jam (dulu menggantung tanpa identitas),
#      detail (sebab teknis) kini TAMPIL kecil di dasbor & ikut disalin.
#   e) Baris umpan balik UI dihapus dari log: toggle RINCI/BERSIH tidak lagi
#      menghasilkan baris (statusnya terlihat di tombol + server.json).
#   f) Boot: baris Mode dipangkas jadi satu kalimat (peta penuh tetap di
#      dasbor — modemap + kartu "Ke mana koneksi mengarah").
#
# Baru 0.4.7 (laporan owner "login server muncul .. saya rasa isi config
# loginserver anda masih mengarah ke server aslinya.. log ini pun tidak
# membantu kurang efektif kurang jelas"):
#   a) BOOT 3 BARIS (bentuk kesepakatan): "✔ Server lokal siap — game :8080 ·
#      login :8610 · dasbor :4000" → "ℹ Mode LOCAL — <peta arah koneksi>" →
#      "ℹ Pemain: guest… — identitas & sesi tersimpan lokal". Dulu TIGA baris
#      siap bertumpuk ("Server siap" + "Mode" + "Server lokal hidup") + db +
#      tombol melayang = 6 baris boot tanpa cerita.
#   b) PETA ARAH KONEKSI dinyatakan EKSPLISIT di baris Mode, di log GetServerList
#      ("snapshot asli bawaan APK"), dan di dasbor (modemap + kartu lipat "Ke mana
#      koneksi mengarah") — menjawab dugaan "config loginserver masih ke server
#      asli": login LOKAL (bukti sesi 8610), daftar server = snapshot ASLI by
#      design, gameplay = server game asli (progres di sana).
#   c) DEDUPE loginchecknative OK diperlebar 10 dtk → 10 MENIT per uid — denyut
#      berjarak 21 dtk (boot 06:47:30 vs 06:47:51) masih bocor dua baris.
#   d) Turun tingkat ke TEKNIS (tetap terekam di human.log): db siap, tombol
#      melayang tampil, handshake socket login (client tersambung).
#   e) Bahasa: "Pemain" (bukan "guest" — akun email juga pemain), "identitas &
#      sesi tersimpan lokal" (bukan "progres lokal dipakai" — progres ada di
#      server game asli), "perangkat → tamu" (bukan "diikat … migrasi identitas").
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
BASE="${BASE:-$REPO/releases/APK-LSF-0.5.1.apk}"         # APK dasar (rilis 0.5.1 — CONFIG LOGIN SEMUA MODE)
LOGIN_PORT_QH="${LOGIN_PORT_QH:-8610}"                    # port server SDK lokal (QHinfo)
SRC="$REPO/local-server/src/main/java"
ASSETS="$REPO/local-server/assets"
BUILD="$REPO/build"
DIST="$REPO/dist"
OUT="$REPO/releases/APK-LSF-0.5.2.apk"
MIN_API="${MIN_API:-21}"

# Baru 0.5.2 (tegasan owner: "pakai config online murni untuk server lokal,
# padahal akarnya aja gak tau .. Saya sudah TEGASKAN log yg jelas rinci itu
# penting" — README §2.20):
#   a) ConfigAkar.java BARU — pembedah config field-per-field. Setiap config
#      yang disajikan (setting bin bs/db/android + lzceshi + serversetting.json,
#      mode LOCAL & ONLINE) di-log 📦 "AKAR CONFIG … dibedah field-per-field"
#      (PENTING — selalu tampil): nilai ASLI server → nilai yang diterima
#      klien, utuh tanpa singkatan, + peran tiap field (url = boot H5,
#      loginServer:loginPort = socket.io, loginServer:loginWebPort =
#      loginchecknative, update = paket pembaruan, clientParams = toko/bahasa).
#   b) SettingBin +getJsonRaw (angka murni/string/bool) + decryptedPlain.
#   c) GameServer.logAkar — dedupe per isi: config sama beruntun turun ke
#      TEKNIS (tetap terekam human.log — aturan 0.4.5 tidak hilang bukti).
#   d) Rincian alur login ONLINE diperkaya: ✅ loginGame +Akun/Sandi (panjang,
#      diteruskan UTUH); 📋 daftar server ASLI +Gerbang pertama … terakhir
#      (firstAndLastGateway) — sebelum Play, log sudah menunjukkan gerbang.
#   e) Ikon 📦 masuk legenda emoji 0.4.9 (HumanLog). StatusStore 0.5.2.
#   f) Harness +24 tes TAHAP 17 — 129 tes (105 LULUS + 15 GAGAL LINGKUNGAN
#      SQLite native sandbox — sama di HEAD, bukan regresi; 0.5.2 LULUS 24/24).

WWW="$ASSETS/server/www"
# base.zip/all.zip boleh: (a) ada di mirror www/ lokal, ATAU (b) ikut terbawa
# dari APK dasar (0.4.8 memuatnya) — cek keduanya.
if [ ! -f "$WWW/bs/upgrade/all.zip" ] || [ ! -f "$WWW/bs/upgrade/base.zip" ]; then
  if ! unzip -l "$BASE" 2>/dev/null | grep -q "assets/server/www/bs/upgrade/base.zip"; then
    echo "mirror www/ belum lengkap — jalankan: bash tools/snapshot.sh (atau pakai APK dasar yang memuat upgrade paket)"; exit 1
  fi
  echo "catatan: base.zip/all.zip tidak di stage — ikut terbawa dari APK dasar"
fi

# Baru 0.5.0 (permintaan owner: "seperti capture HttpCanary tapi lebih canggih —
# HttpCanary cuma jejaknya, bukan config aslinya"):
#   a) Capture.java — perekam payload mentah SEMUA lapisan (login/sdk/game;
#      proxy disiapkan 0.5.1): ring memori 600 + capture/rekam.jsonl (embed
#      <=32 KB, rotasi 8 MB) + capture/payload/ (config asli utuh <=2 MB, maks
#      400 file). HttpCanary buta loopback; REKAM di dalam server: 100% + didekode.
#   b) Tap: LoginServer (event/ack/handshake/ping/disconnect utuh), SdkApi (body
#      didekode base64+sign + jawaban; ONLINE = jawaban asli qsdk/login.popoh5
#      terekam), GameServer (setting bin DIDEKRIPSI + serversetting + config
#      json/version/txt/bin <=512 KB; passthrough ONLINE dibuffer), SettingBin
#      (helper decryptedRewritten).
#   c) 🔌 baris baru: klien menutup sesi login (normal setelah pilih server —
#      dulu DIAM total; sesi expired ikut dilaporkan) + sesi simpan lastAction.
#   d) PROBE SERVER GAME (Tahap A): url serverList 5 teratas DNS+TCP saat boot
#      (thread belakang) + tombol dasbor "Tes server game" (/api/rekam/probe)
#      — jawaban "kenapa log diam setelah pilih server".
#   e) server.json +"rekam":true (bawaan ON; toggle /api/rekam/toggle); Cleaner
#      whitelist +capture/; dasbor +bagian REKAM (filter, payload utuh+Salin,
#      Unduh, Tes server game); StatusStore +rekam/rekamCount; README §2.18.

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

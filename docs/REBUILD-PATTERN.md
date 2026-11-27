# POLA REBUILD db-local — panduan belajar owner (27 Nov 2026, Bagian 74)

Ditulis karena arahan owner: *"ana tinggal belajar pola rebuild lalu tinggal
lanjutkan port log debug"* — jadi ini peta LENGKAP alur rebuild, dari sumber
Java sampai APK bertanda tangan. Semua langkah sudah terbukti v14→v26.

## 0. Peta folder

```
rebuild-src/
├── javasrc/com/db/local/server/   ← SUMBER KODE server mod (26 file Java)
├── stubs/android/…                ← 8 stub kelas Android (compile-only;
│                                     tidak masuk dex — device punya aslinya)
├── scripts/
│   ├── patch_marker_v26.py        ← rotasi versi + header dex + verifikasi
│   └── rebuild_v26_raw.py         ← sisip dex ke APK base byte-per-byte
├── smoke/run_smoke.py + SmokeMain.java  ← uji desktop (server jalan di JVM)
├── verify_capture_v26.js          ← uji browser nyata (Playwright/Chromium)
└── build-smoke/                   ← hasil compile class untuk smoke
release-mod/
├── db-local(MOD-LOCALSERVER-vNN).apk  ← hasil final (Git LFS)
├── dblocal-mod.keystore               ← kunci sign (SAMA sejak v14 — wajib,
│                                         agar install-over antar versi aman)
└── server.ndjson                      ← contoh log device owner
docs/ + readme.md                   ← catatan Bagian per Bagian (bukan karangan)
verify-artifacts/                   ← bukti build + smoke tiap versi
```

## 1. Rantai build (urutannya TIDAK boleh dibalik)

```
javasrc + stubs
   │  1. javac -encoding UTF-8 -source 8 -target 8   (javac 21.0.13)
   ▼
*.class  (52 file = 42 javasrc + 10 stub)
   │  2. jar cf classes.jar .
   │  3. java -cp r8.jar com.android.tools.r8.D8 --release --min-api 21
   ▼
classes.dex  (fresh D8, masih ber-version "v19" ×4)
   │  4. patch_marker_v26.py   (SATU langkah, TIGA pekerjaan:)
   │     a. rotasi marker  v19→v26  (tepat 4 string user-visible:
   │        judul dashboard, banner, baris BOOT, header crash)
   │     b. cek KONTRAK string (fitur wajib harus ada di dex — kalau ada
   │        yang hilang = build BERHENTI, tidak peduli alasan)
   │     c. header dex sesuai SPEC + verifikasi struktur penuh
   ▼
classes2.dex
   │  5. rebuild_v26_raw.py    (base APK v23 + dex baru, sisanya
   │     byte-per-byte IDENTIK; verifikasi: beda HANYA classes2.dex)
   ▼
v26_unsigned.apk
   │  6. uber-apk-signer 1.3.0 --zipalign --sign (keystore dblocalmod)
   ▼
db-local(MOD-LOCALSERVER-v26).apk  → release-mod/ + LFS
   │  7. smoke desktop + E2E browser → verify-artifacts/
   ▼
commit + readme Bagian berikutnya
```

## 2. Tiga aturan yang menyelamatkan (warisan luka v24)

1. **Header dex = SPEC, bukan karangan.**
   - SHA-1  ditulis @offset 12..32, dihitung atas `d[32:]` s/d akhir file.
   - adler32 ditulis @offset 8..12,  dihitung atas `d[12:]` s/d akhir file.
   - DILARANG menulis apa pun SETELAH header — 20 byte terakhir dex adalah
     ekor map_list. v24 menimpanya dengan SHA1 → ART menolak dex → force
     close INSTAN tanpa satu baris log (karena LoggerCore juga di dex itu).
2. **Dex hasil patch WAJIB diverifikasi strukturnya** (v25+): checksum &
   signature dihitung ulang, map_list di-parse penuh (17 entri berakhir
   tepat di EOF), section header dalam batas. Gagal = sys.exit — dex cacat
   TIDAK PERNAH sampai ke APK.
3. **JS hasil generate WAJIB di-check sintaksnya** (v26+, pelajaran baru):
   console dashboard 4000 mati TOTAL di v24–v25 hanya karena SATU kutip
   tidak ditutup (`icon:'check}}` — seharusnya `icon:'check'}}`). Smoke
   desktop tidak menangkapnya karena menguji JSON server, bukan JS browser.
   Sekarang: E2E mengeksekusi `<script>` dashboard dengan `new Function()`
   — sintaks rusak = FAIL.

## 3. Marker versi — mekanisme rotasi

Sumber Java SELALU menulis "v19" (4 tempat). patch_marker memutar v19→vNN.
Jadi:
- JANGAN mengganti "v19" di sumber — biarkan; patch yang menggantinya.
- JANGAN menambah/menghapus satu pun "v19" (assert count == 4 akan gagal).
- JANGAN memakai token v18..v26 lain di STRING yang masuk dex (komentar
  Java boleh — komentar tidak masuk dex).

## 4. Kontrak string (cara kerjanya)

patch_marker_v26.py punya daftar string yang WAJIB ada di dex
(`/log/client`, `__dbClientCapture`, `icon:'check'}}`, `device halaman
game: ua=`, dst). Fungsi: kalau refactor tidak sengaja menghapus fitur,
build BERHENTI di kontrak — bukan di device owner. Menambah fitur baru =
tambah kontrak baru = fitur terjaga selamanya.

## 5. Uji (dua lapis, keduanya wajib hijau)

| Lapis | Apa | Menangkap |
|---|---|---|
| smoke desktop (`run_smoke.py`) | server jalan di JVM; 263+ kasus HTTP/NDJSON/logika | logika server, format, kontrak |
| E2E browser (`verify_capture_v26.js`) | Chromium nyata; halaman game + dashboard 4000 | JS sintaks, render DOM, hook halaman, POST nyata |

## 6. Ringkasan "bikin versi baru" (checklist)

1. Edit `javasrc/...` (ingat: tetap tulis "v19" di marker).
2. Compile → jar → D8 → **patch_marker_vNN.py** (kontrak + verifikasi).
3. **rebuild_vNN_raw.py** (base v23 + dex; beda hanya classes2.dex).
4. zipalign + sign (keystore SAMA).
5. smoke desktop + E2E browser → verify-artifacts/ (tempel output penuh).
6. readme Bagian berikutnya (fakta apa adanya) + commit + rotasi LFS.

## 7. Perangkat (reproduksi toolchain dari nol)

- javac 21.0.13 — `openjdk-21-jdk-headless_21.0.13+9-1~deb13u2_amd64.deb`
  (+ jre-headless) → `dpkg -x`, pakai `LD_LIBRARY_PATH` hasil ekstraksi.
- D8 8.3.37 — `storage.googleapis.com/r8-releases/raw/8.3.37/r8.jar`.
- uber-apk-signer 1.3.0 — GitHub releases patrickfav.
- git-lfs 3.6.1 — untuk rotasi pointer APK di release-mod/.
- Base APK v23 — dari LFS history via batch API + `tools/download_chunked2.py`
  (sha256 `722eee78…` = pointer LFS v23; rantai yang terbukti jalan).

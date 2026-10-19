# Resep Build APK Offline — DB-Local (Super Fighter Idle)

> Aturan utama proyek: **ikuti aturan APLIKASI, bukan aturan sendiri.**
> Semua file dari server diambil **1:1 asli tanpa mengubah isi** (lihat `../server/VERIFIKASI.md`).
> Hanya ada **2 titik alamat** yang diarahkan ke server lokal:
> 1. `EntryPoint1` **+ `EntryPoint2` + `EntryPoint2_debug`** di `assets/config_BS.properties`
>    (semua EntryPoint = URL lokal yang sama — lihat Pelajaran 3: unduhan EntryPoint
>    TERAKHIR yang dipakai aplikasi)
> 2. Field `url` + `update` di `setting_BS_Android.bin` (versi "penyambung")

## Arsitektur

```
LaunchActivity.onCreate
  └─ (sisipan) com.db.local.MiniServer.start(context)
       └─ Thread daemon → ServerSocket 127.0.0.1:28110 (SO_REUSEADDR)
            └─ per koneksi: Thread(ConnRun) → MiniServer.handle(socket)
                 ├─ GET /bs/db/android/setting_BS_Android.bin  → mirror (penyambung)
                 ├─ GET /upgrade/{base.version,resource.version,size.json,upgrade.json,all.zip}
                 ├─ GET /bs/index-native.html, /bs/manifest.json, /bs/js/* (15 file)
                 └─ path lain → 404 (persis perilaku server asli, mis. com_db_local.bin)

Sumber baca `resolve()` — test7, SATU lapis (arahan owner: "baca bukan keluar lagi"):
  Android/data/com.db.local/files/server/<path>  →  404
  (seed `assets/mirror/` BUKAN sumber baca; hanya sumber ekstraksi bootstrap)

Gerbang siap (gated startup, test7):
  LaunchActivity.onCreate
    → MiniServer.start(ctx):
        1. bootstrapSync: ekstraksi assets/mirror/** → files/server/** SINKRON
           (salin-jika-belum-ada → override manual tidak pernah ditimpa;
            tmp+rename atomik → mustahil file setengah jalan;
            cap 8 detik + try/catch Throwable → tanpa FC/ANR)
        2. BARU thread server bind 127.0.0.1:28110
  → request pertama game pasti dilayani dari path server resmi.
    Run pertama +1–3 detik (tertutup splash bawaan game); run berikutnya <0,1 detik.
  Pola ini MENGIKUTI pola aplikasi asli: app sendiri memakai
  Android/data/com.db.local/files/game/ sebagai preloadPath (DecodeUtil
  mengekstrak assets game/*.bin ke situ, bukti smali) — folder server/ = sibling
  dengan pola yang sama.
```

- Semua method `MiniServer`/`ConnRun` dibungkus `try/catch Throwable` → **kode tambahan mustahil membuat force close**.
- Setiap request dicatat ke logcat: `Log.i("MiniServer", "<status> <path>")` → diagnosis mudah tanpa PC.
- Port 28110 dipilih acak-tetap (bukan port umum); bind loopback saja.

## Pelajaran penting (JANGAN diulang)

1. **FC test1 & test2:** `MiniServer.handle` tertulis `.method private static` padahal
   dipanggil lintas class oleh `ConnRun` → `IllegalAccessError` di koneksi pertama → FATAL
   EXCEPTION → proses mati. **Method yang dipanggil lintas class WAJIB `public`.**
   Selain itu: exception tak tertangkap di thread mana pun membunuh seluruh proses — makanya
   semua titik masuk dibungkus `try/catch Throwable`, termasuk `ConnRun.run()`.
2. **404 semua request (test3):** di `parsePath`, buang `/` depan tertulis
   `substring(length)` (hasil: string kosong) padahal maksudnya `substring(1)` → SEMUA
   request dianggap malformed → 404 → game fallback ke CDN (EntryPoint2).
   **Smali tulisan tangan WAJIB diuji logikanya** (port ke bahasa lain + jalankan kasus
   request nyata) sebelum dirilis. Kasus uji wajib: setting bin `?rnd=`, `all.zip?v=`,
   path `/`, traversal `..`, request rusak.
3. **"2 server" untuk setting bin (test4, dibuktikan HAR user):** server lokal sudah
   benar (200, 561 B) tapi game tetap pakai CDN. Aturan APLIKASI di `LaunchActivity`:
   ia mengunduh `setting_BS_Android.bin` **dan** `com_db_local.bin` (nama = packageName,
   titik→underscore) dari **SETIAP EntryPoint berurutan** (EP1 → EP2 → …), dan
   **buffer unduhan TERAKHIR yang dipakai** — field `worktopichardtails:[B` tertimpa
   terus. Jadi EP2 masih ke server asli ⇒ config CDN SELALU menang walau EP1 lokal 200.
   Catatan: `com_db_local.bin` 404 itu NORMAL — server asli pun 404 `NoSuchKey`
   (XML COS 452 B, bukti HAR) — dan aplikasi menganggapnya sukses (lanjut EP berikut).
   **Fix test5:** `EntryPoint2` & `EntryPoint2_debug` = URL lokal yang sama dengan
   `EntryPoint1`. Tanpa mengubah aturan aplikasi sama sekali.
   **Pelajaran metodologi: selalu minta/bedah HAR (atau logcat) sebelum menebak.**
4. **all.zip terpotong (test5, dibuktikan HAR+logcat user lalu direproduksi di lab):**
   logcat: `I/MiniServer: 200 upgrade/all.zip` (server SELESAI menulis seluruh
   19.385.098 B — `logReq` dipanggil setelah `respond`) lalu **128 ms** kemudian klien:
   `java.net.ProtocolException: unexpected end of stream` di
   `okhttp Http1xStream$FixedLengthSource.read` = EOF padahal Content-Length belum
   terpenuhi. HAR: entri all.zip `200 OK` + `Content-Length: 19385098` tapi body tidak
   pernah sampai utuh ke klien.
   **Akar (terbaca dari smali, bukan teori):** `readLine` membaca byte-per-byte dan
   berhenti di baris pertama request → ±200 B sisa header request tetap tertinggal di
   antrean terima socket server → `close()` dengan data belum terbaca memicu TCP
   **RST** (bukan FIN) → ekor data response yang masih di buffer klien/in-flight
   dimusnahkan.
   **Lab** (`rstlab/lab.py`, replika semantik 1:1, header request persis HAR, klien
   lambat ±13 MB/s seperti perangkat): logika lama TERPOTONG 3/3 (kurang 427–558 KB,
   `Connection reset by peer`); logika baru UTUH 3/3 tepat 19.385.098 B. Klien cepat
   kadang utuh karena menang race — di perangkat (klien menulis ke disk) selalu rugi.
   **Fix test6 (2 titik di `handle`, tanpa mengubah hal lain):**
   (a) setelah membaca baris pertama, habiskan sisa header sampai baris kosong/EOF
   (mencegah RST);
   (b) `shutdownOutput()` sebelum `close()` (data antrean kirim dikirim dengan FIN
   normal).

## Isi folder ini

| File | Fungsi |
|---|---|
| `smali/MiniServer.smali` | Server mini (start, bootstrapSync+copyDir, run/accept-loop, handle, resolve, respond, log) |
| `smali/ConnRun.smali` | Runnable per-koneksi (pemanggil handle) |
| `rstlab/lab.py` | Lab pembuktian RST (replika 1:1 semantik MiniServer + klien header persis HAR) |
| `rstlab/lab_bootstrap.py` | Lab uji logika bootstrap 6 kasus (port setia dari smali) |
| `mirror/bs/db/android/setting_BS_Android.bin` | Versi penyambung (XOR "DragonBall", hanya url+update lokal) |
| `make_setting_bin.py` | Generator penyambung dari file asli (bukti transform 1:1) |

## Langkah build (dari APK asli)

Tool: `smali.jar` + `baksmali.jar` (JesusFreke/smali 2.5.x), `uber-apk-signer-1.3.0.jar`.

1. **Patch `classes.dex`** (class LaunchActivity):
   - `baksmali d` classes.dex asli →
   - di `com/zhuhuan/game/LaunchActivity.smali`, method `onCreate`, tepat setelah
     `invoke-super {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->onCreate(...)V`
     sisipkan satu baris:
     ```smali
     invoke-static {p0}, Lcom/db/local/MiniServer;->start(Landroid/content/Context;)V
     ```
   - `smali a` → classes.dex baru.
2. **Suntik `classes2.dex` baru** dari folder `smali/` ini:
   ```bash
   java -jar smali.jar a smali -o classes2.dex
   ```
3. **Siapkan assets** (zip-level, tanpa apktool):
   - Ganti `assets/config_BS.properties` — **EntryPoint1, EntryPoint2, EntryPoint2_debug
     SEMUANYA** = `aHR0cDovLzEyNy4wLjAuMToyODExMC9icy9kYi9hbmRyb2lk`
     (= base64 dari `http://127.0.0.1:28110/bs/db/android`).
     WAJIB ketiganya: aplikasi mengunduh dari semua EntryPoint berurutan dan unduhan
     TERAKHIR yang dipakai (Pelajaran 3).
   - Tambahkan `assets/mirror/` (26 file: 23 asli dari `../server/` + 3 panenan
     test7 — lihat `../server/VERIFIKASI.md` bagian panenan):
     - `mirror/bs/db/android/setting_BS_Android.bin` ← pakai **penyambung** dari folder ini
     - `mirror/upgrade/{base.version, resource.version, size.json, upgrade.json, all.zip}`
     - `mirror/bs/{index-native.html, manifest.json, js/…}` (15 file JS)
4. **Rakit & tanda tangani**:
   ```bash
   cp DB-Local.apk kerja.apk
   zip kerja.apk classes.dex classes2.dex          # dari folder staging root
   zip -r kerja.apk assets                          # tambahan mirror + config
   java -jar uber-apk-signer.jar -a kerja.apk       # zipalign + sign v1/v2/v3
   ```
5. **Verifikasi wajib sebelum rilis** (semua harus ✅):
   - `baksmali d` classes2.dex hasil → `bootstrapSync`/`copyDir` ada, `resolve` TANPA `getAssets` ✔
   - 26 file `assets/mirror/` ada, SHA `all.zip` + 3 panenan cocok `../server/VERIFIKASI.md` ✔
   - `config_BS.properties` decode → EntryPoint1 lokal ✔
   - `lib/arm64-v8a/libegret.so`, `lib/armeabi-v7a/libegret.so` utuh ✔
   - signature verified v1+v2+v3 ✔

## Alur yang diharapkan saat dijalankan

1. Buka app → **bootstrap sinkron** (logcat `MiniServer: bootstrap: N file disalin`;
   run pertama 26, run berikutnya 0) → server bind → baru game jalan.
2. Game baca `config_BS.properties` → minta `setting_BS_Android.bin?rnd=` → **200**
   (dilayani dari `files/server/`).
3. Cek versi: `base.version` (110), `resource.version` (11390 > 11220 bawaan).
4. Unduh `all.zip` ±19,4 MB (baru instal → fallback `all.zip`, bukan patch).
5. Ekstraksi → muat `index-native.html` → `manifest.json` → 15 JS → layar login.

---

## test8 — redirection murni (kondisi terbukti test6 + gerbang siap + audit fix)

**Prinsip**: hanya ganti ARAH endpoint lewat mekanisme config asli aplikasi; tidak ada logika baru
di luar yang terbukti. Seluruh audit forensik vs APK original (SHA `3650cbe5…`):
3.663/3.666 entry byte-identik; kode berubah = 1 sisipan 7 baris di LaunchActivity.

**Perubahan test7 → test8** (diff zip terverifikasi):
1. **Hapus 3 file panenan** (`clientversion.json`, `serversetting.json`, `sound_click.mp3`)
   → mirror kembali **23 file terbukti test6** (SHA 22/23 cocok VERIFIKASI.md;
   1 yang beda = setting bin, memang disengaja karena redirection).
2. **`config_BS.properties` restore 4 kunci** persis original
   (EntryPoint1, EntryPoint1_debug, EntryPoint2, EntryPoint2_debug — test7 kehilangan
   `EntryPoint1_debug`), semua nilai → `http://127.0.0.1:28110/bs/db/android`
   (lihat `config_BS.properties` di folder ini).
3. **`setting_BS_Android.bin` byte-preserving**: dibuat dari panenan asli `../server/configus/…`
   dengan substitusi TEPAT 2 substring (`url` + `update`); 711 → 692 B; field lain byte-asli.
4. **`MiniServer.resolve()`**: fallback assets dikembalikan (semantik test6 yang terbukti):
   `files/server` → `assets/mirror` → 404. Menutup risiko 404 palsu bila salinan bootstrap
   belum tuntas (perangkat lambat). Sumber byte identik → tak terlihat oleh aplikasi.
5. **Gerbang siap-dulu tetap** (bootstrapSync → bind → baru app jalan; salin-jika-belum-ada,
   atomik, cap 8 s) + fix test6 (drain header + shutdownOutput) tetap.

**Verifikasi rilis test8** (semua ✅):
- diff zip vs test7: −3 panenan; berubah hanya `classes2.dex`, `config_BS.properties`,
  `setting_BS_Android.bin`, META-INF (re-sign). Sisanya byte-identik.
- dex final = assemble sumber repo; baksmali: `try_assets` + `mirror/` + `bootstrapSync` +
  `shutdownOutput` + bind `127.0.0.1:28110` ✔
- `classes.dex` byte-identik test7 (sisipan LaunchActivity tak tersentuh) ✔
- `AndroidManifest.xml` byte-identik ORIGINAL ✔
- SHA-256 APK final: `682d284a96ac75b8acb6e6844341c1cf25ecfd905a2cc50b0a64d09c69ad6c6b`

**Cara pasang (WAJIB)**: **UNINSTALL dulu APK lama** (Android/data ikut terhapus → bootstrap
bersih 23 file; signature debug baru berbeda dari build lama) → install test8 → jalankan →
kirim HAR + logcat ke `log/`.

---

## test9 — fix VerifyError (penyebab FC test7 & test8)

**Penyebab FC test7 & test8** (bukti logcat perangkat `log/logcat_2026-10-19_08-17-34.txt`):
`java.lang.VerifyError` — verifier ART menolak kelas `MiniServer`: method `bootstrapSync`
offset 0x9 = `invoke-virtual {p0}` ke `Context.getExternalFilesDir(String)`
**tanpa register argumen String** (butuh 2 register: this + String, walau null).
Kelas ditolak saat load → FC di `LaunchActivity.onCreate` ±0,5 dtk, sebelum splash,
sebelum request pertama. Aplikasi tidak pernah jalan — konsep redirection/gerbang
tidak terbantahkan oleh dua FC ini (bootstrapSync rusak sejak test7, byte-identik).

**Pelajaran 5**: smali/baksmali TIDAK memverifikasi bytecode; hanya verifier ART
perangkat yang menolak. Wajib jalankan `python3 check_invoke_regs.py smali/*.smali`
sebelum SETIAP build (jumlah register tiap invoke vs signature, bounds register,
validitas pN, pairing move-result). Checker menangkap persis bug ini pada smali lama,
dan BERSIH pada smali test9 maupun hasil baksmali dex final.

**Perubahan test8 → test9**: HANYA `classes2.dex` — satu instruksi:
`const/4 v7, 0x0` + `invoke-virtual {p0, v7}, ...getExternalFilesDir(...)`
(pola identik dengan `resolve()` yang terbukti verify+run sejak test6).
Diff zip vs test8: **3.688/3.692 entry byte-identik**; berubah hanya `classes2.dex`
(6020 B, SHA `44339d7d…`) + 3 file META-INF (re-sign). config_BS 4 kunci, setting bin
692 B, 23 file mirror, `classes.dex`, AndroidManifest — semuanya byte-identik test8.

SHA-256 APK final: `a946ef6cc2d710e3b8812d836f654da42ec1f572370ef9f715e7ba3487c70b8e`

**Cara pasang (WAJIB)**: **UNINSTALL dulu test8** (Android/data bersih → bootstrap
23 file segar) → install test9 → jalankan → kirim logcat ke `log/`
(HAR bila sudah lolos tahap unduhan).

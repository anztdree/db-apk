# Resep Build APK Offline — DB-Local (Super Fighter Idle)

> Aturan utama proyek: **ikuti aturan APLIKASI, bukan aturan sendiri.**
> Semua file dari server diambil **1:1 asli tanpa mengubah isi** (lihat `../server/VERIFIKASI.md`).
> Hanya ada **2 titik alamat** yang diarahkan ke server lokal:
> 1. `EntryPoint1` di `assets/config_BS.properties`
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

Sumber berlapis resolve():
  1. Android/data/com.db.local/files/mirror/<path>   (slot konten tambahan, override assets)
  2. assets/mirror/<path>                             (sumber kebenaran saat install)
  3. 404
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

## Isi folder ini

| File | Fungsi |
|---|---|
| `smali/MiniServer.smali` | Server mini (start, run/accept-loop, handle, resolve, respond, log) |
| `smali/ConnRun.smali` | Runnable per-koneksi (pemanggil handle) |
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
   - Ganti `assets/config_BS.properties`:
     `EntryPoint1=aHR0cDovLzEyNy4wLjAuMToyODExMC9icy9kYi9hbmRyb2lk`
     (= base64 dari `http://127.0.0.1:28110/bs/db/android`)
     EntryPoint2 & EntryPoint2_debug **tetap asli** (fallback online).
   - Tambahkan `assets/mirror/` (23 file dari `../server/`):
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
   - `baksmali d` classes2.dex hasil → `.method public static handle` ✔
   - 23 file `assets/mirror/` ada, ukuran cocok dengan `../server/` ✔
   - `config_BS.properties` decode → EntryPoint1 lokal ✔
   - `lib/arm64-v8a/libegret.so`, `lib/armeabi-v7a/libegret.so` utuh ✔
   - signature verified v1+v2+v3 ✔

## Alur yang diharapkan saat dijalankan

1. Buka app → server mini hidup (logcat `MiniServer`).
2. Game baca `config_BS.properties` → minta `setting_BS_Android.bin?rnd=` → **200**.
3. Cek versi: `base.version` (110), `resource.version` (11390 > 11220 bawaan).
4. Unduh `all.zip` ±19,4 MB (baru instal → fallback `all.zip`, bukan patch).
5. Ekstraksi → muat `index-native.html` → `manifest.json` → 15 JS → layar login.

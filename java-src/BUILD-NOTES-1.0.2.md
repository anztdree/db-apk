# DB-Local 1.0.2 — Ambil alih Facebook Graph & AppsFlyer ke lokal + config penuh di Android/data + server siap lebih awal

Base = 1.0.1-C (03d23bae) yang sudah teruji tanpa force close.

## Identitas file
- File: `DB-Local-1.0.2-D-3b0bd303.apk` · 125.822.586 byte
- SHA-256: `3b0bd30348aabb7cbb77af127d4e9a04644ff0167c586fc2f09f80415f5f9ca2`
- Cert: `9f516096…40265` (sama dgn 1.0.1-A/B/C → install-over langsung)

## Port lengkap sekarang (5 listener)
| Port | Fungsi |
|---|---|
| 10100 | CDN game (bs/, upgrade/, qsdk/) |
| 10101 | SDK akun QuickGame (login/register/visitor) |
| 10102 | LoginServer + loginchecknative + socket.io world |
| **20880** | **Facebook Graph** (graph.facebook.com diarahkan ke sini) |
| **20881** | **AppsFlyer** (*.appsflyer.com diarahkan ke sini) |

## Yang berubah vs 1.0.1-C
1. **Facebook Graph diambil alih** — ServerProtocol/Utility diarahkan ke `http://127.0.0.1:20880`. Respons = REKAMAN ASLI dari server Facebook (HAR original): mobile_sdk_gk 200, profile 200, activities 200 {"success":true}, model_asset 400, me 400 → SDK behave persis seperti online.
2. **AppsFlyer diambil alih** — template URL internal SDK (inapps/launches/conversions/adrevenue/attr/register/validate/stats/sdk-services) diarahkan ke `http://127.0.0.1:20881`. Respons default 400 tanpa body = IDENTIK dgn respons server asli (terbukti di HAR original semua 400). Bisa diganti 200 via file override.
3. **Config server penuh di Android/data** — `/storage/emulated/0/Android/data/com.db.local/files/server/` kini DIISI SEMUA config bawaan (bs/ upgrade/ qsdk/ override/) saat pertama jalan; editan Anda tidak pernah ditimpa; file di sini dipakai lebih dulu daripada bawaan APK.
4. **MiniServer siap lebih awal** — hook dipindah ke `Application.onCreate` (sebelum LaunchActivity), listener di-bind duluan sebelum init berat, request internal menunggu server ready (maks 5 dtk) sehingga baca `server/` Android/data tidak keburu dilalui game.
5. versionName 1.0.1 → **1.0.2**.

## Catatan jujur (bukti HAR/binary)
- `gcdsdk.appsflyer.com/install_data` (4 request): string URL-nya ter-obfuscasi di SDK, tidak ditemukan di dex → tetap ke internet, hasil 400 SAMA seperti di server asli (HAR original juga 400). Perilaku tidak berubah.
- `plugin-check.egret.com` (2 request): URL dibangun native terenkripsi (tidak ada di dex/JS/.so) → tidak bisa diarahkan; di HAR original pun request ini SELALU gagal (status 0) dan game tetap jalan normal.
- Audit: Verify ART-style 233/233 metode com.db.local LOLOS; vendor dex = 92 temuan dexlib2 yang SAMA PERSIS dgn baseline terbukti jalan (diff = 0).

## Instal
- Dari 1.0.1-A/B/C: langsung install-over (cert sama).
- Dari APK LSF 1.0.0 original: uninstall dulu (cert beda).
- Floating logger DB-LOCAL tetap aktif (log bisa di-copy).

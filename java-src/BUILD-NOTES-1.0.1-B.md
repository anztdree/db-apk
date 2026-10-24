# BUILD-NOTES 1.0.1-B — Base LSF + Ambil Alih SDK Eksternal

Tanggal: 2026-10-24 · Build: `DB-Local-1.0.1-B-387d139c.apk` (125.821.985 B,
SHA-256 `387d139cd62a70cc134e0456a17c4c51471b1c864e1cb3035eb6fe0d571c0c1b`)

## 1. Base = APK LSF user ("DB - Local.apk")
- LFS repo: SHA-256 `45414ff1fdb931880fcb54db87b2b0f4faca4595a482a09b41f98252fef1e969`, 104.964.509 B
- versionName 1.0.0, label "DB - Local", cert a40da80a…, 3668 entri, 1 dex, tanpa com.db.local / mirror/

## 2. Bukti diff base LSF vs build lama (baksmali diff penuh)
Hanya 3 file smali berbeda (sisanya identik):
1. `LaunchActivity.onCreate` — sisip `MiniServer.start(p0)` (try/catch)
2. `MainActivity.onResume` — sisip `MiniServer.checkOverlay(p0)` (floating logger)
3. `com.quickgame.android.sdk.feedtonight` (P5) — gate konektivitas dipaksa true
Aset: `QHinfo` (online `qsdk.t4game.com` vs lokal `127.0.0.1:10101` — plaintext),
`config_BS.properties` (base64 EntryPoint online vs lokal `127.0.0.1:10100`), 46 aset mirror/, ikon/label kustom user.

## 3. classes.dex final
- smali assemble dari tree base LSF + 3 file patch terbukti → baksmali ulang = **0 diff** vs pohon patch.
- SHA-256 `c0e00b054ae9138d4fa7ff4d3c57be699ed19c74d48cd56083098e299f5f4108`

## 4. classes2.dex final
- 100% Java (`java-src/com/db/local`, 11 file) → ecj → d8 (`--lib android.jar --min-api 21`)
- SHA-256 `7be569b8d2ad514503c40b6cefce3e7a6d8a05a35e75d86e3b34bedb41b47134` — **byte-identik** dengan build ter-audit (deterministik).
- Gate Verify (dexlib2 + propagasi tipe): 17171 metode dianalisis; 16 temuan = noise vendor bawaan
  (adjust/appsflyer/facebook/gms-ads/zxing, kelas asli tak disentuh, terbukti jalan di device) — sama persis dengan baseline; com.db.local & klien: 0 gagal.
- Verify-after-pack pada dex DALAM APK bertanda tangan: hasil sama (16 noise, 0 pada com.*).

## 5. Ambil alih request eksternal — bukti & mekanisme
### Host nyata dari HAR original (`log running/original-apk.har`)
graph.facebook.com ×17 · inapps/gcdsdk/launches/conversions.appsflyer.com ×13 ·
plugin-check.egret.com ×1 · 119.29.29.29 ×1 — sisanya sudah diarahkan lokal (qsdk→10101,
CDN/config→10100, login.popoh5.com:510→10102).

### Bukti call-site (dex LSF)
- `QuickGameManager.initThirdDataSDK`: AppsFlyer di-gate `DEV_KEY` (kosong/"unknown" → skip init/start/logSession,
  log "init DEV_KEY is empty"); Facebook di-gate `unnecessarysperrylites.seventygenom.pursetruechild()`
  (meta-data `com.facebook.sdk.ApplicationId` absen → helper return "unknown" → false → skip `AppEventsLogger.newLogger`).
- Semua metode logFb* (QuickGameManager/QuickGameSdkService/tidyleadership) **null-check** logger → no-op saat null.
- `QuickGameSdkService` site AF di-gate flag `nationalcommunitymissing.singersmooth` (hanya true setelah init sukses) + try/catch.
- `FacebookInitProvider.onCreate` = try/catch Exception (SDK tidak init ≠ crash).
- Adjust: 0 hit di HAR original — tidak disentuh.

### Patch manifest (AXML in-place, diverifikasi aapt2: tepat 3 diff)
1. `versionName` 1.0.0 → **1.0.1**
2. `DEV_KEY` value → **"unknown"** → AppsFlyer mati by design
3. nama meta-data `com.facebook.sdk.ApplicationId` → `com.facebook.sdk.Applicationld` → FB mati by design
   (SDK tidak pernah init; semua gate wrapper melihat "absen")

### Tidak dapat/tidak perlu dipatch
- `plugin-check.egret.com`, `119.29.29.29` (HTTPDNS): di **libegret.so** (native) — offline → gagal diam, game lanjut (bukti HAR original).

## 6. Konten final vs base LSF (verifikasi penuh)
- Berubah: AndroidManifest.xml, classes.dex, assets/QHinfo, assets/config_BS.properties (+META-INF)
- Tambahan: classes2.dex + assets/mirror/ 46 file (dari build ter-audit 87262a0d: qsdk/bs/upgrade)
- Sisanya byte-identik (ikon/label/resources/lib milik base LSF user)
- Entri total 3715; zipalign -p 4; sign v1+v2+v3 (keystore dblocal-release.keystore, cert 9f516096…40265)

## 7. Catatan instalasi
Cert build ≠ cert APK LSF baseline user → **uninstall baseline "DB - Local" 1.0.0 dulu**, lalu install.

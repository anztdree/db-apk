# STUDI SDK QUICKGAME — CARA SDK BERJALAN & BEKERJA (100% BUKTI, NOL ASUMSI)

> Permintaan owner 23 Nov 2026: *"fokus pelajari sdk .. gimana SDK berjalan dan
> bekerja.. sesuai dengan bukti .. karena sampai detik ini anda masih juga pakai
> asumsi"*. Dokumen ini memenuhi itu: **setiap klaim disertai bukti** —
> file smali + nomor baris persis dari decode fresh APK ORIGINAL
> (`db-local(Original-DO NOT DELETE!).apk`, sha256 `b308c44d…`), atau rekam live
> server asli (verify-artifacts), atau kode server lokal kita.
>
> Sumber decode: `work/dec_orig/smali/` (apktool). Semua path smali di bawah
> relatif terhadap folder itu. Paket SDK: `com.quickgame.android.sdk.*`.

---

## 1. Peta kelas kunci (nama obfuscated -> peran, dibuktikan dari xref & isi kode)

| Kelas (smali) | Peran | Bukti |
|---|---|---|
| `QuickGameManager.smali` | Facade publik yang dipanggil game; memegang `sdkInstance` | `feedtonight` dijadikan `sdkInstance` (xref di `initThirdDataSDK`) |
| `feedtonight.smali` | Instansi SDK (otak utama alur login/init) | `QuickGameManager.smali` (initThirdDataSDK memakai `sdkInstance`); daftar method di bawah |
| `bindcommunist/cointhreat.smali` | Helper HTTP SDK: bangun URL dari base + path `/v1/…`, kirim POST, parse JSON | `:75` membaca `bindcommunist.cointhreat.seventygenom[0]` (base) untuk bangun URL |
| `bindcommunist/singersmooth.smali` | Pembaca asset & helper request | dipanggil `QuickGameManager.smali:67` untuk baca `QHinfo` |
| `unnecessarysperrylites/seventygenom.smali` | Utilitas: **sign MD5** + device id | `:531` TreeMap, `:602` secret, `:635` MD5 (detail §4) |
| `unnecessarysperrylites/singersmooth.smali` | Penyimpan file sesi `info.qg` | `:193` `:581` `info.qg`; `:124` `quickgame/linfo/` |
| `psalmicvolleyball/seventygenom.smali` | Penyimpan prefs sesi `qg_Users` | `:62` `"QGSp"` (nama prefs), `:56` `:257` `"qg_Users"` (key) |
| `psalmicvolleyball/singersmooth.smali` | Request `/v1/system/init` | `:388` `const-string v5, "/v1/system/init"` |
| `service/dishbesideshockey$*.smali` | Tahapan internal `QuickGameSdkService` (init, cek tamu, dsb.) | `dishbesideshockey$feedtonight.smali:55` ckRegVistor; `…$nationalcommunitymissing$seventygenom.smali:60` registerVisitor |
| `activity/HWLoginActivity.smali` + inner `$*` | UI layar login | `HWLoginActivity$singersmooth.smali` = `onServiceConnected` (`:38`) |
| `d/e.smali` | Dialog tunggu auto-login (DialogFragment) | `:465` `const/16 p2, 0x7d8` (window type 2008) |
| `model/e.smali` | Model jawaban `/v1/system/init` | `:171` `"payTypes"`, `:195` `"loginTypes"` |

## 2. DARI MANA SDK MENDAPAT ALAMAT SERVER — BUKTI QHINFO

```
QuickGameManager.smali
:63  .method private initThirdDataSDK(Landroid/app/Activity;Ljava/lang/String;)V
:66      const-string p2, "QHinfo"
:67      invoke-static {p1, p2}, Lcom/quickgame/android/sdk/bindcommunist/singersmooth;
                ->cointhreat(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;
:74      sget-object v0, Lcom/quickgame/android/sdk/bindcommunist/cointhreat;->seventygenom:[Ljava/lang/String;
:75      aput-object p2, v0, v1   (v1 = 0 -> slot [0] = BASE URL SDK)
```
Dipanggil dari `QuickGameManager.smali:1829` dan `:1863` (dua varian init).

**Artinya (fakta, bukan asumsi):**
1. SDK membaca file asset bernama **`QHinfo`** dari DALAM APK satu kali saat init,
   menyimpannya di `bindcommunist.cointhreat.seventygenom[0]`.
2. SEMUA request `/v1/…` dibangun dari slot itu: bukti `bindcommunist/cointhreat.smali:75`
   (`sget-object v4, …seventygenom:[Ljava/lang/String;` lalu `aget-object v0, v4, v6` dengan
   `v6 = 0`) sebelum menyusun URL.
3. Konsekuensi teknis: mengubah endpoint SDK = mengubah `assets/QHinfo` **di dalam APK**
   (yang v1 sudah lakukan: `http://127.0.0.1:9530`). File `sdk/QHinfo` di storage device
   adalah **sumber kebenaran/catatan** — client TIDAK membacanya dari sana.

## 3. DAFTAR LENGKAP ENDPOINT `/v1/…` YANG DIKIRIM CLIENT

Hasil grep smali `com/quickgame/android/sdk/` (string literal `"/v1/…"`), 33 endpoint unik:

```
SISTEM   /v1/system/init            /v1/system/getNotice       /v1/system/getAgreement
         /v1/system/dmsg            /v1/system/onNewToken
USER     /v1/user/ckRegVistor       /v1/user/registerVisitor   /v1/user/registerUser
         /v1/user/loginByName       /v1/user/autoLogin         /v1/user/findPassByEmail
         /v1/user/sendCodeByEmail   /v1/user/getServerAgreement /v1/user/actCdKey
         /v1/user/userLoginByOtherSdk                            /v1/user/postGooglePlayVerify
AUTH     /v1/auth/bindMail          /v1/auth/changePassword    /v1/auth/getUserInfo
         /v1/auth/cksnslogin        /v1/auth/createOrder       /v1/auth/viewMigCode
         /v1/auth/cUserTrash        /v1/auth/setGameRoleInfo   /v1/auth/jianchaLayLx
         /v1/auth/unBindOtherInfo   /v1/auth/userBindOtherSdk  /v1/auth/fbActInit
         /v1/auth/fbInviteEvent     /v1/auth/fbLikeEvent       /v1/auth/fbShareEvent
         /v1/auth/fbUserClaimEvent
```

**Endpoint yang diimplement server lokal v1** (bukti `rebuild-src/javasrc/com/db/local/server/SdkHandler.java`):

| Endpoint | Baris SdkHandler.java | Peran |
|---|---|---|
| `/v1/system/init` | :260 | Gerbang pertama; cek productCode (30001), balas loginTypes/payTypes dari `sdk/v1/system/init.json` |
| `/v1/user/ckRegVistor` | :437 | Cek tamu (mirror `isReged:0`) dari `sdk/v1/user/ckRegVistor.json` |
| `/v1/user/registerVisitor` | :267 | BUAT akun TAMU baru |
| `/v1/user/registerUser` | :274 | DAFTAR (email+sandi) |
| `/v1/user/loginByName` | :300 | LOGIN email |
| `/v1/user/autoLogin` | :322 | Login sesi tersimpan |
| `/v1/auth/getUserInfo` | :363 | Data akun |
| `/v1/auth/bindMail` | :404 | Ikat email ke akun tamu |
| `/v1/auth/changePassword` | :386 | Ganti sandi |
| `/v1/user/sendCodeByEmail` | :344, `/v1/user/findPassByEmail` :350 | Lupa sandi |
| `/v1/system/getNotice` | :425, `/v1/system/dmsg` `onNewToken` `getAgreement` :429-430, `setGameRoleInfo` :433, `getServerAgreement` :445 | Balasan sukses kosong/kontrak |
| path lain | fallback | `sdk/fallback.json` (result:false id 20000) — game lanjut tanpa fitur tsb |

## 4. PROTOKOL REQUEST — SIGN (BUKTI SMALI + LIVE)

```
unnecessarysperrylites/seventygenom.smali
:531     new-instance v0, Ljava/util/TreeMap;   (param di-sort kunci alfabetis)
:536     invoke-virtual {v0, p0}, Ljava/util/TreeMap;->putAll(Ljava/util/Map;)V
:544     invoke-virtual {v0}, Ljava/util/TreeMap;->keySet()Ljava/util/Set;
         ... loop: append kunci, "=", nilai, "&"   ->  "k1=v1&k2=v2&"
:602     const-string v0, "0b2a18e45d7df321"     (SECRET ditambah TANPA "&" di akhir)
:635     invoke-static {p0}, ...seventygenom;->singersmooth(Ljava/lang/String;)  (MD5)
```
- Rumus terverifikasi: `sign = MD5( sort(params) sebagai "k=v&" berurutan + "0b2a18e45d7df321" )` lowercase.
- **Bukti live**: rekam probe ke server asli `qsdk.t4game.com` 23 Nov 2026
  (verify-artifacts/LIVE_CONTRACT_20261123.md) — request ber-sign dgn rumus ini DITERIMA
  server asli (balasan katalog error berurutan 20000 -> 20002 -> 20003 bertahap),
  membuktikan algoritma + secret benar.
- **Server lokal** memakai secret yang sama (bukti `SdkConfig.java:88` `"signSecret": "0b2a18e45d7df321"`).
- Envelope respons SDK (bukti live + kode): `{"result":true/false,"data":…,"error":{"id":N,"message":"…"}}`.
  Katalog error live: 20000 Params error, 20002 Params sign error, 20003
  LostSomeMustParams:<nama>, 30001 Product doesn't exist (bukti errors.json + probe live).

## 5. ALUR BOOT & LOGIN (URUTAN, SEMUA ADA BUKTINYA)

1. **Game init SDK**: `QuickGameManager.init` -> `initThirdDataSDK` (QuickGameManager.smali:1829/:1863).
2. **Baca QHinfo** -> base URL (`§2`). Di APK v1 = `http://127.0.0.1:9530`.
3. **`/v1/system/init`** (psalmicvolleyball/singersmooth.smali:388) = gerbang pertama.
   - Gagal 30001 (product tidak ada) => SDK BERHENTI DI SINI (silent error v15 — bukti live + log device 23 Nov 16:54).
   - Lolos => jawaban init diparse ke `model/e`: `:171` `"payTypes"`, `:195` `"loginTypes"`,
     `:166` `"productConfig"` -> `model/d.smali` (10 field: :74-:92 adZhifuSwt,
     initCZurl, single_play, guestShowBind, isShowFloat, serviceInfo, useSms,
     logo, title, useServiceCenter; dibandingkan konstanta `"1"` :114, `"http"`
     :190, `"0"` :309) dan `:156` `"version"` -> `model/g.smali` (6 field
     :74-:276 versionNo/versionName/versionUrl/isMust/updateTips/updateTime;
     `:59` sentinel literal `"empty"` MILIK SDK SENDIRI — server asli mengirim
     `"empty"` karena itulah nilai banding SDK).
   - Field `productConfig`/`version` diekstraksi dgn `has()`+`getJSONObject()` di
     parser — TAPI pemakai field-nya TIDAK dijaga: **CRASH NYATA 23 Nov 21:49**
     (laporan crash device, init.json minimal tanpa productConfig):
     `NullPointerException: Attempt to read from field 'boolean model.d
     .worktopichardtails' in 'void HWLoginActivity.singersmooth()'` —
     HWLoginActivity.smali:4111-4136 baca `e.singersmooth` (model/d) lalu
     `.worktopichardtails` (= guestShowBind, model/d.smali:296-:331) TANPA
     null-check; runnable `awfulcongratulatepunishment.run` via Handler.
     Koreksi: APK v2 — seed DEFAULT dex + preset = mirror PENUH original
     (SdkConfig.java v2); log readme Bagian 37.
   - `first_install` = 0 hit di seluruh smali (tidak diparse client; dikirim
     mirror asli). `countryCode` = param request init
     (psalmicvolleyball/singersmooth.smali:171) + ikut di respons asli.
4. **loginTypes menentukan UI login** yang muncul di `HWLoginActivity` (konsumen `model/e`:
   `HWLoginActivity.smali`, `HWLoginActivity$pursetruechild.smali`, `d/d`, `d/e`, `d/k`, `d/l`).
   Nilai dari `sdk/v1/system/init.json` di device (bisa diedit tanpa rebuild):
   - `["1"]` (default v1) = form email + tombol TAMU
   - `["12"]` = tamu saja (terbukti v11/v12)
   - `["1","13"]` = mirror server asli (form email + Kode Migrasi)
5. **UI LOGIN MANUAL** (jalur aman): `feedtonight.ironoriginhoblike` (feedtonight.smali:674)
   -> layar TAMU / DAFTAR / LOGIN. Login email jalur ini TERBUKTI sukses di device
   (log 23 Nov 06:08, tanpa crash).
6. **TAMU**: cek `/v1/user/ckRegVistor` (dishbesideshockey$feedtonight.smali:55, runnable,
   log `checkVistor===`) -> `/v1/user/registerVisitor` (dishbesideshockey$nationalcommunitymissing$seventygenom.smali:60)
   -> akun tamu dibuat server (`isReged:0` mirror asli).
7. **AUTO-LOGIN (jalur berisiko)**: game -> `feedtonight.hardlinerspare` (feedtonight.smali:337);
   bila sesi in-memory kosong -> `startActivity(HWLoginActivity)` dengan extra
   `"autologin"` (feedtonight.smali:480) -> `HWLoginActivity$singersmooth.onServiceConnected`
   (:38) -> **bila record sesi tersimpan valid** -> dialog tunggu `d/e.show()` (HWLoginActivity$singersmooth.smali:191).
   - **Dialog itu memakai window type `0x7d8` = 2008 `TYPE_APPLICATION_OVERLAY`** (d/e.smali:465)
     dan TIDAK ada pemanggilan `canDrawOverlays` di seluruh SDK (grep = 0).
   - Akibat terukur: Android 6+ tanpa izin "Display over other apps" =
     `WindowManager.BadTokenException` = crash "setelah sdk selesai LOADING"
     (rekam device v16 17:27 — inilah akar crash yang dulu salah dikira bug server).
8. **Sesi tersimpan dibaca/ditulis SDK di dua tempat** (bukti):
   - Prefs `"QGSp"` key `"qg_Users"` — psalmicvolleyball/seventygenom.smali:56/:62/:257
   - File `info.qg` di `quickgame/linfo/<pkg>` — unnecessarysperrylites/singersmooth.smali:124/:193/:581
     (API29+ <extFiles>/Documents/info.qg — analisa v18)
   - Karena itu v18 punya SessionReset (hapus keduanya) untuk memutus siklus crash:
     sesi dihapus -> SDK masuk jalur 5 (UI manual) yang aman.
9. **Update/CDN game BUKAN bagian SDK**: `assets/config_BS.properties` (EntryPoint base64
   -> `http://127.0.0.1:9527`) dan file `bs/` dilayani server lokal port 9528 —
   di luar paket quickgame (grep tidak ada di smali SDK).

## 6. KESIMPULAN OPERASIONAL UNTUK OWNER

1. **Login/Register/Tamu = kerja SERVER LOKAL** (port 9530) yang meniru protokol SDK di atas.
   Kode SDK tidak boleh disentuh (forbidden zone) dan TIDAK PERLU disentuh: semua
   perbedaan perilaku dikontrol dari sisi server + file config.
2. **Config yang bisa diubah TANPA rebuild** = file di
   `Android/data/com.db.local/server/sdk/` (dibaca ulang tiap request — SdkConfig.java:17):
   `profile.json`, `v1/system/init.json`, `v1/user/ckRegVistor.json`, `fallback.json`,
   `errors.json`. Folder `sdk/` siap-download ada di repo (folder `sdk/`) —
   `init.json` di sana = **mirror PENUH respons init original** (koreksi owner
   23 Nov; perbandingan field-per-field: docs/SDK-CONFIG.md §5). APK v2:
   seed DEFAULT di dex JUGA mirror penuh (SdkConfig.DEFAULT_INIT v2) —
   fresh install v2 aman tanpa tempel; file tempelan tetap selalu menang
   (contract(), SdkConfig.java:142-153).
3. **`sdk/QHinfo` = catatan/sumber kebenaran** — perubahan TIDAK memengaruhi client
   kecuali direbuild ke `assets/QHinfo` (bukti §2).
4. **Kalau JSON config rusak** -> server auto-seed ulang default (SdkConfig.java:148).
   `index.txt` akan dihapus otomatis kalau ada (SdkConfig.java:261).
5. **Crash setelah loading bukan bug server** — akar: dialog auto-login window 2008
   (§5.7). Solusi tanpa rebuild: biarkan auto-guard mereset sesi (login manual), atau
   aktifkan izin overlay lewat banner dashboard.

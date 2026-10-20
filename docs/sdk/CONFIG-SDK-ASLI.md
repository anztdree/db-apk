# CONFIG SDK ASLI — 1.0.2 (SATU-SATUNYA DOC SDK)

> Status: **CONFIG = KETEMU & TERBUKTI** · **GERBANG 4 = TERBEDAH + MATERI LIVE LENGKAP 3 MODE (guest/register/login)** (2026-10-20, TANPA implementasi).
> Prinsip user: **capture = alat bantu saja, bukan acuan pondasi — termasuk login/register/guest.**
> Pondasi = jawaban ASLI server hidup. Bukti dari **3 sumber independen** yang saling mengunci:
> 1. **DEX ORIGINAL APK** (DB-Local.apk, sha256 `3650cbe5…`): field mana yang BENAR-BENAR diparse SDK (instruksi, bukan tebakan).
> 2. **SERVER ASLI HIDUP** (qsdk.t4game.com): panen live respons config → `bukti/panen-live/`.
> 3. **HAR PERANGKAT** (sesi uji 2026-10-20, `log/sdk.har`): tiket request asli + bukti **leg A ≡ leg B byte-identik 8/8** → `bukti/device-har/` + `bukti/panen-gerbang4/`.
>
> ATURAN #1 (dari user, selalu berlaku): **capture ≠ config asli**. HAR = alat bantu petunjuk.
> Material config = jawaban server asli yang masih hidup. Target: 1.0.2 — config SDK di LOCAL,
> bebas diatur (edit → langsung efektif), TANPA penerusan, BUKAN hasil capture.
> APK yang dipakai = APK ORIGINAL: SDK-nya sendiri membuat request asli (sv/data/sign) —
> kita hanya perlu MELAYANI config aslinya secara lokal.

---

## 0. MULAI DARI SINI — INI CONFIG-NYA: `config-local/`

Folder **`config-local/`** berisi **config SDK final yang dilayani MiniServer 1.0.2 secara
lokal** — bukan jejak, bukan capture: ini materi jawaban yang dikirim ke SDK di dalam APK
original. **Edit file → langsung efektif.**

| File | Endpoint | Status material |
|---|---|---|
| `config-local/system/init.json` | `POST /v1/system/init` | ✅ FINAL — isi = respons asli server hidup (2026-10-20); hanya `requestIp` disunting jadi `127.0.0.1` (terbukti tidak dipakai koneksi) |
| `config-local/system/getNotice.json` | `POST /v1/system/getNotice` | ✅ FINAL — versi tanpa notice = identik respons server hidup (55 B) |
| `config-local/system/getAgreement.json` | `POST /v1/system/getAgreement` | ✅ FINAL — semua field kosong = identik respons server hidup (188 B) |
| `config-local/user/ckRegVistor.json` | `POST /v1/user/ckRegVistor` | ✅ FINAL — `isReged:1` identik respons server hidup |
| `config-local/user/registerVisitor.json` | `POST /v1/user/registerVisitor` | ✅ FINAL — **materi LIVE Gerbang 4** (sesi guest, 2345 B, leg A≡B) |
| `config-local/user/registerUser.json` | `POST /v1/user/registerUser` | ✅ FINAL — respons sukses ASLI (satu-kali/state-bound, leg A≡B) |
| `config-local/user/loginByName.json` | `POST /v1/user/loginByName` | ✅ FINAL — **materi LIVE** (sesi akun, 2429 B) |
| `config-local/auth/getUserInfo.json` | `POST /v1/auth/getUserInfo` | ✅ FINAL — **materi LIVE** (profil bersesi) |
| `config-local/game/loginchecknative.json` | `GET <loginServer>/loginchecknative` | ✅ FINAL — format jawaban `code:0` verifikasi game (live, 3 mode lolos) |

Aturan edit lengkap + sifat sesi (terikat perangkat, dapat diulang, registerUser satu-kali):
[`config-local/README.md`](config-local/README.md). Peta field-per-field + bukti instruksi dex:
bagian 4–8 di doc ini.

---

## 1. RINGKASAN: APA ITU "CONFIG SDK" PADA APK INI

Config SDK asli = **2 lapisan**:

| Lapisan | Isi | Lokasi fisik | Bukti |
|---|---|---|---|
| **A. Tertanam di APK** | Penunjuk alamat `QHinfo` (22 B, `http://qsdk.t4game.com`), `product_code` (string resource `0x7f0c0143` = `75880787318053043365515388491588`), `sdkVersion=223` (hardcode dex), **secret sign `0b2a18e45d7df321`** | `assets/QHinfo`, `resources.arsc`, `classes.dex` | dex: `QuickGameManager.initThirdDataSDK` satu-satunya pembaca QHinfo; `unnecessarysperrylites.seventygenom` |
| **B. Dijawab server** (`/v1/*`) | **`init`** = otak (saklar UI/login/update/pembayaran), **`getNotice`** = pengumuman, **`getAgreement`** = perjanjian, **`registerVisitor`** = sesi guest (Gerbang 4) | server `qsdk.t4game.com` (kini di-LOCAL-kan via `config-local/`) | `bukti/panen-live/` + parser dex (bagian 3–8) |

**Mekanisme "APK original mengirim config SDK aslinya ke kita"** (terbukti dari dex):
SDK original MEMBANGUN sendiri request sah-nya — `sv=v2`, `data=Base64(JSON perangkat)`,
`sign=MD5(...)` dengan secret tertanam — lalu POST ke isi `QHinfo` + endpoint. Setelah
QHinfo di-flip ke `http://127.0.0.1:28110`, **request asli yang sama mendarat di MiniServer
kita** → yang perlu kita lakukan hanyalah **menjawab** dengan config (yang kini ada di
LOCAL dan bebas diedit). Tidak ada capture, tidak ada tiruan jejak — request-nya punya
APK sendiri, jawabannya punya kita.

---

## 2. GERBANG 4: RANTAI LOGIN LENGKAP — TERBEDAH DARI DEX (baru 2026-10-20)

Ini jawaban atas pertanyaan *"gimana rencana local standalone running SDK bisa sukses
menuju ke loginserver"*. Seluruh rantai di bawah = bukti instruksi dex original
(dump mentah: `bukti/riset-login/`), bukan tebakan.

### 2a. Rantai nyata dari boot sampai masuk game

```
[1] onInitFinished(true)                         ← init lokal BERHASIL (bagian 4)
      └─ game memanggil SDK.login(activity)      (feedtonight/singersmooth.onInitFinished)

[2] SDK: POST /v1/user/registerVisitor           (service/dishbesideshockey$…seventygenom.run)
      respon: {"result":true,"data":{"authToken":"…","userData":{…}}}     ← format di 2b
      ★ MATERI LIVE: 2345 B — uid 28486093, username cz90406643, isGuest 1
        (tiket sama → sesi SAMA persis = terikat perangkat, dapat diulang)

[3] onLoginFinished(QGUserData, QGUserHolder)    (feedtonight/singersmooth — dex)
      showFloatView(activity)
      HashMap params PERSIS (dipastikan HAR nyata):
        uid=28486093  username=cz90406643  token=@154@171@…  usermode=1

[4] Thread → GET  https://login.popoh5.com:510/loginchecknative
              ?uid=…&username=…&token=…&usermode=…
              &appVersion=1&osVersion=31&sourceVersion=20200418
              &model=itel+S665L&packageName=com.db.local&brand=Itel&os=Android&sdk=BS
      retry 3×; respon JSON (TERBUKTI LIVE):  code==0 → LOLOS lanjut masuk game
                                              code≠0 → msg/message → dialog error
      (log tag asli: "sdk登录验证url" / "sdk登录验证返回")

[4b] JAWABAN loginchecknative ASLI (live, 195 B guest / 209 B akun):
     {"code":0,"sdk":"BSNative","nickName":"cz90406643",
      "loginToken":"114b9afe…","userId":"28486093",
      "sign":"171d4576675f395c4aa53f23c4f321bb","sdkType":"BS","osType":"Android"}
     — loginToken berubah tiap panggil; sign STABIL per user; code:0 diterima BERULANG

[5] game lanjut: startGame(params: version, thirdParams, language, loginServer, clientParams)
      → engine/game world (socket/web server game asli — di luar config SDK)
```

### 2b. Format sesi login (parser dex, WAJIB dipenuhi) — DENGAN MATERI LIVE

Parser: `psalmicvolleyball.cointhreat.cointhreat(String)` → `QGUserData.generateFromJson`.
Format persis dari respons server hidup (live registerVisitor 2345 B):

```json
{"result":true,"data":{
   "authToken":"@149@121@127@…",      ← WAJIB (getString) — format token obfuscated @…@
   "tips":"",                          ← opsional
   "checkRealName":"0",                ← opsional (default "0")
   "userData":{
     "uid":"28486093",                 ← WAJIB — guest per perangkat (angka/str kedua2nya sah)
     "username":"cz90406643",          ← WAJIB
     "displayUid":"",                  ← opsional (bila kosong → tampil = uid)
     "token":"@154@171@167@…",         ← WAJIB — INI yang dikirim ke loginchecknative
     "isGuest":1,                      ← WAJIB (respons asli ANGKA; getString → "1")
     "isNewUser":0,                    ← opsional
     "mobile":"",                      ← opsional (respons asli mengandung)
     "bindInfo":{"bindFB":{"isBind":0,"bid":6,"buid":"",…}, …}   ← opsional (respons asli lengkap 12 kanal bind)
   }
 },"error":{"id":0,"message":""}}
```

Hilang `authToken` / `userData.uid` / `username` / `token` / `isGuest` → parser exception
→ login gagal. File final: `config-local/user/registerVisitor.json` (materi live utuh).
Format sama untuk `registerUser` (isGuest 0, isNewUser 1) dan `loginByName`
(isGuest 0 + field opt fbName/pushToken/noLoginLongTime) — ketiganya sudah ada di
`config-local/user/` dari materi LIVE.

### 2c. DARI MANA ALAMAT loginserver? — SUDAH DI TANGAN KITA

Fakta kunci (dex `pursetruechild/ironoriginhoblike` + `LaunchActivity`):

1. Base URL loginchecknative = `<url>:<port>` dari **config game `setting_BS_Android.bin`**
   (JSON XOR — parser `LaunchActivity.worktopichardtails`): field `url` + port, plus
   `webUrl/tsUrl/tsWebUrl/update/forceBuild/…`, dan juga **`loginServer`/`tsLoginServer`**.
2. **Config game itu SUDAH dilayani LOCAL sejak 1.0.1** (Fase 1–2 alur depan).
3. Artinya: **alamat "login server" yang dituju APK sepenuhnya berada di config yang
   kita kuasai** — bisa tetap menunjuk `login.popoh5.com:510` yang asli, atau (bila suatu
   saat diperlukan) diarahkan ke server lokal kita. Tidak ada alamat yang berada di luar
   kendali config.

### 2d. STATUS local standalone → menuju gerbang loginserver (KOREKSI: GENERATOR, bukan sisa jejak)

| Tahap | Apa yang lokal | Material | Status |
|---|---|---|---|
| **A. Config SDK** | `init` + `getNotice` + `getAgreement` + `ckRegVistor` dijawab MiniServer dari `config-local/` | panen live server hidup (acuan format) | ✅ SIAP |
| **B. Sesi SDK** | `registerVisitor` dijawab MiniServer — **TEMPLATE GENERATOR**: `{{UID}}`/`{{USERNAME}}`/`{{TOKEN}}`/`{{AUTH_TOKEN}}` diisi runtime — **token dibangkitkan BARU** *(koreksi user 2026-10-20: kalau config benar → generate token baru, bukan ambil sisa jejak)* | basis sahnya terbukti dari dex: parser SDK hanya `getString` tanpa validasi isi (`bukti/riset-login/dump-generator-basis.txt`); panen live = acuan FORMAT | ✅ SIAP (tanpa tiket panen) |
| **C. Verifikasi game** | Game mengirim `{uid,username,token,usermode}` hasil generator ke gerbang **LOKAL** (setting bin `loginServer` → `127.0.0.1:28110` — murni edit config); MiniServer jawab `code:0` bentuk 10 field live | game hanya membaca `getInt("code") == 0` (bukti dex); respons diteruskan JS via startGame | ✅ rencana siap — uji di perangkat |

**Kenapa ini sah (BASIS GENERATOR, terbukti — bukan asumsi):** token/authToken adalah
string OPAQUE di seluruh rantai klien — bean sesi `QGUserData.generateFromJson` dan parser
sesi hanya `getString` (tanpa cek format/panjang/signature); penyusun request game
meneruskan token apa adanya; pemeriksaan gerbang game membaca `code` saja. Pemegang
server-lah yang menentukan nilai token — server lokal = pemegang baru → token BARU sah
secara struktur. Pola perilaku server asli ditiru: uid stabil per perangkat, token baru
per sesi, `sign` stabil per uid (bukti panen: register & login uid sama → sign sama).

**Konsekuensi arah gerbang (konsisten koreksi):** loginserver asli memvalidasi token di
sisi server (klien tidak pernah mendekode token) → token generate tidak akan lolos di
sana → alamat gerbang diarahkan LOKAL lewat setting bin (file config yang sudah lokal
sejak 1.0.1; `copyDir` tidak menimpa file ada → edit bertahan). Jalur lama "gerbang live
dengan token panen" DIBATALKAN oleh koreksi.

**Catatan registerUser (state-bound):** register sukses hanya SATU kali per guest —
panen ulang server asli jawab `40008 pengguna ini telah digunakan`. Material sukses
(2389 B, leg A≡B) tersimpan di `config-local/user/registerUser.json` + bukti di
`bukti/panen-gerbang4/sukses-sekali__user__registerUser.bin`.

---

## 3. ALUR SEBUAH SESI (dex + HAR perangkat, urutan nyata)

```
LaunchActivity (channel "BS")
 ├─ Fase 1–2 : baca config_BS.properties → setting_BS_Android.bin        [sudah LOCAL sejak 1.0.1]
 ├─ Fase 3–5 : mirror bs/ + upgrade/ dari 127.0.0.1:28110                [sudah LOCAL sejak 1.0.1]
 └─ SDK : QuickGameManager.initThirdDataSDK
      1. POST /v1/system/init      → parser model/e                     [CONFIG OTAK — bagian 4]
           callback sukses:
      2. POST /v1/system/getNotice → content + showNode                 [bagian 6]
           cek update: versionCode APK < versionNo → dialog update      [bagian 5]
      3. POST /v1/user/registerVisitor → sesi guest                    [GERBANG 4 — bagian 2]
      4. GET <loginServer>/loginchecknative → code==0                  [GERBANG 4 — bagian 2]
      5. startGame → engine / server game asli
   (getAgreement dipanggil dari UI perjanjian — bagian 7)
```

---

## 4. RESPONS `v1/system/init` — FIELD PER FIELD (BUKTI DEX)

Parser: `com.quickgame.android.sdk.model.e.cointhreat(String)` — menerima **teks objek `data`**
(callback `psalmicvolleyball.singersmooth.run` membaca wrapper: `getBoolean("result")` →
`getString("data")` → parser). Semua konsumen di bawah = kelas/metode asli di dex
(`bukti/dex-evidence/`).

### 4a. Field DIPARSE — wajib ada & bisa diatur

| Field | Tipe | Rule baca (bukti dex) | Konsumen / efek |
|---|---|---|---|
| `nodes.serverInfo` | int | `getInt` | `HWLoginActivity.onCreate` — gerbang menampilkan data server |
| `nodes.serverInfoData` | array `{id, title}` | `getJSONArray` | List pusat layanan; dipakai hanya bila `serverInfo==1` |
| `nodes.removeEmail` | int | `==1 → true` | Sembunyikan opsi email di Account Center |
| `nodes.deleteAccount` | int | **dibaca lalu dibuang** (dead read) | tanpa efek |
| `loginTypes` | array string | `toString().contains(...)` — bukan parse elemen! | Flag login: `"1"`→guest, `"6"`→FB, `"8"`→Google (+cek GMS), `"9"`→Naver, `"10"`→Twitter, `"11"`→Line, `"12"`, `"14"`→VK, `"17"`→Play Games (+cek GMS); jumlah layanan aktif → counter. **Missing → "only show default fb login"** |
| `isNotGift` | int | `getInt` | `FacebookShareActivity.onCreate` |
| `showShare` | int | `getInt` (koersi `"1"`→1) | `HWAccountCenterActivity.onCreate` |
| `showDiscount` | int | `getInt` | `HWAccountCenterActivity.onCreate` |
| `webUrl` | string | `getString` | disimpan — **tidak ada konsumen lain** |
| `requestIp` | string | `getString` | disimpan → **`QuickGameManager.getCountryInfo()` = `"<channel>|<requestIp>"`** — API untuk GAME (deteksi wilayah). Bukan alamat koneksi! |
| `payTypes` | array `{payTypeId:int, payName}` | `getJSONArray` | Daftar metode pembayaran. **KUNCI WAJIB: key hilang → parser return null → "productInfo null" → init dianggap gagal.** `[]` = sah |
| `productConfig` | object | `getJSONObject` → `model/d` | Saklar UI global — rincian 4b. Hilang → instance null (konsumen UI berisiko NPE) — **sebaiknya selalu ada** |
| `otherpayType` | (string koersi) | `getString` | Payment flow |
| `payFast` | (string koersi) | `getString` — di respons live `[]` dikonversi jadi teks `"[]"` | Payment flow |
| `lightPackage` | object `{mustUpdate,packageName,versionNum,signKey,downloadUrl}` | `optString`/`parseInt` | Paket ringan; tidak ada di respons live → aman dilewati |
| `version` | object | → `model/g` (bagian 5) | Cek update SDK |

### 4b. `productConfig` — rule parser `model/d` (semua nilai dibaca sebagai STRING)

| Field | Rule (bukti instruksi) | Default bila hilang | Efek |
|---|---|---|---|
| `useServiceCenter` | `=="1"` → true; **lalu DITIMPA**: `serviceInfo` tak kosong → true | false | tombol layanan pelanggan |
| `title` | langsung | null | judul layanan |
| `logo` | hanya diterima bila **diawali `http`** | null | logo layanan |
| `useSms` | `=="1"` → true | false | saluran SMS |
| `serviceInfo` | langsung; keberadaannya menyalakan useServiceCenter | null | teks layanan |
| `isShowFloat` | **`=="0"` → true (KEBALIKAN!)** | false | floating icon |
| `guestShowBind` | `=="1"` → true | **TRUE** | tampilkan bind utk guest |
| `single_play` | `=="1"` → true | false | mode single |
| `initCZurl` | `=="1"` → true (tak pernah ada di respons live — SDK mendukung, server tak kirim) | false | — |
| `adZhifuSwt` | `=="1"` → true | false | saklar pembayaran iklan |

### 4c. Field TIDAK DIPARSE — bebas diubah/dihilangkan (string TIDAK ADA di dex)

`first_install` (top-level), `nodes.oneYeaerTips`, `productConfig.{skinStyle, noticeNode,
regMailVerify, pycenttype, onGoToPUrl, onGoToPUrlData, trashTime, noFirebaseLog, logEvtReport}`,
wrapper `countryCode` (gema negara pemanggil — **client mengabaikannya**; countryCode yang
dipakai SDK justru dari SIM perangkat utk REQUEST), `version.updateTime` (dibaca lalu dibuang).

### 4d. Respons live (panen 2026-10-20 — asli `bukti/panen-live/system__init.bin`, 767 B)

```json
{"result":true,"data":{"payTypes":[],"version":{"versionName":"empty","versionNo":"empty","versionUrl":"empty","updateTime":"empty","isMust":"empty","updateTips":"empty"},"productConfig":{"useServiceCenter":"0","isShowFloat":"0","showShare":"1","title":"","logo":"","useSms":"0","skinStyle":"0","serviceInfo":"","guestShowBind":1,"single_play":0,"adZhifuSwt":0,"noticeNode":0,"regMailVerify":0,"pycenttype":0,"onGoToPUrl":0,"onGoToPUrlData":"","trashTime":0,"noFirebaseLog":0,"logEvtReport":0},"otherpayType":2,"payFast":[],"isNotGift":0,"showShare":"1","requestIp":"47.57.232.232","showDiscount":0,"nodes":{"deleteAccount":0,"serverInfo":0,"oneYeaerTips":0,"removeEmail":0},"loginTypes":["1","13"],"countryCode":"HK","first_install":0},"error":{"id":0,"message":""}}
```

---

## 5. `version` (parser `model/g`) — cek update SDK

| Field | Rule | Efek |
|---|---|---|
| `versionNo` | `"empty"` → diabaikan; selain itu `parseInt` → long | dibanding **versionCode APK**: `versionCode < versionNo` → dialog update |
| `versionName` | `"empty"` → diabaikan | teks dialog |
| `versionUrl` | `"empty"` → diabaikan | tujuan unduh |
| `isMust` | `"1"` atau boolean true | paksa update |
| `updateTips` | `""`/`"empty"` → diabaikan | pesan dialog |
| `updateTime` | dibaca lalu **dibuang** | tanpa efek |

Respons live semua `"empty"` → **tanpa dialog update** (perilaku nyata).

---

## 6. `v1/system/getNotice` — format yang BENAR (bukti `singersmooth.cointhreat`)

Dipanggil **langsung di dalam callback sukses init**. Pembacaan:

```
resp.getBoolean("result")
 → getString("data")            // data live = []  → "[]" (panjang 2)
 → if (panjang > 2)             // array kosong → TANPA notice (sah, tidak error)
      getJSONObject("data")     // WAJIB OBJECT
      .getString("content")     → teks notice
      .getInt("showNode")       → node tampil
```

- Live: `{"result":true,"data":[],"error":{"id":0,"message":""}}` (55 B, md5 `dfc2f19b1273…`
  — byte-identik antar hari & identik dengan mirror perangkat).
- **Notice di config lokal**: `data` HARUS **object**: `{"content":"teks","showNode":0}` —
  array berisi akan dilempar exception dan notice diabaikan.

---

## 7. `v1/system/getAgreement` (188 B, panen live)

9 field perjanjian, semuanya string kosong — dipakai UI perjanjian. Byte-identik antar
panen → sah sebagai file lokal statik (lihat `config-local/system/getAgreement.json`).

---

## 8. SEMANTIK WRAPPER (semua endpoint `/v1/*`)

Respons selalu `{result: bool, data: ..., error: {id, message}}`.
Client: `getBoolean("result")` → bila true, `getString("data")` (koersi JSON→teks) → parser spesifik.
`result:false` → jalur error (contoh nyata probe: `40005 Otorisasi gagal` (getUserInfo tanpa sesi),
`40008 pengguna ini telah digunakan` (replay registerUser), `20003` kurang parameter).

---

## 9. REQUEST ASLI APK ORIGINAL (bukti "APK mengirim config-nya sendiri")

Pembuat request: `bindcommunist/singersmooth.singersmooth(Context, HashMap)`:

```
map inti (semua endpoint) : sdkVersion=223, gameVersion, deviceId(MD5 perangkat,
  SharedPreferences "qk_sdk"), serialNum, devIDShort, platform, productCode(0x7f0c0143),
  channelCode, authToken, clientLang, suggestCurrency:"", time
init menambah ~20 field fingerprint: deviceName, gaid/adId, imei, androidId, netType,
  screenWidth/Height/dpi, osName="android", osVersion, osLanguage,
  countryCode=getSimCountryIso(), isjailbroken=0, imsi, longitude/latitude=0, dst.

data = Base64( JSON(map) )          // Base64.NO_WRAP
sign = MD5( sort-alfabet semua "k=v&" ... + "0b2a18e45d7df321" )   // lowercase hex
POST base+"/v1/…"  CT: application/x-www-form-urlencoded   body: sv=v2&data=…&sign=…
```

- **Secret sign `0b2a18e45d7df321` tertanam di dex** — terverifikasi cocok utk request
  init & getNotice di HAR.
- Executor: URL = `base[0] + endpoint` (base = QHinfo, tanpa path), syarat sukses
  **`getResponseCode()==200`** lalu parse JSON; gagal → retry 5 dtk → `null`
  (SDK menerima null tanpa crash).
- Konsekuensi: **server lokal tidak perlu memvalidasi sign untuk menjawab config** —
  sign hanya alat server asli memverifikasi klien. Yang kita butuhkan = respons dengan
  wrapper & field yang benar (bagian 2–7).

---

## 10. DAFTAR LENGKAP 32 ENDPOINT `/v1/*` DI DEX

| Kategori | Endpoint |
|---|---|
| **system (config)** | `init` ✅ · `getNotice` ✅ · `getAgreement` ✅ · `dmsg` (butuh func_code) · `onNewToken` (push) |
| **user (sesi/akun)** | `registerVisitor` ✅ **MATERI LIVE** · `registerUser` ✅ sukses asli (satu-kali; panen ulang = 40008) · `ckRegVistor` ✅ · `autoLogin` ✅ **DIIMPLEMENTASI 1.0.3** (format = registerVisitor — terbukti parser dex; validasi authToken terbitan, Opsi A setia server asli; lihat `BUILD-1.0.3.md`) · `loginByName` ✅ **MATERI LIVE** · `userLoginByOtherSdk` · `findPassByEmail` · `sendCodeByEmail` · `actCdKey` · `postGooglePlayVerify` · `getServerAgreement` (butuh agmId) · **1.0.4: registerUser/loginByName/getUserInfo dijawab GENERATOR dgn DATABASE AKUN + validasi password (bentuk error live 40008; lihat `BUILD-1.0.4.md`)** |
| **auth (aksi akun/bayar/event)** | `getUserInfo` ✅ **MATERI LIVE** (guest 280 B + terdaftar 280 B) · `createOrder` · `bindMail` · `changePassword` · `cksnslogin` · `fbActInit` · `fbInviteEvent` · `fbLikeEvent` · `fbShareEvent` · `fbUserClaimEvent` · `setGameRoleInfo` · `userBindOtherSdk` · `unBindOtherInfo` · `viewMigCode` · `cUserTrash` · `jianchaLayLx` |

**Yang merupakan CONFIG + SESI utk Gerbang 4 — SEMUA SIAP**: `init`, `getNotice`,
`getAgreement` (config) + `registerVisitor` (guest), `registerUser` (register),
`loginByName` (login) + `getUserInfo` — sembilan material di `config-local/`, semuanya
jawaban ASLI server hidup (bukan capture). Sisanya = aksi berbasis sesi sekunder — bukan
pondasi login; panen tiketnya menyusul bila diperlukan.

---

## 11. BUKTI PERANGKAT (HAR sesi 2026-10-20 09:12 — `log/sdk.har` + `bukti/panen-gerbang4/`)

Sesi perangkat user (itel S665L, Android 12) lewat 1.0.2-tes1 — **menutup Gerbang 4**:

| Bukti | Nilai |
|---|---|
| leg A ≡ leg B | **8/8 pasang byte-identik** (init ×3, registerVisitor, registerUser, loginByName, getUserInfo ×2) — proxy verbatim terbukti lagi |
| GUEST | registerVisitor 2345 B → loginchecknative `uid=28486093&usermode=1` → `code:0` (195 B) |
| REGISTER | registerUser 2389 B (sukses satu-kali) → loginchecknative `uid=28506361&usermode=0` → `code:0` (209 B) |
| LOGIN | loginByName 2426 B → loginchecknative `uid=28506361&usermode=0` → `code:0` (209 B) |
| getUserInfo | guest 280 B (`regType:2`) & terdaftar 280 B (`regType:1`) — profil bersesi |
| PANEN ULANG LIVE (tiket sama) | registerVisitor → sesi SAMA persis (uid+token identik); loginByName → sesi akun sama; loginchecknative → `code:0` lagi (loginToken baru, sign stabil); registerUser → 40008 (state-bound) |
| respons init perangkat user | `requestIp:"182.9.2.96"`, `countryCode:"ID"` — gema per-pemanggil; tidak dipakai koneksi |

---

## 12. PENJAGAAN "TIDAK ADA CONFIG YANG SALAH"

1. `payTypes` **harus ada** (boleh `[]`) — hilang = parser return null = init gagal.
2. `productConfig` **sebaiknya ada** meski minimal — konsumen UI membaca instance-nya langsung.
3. `isShowFloat:"0"` berarti float **tampil**; `"1"` berarti sembunyi (kebalikan intuisi).
4. `logo` diabaikan bila tidak diawali `http`.
5. `guestShowBind` default TRUE bila hilang (bukan false).
6. Nilai `"empty"` pada `version.*` = cara server asli menyembunyikan update — pertahankan
   bila tidak ingin dialog update.
7. Notice: `data` harus object `{content, showNode}` — array berisi tidak akan tampil.
8. `loginTypes` dibaca dgn `String.contains` atas teks array → tulis persis seperti live
   (`["1","13"]`); `"1"` wajib ada agar login guest tampil.
9. Content-Type respons live = `text/json` — pertahankan demi kesetiaan.
10. **Sesi (Gerbang 4):** `registerVisitor`/`registerUser`/`loginByName` wajib memuat
    `authToken` + `userData.{uid,username,token,isGuest}` — salah satu hilang = login
    gagal (bagian 2b). `isGuest` di respons asli berupa ANGKA (1/0) — ikuti format asli.
    `usermode` dihitung game dari `isGuest`, bukan field terpisah.

---

## 13. INDEKS BAHAN (folder ini)

| Bahan | Isi |
|---|---|
| `config-local/` | **THE CONFIG** — 10 file JSON siap-layani (system 3 + user 4 + auth 1 + game 1 + README) (bagian 0) |
| `bukti/panen-gerbang4/` | **Materi LIVE Gerbang 4** (2026-10-20): registerVisitor/loginByName/registerUser-state/getUserInfo ×2/loginchecknative ×3 (`live__*.bin`) + `_meta-panen-live.json` (status/CT/md5/durasi) + `_indeks-tiket.json` + `sukses-sekali__user__registerUser.bin` |
| `bukti/panen-live/` | 8 respons asli server hidup fase config (init 767 B, getNotice 55 B, getAgreement 188 B, onNewToken 55 B, ckRegVistor 66 B, getUserInfo 75 B, getServerAgreement 104 B, dmsg 108 B) + `_probe-semua.json` + `_meta-panen-live.json` |
| `bukti/riset-login/` | Dump dex alur login: parser registerVisitor/autoLogin + loginchecknative (`dump-login-endpoint-parser.txt`), params loginchecknative dari `onLoginFinished` (`dump-onLoginFinished-params.txt`), base URL loginserver dari config game (`dump-loginserver-baseurl.txt`) |
| `bukti/dex-evidence/` | Dump instruksi mentah dex ORIGINAL: `dump-full.txt`, `dump-model.txt`, `xref-fields.txt`, `map-usage-config.txt`, `dump-qgm.txt`, `dump-bind.txt`, `dump-sign.txt`+`dump-md5.txt`, `dump-apply.txt`, `har-inventory.txt` |
| `bukti/device-har/` | `bukti-leg-A-identik-leg-B.json`, `tiket-request-asli-har.json` (TIKET, bukan config) |
| `../../log/sdk.har` | HAR sesi perangkat 2026-10-20 (668 KB, 144 entri) — sumber tiket Gerbang 4 |

## 14. STATUS & BATAS

- ✅ Config SDK asli: KETEMU, terbukti 3 sumber — **tersusun sebagai `config-local/`** (bukan jejak).
- ✅ Alur update/blok (Fase 1–5): dikuasai LOCAL sejak 1.0.1.
- ✅ Gerbang 4: **rantai login terbedah penuh dari dex + MATERI LIVE 3 MODE** —
  registerVisitor (guest), registerUser (register), loginByName (login), getUserInfo,
  loginchecknative (`code:0` berulang) — semuanya jawaban ASLI server hidup, capture
  hanya alat bantu (tiket), sesuai prinsip user.
- ✅ Sifat sesi terbukti: terikat perangkat, dapat diulang (registerVisitor/loginByName);
  registerUser satu-kali (40008 setelah dipakai).
- ✅ Alamat loginserver game = config game `setting_BS_Android.bin` — SUDAH LOCAL sejak 1.0.1.
- ⛔ Implementasi build: **DITAHAN sampai user memerintahkan** (riset saja per arahan).

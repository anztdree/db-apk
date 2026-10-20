# CONFIG SDK ASLI — 1.0.2 (SATU-SATUNYA DOC SDK)

> Status: **CONFIG = KETEMU & TERBUKTI** · **GERBANG 4 (login) = TERBEDAH DARI DEX** (riset 2026-10-20, TANPA implementasi — menunggu keputusan user).
> Bukti dari **3 sumber independen** yang saling mengunci:
> 1. **DEX ORIGINAL APK** (DB-Local.apk, sha256 `3650cbe5…`): field mana yang BENAR-BENAR diparse SDK (instruksi, bukan tebakan).
> 2. **SERVER ASLI HIDUP** (qsdk.t4game.com): panen live respons config → `bukti/panen-live/`.
> 3. **HAR PERANGKAT** (sesi uji 1.0.2-tes1): alur nyata + bukti leg A ≡ leg B → `bukti/device-har/`.
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
| `config-local/user/registerVisitor.json` | `POST /v1/user/registerVisitor` | ⏳ TEMPLATE — format terbukti dari parser dex (bagian 3); **nilai sesi asli diisi dari panen live Tahap 2** |

Aturan edit lengkap (kunci wajib, rule string, jebakan parser): [`config-local/README.md`](config-local/README.md).
Peta field-per-field + bukti instruksi dex: bagian 4–8 di doc ini.

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

[3] onLoginFinished(QGUserData, QGUserHolder)    (feedtonight/singersmooth — dex)
      showFloatView(activity)
      HashMap params PERSIS:
        uid       = QGUserData.getUid()
        username  = QGUserData.getUserName()
        token     = QGUserData.getToken()
        usermode  = isGuest() ? "1" : "0"

[4] Thread → GET  <loginServer>/loginchecknative?uid=…&username=…&token=…&usermode=…
              &osVersion=…&packageName=…&appVersion=…&sourceVersion=…&brand=…&model=…
              &os=Android&sdk=<versiSDK>          (pursetruechild/nationalcommunitymissing.run)
      retry 3×; respon JSON:  code==0 → LOLOS lanjut masuk game
                              code≠0 → msg/message → dialog error
      (log tag asli: "sdk登录验证url" / "sdk登录验证返回")

[5] game lanjut: startGame(params: version, thirdParams, language, loginServer, clientParams)
      → engine/game world (socket/web server game asli — di luar config SDK)
```

### 2b. Format sesi login `registerVisitor` (parser dex, WAJIB dipenuhi)

Parser: `psalmicvolleyball.cointhreat.cointhreat(String)` → `QGUserData.generateFromJson`:

```json
{"result":true,"data":{
   "authToken":"…",                    ← WAJIB (getString)
   "tips":"",                          ← opsional
   "checkRealName":"0",                ← opsional (default "0")
   "userData":{
     "uid":"28486093",                 ← WAJIB (getString) — guest per perangkat
     "username":"Guest_…",             ← WAJIB (getString)
     "displayUid":"",                  ← opsional (bila kosong → tampil = uid)
     "token":"…",                      ← WAJIB (getString)
     "isGuest":"1",                    ← WAJIB (getString "1"/"0")
     "openType":"",                    ← opsional
     "isNewUser":0,                    ← opsional (1 = user baru)
     "isTrash":0,                      ← opsional
     "bindInfo":null                   ← opsional (objek bind akun)
   }
 },"error":{"id":0,"message":""}}
```

Hilang `authToken` / `userData.uid` / `username` / `token` / `isGuest` → parser exception
→ login gagal. Inilah template `config-local/user/registerVisitor.json`.

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

### 2d. RENCANA local standalone → sukses menuju loginserver (3 tahap, tanpa penerusan permanen)

| Tahap | Apa yang lokal | Material | Status |
|---|---|---|---|
| **A. Config SDK** | `init` + `getNotice` + `getAgreement` + `ckRegVistor` dijawab MiniServer dari `config-local/` | panen live server hidup | ✅ SIAP |
| **B. Sesi SDK** | `registerVisitor` dijawab MiniServer: `{authToken, userData{uid,username,token,isGuest:"1"}}` **sesi ASLI per perangkat** | ⏳ butuh 1 capture login dari perangkat user sebagai **tiket** → panen live registerVisitor (metode bertahap yang sudah disepakati) | format ✅ terbukti; nilai ⏳ menunggu tiket |
| **C. Verifikasi game** | Game mengirim `{uid,username,token,usermode}` (dari sesi Tahap B — **token ASLI hasil panen**, bukan karangan) ke loginchecknative di alamat yang datang dari config game lokal | kombinasi Tahap A+B | rencana siap; verifikasi akhir di perangkat |

**Kenapa ini BUKAN "jejak kaki" lagi:** request login dibuat oleh APK original sendiri
(fingerprint perangkat asli), token/uid yang dikirim = token/uid **asli dari server SDK**
hasil panen live dengan tiket request asli per perangkat — server game memverifikasi
pasangan asli, bukan tiruan.

**Opsi cadangan bila loginchecknative menolak token panen** (mis. sesi diikat waktu/server):
karena alamat loginserver ada di config game LOCAL (bagian 2c), kita bisa mengarahkan
endpoint `loginchecknative` ke MiniServer dan menjawab `{"code":0}` — murni keputusan
config, tanpa penerusan. **Ini OPSI — hanya dieksekusi bila user memutuskan** (menilai
sendiri risikonya: game world sesudahnya tetap server asli).

**Gap yang jujur:** HAR perangkat berhenti di getNotice → belum ada capture respons
registerVisitor nyata → format sudah terbukti dari parser, ISI token/uid asli menunggu
Tahap B. Ini satu-satunya bahan yang belum di tangan.

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
| **user (sesi/akun)** | `registerVisitor` ✅ format terbukti (bagian 2b) · `registerUser` (state-bound) · `ckRegVistor` ✅ · `autoLogin` (format = registerVisitor, parser sama) · `loginByName` · `userLoginByOtherSdk` · `findPassByEmail` · `sendCodeByEmail` · `actCdKey` · `postGooglePlayVerify` · `getServerAgreement` (butuh agmId) |
| **auth (aksi akun/bayar/event)** | `getUserInfo` · `createOrder` · `bindMail` · `changePassword` · `cksnslogin` · `fbActInit` · `fbInviteEvent` · `fbLikeEvent` · `fbShareEvent` · `fbUserClaimEvent` · `setGameRoleInfo` · `userBindOtherSdk` · `unBindOtherInfo` · `viewMigCode` · `cUserTrash` · `jianchaLayLx` |

**Yang merupakan CONFIG hanya 3 + 1 sesi**: `init`, `getNotice`, `getAgreement` (semua
✅ dipanen dari server hidup), dan `registerVisitor` untuk Gerbang 4 (format ✅, nilai
sesi menunggu panen Tahap B). Sisanya = **aksi berbasis sesi** — bukan config; terbukti:
uid antar perangkat berbeda (28486093 vs 28505968); replay registerUser → 40008.

---

## 11. BUKTI PERANGKAT (HAR sesi uji 1.0.2-tes1 — `bukti/device-har/`)

| Bukti | Nilai |
|---|---|
| init leg A (SDK → 127.0.0.1:28110) | 200, 764 B, md5 `06a396d4655a44adf4e101e8519da164` |
| init leg B (MiniServer → qsdk.t4game.com) | 200, 764 B, **md5 sama → byte-identik** (proxy verbatim terbukti) |
| getNotice via local | 200, 55 B, md5 `dfc2f19b1273…` = sama dengan panen live, **tanpa leg upstream** (mirror terbukti) |
| respons init perangkat user | `requestIp:"182.9.2.96"`, `countryCode:"ID"` — gema per-pemanggil; **keduanya tidak dipakai koneksi** (bagian 4a/4c) |
| Batas capture | sesi berhenti di getNotice → registerVisitor/loginchecknative belum terlihat → Tahap B menunggu tiket |

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
10. **Sesi (Gerbang 4):** `registerVisitor` wajib memuat `authToken` +
    `userData.{uid,username,token,isGuest}` (string) — salah satu hilang = login gagal
    (bagian 2b). `usermode` dihitung game dari `isGuest`, bukan field terpisah.

---

## 13. INDEKS BAHAN (folder ini)

| Bahan | Isi |
|---|---|
| `config-local/` | **THE CONFIG** — 5 file JSON siap-layani + README aturan edit (bagian 0) |
| `bukti/panen-live/` | 8 respons asli server hidup (init 767 B, getNotice 55 B, getAgreement 188 B, onNewToken 55 B, ckRegVistor 66 B, getUserInfo 75 B, getServerAgreement 104 B, dmsg 108 B) + `_probe-semua.json` + `_meta-panen-live.json` |
| `bukti/riset-login/` | Dump dex alur login (2026-10-20): parser registerVisitor/autoLogin + loginchecknative (`dump-login-endpoint-parser.txt`), params loginchecknative dari `onLoginFinished` (`dump-onLoginFinished-params.txt`), base URL loginserver dari config game (`dump-loginserver-baseurl.txt`) |
| `bukti/dex-evidence/` | Dump instruksi mentah dex ORIGINAL: `dump-full.txt`, `dump-model.txt`, `xref-fields.txt`, `map-usage-config.txt`, `dump-qgm.txt`, `dump-bind.txt`, `dump-sign.txt`+`dump-md5.txt`, `dump-apply.txt`, `har-inventory.txt` |
| `bukti/device-har/` | `bukti-leg-A-identik-leg-B.json`, `tiket-request-asli-har.json` (TIKET, bukan config) |
| `../../log/sdk.har` | HAR lengkap perangkat (26 MB) — sumber bukti device-har/ |

## 14. STATUS & BATAS

- ✅ Config SDK asli: KETEMU, terbukti 3 sumber — **tersusun sebagai `config-local/`** (bukan jejak).
- ✅ Alur update/blok (Fase 1–5): dikuasai LOCAL sejak 1.0.1.
- ✅ Gerbang 4: **rantai login terbedah penuh dari dex** (bagian 2) — format sesi + params
  loginchecknative + sumber alamat loginserver (config game, sudah local).
- ⏳ Tahap B (isi sesi asli registerVisitor): menunggu 1 capture login dari perangkat user
  sebagai tiket panen live — satu-satunya bahan yang belum di tangan.
- ⛔ Implementasi build: **DITAHAN sampai user memerintahkan** (riset saja per arahan).

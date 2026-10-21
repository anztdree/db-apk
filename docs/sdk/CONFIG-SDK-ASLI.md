# SDK — DOC TUNGGAL (config + gerbang login + kondisi final)

> **Ini satu-satunya doc SDK — selalu di-upgrade file ini, TIDAK pernah dibuat doc/folder
> baru atau per-versi.** (Aturan user: repo hanya berisi KONDISI FINAL; terlalu banyak
> file/folder per-versi = sampah + bikin bingung mana config yang benar.)
>
> **Produk = DB-Local 1.0.2 — FINAL, dan tetap 1.0.2.** Tidak ada 1.0.3/1.0.4/1.0.5 sbg
> versi: semua pengerjaan itu TAHAP-TAHAP dalam 1.0.2 yang sama (sejarahnya ringkas di §10).
> Rilis tayang: release `v1.0.2`, aset `DB-Local-1.0.2.apk` (125.280.859 B), sha256
> `b32352ce…585300` — berisi SDK lokal penuh: config + gerbang login + autoLogin +
> database akun + 8 endpoint akun/keamanan. **Terverifikasi lapangan di perangkat**
> (HAR 2026-10-21: register → gerbang game → restart → password salah ditolak → password
> benar diterima — 100% lokal, nol qsdk).
>
> Penanda `VERSION` di dalam mirror APK = pemicu re-sync internal antar-install SAJA —
> BUKAN nomor versi produk. Nomor produk tidak pernah berubah dari 1.0.2.

---

## 1. PRINSIP & ATURAN YANG MENGIKAT (dari user, selalu berlaku)

1. **ATURAN #1: capture ≠ config asli.** HAR/capture = alat bantu & tiket petunjuk saja,
   BUKAN acuan pondasi — berlaku juga untuk login/register/guest. Pondasi = jawaban ASLI
   server hidup + bukti instruksi dex.
2. **GENERATE, bukan sisa jejak:** token/uid dibangkitkan BARU secara lokal. Sah karena
   token = string opaque di seluruh rantai klien (§9). Dilarang memakai identitas
   panen/perangkat di APK (dicek otomatis saat packaging).
3. **NATURAL, tanpa jaring:** *"semua fitur SDK ini seharusnya wajib bisa di local karena
   kita sudah sepakat baik itu pembayaran atau CDK"* — endpoint diimplementasi sungguhan
   berbasis kontrak dex; TIDAK ada catch-all error sebagai tujuan akhir.
4. **TANPA bridge/forward** — server lokal MENJAWAB, bukan meneruskan ke qsdk.t4game.com.
5. **Config SDK di LOCAL, bebas diatur user** — edit file di `Android/data/com.db.local/
   files/server/` → langsung efektif antar boot (copyDir tidak menimpa file yang ada).
6. **Versioning:** produk selalu **1.0.2**. Tidak ada folder/doc per-versi (1.0.5, 1.0.6,
   dst. = DILARANG). Milestone baru = upgrade doc ini + config lokal di tempat yang sama.
7. **Pembayaran + config lainnya → WEBSITE LOKAL** (fase berikutnya; folder
   `docs/login-server/` disiapkan user): player membeli → order muncul di web →
   dikonfirmasi via web; web = developer config local (rumah CDK, migcode, order, config).
   **SNS/Facebook diskip** (*"tidak perlu karena logis local"*).

### Sumber kebenaran (metode kerja — berlaku meski artefak bukti tidak disimpan di repo)

| Sumber | Isi |
|---|---|
| 1. DEX ORIGINAL APK | field yang BENAR-BENAR diparse SDK (instruksi, bukan tebakan) — kontrak terpenting terdokumentasi §5–§8 doc ini |
| 2. SERVER ASLI HIDUP (qsdk.t4game.com) | panen live respons config & sesi = acuan FORMAT (panen terdokumentasi nilai/hash-nya di doc ini) |
| 3. HAR PERANGKAT (`log running/sdk.har`) | tiket request asli + verifikasi lapangan |

> Dump bukti mentah & folder sumber build sengaja TIDAK disimpan di repo (dibersihkan
> user — repo hanya kondisi final). Kontrak yang dibutuhkan terdokumentasi penuh di doc
> ini; provenance final = APK rilis itu sendiri (classes2.dex 35.056 B).

---

## 2. ARSITEKTUR

Config SDK asli = **2 lapisan**:

| Lapisan | Isi | Lokasi fisik |
|---|---|---|
| A. Tertanam di APK | penunjuk alamat `QHinfo` (22 B, asli `http://qsdk.t4game.com`), `product_code` (resource `0x7f0c0143` = `75880787318053043365515388491588`), `sdkVersion=223`, **secret sign `0b2a18e45d7df321`** | `assets/QHinfo`, `resources.arsc`, `classes.dex` |
| B. Dijawab server (`/v1/*` + gerbang) | `init` = otak (saklar UI/login/update/pembayaran), `getNotice`, `getAgreement`, sesi (registerVisitor/registerUser/loginByName/autoLogin), aksi akun/CDK/order | kini di-LOCAL-kan: MiniServer di `127.0.0.1:28110` |

**Mekanisme inti (terbukti dex):** SDK original MEMBANGUN sendiri request sah-nya
(`sv=v2`, `data=Base64(JSON)`, `sign=MD5(...+secret)`) lalu POST ke isi `QHinfo` +
endpoint. `QHinfo` di-flip 22→22 B ke `http://127.0.0.1:28110` → request asli yang sama
mendarat di MiniServer → kita cukup **menjawab** dengan config lokal. Satu flip menutup
seluruh jalur SDK karena pembaca `QHinfo` hanya satu: `QuickGameManager.initThirdDataSDK`
→ `bindcommunist.cointhreat.seventygenom[0]` (teraudit: tidak ada hardcode `qsdk` lain di
dex/.so/asset).

**Komponen MiniServer (classes2.dex, hook `MiniServer.start(Context)` di
`LaunchActivity.onCreate` instruksi #2, warisan 1.0.1):**
- `ServerSocket 127.0.0.1:28110` (daemon), drain body POST, mime map (CT live `text/json`,
  `.mp3=audio/mpeg`, `.zip=application/zip`), reject `..`, `Connection: close`.
- `bootstrapSync`: `assets/mirror/` → `getExternalFilesDir(null)/server/` (file yang SUDAH
  ADA → DILEWATI, edit user bertahan).
- **Re-sync versi:** boot membaca `mirror/VERSION` vs `server/VERSION` (penanda INTERNAL,
  bukan nomor produk); beda/kosong → sync penuh (file lama dibuang) **kecuali DB user
  dijaga**: `generator.json`, `accounts.txt`, `codes.txt`, `bindings.txt`, `migcodes.txt`,
  `outbox.txt`.
- `resolve`: `files/server/<path>` dulu → fallback assets mirror.
- Generator `DbGen` + mini JSON `DbJson`; decoder amplop `valueFromEnvelope`
  (form `data` → Base64 → JSON) — dipakai semua endpoint sesi/aksi.

**Gerbang game lokal:** alamat `loginchecknative` bersumber 100% dari config game
`setting_BS_Android.bin` (JSON XOR "DragonBall"): `loginServer` + `loginWebPort` (parser
`LaunchActivity.worktopichardtails` → builder `pursetruechild.ironoriginhoblike
.seventygenom` format `%s:%d`; TIDAK ada hardcode host di dex). Bin mirror dibuat lokal:
`loginServer: http://127.0.0.1` + `loginWebPort: 28110`.

---

## 3. ALUR SESI (dex + HAR, urutan nyata)

```
LaunchActivity (channel "BS")
 ├─ Fase 1–2 : config_BS.properties → setting_BS_Android.bin      [LOCAL sejak 1.0.1]
 ├─ Fase 3–5 : mirror bs/ + upgrade/ dari 127.0.0.1:28110         [LOCAL sejak 1.0.1]
 │              (com_db_local.bin 404 = NORMAL — probe launcher, fallback aman)
 └─ SDK : QuickGameManager.initThirdDataSDK
      1. POST /v1/system/init      → parser model/e               [OTAK — bagian 5]
      2. POST /v1/system/getNotice → content + showNode           [bagian 5]
      3. sesi: registerVisitor (guest) / loginByName / registerUser / autoLogin
         → {authToken, userData{uid,username,token,isGuest}}      [bagian 4, 9]
      4. game: GET <loginServer:loginWebPort>/loginchecknative
              ?uid=…&username=…&token=…&usermode=…&appVersion=1&osVersion=31
              &sourceVersion=20200418&model=…&packageName=com.db.local
              &brand=…&os=Android&sdk=BS
         → code==0 → LOLOS (log tag asli "sdk登录验证url"/"sdk登录验证返回", retry 3×)
      5. startGame → engine/dunia game (respons gerbang diteruskan utuh ke JS:
         MainActivity.hardlinerspare → payload event startGame/startTsGame)
   (getAgreement dari UI perjanjian; getUserInfo pasca-login)
```

**LEG 0** injeksi 1.0.1: diff classes.dex original→1.0.1 = SATU method berubah
(LaunchActivity.onCreate 234→244 B, invoke `MiniServer.start`, dibungkus try/catch
senyap); classes2.dex awal 6.020 B = MiniServer/ConnRun statik.

---

## 4. THE CONFIG — `config-local/` + aturan edit

Folder `config-local/` = **satu-satunya config yang benar** (materi yang dilayani
MiniServer, bukan jejak). **Edit file → langsung efektif.**

| File (config-local/) | Endpoint | Peran / status material |
|---|---|---|
| `system/init.json` | POST /v1/system/init | **OTAK** — ✅ FINAL, panen live 2026-10-20 (requestIp disunting; terbukti tidak dipakai koneksi) |
| `system/getNotice.json` | POST /v1/system/getNotice | Pengumuman — ✅ FINAL (55 B live) |
| `system/getAgreement.json` | POST /v1/system/getAgreement | Perjanjian (semua kosong) — ✅ FINAL (188 B live) |
| `user/ckRegVistor.json` | POST /v1/user/ckRegVistor | Cek registrasi guest — ✅ FINAL (`isReged:1`) |
| `user/registerVisitor.json` | POST /v1/user/registerVisitor | **SESI GUEST — TEMPLATE GENERATOR** (bentuk = live 2345 B) |
| `user/registerUser.json` | POST /v1/user/registerUser | REGISTER — GENERATOR + DB AKUN; acuan sukses asli satu-kali |
| `user/loginByName.json` | POST /v1/user/loginByName | LOGIN — GENERATOR + DB AKUN; acuan live 2429 B |
| `auth/getUserInfo.json` | POST /v1/auth/getUserInfo | Profil bersesi — GENERATOR (acuan live 280 B ×2 varian) |
| `user/autoLogin.template` + `autoLogin.error.template` | POST /v1/user/autoLogin | Sesi akun/guest ter-validasi + bentuk error live 40008 |
| `user/error.template` | (bersama) | Bentuk error live 40008, id/message → placeholder |
| `game/loginchecknative.json` | GET <loginServer>/loginchecknative | **GERBANG — TEMPLATE GENERATOR** (bentuk 10 field `code:0` live) |

### Aturan edit (salah format = SDK tolak — bukti parser dex)

1. **Kunci wajib jangan hilang.** `init.json`: `payTypes` (boleh `[]`, tapi kuncinya HARUS
   ada — hilang = parser return null = init gagal), `productConfig`, `version`,
   `loginTypes`, `nodes`. Sesi: `authToken` + `userData.{uid, username, token, isGuest}` —
   hilang satu = parser exception → login gagal.
2. `productConfig` & `version` dibaca sebagai **STRING** (`"0"`/`"1"`, pakai kutip).
   **`isShowFloat` KEBALIKAN:** `"0"` = float TAMPIL, `"1"` = sembunyi.
   (Sebaliknya `isGuest` di sesi = ANGKA di respons asli — ikuti format asli.)
3. `getNotice.json`: `data` harus **OBJECT** `{content, showNode}`; `data: []` = tanpa
   notice (normal); array berisi = exception → notice diabaikan.
4. `version` semua `"empty"` = tanpa dialog update (perilaku asli). Mau paksa update:
   `versionNo` angka > versionCode + `isMust`.
5. `loginTypes` dibaca dengan `String.contains` atas teks array → tulis persis seperti
   live `["1","13"]`; `"1"` wajib ada agar login guest tampil; `"6"` FB, `"8"` Google,
   dst. — hapus elemen = sembunyikan layanan.
6. `requestIp` bebas — tidak dipakai koneksi; hanya API game `getCountryInfo()`.
7. `logo` hanya diterima bila diawali `http`; `guestShowBind` default TRUE bila hilang.
8. JSON boleh pretty-print (parser `org.json`, whitespace tak berpengaruh).
9. Content-Type respons = `text/json` — pertahankan.
10. Gerbang: jawaban harus 10 field live dengan `code` ANGKA (`getInt("code")==0`).

### Placeholder generator (token BARU, bukan jejak) — semua bisa diatur user

| Placeholder | Diisi | Aturan |
|---|---|---|
| `{{UID}}` | uid lokal | angka; persisten per perangkat; bisa DIKUNCI user via generator.json |
| `{{USERNAME}}` | nama user | default `Guest<uid>`/pola bebas |
| `{{TOKEN}}` | token BARU per sesi | format BEBAS — parser hanya `getString` |
| `{{AUTH_TOKEN}}` | authToken sesi SDK | format BEBAS, disimpan apa adanya |
| `{{LOGIN_TOKEN}}` | token jawaban gerbang | baru per request; game tak membaca di jalur gerbang, JS terima via startGame |
| `{{SIGN}}` | tanda gerbang | pola live STABIL per uid → `md5(uid + salt)`, salt diatur user |
| `{{IS_GUEST}}` / `{{ERROR_ID}}` / `{{ERROR_MESSAGE}}` | jenis sesi / error | diisi DbGen |

`generator.json` (external files, dijaga re-sync): `uid` (kosong = auto-seed sekali lalu
terkunci), `username`, `salt` (default `dblocal-102`), + sesi terakhir: `lastAuthToken`,
`lastUsername`, `lastIsGuest`, `lastUid`.

### Sifat sesi (TERBUKTI panen live 2026-10-20)

| Sifat | Bukti | Konsekuensi lokal |
|---|---|---|
| Sesi guest terikat perangkat & dapat diulang | tiket sama → sesi SAMA persis | uid generator persisten per perangkat |
| loginchecknative menerima token berulang | GET sama → `code:0` tiap panen; loginToken baru, sign stabil per uid | validasi per-sesi: token baru per sesi, uid stabil, jawaban `code:0` |
| registerUser SATU-KALI (state-bound) | panen ulang → `40008 pengguna ini telah digunakan` | server lokal = DB: register diterima sekali, duplikat ditolak (id=3) |
| loginByName dapat diulang | panen ulang → sesi akun sama | login = validasi password (DB akun) |
| uid antar perangkat berbeda | 28486093 vs 28506361 | generator membangkitkan uid sendiri |

---

## 5. KONTRAK PARSER `/v1/*` (bukti instruksi dex)

Semua respons `/v1/*` ber-wrapper `{result: bool, data: ..., error: {id, message}}`.
Client: `getBoolean("result")` → bila true `getString("data")` (koersi JSON→teks) → parser
spesifik. `result:false` → jalur error (SDK menampilkan `message`). Contoh error live:
`40005 Otorisasi gagal` (getUserInfo tanpa sesi), `40008` (replay registerUser),
`20003` kurang parameter.

### 5a. `v1/system/init` — parser `model/e` (menerima teks objek `data`)

| Field | Rule baca | Konsumen / efek |
|---|---|---|
| `nodes.serverInfo` | int | gerbang menampilkan data server |
| `nodes.serverInfoData` | array `{id,title}` | list pusat layanan (bila serverInfo==1) |
| `nodes.removeEmail` | ==1 → true | sembunyikan opsi email di Account Center |
| `nodes.deleteAccount` | dibaca lalu dibuang (dead read) | tanpa efek |
| `loginTypes` | `toString().contains(...)` — bukan parse elemen | flag login per layanan; missing → "only show default fb login" |
| `isNotGift`, `showShare`, `showDiscount` | int / koersi | FacebookShareActivity / AccountCenter |
| `webUrl` | getString | disimpan, tanpa konsumen lain |
| `requestIp` | getString | API game `getCountryInfo()` = `"<channel>|<requestIp>"` — bukan alamat koneksi |
| `payTypes` | getJSONArray | **KUNCI WAJIB** — hilang = init gagal; `[]` sah |
| `productConfig` | object → `model/d` | saklar UI global — sebaiknya selalu ada (konsumen UI baca instance langsung) |
| `otherpayType`, `payFast` | string koersi | payment flow (`[]` live dikonversi `"[]"`) |
| `lightPackage` | optString/parseInt | paket ringan; tak ada di live → aman dilewati |
| `version` | object → `model/g` | cek update (5b) |

`productConfig` (semua nilai STRING, parser `model/d`): `useServiceCenter` (=="1" ATAU
`serviceInfo` tak kosong → true), `title`, `logo` (wajib awalan http), `useSms`,
`serviceInfo`, `isShowFloat` (**=="0" → true, KEBALIKAN**), `guestShowBind` (=="1" → true;
default TRUE), `single_play`, `initCZurl`, `adZhifuSwt`.

TIDAK diparse (bebas diubah/hilangkan): `first_install`, `nodes.oneYeaerTips`,
`productConfig.{skinStyle, noticeNode, regMailVerify, pycenttype, onGoToPUrl,
onGoToPUrlData, trashTime, noFirebaseLog, logEvtReport}`, `countryCode` (gema negara
pemanggil — diabaikan klien), `version.updateTime` (dibaca lalu dibuang).

Respons live init (767 B; di perangkat user `requestIp:"182.9.2.96"`, `countryCode:"ID"`
— gema per-pemanggil):

```json
{"result":true,"data":{"payTypes":[],"version":{"versionName":"empty","versionNo":"empty","versionUrl":"empty","updateTime":"empty","isMust":"empty","updateTips":"empty"},"productConfig":{"useServiceCenter":"0","isShowFloat":"0","showShare":"1","title":"","logo":"","useSms":"0","skinStyle":"0","serviceInfo":"","guestShowBind":1,"single_play":0,"adZhifuSwt":0,"noticeNode":0,"regMailVerify":0,"pycenttype":0,"onGoToPUrl":0,"onGoToPUrlData":"","trashTime":0,"noFirebaseLog":0,"logEvtReport":0},"otherpayType":2,"payFast":[],"isNotGift":0,"showShare":"1","requestIp":"47.57.232.232","showDiscount":0,"nodes":{"deleteAccount":0,"serverInfo":0,"oneYeaerTips":0,"removeEmail":0},"loginTypes":["1","13"],"countryCode":"HK","first_install":0},"error":{"id":0,"message":""}}
```

### 5b. `version` — parser `model/g`
`versionNo` `"empty"` diabaikan; selain itu parseInt → dibanding versionCode APK (lebih
besar → dialog update). `versionName`/`versionUrl`/`updateTips` = teks dialog;
`isMust` "1"/true = paksa; `updateTime` dibuang.

### 5c. `getNotice` — parser `singersmooth.cointhreat`
`result` → `getString("data")` → bila panjang > 2: `data` WAJIB OBJECT → `content` +
`showNode`. Live: `{"result":true,"data":[],"error":{"id":0,"message":""}}` (55 B,
byte-identik antar hari).

### 5d. `getAgreement`
9 field string kosong (188 B live, byte-identik antar panen) → statik sah.

### 5e. Format sesi (parser `psalmicvolleyball.cointhreat.cointhreat` → `QGUserData.generateFromJson`) — WAJIB

```json
{"result":true,"data":{
   "authToken":"…",                    ← WAJIB (getString)
   "tips":"", "checkRealName":"0",     ← opsional
   "userData":{
     "uid":"…", "username":"…",        ← WAJIB
     "displayUid":"",                  ← opsional (kosong → tampil = uid)
     "token":"…",                      ← WAJIB — dikirim ke loginchecknative
     "isGuest":1,                      ← WAJIB (ANGKA di respons asli)
     "isNewUser":0, "mobile":"", "fbName":"", "pushToken":"", "noLoginLongTime":"", ← opsional
     "bindInfo":{"bindFB":{…}, …}      ← opsional (12 kanal bind di respons asli)
   }
 },"error":{"id":0,"message":""}}
```

Format sama untuk registerUser (isGuest 0, isNewUser 1), loginByName, autoLogin.
`usermode` dihitung game dari isGuest (bukan field terpisah). Android `org.json` lenient:
`getString("data")` pada objek mengembalikan teks JSON-nya → bentuk templat sah untuk
parser mana pun.

### 5f. Gerbang `loginchecknative` — jawaban 10 field
`{"code":0,"sdk":"BSNative","nickName":"…","loginToken":"…","userId":"…","sign":"…",
"sdkType":"BS","osType":"Android"}` — `code` ANGKA (`getInt`); loginToken baru per
panggilan; sign stabil per uid. code≠0 → `msg`/`message` → dialog error.

---

## 6. REQUEST ASLI APK ORIGINAL (& kenapa server lokal tak perlu validasi sign)

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

Secret sign `0b2a18e45d7df321` tertanam dex — terverifikasi cocok utk init & getNotice di
HAR. Executor: URL = `base[0] + endpoint` (tanpa path), syarat sukses
`getResponseCode()==200`; gagal → retry 5 dtk → null (SDK terima null tanpa crash).
**Konsekuensi:** sign hanya alat server asli memverifikasi klien — server lokal cukup
menjawab dgn wrapper & field yang benar. Password klien = **MD5 hex** sebelum dikirim
(dex `MessageDigest.getInstance("MD5")`; terkonfirmasi HAR: `md5("123456")`).

---

## 7. DAFTAR LENGKAP 32 ENDPOINT `/v1/*` DI DEX + STATUS

| Kategori | Endpoint | Status |
|---|---|---|
| **system (config)** | `init` ✅ · `getNotice` ✅ · `getAgreement` ✅ · `dmsg` ✅ statik · `onNewToken` ✅ statik | 5/5 LOKAL |
| **user (sesi/akun)** | `registerVisitor` ✅ generator · `registerUser` ✅ generator+DB · `ckRegVistor` ✅ statik · `autoLogin` ✅ generator tervalidasi (Opsi A) · `loginByName` ✅ generator+DB · `getServerAgreement` ✅ statik · `sendCodeByEmail` ✅ · `findPassByEmail` ✅ · `actCdKey` ⬜ tahap CDK · `postGooglePlayVerify` ⬜ fase C · `userLoginByOtherSdk` ⛔ SNS diskip | |
| **auth (aksi akun/bayar/event)** | `getUserInfo` ✅ generator · `changePassword` ✅ · `bindMail` ✅ · `viewMigCode` ✅ · `cUserTrash` ✅ · `unBindOtherInfo` ✅ · `userBindOtherSdk` ✅ · `setGameRoleInfo` ⬜ tahap CDK · `createOrder` ⬜ fase C · `jianchaLayLx` ⬜ fase C · `cksnslogin` ⛔ · `fbActInit` ⛔ · `fbInviteEvent` ⛔ · `fbLikeEvent` ⛔ · `fbShareEvent` ⛔ · `fbUserClaimEvent` ⛔ | |

⬜ = antrian sesuai rencana; ⛔ = diskip keputusan user (SNS "logis local"). Gerbang
`loginchecknative` (bukan /v1) = ✅ lokal.

---

## 8. KONTRAK 19 RUTE TAMBAHAN (bukti dex — bedah androguard atas classes.dex asli)

### 8a. Amplop request BERSAMA (wrapper `bindcommunist.singersmooth.singersmooth(...)`)

Field umum **di dalam JSON**: `sdkVersion`(223), `gameVersion`, `deviceId`, `serialNum`,
`devIDShort`, `platform`, `productCode`, `channelCode`, **`authToken`** (bila ada sesi),
`clientLang`, `suggestCurrency`, `time` + field spesifik endpoint. Lewat kabel:
`sv=v2&data=Base64(JSON)&sign=…` — decoder `valueFromEnvelope` (terbukti di perangkat)
dipakai ulang semua endpoint. `authToken` = identitas pemanggil untuk operasi per-akun →
dipetakan ke DB via generator.

### 8b. Kontrak per endpoint (req = field JSON; resp = yang dibaca SDK)

| Endpoint | Request (field spesifik) | Respons yang dibaca SDK |
|---|---|---|
| `/v1/user/actCdKey` | `cdkey` | `result`; **sukses → `data` = SESI REFRESH** (8c), callback 14 |
| `/v1/auth/setGameRoleInfo` | `uid, gameRoleId, gameRoleName, serverName, gameRoleLevel, vipLevel, partyName, gameRoleBalance` | `result` (+`error` bila gagal); fire-and-forget |
| `/v1/auth/createOrder` | `uid, goodsId, orderSubject, payType, productOrderNo, roleLevel, roleName, serverName, callbackUrl, extrasParams` | `result`; `data.orderNo` |
| `/v1/auth/jianchaLayLx` | `amount, currency, goodsId, roleLevel` | `result`, `data`, `apiUrlH5`, `firstPy`, `othpy_type`, `error` — pemilih kanal bayar |
| `/v1/user/postGooglePlayVerify` | map verifikasi purchase | shaped result |
| `/v1/user/sendCodeByEmail` | `email, sendType` | `result`, `data`/`error` object |
| `/v1/user/findPassByEmail` | `email, code, newPassword` | `result`, `data` |
| `/v1/auth/changePassword` | `uid, oldPassword, newPassword` | `result` |
| `/v1/auth/bindMail` | `uid, email, code, password` | `result`, `data`/`error` object |
| `/v1/auth/viewMigCode` | `uid` | `result`, `data`(object), `error` |
| `/v1/auth/cUserTrash` | `uid, tStatus` | `result` |
| `/v1/auth/unBindOtherInfo` | `uid, openType, userOpenId` | `result`, `data`/`error` object |
| `/v1/auth/userBindOtherSdk` | `uid, appToken, access_token, openType, otherAccountName, userOpenId` | `result`, `data`/`error` object |
| `/v1/auth/cksnslogin` + `/v1/user/userLoginByOtherSdk` | `uid, authToken, appToken, access_token, openType, userOpenId` | `result`, `data` |
| `/v1/auth/fbActInit` | `uid, roleInfo, serverInfo` | `result`, data array, `error` |
| `/v1/auth/fbInviteEvent` | `uid, roleInfo, serverInfo, totalUser` | idem |
| `/v1/auth/fbLikeEvent` | `uid, roleInfo, serverInfo, edgeType, fburl` | idem |
| `/v1/auth/fbShareEvent` | `uid, roleInfo, serverInfo, fburl, shareStatus` | idem |
| `/v1/auth/fbUserClaimEvent` | `uid, roleInfo, serverInfo, actId, claimStatus` | idem |

Catatan: pasangan `.put("id"/.put("message")` di beberapa dump = pembangun objek error
lokal untuk callback (bukan field request).

### 8c. Temuan kunci CDK: `data` actCdKey = SESI REFRESH (parser `psalmicvolleyball.cointhreat`)

```json
{ "authToken": "…", "checkRealName": "0", "tips": "…", "userData": { … "bindInfo": {…} } }
```

Sukses CDK → SDK **mengganti sesi aktif** dengan payload ini + callback 14. Bentuk
`userData` = bentuk yang SUDAH dihasilkan generator (registerVisitor/login) → template
sesi dipakai ulang; CDK sukses = token baru + userData akun.

---

## 9. BASIS BUKTI GENERATOR — kenapa token generate SAH

Token = **string opaque di seluruh rantai klien**:

| Titik | Pembuktian (dex original) | Konsekuensi |
|---|---|---|
| `QGUserData.generateFromJson` | `token` & `authToken` hanya `getString`, TANPA cek format/panjang/signature | nilai token bebas |
| Parser sesi `psalmicvolleyball.cointhreat` | pre-cek hanya input kosong/`[]`/`{}` | generator bebas menentukan isi |
| Penyusun request gerbang `onLoginFinished` | HashMap `{uid, username, token, usermode}` langsung dari bean | token generate dikirim apa adanya |
| Runnable gerbang `nationalcommunitymissing.run` | `getInt("code") == 0` → LOLOS; else `msg`/`message` | jawaban lokal cukup `code:0` |
| Konsumen lanjut `MainActivity.hardlinerspare` | respons gerbang utuh diteruskan ke JS (payload startGame) | bentuk 10 field live dipertahankan, nilai generate |

Pola server asli ditiru perilakunya: uid stabil per perangkat, token baru per sesi,
`sign` stabil per uid, `loginToken` baru per panggilan.

**Aturan autoLogin (Opsi A — setia server asli):** generator menerbitkan token →
generator berhak memvalidasi. authToken == terbitan terakhir → sesi dipulihkan (rotasi
token seperti server asli); asing/kosong/rusak → `result:false` bentuk 40008 → layar
login (persis token kadaluarsa di server asli). Sesi terakhir dipersist di
`generator.json` (lintas boot, dijaga re-sync). Upgrade pertama dari build lama: login
manual sekali, lalu auto-masuk selamanya — natural.

---

## 10. KONDISI FINAL 1.0.2 (APA YANG ADA DI APK RILIS SEKARANG)

**Satu APK final:** release `v1.0.2` → `DB-Local-1.0.2.apk` — 125.280.859 B, sha256
`b32352ce35d0006111a6a1ba766b61881ccd1e65399cf558ca62abf6be585300`. Sertifikat IDENTIK
dengan 1.0.1 (serial `56c1a15`, SHA256 `1e08a903…b5953`) → install-over aman. NOL
kebocoran identitas panen/perangkat (dicek otomatis saat packaging). `classes.dex` asli
hanya membawa injeksi warisan 1.0.1; seluruh server lokal ada di `classes2.dex`
(35.056 B) berisi MiniServer + DbGen + DbJson + ConnRun.

**Isi fungsional final:**
- Config boot & SDK 100% lokal: setting bin, mirror bs/upgrade, 5 statik `v1/system/*`,
  `ckRegVistor`, `getServerAgreement` (byte-exact live).
- Sesi: `registerVisitor`, `registerUser`, `loginByName` (generator + DB akun),
  `autoLogin` (Opsi A tervalidasi), `getUserInfo` (per sesi).
- Akun & keamanan (8): `sendCodeByEmail`, `findPassByEmail`, `changePassword`, `bindMail`,
  `viewMigCode`, `cUserTrash`, `unBindOtherInfo`, `userBindOtherSdk`.
- Gerbang lokal `loginchecknative` (`code:0`, 10 field live, sign = md5(uid+salt)).

**Tahap pengerjaan (semua 20-10-2026 — HANYA catatan sejarah, BUKAN versi terpisah;
produk tetap 1.0.2):**

| Tahap | Isi | uji JVM |
|---|---|---|
| basis 1.0.1 | offline: config game + mirror + upgrade lokal (3 patch: EntryPoint, classes2.dex statik 6.020 B, hook LaunchActivity) | — |
| SDK menjawab + gerbang | 7 statik byte-exact + generator sesi + gerbang lokal + re-sync VERSION + generator.json (dex 17.056 B) | 22/22 |
| autoLogin | rute + validasi authToken terbitan + persist sesi (dex 18.840 B) — dipicu bug autoLogin 404 dari HAR perangkat | 29/29 |
| database akun | `accounts.txt` + passHash md5(klien+salt) + uid per akun + login membandingkan password (dex 23.312 B) — dipicu keberatan user *"password cocok login!!"* yang BENAR | 42/42 |
| akun & keamanan (FINAL) | 8 endpoint + 4 file DB (dex 35.056 B) — **terbang di perangkat 21-10** | 32/32 |

**Rute MiniServer final (semua 127.0.0.1:28110):** statik: setting bin,
`com_db_local.bin` (404 normal), bs/*, upgrade/*, `v1/system/{init,getNotice,getAgreement,
dmsg,onNewToken}`, `v1/user/{ckRegVistor,getServerAgreement}` · generator+DB:
`v1/user/{registerVisitor,registerUser,loginByName,autoLogin}`, `v1/auth/getUserInfo` ·
akun/keamanan: `v1/user/{sendCodeByEmail,findPassByEmail}`,
`v1/auth/{changePassword,bindMail,viewMigCode,cUserTrash,unBindOtherInfo,userBindOtherSdk}`
· gerbang: `GET /loginchecknative?…`.

**Rebuild:** sumber/toolchain TIDAK disimpan di repo (keputusan user — repo hanya kondisi
final). Provenance = APK rilis itu sendiri; bila perlu membedah ulang: decompile
`classes2.dex` dari APK + kontrak di doc ini.

---

## 11. DATABASE LOKAL (di `Android/data/com.db.local/files/server/` — semua DIJAGA re-sync)

```
generator.json : uid, username, salt, lastAuthToken, lastUsername, lastIsGuest, lastUid
accounts.txt   : name<TAB>passHash<TAB>uid<TAB>isGuest<TAB>email<TAB>trash
                 (baris 4-kolom era awal tetap sah — install-over aman)
                 passHash = md5(password_dari_SDK + salt); kosong = guest
codes.txt      : email<TAB>code<TAB>sendType<TAB>createdMs<TAB>used   ← kode email, TTL 10 menit, sekali pakai
bindings.txt   : uid<TAB>openType<TAB>userOpenId<TAB>otherAccountName
migcodes.txt   : code<TAB>uid<TAB>createdMs<TAB>used                  ← kode migrasi DB+8HEX, reuse sampai terpakai
outbox.txt     : ts<TAB>email<TAB>code<TAB>sendType                   ← jurnal "email keluar" lokal (pengganti SMTP)
```

**Perilaku 8 endpoint akun/keamanan (natural, kontrak dex):** `sendCodeByEmail` kode 6
digit → outbox; `findPassByEmail` kode sah → password baru + **SESI REFRESH** (auto-login);
`changePassword` verifikasi lama (salah → id 8); `bindMail` identitas akun MENJADI email
(isGuest→0; duplikat → id 6); `viewMigCode` DB+8HEX reuse; `cUserTrash` tStatus 1=trash
(login/autoLogin DITOLAK id 7) 2=pulih; `unBindOtherInfo`/`userBindOtherSdk` baris
binding (data.status).

---

## 12. BUKTI PERANGKAT (HAR, itel S665L — `log running/sdk.har`)

| Sesi | Kondisi yang diuji | Hasil |
|---|---|---|
| 2026-10-20 09:26 | 1.0.1 | baseline murni: 7 request lokal → loginchecknative :510 (internet) = 200 OK |
| 2026-10-20 09:12 | tes1 (jembatan, historis) | leg A ≡ leg B **8/8 byte-identik** (nilai historis: isi respons asli per perangkat); 3 mode guest/register/login → `code:0` |
| 2026-10-20 02:19 | rilis tag 1.0.2-tes1 (jembatan) | gerbang boot hijau: all.zip 19.385.098 B dalam 3,55 s; getNotice mirror murni |
| 2026-10-20 11:57 | tahap "SDK menjawab" | 90% lulus; **autoLogin 404 ×4** → pemicu tahap autoLogin (rantai generator terbukti hidup: envelope autoLogin membawa authToken terbitan loginByName sesi-1) |
| (lapangan) | setelah tahap DB akun | konfirmasi user: *"password cocok login !!"* |
| **2026-10-21 07:44 WIB** | **FINAL** | **HIJAU penuh**: boot lokal → registerUser 200 (akun BARU tes@mail.com, uid 91429108, isNewUser=1, bindEmail isBind=1) → gerbang `code=0` masuk game → restart → loginByName SALAH ×2 ditolak (`id=2 "username atau password salah"`) → BENAR → 200 sesi penuh (authToken baru) → gerbang `code=0` lagi. **NOL trafik qsdk**; 18/18 lokal = 200; tidak ada autoLogin (instalasi segar, wajar); 77×400 di log = telemetri pihak ketiga (66× graph.facebook.com app-id placeholder `999999999999999`, 11× appsflyer) — bukan jalur game |

---

## 13. KEJUJURAN DESAIN & BATAS (diakui terbuka)

1. **R1' (satu variabel di luar pintu):** dunia JS `main.min.js` (4,9 MB) menerima payload
   startGame utuh; validasi internal JS BELUM dibedah. Respons gerbang dibentuk persis 10
   field live + pola sign → terbukti lolos di perangkat. Bedah main.min.js hanya bila
   dunia game JS ternyata menolak.
2. ISI pesan error lokal (id 1–8) = **kebijakan lokal**; BENTUKNYA dari panen error live
   40008. Server asli tidak pernah mempublikasi teks error ini.
3. authToken pada 8 endpoint akun/keamanan tidak divalidasi ketat (lokal memakai uid dari
   sesi SDK); autoLogin TETAP Opsi A ketat.
4. Kode email TIDAK dikirim via internet — `outbox.txt` = bentuk natural lokalnya.
5. Binding pihak ketiga = identitas LOKAL (tidak ada FB/Google asli — SNS diskip).
6. `isNewUser` pada templat bindMail/findPass = 0 (bukan registrasi baru).
7. `tsLoginServer`/`tsUrl` masih internet (di luar lingkup — target user "sampai masuk
   loginserver saja").
8. Telemetri pihak ketiga (Facebook/AppsFlyer, HTTP 400 dgn app-id placeholder) = noise
   SDK embedded, bukan jalur game; tidak mempengaruhi fungsi.
9. `requestIp`/`countryCode` respons init terbukti MATI di dex (dibaca lalu dibuang) —
   beda antar-panen bukan masalah.

---

## 14. RENCANA LANJUT (menunggu lampu hijau user)

- **Tahap CDK & peran:** `actCdKey` (DB kode CDK → sukses = SESI REFRESH bentuk live,
  §8c) + `setGameRoleInfo` (tabel role per akun — fondasi tahap belakang).
- **Fase C — WEBSITE LOKAL = developer config local:** pembayaran diproses via website —
  player membeli → order muncul di web → dikonfirmasi via web; web jadi rumah bagi CDK,
  migcode, order, dan config server. Endpoint terkait (`jianchaLayLx`, `createOrder`,
  `postGooglePlayVerify`) diarahkan ke alur ini. Folder kerjanya: `docs/login-server/`
  (disiapkan user; doc fase berikutnya = SATU file di situ).
- **SNS/Facebook: DISKIP** (keputusan user: *"tidak perlu karena logis local"*).
- Aturan kerja fase berikutnya SAMA: satu doc di-upgrade, tanpa folder per-versi,
  produk tetap 1.0.2.

# CONFIG SDK ASLI — 1.0.2 (SATU-SATUNYA DOC SDK)

> Status: **CONFIG SDK ASLI = KETEMU & TERBUKTI** (riset 2026-10-20, TANPA implementasi — menunggu keputusan user).
> Bukti dari **3 sumber independen** yang saling mengunci:
> 1. **DEX ORIGINAL APK** (DB-Local.apk, sha256 `3650cbe5…`): field mana yang BENAR-BENAR diparse SDK (instruksi, bukan tebakan).
> 2. **SERVER ASLI HIDUP** (qsdk.t4game.com): panen live respons config → folder `panen-live/`.
> 3. **HAR PERANGKAT** (log/sdk.har, sesi uji 1.0.2-tes1): alur nyata + bukti leg A ≡ leg B → folder `device-har/`.
>
> ATURAN #1 (dari user, selalu berlaku): **capture ≠ config asli**. HAR = alat bantu petunjuk.
> Material config = jawaban server asli yang masih hidup. Target: 1.0.2 — config SDK di LOCAL,
> bebas diatur (edit → langsung efektif), TANPA penerusan, BUKAN hasil capture.
> APK yang dipakai = APK ORIGINAL: SDK-nya sendiri membuat request asli (sv/data/sign) —
> kita hanya perlu MELAYANI config aslinya secara lokal.

---

## 1. RINGKASAN: APA ITU "CONFIG SDK" PADA APK INI

Config SDK asli = **2 lapisan**:

| Lapisan | Isi | Lokasi fisik | Bukti |
|---|---|---|---|
| **A. Tertanam di APK** | Penunjuk alamat `QHinfo` (22 B, `http://qsdk.t4game.com`), `product_code` (string resource `0x7f0c0143` = `75880787318053043365515388491588`), `sdkVersion=223` (hardcode dex), **secret sign `0b2a18e45d7df321`** | `assets/QHinfo`, `resources.arsc`, `classes.dex` | dex: `QuickGameManager.initThirdDataSDK` satu-satunya pembaca QHinfo; `unnecessarysperrylites.seventygenom` |
| **B. Dijawab server** (`/v1/system/*`) | **`init`** = otak (semua saklar UI/login/pembayaran), **`getNotice`** = pengumuman, **`getAgreement`** = perjanjian | respons server `qsdk.t4game.com` (kini bisa LOCAL) | panen-live/ + dex parser (bagian 3–6) |

**Mekanisme "APK original mengirim config SDK aslinya ke kita"** (terbukti dari dex):
SDK original MEMBANGUN sendiri request sah-nya — `sv=v2`, `data=Base64(JSON perangkat)`,
`sign=MD5(...)` dengan secret tertanam `0b2a18e45d7df321` — lalu POST ke
`seventygenom[0]` (isi QHinfo) + endpoint. Setelah QHinfo di-flip ke `http://127.0.0.1:28110`,
**request asli yang sama mendarat di MiniServer kita** → yang perlu kita lakukan hanyalah
**menjawab** dengan config (yang kini ada di LOCAL dan bebas diedit). Tidak ada capture,
tidak ada tiruan jejak — request-nya punya APK sendiri, jawabannya punya kita.

---

## 2. ALUR SEBUAH SESI (dex + HAR perangkat, urutan nyata)

```
LaunchActivity (channel "BS")
 ├─ Fase 1–2 : baca config_BS.properties → setting_BS_Android.bin        [sudah LOCAL sejak 1.0.1]
 ├─ Fase 3–5 : mirror bs/ + upgrade/ dari 127.0.0.1:28110                [sudah LOCAL sejak 1.0.1]
 └─ SDK : QuickGameManager.initThirdDataSDK
      1. POST /v1/system/init      → model/e.cointhreat(data)            [CONFIG OTAK — bagian 3]
           callback sukses (singersmooth.cointhreat):
      2. POST /v1/system/getNotice → content + showNode                  [CONFIG notice — bagian 5]
           cek update: versionCode APK < versionNo → dialog update
      3. POST /v1/user/registerVisitor → uid+username+token             [SESI per-perangkat, BUKAN config]
      4. GET login.popoh5.com:510/loginchecknative → loginToken         [SESI]
      5. socket :610 (game)                                             [SESI]
   (getAgreement dipanggil dari UI perjanjian — bagian 6)
```

---

## 3. RESPONS `v1/system/init` — FIELD PER FIELD (BUKTI DEX)

Parser: `com.quickgame.android.sdk.model.e.cointhreat(String)` — menerima **teks objek `data`**
(callback `psalmicvolleyball.singersmooth.run` membaca wrapper: `getBoolean("result")` →
`getString("data")` → parser). Semua konsumen di bawah = kelas/metode asli di dex (lihat
`dex-evidence/`).

### 3a. Field DIPARSE — wajib ada & bisa diatur

| Field | Tipe | Rule baca (bukti dex) | Konsumen / efek |
|---|---|---|---|
| `nodes.serverInfo` | int | `getInt` | `HWLoginActivity.onCreate` — gerbang menampilkan data server |
| `nodes.serverInfoData` | array `{id, title}` | `getJSONArray` | List pusat layanan (`d/d.onCreateView`, klik: `dishbesideshockey/customreading.onItemClick`); dipakai hanya bila `serverInfo==1` |
| `nodes.removeEmail` | int | `==1 → true` | Sembunyikan opsi email di Account Center (`HWAccountCenterActivity`, `f/a/*`) |
| `nodes.deleteAccount` | int | **dibaca lalu dibuang** (dead read) | tanpa efek |
| `loginTypes` | array string | `toString().contains(...)` — bukan parse elemen! | Flag login: `"1"`→influencecomparisonrestore, `"6"`→FB, `"8"`→Google (+cek GMS), `"9"`→Naver, `"10"`→Twitter, `"11"`→Line, `"12"`→+rawturn, `"14"`→VK, `"17"`→Play Games (+cek GMS); jumlah layanan aktif → counter `dishbesideshockey`. **Missing → "only show default fb login"** |
| `isNotGift` | int | `getInt` | `FacebookShareActivity.onCreate` |
| `showShare` | int | `getInt` (koersi `"1"`→1) | `HWAccountCenterActivity.onCreate` |
| `showDiscount` | int | `getInt` | `HWAccountCenterActivity.onCreate` |
| `webUrl` | string | `getString` | disimpan (`e.porkactorcompanion`) — **tidak ada konsumen lain** |
| `requestIp` | string | `getString` | disimpan (`e.authorpair`) → **`QuickGameManager.getCountryInfo()` = `"<channel>|<requestIp>"`** — API untuk GAME (deteksi wilayah). Bukan alamat koneksi! |
| `payTypes` | array `{payTypeId:int, payName}` | `getJSONArray` | Daftar metode pembayaran (bean/a). **KUNCI WAJIB: key hilang → parser return null → "productInfo null" → init dianggap gagal.** `[]` = sah (tak ada channel pembayaran) |
| `productConfig` | object | `getJSONObject` → `model/d` | Saklar UI global — rincian 3b. Hilang → `e.singersmooth=null` (konsumen UI berisiko NPE) — **sebaiknya selalu ada** |
| `otherpayType` | (string koersi) | `getString` | Payment flow (`gangclothing/singersmooth`) |
| `payFast` | (string koersi) | `getString` — di respons live `[]` dikonversi jadi teks `"[]"` | Payment flow (sama) |
| `lightPackage` | object `{mustUpdate,packageName,versionNum,signKey,downloadUrl}` | `optString`/`parseInt` | Paket ringan; tidak ada di respons live → aman dilewati |
| `version` | object | → `model/g` (bagian 4) | Cek update SDK |

### 3b. `productConfig` — rule parser `model/d` (semua nilai dibaca sebagai STRING)

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
| `initCZurl` | `=="1"` → true (**field ini tak pernah ada di respons live — SDK mendukungnya tapi server tak mengirim**) | false | — |
| `adZhifuSwt` | `=="1"` → true | false | saklar pembayaran iklan |

### 3c. Field TIDAK DIPARSE — bebas diubah/dihilangkan (string TIDAK ADA di dex)

`first_install` (top-level), `nodes.oneYeaerTips`, `productConfig.{skinStyle, noticeNode,
regMailVerify, pycenttype, onGoToPUrl, onGoToPUrlData, trashTime, noFirebaseLog, logEvtReport}`,
wrapper `countryCode` (respons server berisi gema negara pemanggil — **client mengabaikannya**;
countryCode yang dipakai SDK justru dari SIM perangkat utk REQUEST), `version.updateTime`
(dibaca lalu dibuang).

### 3d. Contoh respons live (panen 2026-10-20, `panen-live/system__init.bin`, 767 B)

```json
{"result":true,"data":{"payTypes":[],"version":{"versionName":"empty","versionNo":"empty","versionUrl":"empty","updateTime":"empty","isMust":"empty","updateTips":"empty"},"productConfig":{"useServiceCenter":"0","isShowFloat":"0","showShare":"1","title":"","logo":"","useSms":"0","skinStyle":"0","serviceInfo":"","guestShowBind":1,"single_play":0,"adZhifuSwt":0,"noticeNode":0,"regMailVerify":0,"pycenttype":0,"onGoToPUrl":0,"onGoToPUrlData":"","trashTime":0,"noFirebaseLog":0,"logEvtReport":0},"otherpayType":2,"payFast":[],"isNotGift":0,"showShare":"1","requestIp":"47.57.232.232","showDiscount":0,"nodes":{"deleteAccount":0,"serverInfo":0,"oneYeaerTips":0,"removeEmail":0},"loginTypes":["1","13"],"countryCode":"HK","first_install":0},"error":{"id":0,"message":""}}
```

---

## 4. `version` (parser `model/g`) — cek update SDK

| Field | Rule | Efek |
|---|---|---|
| `versionNo` | nilai `"empty"` → diabaikan; selain itu `parseInt` → long | dibanding **versionCode APK**: `versionCode < versionNo` → dialog update |
| `versionName` | `"empty"` → diabaikan | teks dialog |
| `versionUrl` | `"empty"` → diabaikan | tujuan unduh |
| `isMust` | `"1"` atau boolean true | paksa update |
| `updateTips` | `""`/`"empty"` → diabaikan | pesan dialog |
| `updateTime` | dibaca lalu **dibuang** | tanpa efek |

Respons live semua `"empty"` → **tanpa dialog update** (perilaku nyata).

---

## 5. `v1/system/getNotice` — format yang BENAR (bukti `singersmooth.cointhreat`)

Dipanggil **langsung di dalam callback sukses init** (bukan alur terpisah). Pembacaan:

```
resp.getBoolean("result")
 → getString("data")            // data direspons live = []  → "[]" (panjang 2)
 → if (panjang > 2)             // array kosong → TANPA notice (sah, tidak error)
      getJSONObject("data")     // WAJIB OBJECT
      .getString("content")     → teks notice
      .getInt("showNode")       → node tampil
```

- Live: `{"result":true,"data":[],"error":{"id":0,"message":""}}` (55 B, md5 `dfc2f19b1273…`
  — byte-identik antar hari & identik dengan yang dilayani mirror di perangkat).
- **Kalau ingin menampilkan notice di config lokal**: `data` HARUS **object**:
  `{"content":"teks","showNode":0}` — array berisi akan dilempar exception dan notice diabaikan.

---

## 6. `v1/system/getAgreement` (188 B, panen live)

9 field perjanjian, semuanya string kosong — dipakai UI perjanjian
(`mesoamericanhochmagandies/cointhreat/awfulcongratulatepunishment`, `service/dishbesideshockey$mesoamericanhochmagandies`).
Byte-identik antar panen → sah sebagai file lokal statis.

```json
{"result":true,"data":{"agreement":"","privacy":"","legal":"","eula":"","cookieAgree":"","tsNotAgree":"","unRegAgree":"","prototalSBCM":"","prototalFDML":""},"error":{"id":0,"message":""}}
```

---

## 7. SEMANTIK WRAPPER (semua endpoint `/v1/*`)

Respons selalu `{result: bool, data: ..., error: {id, message}}`.
Client: `getBoolean("result")` → bila true, `getString("data")` (koersi JSON→teks) → parser spesifik.
`result:false` → jalur error (contoh nyata dari probe: `40005 Otorisasi gagal` (getUserInfo tanpa sesi),
`40008 pengguna ini telah digunakan` (replay registerUser), `20003` kurang parameter).

---

## 8. REQUEST ASLI APK ORIGINAL (bukti "APK mengirim config-nya sendiri")

Pembuat request: `bindcommunist/singersmooth.singersmooth(Context, HashMap)`:

```
map inti (semua endpoint) : sdkVersion=223, gameVersion, deviceId(MD5 perangkat, SharedPreferences
  "qk_sdk"), serialNum, devIDShort, platform, productCode(0x7f0c0143), channelCode, authToken,
  clientLang, suggestCurrency:"", time
init menambah ~20 field fingerprint: deviceName(Build.MODEL), gaid/adId, imei, androidId,
  netType, screenWidth/Height/dpi, osName="android", osVersion, osLanguage,
  countryCode=getSimCountryIso(), isjailbroken=0, imsi, longitude/latitude=0, dst.

data = Base64( JSON(map) )          // Base64.NO_WRAP
sign = MD5( sort-alfabet semua "k=v&" ... + "0b2a18e45d7df321" )   // lowercase hex
POST base+"/v1/…"  CT: application/x-www-form-urlencoded   body: sv=v2&data=…&sign=…
```

- **Secret sign `0b2a18e45d7df321` tertanam di dex** (`unnecessarysperrylites.seventygenom.nationalcommunitymissing`)
  — terverifikasi cocok utk request init & getNotice di HAR (riset `riset-config-ketemu`).
- Executor (`bindcommunist/cointhreat.cointhreat`): URL = `String.format("%s%s", base[0], endpoint)`
  (base = QHinfo, TANPA path — path ditulis di kode), syarat sukses **`getResponseCode()==200`**
  lalu parse JSON; gagal → retry 5 dtk → `null` (SDK menerima null tanpa crash).
- Konsekuensi: **server lokal tidak perlu memvalidasi sign untuk menjawab config** —
  sign hanya alat server asli memverifikasi klien. Yang kita butuhkan = respons dengan
  wrapper & field yang benar (bagian 3–6).

---

## 9. DAFTAR LENGKAP 32 ENDPOINT `/v1/*` DI DEX (call-site asli)

| Kategori | Endpoint |
|---|---|
| **system (config)** | `init` ✅ terpetakan penuh · `getNotice` ✅ · `getAgreement` ✅ · `dmsg` (butuh func_code) · `onNewToken` (push) |
| **user (sesi/akun)** | `registerVisitor` ✅ panen · `registerUser` (state-bound) · `ckRegVistor` · `autoLogin` · `loginByName` · `userLoginByOtherSdk` · `findPassByEmail` · `sendCodeByEmail` · `actCdKey` · `postGooglePlayVerify` · `getServerAgreement` (butuh agmId) |
| **auth (aksi akun/bayar/event)** | `getUserInfo` · `createOrder` · `bindMail` · `changePassword` · `cksnslogin` · `fbActInit` · `fbInviteEvent` · `fbLikeEvent` · `fbShareEvent` · `fbUserClaimEvent` · `setGameRoleInfo` · `userBindOtherSdk` · `unBindOtherInfo` · `viewMigCode` · `cUserTrash` · `jianchaLayLx` |

**Yang merupakan CONFIG hanya 3**: `init`, `getNotice`, `getAgreement` — semua sudah dipanen dari
server hidup (`panen-live/`) dan terpetakan field-per-field dari dex. Sisanya = **aksi berbasis
sesi** (uid/token per perangkat — bukan config; terbukti: uid antar perangkat berbeda
28486093 vs 28505968; replay registerUser → 40008).

---

## 10. BUKTI PERANGKAT (HAR sesi uji 1.0.2-tes1 — `device-har/`)

| Bukti | Nilai |
|---|---|
| init leg A (SDK → 127.0.0.1:28110) | 200, 764 B, md5 `06a396d4655a44adf4e101e8519da164` |
| init leg B (MiniServer → qsdk.t4game.com) | 200, 764 B, **md5 sama → byte-identik** (proxy verbatim terbukti) |
| getNotice via local | 200, 55 B, md5 `dfc2f19b1273…` = sama dengan panen live, **tanpa leg upstream** (mirror terbukti) |
| respons init perangkat user | `requestIp:"182.9.2.96"`, `countryCode:"ID"` — gema per-pemanggil; **keduanya tidak dipakai koneksi** (bagian 3a/3c) |
| Batas capture | sesi berhenti di getNotice → registerVisitor/login belum terlihat → **Gerbang 4 tetap terbuka** |

---

## 11. TEMPLATE CONFIG LOKAL YANG BENAR (edit → langsung efektif)

Berdasarkan bagian 3–6, ini respons init yang **sah untuk parser** dan **aman diedit**.
Field yang tidak diparse boleh dihapus; field berlabel ⚠ jangan dihapus.

```json
{"result":true,"data":{
  "payTypes":[],                                    // ⚠ KEY WAJIB ADA (kosong = sah)
  "version":{"versionName":"empty","versionNo":"empty","versionUrl":"empty","updateTime":"empty","isMust":"empty","updateTips":"empty"},
  "productConfig":{
    "useServiceCenter":"0",  "title":"",  "logo":"",      // logo hanya diterima bila diawali http
    "useSms":"0",  "serviceInfo":"",  "isShowFloat":"0",  // "0" = float TAMPIL (kebalikan!)
    "guestShowBind":1,  "single_play":0,  "adZhifuSwt":0
  },
  "otherpayType":2,  "payFast":[],
  "isNotGift":0,  "showShare":"1",  "showDiscount":0,
  "requestIp":"127.0.0.1",                          // bebas — hanya teks getCountryInfo() utk game
  "nodes":{"serverInfo":0,"serverInfoData":[],"removeEmail":0},
  "loginTypes":["1","13"]                           // "1"+FB;"6" FB;"8" Google;"9" Naver;"10" Twitter;"11" Line;"12";"14" VK;"17" PlayGames
},"error":{"id":0,"message":""}}
```

getNotice (tanpa notice — perilaku live):
```json
{"result":true,"data":[],"error":{"id":0,"message":""}}
```
getNotice (dengan notice — data **wajib object**):
```json
{"result":true,"data":{"content":"teks pengumuman","showNode":0},"error":{"id":0,"message":""}}
```
getAgreement: bagian 6 (statik, semua kosong).

**Kesimpulan arsitektur (fakta, belum diimplementasi):**
- `init` AMAN disajikan **lokal murni** — gema per-pemanggil (requestIp/countryCode) terbukti
  kosong maknanya bagi client; `requestIp` tinggal diisi teks apa pun (editable).
- `getNotice`/`getAgreement` = statis murni → file lokal.
- Endpoint sesi (registerVisitor dll.) = per-perangkat → butuh keputusan user (panen per perangkat /
  mesin sesi lokal) — **di luar lingkup config, jangan disentuh di tahap ini.**

---

## 12. PENJAGAAN "TIDAK ADA CONFIG YANG SALAH"

1. `payTypes` **harus ada** (boleh `[]`) — hilang = parser return null = init gagal.
2. `productConfig` **sebaiknya ada** meski minimal — konsumen UI membaca instance-nya langsung.
3. `isShowFloat:"0"` berarti float **tampil**; `"1"` berarti sembunyi (kebalikan intuisi).
4. `logo` diabaikan bila tidak diawali `http`.
5. `guestShowBind` default TRUE bila hilang (bukan false).
6. Nilai `"empty"` pada `version.*` = cara server asli menyembunyikan update — pertahankan
   bila tidak ingin dialog update.
7. Notice: `data` array (bahwa berisi) **tidak akan tampil** — pakai object `{content, showNode}`.
8. `loginTypes` dibaca dgn `String.contains` atas teks array → tulis nilai persis seperti live
   (`["1","13"]`); hindari angka yang tak terdaftar (mis. `"13"` tak punya flag → diabaikan aman).
9. Content-Type respons live = `text/json` — pertahankan demi kesetiaan (executor tidak mengecek,
   tapi jangan mengubah perilaku yang terbukti).

---

## 13. INDEKS BAHAN (folder ini)

| Bahan | Isi |
|---|---|
| `panen-live/` | 8 respons asli server hidup (init 767 B, getNotice 55 B, getAgreement 188 B, onNewToken 55 B, ckRegVistor 66 B, getUserInfo 75 B, getServerAgreement 104 B, dmsg 108 B) + `_probe-semua.json` (status/CT/md5 tiap probe) + `_meta-panen-live.json` |
| `dex-evidence/` | Dump instruksi mentah dari dex ORIGINAL (bukti tak bisa dibantah): `dump-full.txt` (3 parser + pembuat request init), `dump-model.txt` (semua kelas model), `xref-fields.txt` (konsumen tiap saklar), `map-usage-config.txt` (peta string→metode), `dump-qgm.txt` (initThirdDataSDK + getCountryInfo + 32 endpoint), `dump-bind.txt` (pembuat request + xref instance), `dump-sign.txt`+`dump-md5.txt` (algoritma sign), `dump-apply.txt` (callback pasca-init + getNotice), `har-inventory.txt` (49 entri HAR) |
| `device-har/` | `bukti-leg-A-identik-leg-B.json` (md5 identik leg A/B + isi init perangkat user), `tiket-request-asli-har.json` (body request asli — TIKET, bukan config) |
| `../log/sdk.har` | HAR lengkap perangkat (26 MB) — sumber bukti device-har/ |

## 14. STATUS & BATAS

- ✅ Config SDK asli: KETEMU, terbukti 3 sumber, template lokal benar tersusun.
- ✅ Alur update/blok (Fase 1–5): dikuasai LOCAL sejak 1.0.1.
- ⬜ Gerbang 4 (login → masuk game): belum ada bukti perangkat (registerVisitor/loginchecknative/socket).
- ⛔ Implementasi build: **DITAHAN sampai user memerintahkan** (riset saja per arahan).

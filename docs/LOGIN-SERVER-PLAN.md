# LOGIN SERVER — RENCANA v3: SEMUA KONTRAK WIRE TERVERIFIKASI LANGSUNG KE SERVER ASLI (PLAN ONLY, BELUM DIKERJAKAN)

> Status: **DOKUMEN RENCANA** — bukan implementasi. Nol baris ACK ditulis di APK.
> v3 menggantikan v2 (commit 31d054e). **v2 ternyata masih banyak ngawur** karena
> skema ACK-nya dibangun dari ACK sintetis v17 (jawaban server LOCAL kita sendiri),
> bukan dari server asli. v3 dibangun dari **capture langsung ke server asli yang
> MASIH HIDUP** (26 Nov 2026) + bedah ulang `main.min_777039fc.js` dari APK.
>
> Bukti fisik (repo, folder `verify-artifacts/`):
> - `live-login-contract-20261126.txt` — semua transkrip event login + status server
> - `getserverlist-ack-LIVE-20261126.txt` — ACK GetServerList ASLI utuh (618.309 B, 2112 server)
> - `game-verify-LIVE-20261126.txt` — transkrip handshake verify server game
> - `LIVE_CONTRACT_20261123.md` — kontrak SDK qsdk.t4game.com (23 Nov, tetap sahih)
>
> Alat capture (reproduksi): `tools/probe_live_battery.py`, `tools/probe_live_game_verify.py`,
> `tools/zip_partial.py` (ekstrak main.min dari APK tanpa unduh penuh).
>
> Arahan owner tetap berlaku: "login server belum boleh di kerjakan!!" → dokumen ini
> RENCANA; nol implementasi sampai owner oke.

---

## 0. PEMBAGIAN TUGAS — 3 LAPIS (tetap, kini dengan bukti jalur hidup)

Alur nyata log device v17 + konfirmasi kontrak JS:

```
 1. 9527  GET /bs/db/android/setting_BS_Android.bin        → XOR decode (loginPort 9528, loginWebPort 5050)
 2. 9528  GET /bs/upgrade/* (base.version, upgrade.json, all.zip) → CDN engine
 3. 9530  POST /v1/system/init                             → SDK: loginTypes ["1"] (email + TAMU)
 4. 9530  POST /v1/system/getNotice                        → SDK
 5. 9530  POST /v1/user/registerVisitor                    → SDK: akun TAMU dibuat (uid+token)
 6. 5050  GET /loginchecknative?uid=…&token=…              → SDK GLUE: thirdParams untuk JS
 7. 9528  GET /bs/index-native.html, manifest.json,
          serversetting.json, clientversion.json           → engine Egret boot
 8. 9528  GET /socket.io/?EIO=3&transport=polling          → engine.io OPEN+40 (TRANSPORT)
 9. 9528  POST socket.io  420["handler.process",
          {type:"User",action:"GetServerList",…}]          → (v17: ACK sintetis; v18+: NO ACK; RENCANA: ACK real)
10. 9528  POST socket.io  421[…"SaveHistory",…]            → (RENCANA: ACK real, bukan {})
```

### 0.1 SDK (9530 + glue 5050) — SELESAI sejak v13, TIDAK berubah oleh dokumen ini

### 0.2 loginServer (socket.io login) — BELUM DIBUAT; kontraknya kini §2 (live-verified)

### 0.3 gameServer (serverItem.url) — LUAR SCOPE dokumen ini, TAPI pintunya kini TERBUKTI:
server game asli `s2112-bs.popoh5.com:8041` HIDUP dan mewajibkan handshake **`verify`**
(TEA/XXTEA key `"verification"`) sebelum satu pun event game dilayani (§2.5). Tanpa
loginToken sah → `enterGame` dijawab `{"ret":38}` → JS me-reload halaman (§2.6).
Ini bahan utama rencana gameServer berikutnya — dicatat supaya desain config login
(serverList[].url) tidak salah arah.

---

## 1. SIAPA MENUNJUK ENDPOINT LOGIN + STATUS LIVE 26 Nov 2026

| File | Field | ORIGINAL (live) | MOD (v13–v19) | Pemanggil |
|---|---|---|---|---|
| `setting_BS_Android.bin` (XOR `DragonBall`) | `loginServer` | `https://login.popoh5.com` | `http://127.0.0.1` | smali LaunchActivity |
| idem | `loginPort` | `610` | `9528` | socket.io JS engine |
| idem | `loginWebPort` | `510` | `5050` | native SDK `/loginchecknative` |
| `serversetting.json` | `loginserver` | login.popoh5.com | `http://127.0.0.1:9528` | JS engine (`connectToLogin`: bridge `getLoginServer` dulu, fallback file ini) |
| `clientversion.json` | `clientVersion` | `202109150935` | idem | `getClientVer()` → param `gameVersion` enterGame |

Status probe langsung 26 Nov 2026 (semua tercatat di `live-login-contract-20261126.txt`):

| Target | Status |
|---|---|
| `login.popoh5.com:610` socket.io GET open | **HIDUP** — open frame 103 B persis (`pingInterval:25000,pingTimeout:5000`), sid suffiks berurut |
| `login.popoh5.com:610` POST polling | **502 nginx/1.16.0** — jalur POST polling MATI di infra mereka; WS = satu-satunya jalur event hidup |
| `login.popoh5.com:510` | HIDUP — `{"code":404,"msg":"没有开通"}` |
| `dragonh5cdn.popoh5.com/bs/upgrade/base.version` | HIDUP — isi `110` |
| `tsLoginServer` `http://118.24.68.91:7003` | MATI (timeout 12 dtk) |
| Server game `s2112-bs.popoh5.com:8041` | **HIDUP** — engine.io beda: `pingTimeout:60000`, format sid beda (`…AA8A`), WAJIB verify |

Implikasi desain lokal: server lokal kita melayani polling DAN WS sekaligus (sudah
seperti itu) — justru lebih lengkap dari aslinya sekarang. Tidak ada perubahan port.

---

## 2. KONTRAK WIRE — SEMUA TERVERIFIKASI LANGSUNG (inti v3)

### 2.1 Transport socket.io v2 (EIO=3) — urutan PERSIS yang dilayani server asli

```
GET  /socket.io/?EIO=3&transport=polling&t=<rnd>            → "96:0{sid,upgrades:[websocket],pingInterval:25000,pingTimeout:5000}2:40"  (103 B)
GET  /socket.io/?EIO=3&transport=websocket&sid=<sid>        → 101 upgrade
WS   client → "2probe"        server → "3probe"
WS   client → "5"             (UPGRADE PACKET — TANPA INI SERVER ASLI MEMUTUS KONEKSI SAAT EVENT PERTAMA, terbukti)
WS   client → '420["handler.process",{…}]'
WS   server → '43<ackId>[{…}]'
WS   client → "2" (ping, tiap 25 dtk)   server → "3" (pong)
```

Fakta baru yang belum pernah tercatat di repo:
- Packet engine.io `"5"` WAJIB dikirim client setelah 3probe sebelum event; tanpa itu
  server asli membalas frame kosong + close (terbukti 3 sesi).
- Server login mengirim `2:40` (socket.io CONNECT) SEKALI di open polling; tidak ada
  handshake namespace lain untuk login-server (`verifyEnable=false` — §2.5).
- Envelope ACK server asli: `{"ret":0,"data":"<JSON string>"}` — **field `compress`
  TIDAK PERNAH dikirim** (absen; JS `e.compress && …` → falsy → tidak dekompresi).
  v17 kita mengirim `"compress":false` eksplisit — kompatibel tapi bukan mirror.

### 2.2 GetServerList — ACK ASLI (618.309 B, 2112 server)

Request (persis log device v17 + terpakai probe):
```json
{"type":"User","action":"GetServerList","userId":"8969722632097","subChannel":"","channel":"quick"}
```
Server asli TIDAK memvalidasi userId (userId acak segar tetap dilayani penuh).

ACK ASLI (dipangkas; file utuh 618.309 B = `getserverlist-ack-LIVE-20261126.txt`):
```
430[{"ret":0,"data":"{\"errorCode\":0,\"serverList\":[{…}],\"history\":[]}"}]
```
Struktur `data` (JSON string, double-encoded):
- `errorCode`: 0
- `serverList`: **2112 entri** (serverId "1"…"2112", STRING bukan number!)
- `history`: array STRING serverId — **persist per-akun di server asli**
  (bukti: SaveHistory kita dgn serverId "2112" → GetServerList berikutnya history
  ["2112"]; userId segar acak → history [])

**12 field SETIAP entri** (semua entri konsisten, tanpa kecuali):
| Field | Tipe & contoh | Konsumen JS |
|---|---|---|
| `serverId` | STRING `"2112"` | `selectNewServer` history/first; `clientEnterGame` `parseInt(serverId)` → number utk enterGame |
| `url` | `"https://s2112-bs.popoh5.com:8041"` | `clientStartGame` → `mainClient.connectToServer(url)` — alamat server GAME |
| `new` | bool (`true` hanya server termuda) | ikon ServerNewIcon |
| `hot` | bool | ikon ServerHotIcon |
| `name` | `"Server 2112"` | label tombol server (`replaceServerName` range bisa menimpa) |
| `online` | bool (2112/2112 true saat capture) | false → ServerMaintainIcon; `startPlay` blokir + tampil `offlineReason` |
| `openDate` | ISO `"2026-11-20T07:00:06.858Z"` | **0 konsumen JS** — mirror saja; server baru tiap JUMAT 07:00 UTC |
| `offlineReason` | `""` | ditimpa top-level `offlineReason` bila ada (changeServerInfo) |
| `merge` | bool (2073/2112 true) | **0 konsumen JS** — mirror |
| `mergeTo` | STRING `"2087"` / `""` | **0 konsumen JS** — mirror |
| `urlInner` | `"http://127.0.0.1:8051"` | **0 konsumen JS** — mirror |
| `chatInner` | `""` | **0 konsumen JS** — mirror |

**Field rencana v2 yang TERBUKTI TIDAK ADA di server asli (dibuang dari desain):**
`state`, `recommend`, `registerTime`, `worldRoomId`, `guildRoomId` — nol kemunculan
di 2112 entri; `registerTime` 0 hit di seluruh main.min; `worldRoomId`/`guildRoomId`
yang dipakai chat datangnya dari ACK `registChat` server GAME (`_worldRoomId`
`_guildRoomId` `_chatServerUrl` dst menimpa `serverItem` saat game jalan), bukan dari
serverList login.

Urutan serverList asli: TERMUDA dulu (2112 → 1). `history` kosong → JS auto-pilih
`serverList[0]` + AUTO-ENTER (`showLoginSuccessStatus(isNew)` → `startBtnTap()`).

### 2.3 Event login lain — ACK ASLI verbatim

| Event | Request (field persis JS) | ACK ASLI | Catatan |
|---|---|---|---|
| `SaveHistory` | `{type:"User",accountToken,channelCode,serverId,securityCode,subChannel,version:"1.0"}` (serverId STRING) | `43[{"ret":0,"data":"{\"errorCode\":0,\"todayLoginCount\":2}"}]` | **BUKAN `{}`** — `todayLoginCount` dipakai JS utk report; TIDAK ada `loginToken` di ACK ini (v2 salah); server persist history per-akun |
| `LoginAnnounce` | `{type:"User",action:"LoginAnnounce"}` | **TIDAK DIJAWAB** (6 dtk diam) | v2 menyimpulkan ACK `{}` — server asli malah DIAM; JS: callback tak jalan → tombol notice tersembunyi (hasil UX sama dgn `{}`); mirror-real = diam |
| `SaveLanguage` | `{type:"User",userid,sdk,appid,language}` (userid huruf kecil semua) | `43[{"ret":0,"data":"{\"errorCode\":111,\"errorMessage\":\"不支持选择的语言in\"}"}]` utk bahasa tak didukung | bahasa sah = `supportLang` config .bin (en/fr/kr); "in" ditolak 111 |
| `SaveUserEnterInfo` | `{type:"User",accountToken,channelCode,subChannel,createTime,userLevel,version:"1.0"}` | `43[{"ret":0,"data":"{\"errorCode\":0}"}]` | dikirim SETELAH ACK enterGame; setelahnya JS `loginClient.destroy()` |
| `loginGame` (hanya jalur akun-origin) | `{type:"User",userId,password,fromChannel,channelName:"",headImageUrl:"",nickName:"",subChannel,version:"1.0"}` | `43[{"ret":0,"data":"{\"loginToken\":\"\",\"userId\":\"\",\"nickName\":\"\",\"headerImage\":\"\",\"isCreated\":false,\"errorCode\":103,\"errorMessage\":\"本服务器只允许特定的渠道来源\",\"channelCode\":\"\"}"}]` | server asli menolak channel `game_origin` DAN `quick` (103); **envelope tetap ret:0** dan JS TIDAK mengecek `data.errorCode` — alur lanjut dgn identitas kosong (quirk asli; perilaku ini yang membuat jalur origin asli "masuk" tanpa akun lalu gagal di enterGame) |
| event tak dikenal | — | (belum diuji di server asli; lokal: tetap NO ACK + log verbatim) | disiplin tetap |

### 2.4 Alur JS lengkap pasca-ACK (kutipan main.min_777039fc.js, dipetakan ulang)

Tiga pintu masuk login scene (semua terbaca langsung):
1. **SDK path** (`window.checkSDK()` true — jalur kita): `getSdkLoginInfo()` →
   `loginInfo.userInfo = {loginToken, userId, nickName, channelCode:sdk, securityCode}`
   → `clientRequestServerList(userId, sdk)` (GetServerList) → ACK →
   `selectNewServer(userInfo, data)` + `getNotice()` (LoginAnnounce) — **TANPA loginGame**.
2. **Origin path** (`无渠道登录`): `checkLastLogin()` → URL param `userid` atau cache
   localStorage `lastServerID` → `doOriginLoginRequest` → password default
   `"game_origin"` → `clientLoginUser` (loginGame, **ack flag `t=false`: event hanya
   dikirim bila socket login SUDAH terkoneksi — kalau belum, event DIBUANG SENYAP**)
   → cb: `clientRequestServerList(o.userId, o.channelCode)`.
3. **Manual**: `loginBtnTap` → `doOriginLoginRequest({username, password, lastServerID:""})`.

`selectNewServer`: whitelist bridge → `changeServerInfo` (top-level `offlineReason`
menimpa semua entry; entry>0 `new`→`hot`) → `history[0]` else `serverList[0].serverId`
→ `matchServerUrl` → `onLoginSuccess(userInfo, serverItem, isNew)` →
`showLoginSuccessStatus(isNew)` → **isNew = history kosong → `startBtnTap()` OTOMATIS**.
`startBtnTap`: `!serverItem.online` → `ShowNormalErrror(offlineReason)`; selain itu
kirim SaveHistory → ACK → `e.loginToken && (userInfo.loginToken = e.loginToken)`
(tak pernah terisi dari server asli — loginToken tetap punya SDK) → `clientStartGame`.
`clientStartGame`: `mainClient.connectToServer(serverItem.url)` → sukses:
`startListenNotify()` + `clientEnterGame()`.
`clientEnterGame`: `{type:"user"(kecil),action:"enterGame",loginToken,userId,
serverId:parseInt(serverItem.serverId),version:"1.0",language,gameVersion}` via
`processHandler` (server GAME) → ACK → `loginSuccessCallBack` → `UserDataParser.saveUserData`
→ `reportToLoginEnterInfo` (SaveUserEnterInfo ke login socket) → socket login ditutup.

`processHandler` (server game) envelope tambahan yang dibaca JS: `serverTime` +
`server0Time` → `ServerTime.updateServerTime` (sinkronisasi waktu) — kontrak penting
untuk rencana gameServer, dicatat di sini supaya tidak hilang.
`startListenNotify`: event `"Notify"` server game, envelope BERBEDA:
`{ret:"SUCCESS", data:<json string>, compress}` — aksi `"Kickout"` → destroy semua
socket + kembali ke Login.

### 2.5 HANDSHAKE `verify` — BARU, TIDAK PERNAH ADA DI RENCANA MANA PUN

`TSSocketClient(serverName, verifyEnable)`: `login-server` = **false**; `main-server`,
`chat-server`, `dungeon-server` = **true**. Jalur verify (JS `socketOnVerify`):

```
server → 42["verify","<UUID>"]                      (1x, ~0.2s setelah packet "5")
client → 420["verify","<base64(TEA.encrypt(uuid, "verification"))>"]
server → 430[{"ret":0}]                             (diterima; gagal → challenge ulang / destroy)
```

- Algoritma TEA di JS = **XXTEA** (btea): 32-bit, DELTA 2654435769, rounds
  `floor(6+52/n)`, key `Utf8("verification").slice(0,16)` → **3 long + long ke-4 = 0**
  (JS membaca `k[3]` = undefined → ToInt32 = 0 — detail yang bikin implementasi naif salah).
- Implementasi Python kita DITERIMA server asli: `430[{"ret":0}]` — direproduksi
  2 sesi (`game-verify-LIVE-20261126.txt`). Enkripsi diterapkan pada STRING UUID
  (36 byte → 9 long), hasil base64 standar.
- Server login lokal TIDAK butuh verify (`login-server` = false) — konsisten dgn
  v17 yang langsung GetServerList. Verify = kontrak WAJIB server GAME/chat/dungeon
  (fase gameServer) dan server lokal kelak harus MENJADI peminta challenge.

### 2.6 Pintu game: `enterGame` tanpa sesi sah → `{"ret":38}` → reload

Probesetelah verify sukses: `enterGame` loginToken kosong → `431[{"ret":38}]` (TANPA
data). JS: `ret!=0` → error; `"38"==e.ret` → `TSBrowser.executeFunction("reload")`.
Artinya: batas perilaku "berhenti di pintu" server lokal kelak = ret 38 + reload —
bukan crash. (v19 saat ini: game berhenti SEBELUM serverlist karena GetServerList
tak dijawab — batas berbeda, keduanya normal.)

---

## 3. STRUKTUR FOLDER (tetap dari v2, tidak berubah)

Device: `Android/data/com.db.local/server/` — `bs/` (ekstraksi boot, jangan edit),
`sdk/`, `db/`, `logs/`. Proposal tambahan saat owner oke:

```
server/login/
├── config.json      ← satu sumber kebenaran (skema §4)
├── notes.md         ← log keputusan owner (append-only)
└── captures/        ← salinan verify-artifacts (bukti kontrak, sudah ada)
```

Aturan tetap: config dibaca ulang tiap event (pola SdkConfig); JSON rusak → seed
default `enabled:false` + WARN; `captures/` hanya salinan asli bertanggal.

---

## 4. DESAIN CONFIG LOGIN v3 (menggantikan skema v4.2 yang ngawur)

### 4.1 File & lokasi — tetap: `server/login/config.json`, dibaca tiap event.

### 4.2 Skema baru (default seed — `enabled:false` = byte-identik v19)

```json
{
  "enabled": false,
  "language": "",
  "offlineReasonTop": "",
  "serverList": [
    {
      "serverId": "1",
      "url": "http://127.0.0.1:9528",
      "new": true,
      "hot": false,
      "name": "DragonBall Local",
      "online": true,
      "openDate": "2026-11-26T00:00:00.000Z",
      "offlineReason": "",
      "merge": false,
      "mergeTo": "",
      "urlInner": "",
      "chatInner": ""
    }
  ],
  "historyMode": "auto",
  "announce": {},
  "saveLanguageWhitelist": ["en", "fr", "kr"],
  "todayLoginCount": 1
}
```

| Field | Default | Alasan (bukti) |
|---|---|---|
| `enabled` | false | saklar utama; false = v19 persis (transport only) |
| `language` | "" | isi → field top-level `language` di ACK GetServerList (`a.language && ts.language=a.language`); kosong = field tak dikirim |
| `offlineReasonTop` | "" | top-level `offlineReason` — server asli tak mengirimnya; JS pakai utk menimpa per-entry (changeServerInfo) |
| `serverList[]` | 1 entri 12 field | skema = PERSIS server asli (§2.2); `serverId` **STRING**; `url` tetap 9528 (fakta routing v3: satu-satunya origin yang dicapai runtime XHR) |
| `historyMode` | `"auto"` | `auto` = ACK `history:[]` selalu (v17-proven: auto-enter); `persist` = tiru asli: simpan per-accountToken di SQLite (`db/` sudah ada) → history:[serverId terakhir] → tombol start manual; `fixed` = baca array dari config |
| `announce` | `{}` | `{}` + mode mirror-real: LoginAnnounce TIDAK dijawab (persis asli). Kalau diisi entries `{key:{text:{lang},title:{lang},version,orderNo,alwaysPopup}}` → ACK dgn data itu (kontrak konsumen JS di §2.4) |
| `saveLanguageWhitelist` | ["en","fr","kr"] | SaveLanguage di luar list → ACK `{"errorCode":111,"errorMessage":"不支持选择的语言<lang>"}` (mirror asli); di dalam list → `{"errorCode":0}` |
| `todayLoginCount` | 1 | angka yang di-ACK-kan pada SaveHistory (konsumen JS: report analytics); kelak bisa dihitung nyata dari SQLite |

### 4.3 Perilaku wire saat `enabled:true` (per event — semua bentuk LIVE-verified)

| Event masuk | Respons lokal |
|---|---|
| `User/GetServerList` | ACK `{"ret":0,"data":"<JSON string {errorCode:0,serverList:[…12 field…],history:[…]>,language?}"}` — TANPA field compress (mirror asli) |
| `User/SaveHistory` | ACK data `{"errorCode":0,"todayLoginCount":<config>}`; kalau `historyMode:"persist"` → simpan (accountToken→serverId) ke SQLite |
| `User/LoginAnnounce` | DIAM (no ACK) bila `announce` kosong — mirror asli; ACK data announce bila diisi |
| `User/SaveLanguage` | whitelist → `{"errorCode":0}`; di luar → `{"errorCode":111,"errorMessage":"不支持选择的语言<lang>"}` |
| `User/SaveUserEnterInfo` | ACK data `{"errorCode":0}` |
| `User/loginGame` | ACK data `{"loginToken":"<uid>","userId":"<uid>","nickName":"Player","headerImage":"","isCreated":true,"errorCode":0,"errorMessage":"","channelCode":"<channel>"}` — server lokal MEMBIASAKAN sukses utk jalur origin (beda dari asli yang menolak 103; keputusan §5-K2) |
| event lain | NO ACK + log verbatim (disiplin tetap) |

### 4.4 `enabled:false` — byte-identik v19 (tetap, tak berubah).

### 4.5 FAKTA ROUTING (tetap + penguat): `serverList[].url` = `http://127.0.0.1:9528`.
Server game asli pakai `https://sXXXX-bs.popoh5.com:8041` — pada device asli origin
halaman adalah HTTPS CDN sehingga bisa; device lokal origin halaman = 127.0.0.1:9528
(XHR runtime hanya terbukti capai origin — pelajaran v3 stuck). JANGAN diganti.

### 4.6 Implementasi kelak (urutan, saat owner oke)
1. `LoginConfig.java` (pola SdkConfig) + seed folder — nol efek (enabled:false).
2. Ack-gate `SocketIoHandler` per tabel §4.3 + history persist (SQLite, bila dipilih).
3. Smoke baru: string ACK persis §4.3 (bukan lagi bentuk v17); enabled:false byte-identik.
4. Dashboard tak berubah (disiplin v17: URL penuh, nol pangkas).

### 4.7 Kriteria terima (pesawat, offline penuh) — diperbarui
1. `enabled:false` → v19 persis (nol dialog network; berhenti sebelum serverlist).
2. `enabled:true` → TAMU → serverlist tampil sesuai config → history kosong =
   auto-enter → `clientStartGame` sambung socket game (transport OK) → verify dari
   server game lokal kelak (fase gameServer) → enterGame tak terjawab → game
   berhenti di loading = BATAS NORMAL tahap ini (asli akan ret 38 + reload — beda
   rasa, keduanya batas; lokal bisa memilih mirror ret 38 saat gameServer dibuat).
3. Nol dialog network di semua tahap (patch v19 tetap).
4. Log: URL penuh, tanpa pangkas, gaya Inggris, data real.

---

## 5. KEPUTUSAN UTK OWNER (menggantikan D1–D5 lama)

| # | Keputusan | Rekomendasi (basis bukti) |
|---|---|---|
| K1 | Lanjut implementasi login server sesuai §4? | Ya bila siap — semua kontrak kini live-verified, risiko wire nol |
| K2 | `loginGame` lokal: sukses disiapkan (isCreated:true) atau mirror asli (tolak 103)? | Sukses — jalur origin jadi bonus tanpa merugikan jalur SDK (yang tak pernah kirim loginGame) |
| K3 | `historyMode`: `auto` (auto-enter, v17-proven) atau `persist` (mirror asli: tombol start manual) | `auto` utk sekarang; `persist` mudah ditambah kemudian (SQLite sudah ada) |
| K4 | `LoginAnnounce` lokal: diam (mirror asli) atau ACK `{}` (kompatibel)? | Diam — mirror asli, UX sama |
| K5 | Bahasa SaveLanguage lokal: whitelist en/fr/kr (mirror asli, "in" → 111) atau terima "in"? | Mirror asli — UI game hanya punya aset en/fr/kr (supportLang config asli); "in" akan membuat teks game campur |

## 6. YANG TETAP OPEN (jujur, tak dikarangan)
- loginGame SUKSES server asli: tak bisa direkam — semua channel uji ditolak 103
  ("hanya channel tertentu"); butuh akun SDK asli yang sudah tidak bisa dibuat
  (product DB qsdk dikosongkan — LIVE_CONTRACT_20261123.md).
- Data penuh `enterGame` sukses (UserDataParser): butuh loginToken sah — fase gameServer.
- Isi ACK `registChat` + `_chatServerUrl` dll: kontrak terbaca dari JS, respons asli
  belum terekam (butuh enterGame sukses dulu).

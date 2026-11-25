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
>
> **Bagian 59 (26 Nov 2026)**: §3 DIGANTI TOTAL — struktur folder final + cara
> kerja login-server di Android/data berseleban dengan SDK (peta device dari kode,
> aturan self-seed, titik ACK gate, handoff identitas, keputusan L1–L3 §5.1).
>
> **Bagian 60 (26 Nov 2026) — KOREKSI OWNER**: folder bernama **`login-server/`**
> (bukan `login/`), struktur **PENUH multi-file** (8 file + 3 folder — bukan 1
> file config.json: "masak ya gitu cuma 1 file doang"). §3 + §5.1 direvisi lagi.

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

## 3. STRUKTUR FOLDER + CARA KERJA DI ANDROID/DATA — FINAL (diperbarui Bagian 60: folder "login-server/", struktur penuh)

> Riwayat §3: proposal v2 (`login/` + notes.md + captures/) → Bagian 59 salah
> nama (`login/`) dan TERLALU MINIMAL (1 file config.json) → **KOREKSI OWNER
> Bagian 60**: nama benar **`login-server/`**, struktur **PENUH multi-file**.
> Semua tetap berpijak pada kode yang berjalan v13–v19
> (LocalServerStarter / ServerCore / SdkConfig / Db / SocketIoHandler).

### 3.1 Peta device yang SUDAH terbukti (bukti kode, bukan karangan)

```
/storage/emulated/0/Android/data/com.db.local/
├── files/                     ← milik client/SDK — TIDAK disentuh server
│   └── Documents/             ← hanya SessionReset v18 (hapus sesi SDK crash-loop)
└── server/                    ← serverRoot = getExternalFilesDir(null).getParentFile()
    │                            + "/server"  (LocalServerStarter:47-50)
    ├── bs/                    ← ekstraksi assets/server_root TIAP BOOT (rename
    │   │                        atomik .extracting.tmp) — JANGAN EDIT: editan
    │   │                        hilang saat reboot (ditimpa ekstraksi)
    │   ├── db/                ← setting_BS_Android.bin (XOR "DragonBall")
    │   ├── js/ resource/ upgrade/
    │   └── index-native.html manifest.json serversetting.json clientversion.json
    ├── sdk/                   ← kontrak SDK — SELF-SEEDING, TIDAK pernah via assets
    │   ├── profile.json       ← profil kawat PHP (poweredBy/verifySign/products)
    │   ├── v1/system/init.json / v1/user/ckRegVistor.json / fallback.json
    │   ├── errors.json / QHinfo
    │   └── data/db.sqlite     ← tabel accounts (akun tamu/email) — MILIK SDK;
    │                            login TIDAK BOLEH menulis di sini (Bagian 30)
    └── logs/                  ← NDJSON DiskWriter + crash-*.txt (dari detik pertama)
```

Alasan teknis aturan "self-seed, bukan assets" (header SdkConfig, terbukti
v13–v19): ekstraksi assets jalan TIAP boot dan MENIMPA file — satu-satunya cara
editan owner bertahan = folder yang tidak pernah ada di assets/server_root,
dibuat hanya bila belum ada. Pelajaran v16 (kata owner: "berasa jadi sampah"):
file penanda/index dihapus — folder login wajib MINIMAL.

### 3.2 Struktur final `login-server/` — PENUH, tiap file punya pekerjaan

```
server/login-server/
├── README.txt      ← peta folder + fungsi tiap file (Bahasa Indonesia) —
│                     penuntun edit di file manager; ikut self-seed
│                     (terhapus → dibuat ulang)
├── config.json     ← saklar & perilaku umum: enabled, language, offlineReasonTop,
│                     historyMode(auto/persist/fixed), todayLoginCount,
│                     saveLanguageWhitelist
├── serverlist.json ← DATA GetServerList: serverList[] 12-field PERSIS server
│                     asli (serverId STRING; url tetap http://127.0.0.1:9528) —
│                     dipisah dari config: ini isi yang paling sering diedit
├── announce.json   ← DATA LoginAnnounce: {} = DIAM (mirror asli); diisi → ACK
├── history.json    ← historyMode "fixed": array serverId manual; mode lain tak dibaca
├── profile.json    ← profil kawat + saklar kontrak: envelope {"ret":0,
│                     "data":"<json string>"} TANPA compress (mirror asli
│                     Bagian 58), pingInterval/pingTimeout,
│                     loginGameResponse:"success"|"mirror103" (K2),
│                     loginAnnounce:"silent"|"ack" (K4), validateUserId:false
├── errors.json     ← katalog error login (111 bahasa, 103 channel, ret 38) +
│                     template errorMessage (mis. "不支持选择的语言<lang>")
├── notes.md        ← log keputusan owner (append-only; seed template)
├── captures/       ← salinan bukti kontrak live bertanggal (kosong default)
├── data/           ← state: history.sqlite (hanya historyMode "persist")
└── logs/           ← NDJSON scope login-server (event masuk + ACK keluar) —
                       TERPISAH dari logs/ global; ditulis HANYA saat enabled:true
```

Pembagian kerja file — pola `sdk/` yang sudah jalan (profile.json = profil
kawat, init.json = data kontrak, errors.json = katalog), kini untuk login:

| File | Isi | Kontrak rujukan (live-verified) |
|---|---|---|
| config.json | saklar/perilaku | §4.2 |
| serverlist.json | data 12-field | §2.2 (ACK asli 618.309 B, 2112 server) |
| announce.json | data announce | §2.3 LoginAnnounce (asli: DIAM) |
| history.json | mode fixed | §2.2 history per-akun (asli: persist server) |
| profile.json | bentuk envelope + saklar K2/K4 | §2.1 envelope tanpa compress + §2.3 |
| errors.json | pesan error | §2.3 (111 bahasa / 103 channel) |
| data/history.sqlite | persist | §2.2 (asli: history persist per-akun) |
| logs/ | jejak event login | disiplin log v17: URL penuh, tanpa pangkas |

### 3.3 Aturan folder (5 aturan tetap + seed list)

1. TIDAK PERNAH masuk assets/server_root — tidak akan pernah ditimpa ekstraksi.
2. Self-seed saat BOOT (bukan menunggu event pertama): satu panggilan
   `LoginServerConfig.ensure()` di LocalServerStarter, tepat setelah `Db.open`
   — folder TERLIHAT LENGKAP sejak detik pertama.
3. Dibaca ULANG tiap event (pola `SdkConfig.contract`) — edit owner berlaku
   tanpa restart app.
4. JSON mana pun rusak → reseed default + WARN log — config rusak hanya
   mematikan loginServer, SDK tidak pernah ikut rusak (rusak satu arah).
5. Tulis atomik: `.tmp` + fsync + rename (pola `SdkConfig.writeFile`).

Seed boot (hanya bila BELUM ada — self-seed, editan owner dihormati):
8 file = README.txt, config.json, serverlist.json, announce.json, history.json,
profile.json, errors.json, notes.md; 3 folder = captures/, data/, logs/ (kosong).
`logs/` baru berisi NDJSON saat enabled:true — folder kosong saat mati, bukan
sampah. Nama kelas Java: **LoginServerConfig.java** (konsistensi dgn nama folder).

### 3.4 Cara kerja runtime (enabled:false = v19 persis; enabled:true = ACK real)

```
Boot:  Application.onCreate → LocalServerStarter.start
       → logs/ siap → ExitInfo / CrashHook / OverlayFixer / SessionReset (SDK)
       → Db.open (sdk/data) → [BARU] LoginServerConfig.ensure() (login-server/:
         seed 8 file + 3 folder) → ekstraksi bs/ (thread, latch folder js/)
       → ServerCore.start (5 listener)
```

Peta port & scope (ServerCore — tidak ada port baru):

| Port | Handler | Scope |
|---|---|---|
| 9527 | StaticHandler (bin; XOR decode di log) | boot config |
| 9528 | chain: `/socket.io/` → SocketIoHandler(login); sisanya StaticHandler | CDN engine + **GERBANG ACK loginServer** |
| 5050 | SocketIoHandler(login): `/socket.io/` + `/loginchecknative` | socket login + GLUE SDK (scope SDK) |
| 5051 | SocketIoHandler(game) | gameServer transport — fase berikutnya |
| 9530 | SdkHandler (33 path) | SDK |
| 4000/4001 | LoggerHandler (fallback 4001) | dashboard |

Titik ACK gate — SATU titik logika, dua pintu masuk yang sama:
- POST polling: cabang `pkt.startsWith("42")` di SocketIoHandler (v18: hanya log
  verbatim) → bila `enabled:true`: parse `{type,action}` → `LoginAck.answer(...)`
  → `session.deliver("43<ackId>[{…}]")`. Infra kirim `Session.deliver` (WS
  langsung / antre polling) SUDAH ADA sejak v6 — tinggal dipakai.
- WS frame: titik yang sama di loop frame WS.
- `enabled:false` → kode persis v19 (log "EVENT (no ack)" verbatim).

Envelope ACK = mirror asli (§2): `43<ackId>[{"ret":0,"data":"<JSON string>"}]`
TANPA field `compress`. Daftar ACK per event = §4.3 (semua LIVE-verified).

Peta EVENT → FILE yang dibaca login-server (semua dibaca ulang tiap event —
edit tanpa restart):

| Event masuk | File dibaca | ACK keluar |
|---|---|---|
| `GetServerList` | serverlist.json + config(language, offlineReasonTop) | envelope per profile.json — 12-field persis §2.2 |
| `SaveHistory` | config(todayLoginCount); persist → tulis data/history.sqlite | `{"errorCode":0,"todayLoginCount":N}` |
| `LoginAnnounce` | announce.json | kosong → DIAM (K4 mirror asli); diisi → ACK data itu |
| `SaveLanguage` | config(saveLanguageWhitelist) + errors.json template | `{"errorCode":0}` / `{"errorCode":111,...}` |
| `SaveUserEnterInfo` | — (tetap) | `{"errorCode":0}` |
| `loginGame` | profile(loginGameResponse) + AccountStore read-only | success (K2) / mirror103 |
| historyMode "fixed" | history.json | history:[...] menyertai GetServerList |

`login-server/logs/` menerima NDJSON per event login (URL penuh, gaya v17)
HANYA saat enabled:true — enabled:false = folder tetap kosong.

### 3.5 Titik "berselebahan" SDK ↔ loginServer (rawan salah — dipetakan eksplisit)

1. IDENTITAS: `/loginchecknative` (5050, scope SDK) → `AccountStore.setCurrent
   (uid, username, token, usermode)` → loginServer MEMBACA (read-only) statik
   itu untuk konsistensi ACK `loginGame` (K2) — nol perubahan kode SDK.
2. SALURAN: `GetServerList` dari JS membawa `userId` hasil thirdParams SDK —
   server asli TIDAK memvalidasi userId (terbukti live §2.2) — loginServer
   mirror: tidak memvalidasi juga (profile.validateUserId=false).
3. FILE: `sdk/` dan `login-server/` bersebelahan dengan ATURAN SAMA (self-seed,
   edit-live, atomic) tapi SCOPE TERPISAH — `login-server/` TIDAK boleh membuka
   `sdk/data/db.sqlite` (accounts milik SDK); history pakai file sendiri (L1).
4. BUKAN seleban: gameServer (5051) tetap transport-only di fase ini; enterGame
   tidak dijawab — batas normal "berhenti di loading" (§4.7).

### 3.6 Kriteria terima folder (smoke kelak, saat implementasi)

1. Fresh install → skeleton LENGKAP sejak boot: 8 file + captures/ data/ logs/
   (kosong); wire v19 byte-identik (enabled:false).
2. Edit owner (enabled:true / serverlist.json) → berlaku tanpa restart.
3. Reboot → editan BERTAHAN (bs/ ditimpa ekstraksi; login-server/ TIDAK).
4. JSON mana pun diubah jadi teks sampah → boot berikut reseed + WARN; SDK aman.
5. historyMode persist → `login-server/data/history.sqlite` dibuat; mode auto →
   file tak pernah dibuat.
6. enabled:true → `login-server/logs/` mulai menerima NDJSON event login
   (URL penuh, tanpa pangkas); enabled:false → folder tetap kosong.

---

## 4. DESAIN CONFIG LOGIN v3 (menggantikan skema v4.2 yang ngawur)

### 4.1 File & lokasi — Bagian 60: `server/login-server/config.json` (saklar & perilaku) + `server/login-server/serverlist.json` (data serverList, dipisah per §3.2); keduanya dibaca tiap event.

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

### 5.1 KEPUTUSAN FOLDER (revisi Bagian 60)

KEPUTUSAN OWNER — sudah FIX, tidak ditanya lagi:
- NAMA FOLDER = **`login-server/`** (koreksi langsung owner; `login/` salah nama).
- STRUKTUR = **PENUH multi-file** ("masak ya gitu cuma 1 file doang") — proposal
  minimal Bagian 59 DITOLAK owner; L3 lama SELESAI oleh owner.

| # | Keputusan | Rekomendasi (basis bukti) |
|---|---|---|
| L1 | DB history: file TERPISAH `login-server/data/history.sqlite` atau gabung `sdk/data/db.sqlite`? | **Terpisah** — scope SDK (tabel accounts) tidak tersentuh eksperimen login; Db.open masih hardcoded sdk/data (Bagian 30), memisah = nol risiko migrasi; DbSqlite dipakai ulang dgn dataDir berbeda |
| L2 | Waktu seed `login-server/`: BOOT atau lazy (event pertama)? | **Boot** — satu panggilan `LoginServerConfig.ensure()` tepat setelah `Db.open`; skeleton 8 file + 3 folder terlihat sejak detik pertama; event pertama dijamin punya config |
| L3 | Isi folder | **SELESAI oleh owner**: struktur penuh §3.2 (8 file + 3 folder) |
| L4 | `logs/` per-scope login-server (pisah dari logs/ global), hanya menulis saat enabled:true? | **Ya** — debug login tidak tercampur trafik SDK/CDN; folder kosong saat mati = bukan sampah |
| OPEN | Struktur §3.2 (8 file + 3 folder) sudah pas, atau boss mau tambah/kurangi? | Menunggu jawaban owner — bersama K1–K5 |

## 6. YANG TETAP OPEN (jujur, tak dikarangan)
- loginGame SUKSES server asli: tak bisa direkam — semua channel uji ditolak 103
  ("hanya channel tertentu"); butuh akun SDK asli yang sudah tidak bisa dibuat
  (product DB qsdk dikosongkan — LIVE_CONTRACT_20261123.md).
- Data penuh `enterGame` sukses (UserDataParser): butuh loginToken sah — fase gameServer.
- Isi ACK `registChat` + `_chatServerUrl` dll: kontrak terbaca dari JS, respons asli
  belum terekam (butuh enterGame sukses dulu).

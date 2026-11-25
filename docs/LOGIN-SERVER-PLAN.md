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
>
> **Bagian 61 (26 Nov 2026) — KOREKSI OWNER #2**: "ULANGI YG BENER!! LIHAT
> FAKTA" + "baca tuh server asli dan main.min.js — gimana server mereka
> bekerja, HARUSnya struktur local & config login-server kita gimana". §3.2
> DITULIS ULANG sebagai TABEL PADANAN cara-kerja-server-asli → file lokal;
> README.txt / notes.md / captures/ DIBUANG dari device (pelajaran v16 yang
> sama persis — SdkConfig.java:28, index.txt "berasa jadi sampah");
> announce.json / history.json / profile.json / errors.json ikut DIHAPUS —
> semua perilakunya = KODE mirror asli, bukan file. Final: **config.json +
> serverlist.json + data/ (persist saja) + logs/ (enabled saja)** — 2 file
> multi-file sungguhan (saklar vs DATA DB-padanan), arsitektur mengikuti
> server asli: data bisnis di DB-analog, kebijakan di kode.

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

## 3. STRUKTUR FOLDER + CARA KERJA DI ANDROID/DATA — FINAL (diperbarui Bagian 61: TURUNAN FAKTA server asli + main.min.js)

> Riwayat §3: Bagian 59 salah nama (`login/`) + terlalu minimal (1 file) →
> Bagian 60 benar nama (`login-server/`) tapi SALAH ISI — menghidupkan kembali
> proposal sampah era v16 (README.txt / notes.md / captures/) yang sudah pernah
> dihukum owner ("berasa jadi sampah") → **KOREKSI OWNER Bagian 61**: struktur
> DITURUNKAN dari cara kerja SERVER ASLI (capture live 26 Nov) + main.min.js
> (bedah ulang), bukan dari proposal lama mana pun. Semua tetap berpijak pada
> kode yang berjalan v13–v19
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

### 3.2 Struktur final `login-server/` — DITURUNKAN DARI CARA KERJA SERVER ASLI (Bagian 61)

Metode Bagian 61 — TIDAK mengarang: setiap file wajib punya PADANAN perilaku
server asli yang TERBACA dari capture live 26 Nov (verify-artifacts/,
`live-login-contract-20261126.txt` + ACK 618.309 B) dan main.min.js
(tools/out/main.min_777039fc.js):

| Cara server ASLI bekerja (fakta) | Padanan WAJIB di `login-server/` |
|---|---|
| serverList = DATA DINAMIS di DB server: 2.112 entri, `openDate` mingguan Jumat 07:00 UTC, 2.073 entri `merge:true` (ACK 618.309 B) | **serverlist.json** — file DATA; entri 12-field PERSIS asli |
| history = DB persist PER-AKUN: userId segar → `[]`, akun kita yang sudah pernah main → `["2112"]` (bukti [2] kontrak) | **data/history.sqlite** — persist per-accountToken; HANYA saat historyMode `persist` (asli = persist) |
| todayLoginCount = hitungan per hari di DB server — ACK SaveHistory `{"errorCode":0,"todayLoginCount":2}` (bukti [3]); JS baca utk analytics (`4===t`, `6===t` → ReportToSdkCommon) | **config.json** `todayLoginCount` (mode auto) / dihitung dari history.sqlite (mode persist) |
| LoginAnnounce tanpa pengumuman → server DIAM total 6 dtk (bukti [4]) | **BUKAN FILE** — perilaku DIAM = konstanta kode (mirror) |
| whitelist bahasa en/fr/kr berasal dari `supportLang` config game .bin (BUKAN file server); dan main.min: SaveLanguage ditolak 111 PUN client tetap ganti bahasa (`window.changeLanguage(e)` di KEDUA cabang) | **BUKAN FILE** — konstanta kode en/fr/kr (mirror) |
| pesan error = hardcode kode server ("不支持选择的语言in", "本服务器只允许特定的渠道来源") | **BUKAN FILE** — konstanta kode |
| bentuk envelope `{"ret":0,"data":"<json string>"}`, field `compress` ABSEN — 618 KB pun tak dikompres; main.min: `e.compress&&(t=LZString…)`, `0===e.ret` | **BUKAN FILE** — fakta kode, mirror apa adanya |
| top-level `offlineReason` ABSEN di ACK asli — dan main.min `changeServerInfo`: `t[n].offlineReason=e.offlineReason` MENIMPA offlineReason SEMUA entri dengan nilai top-level → absen = dialog offline tak pernah muncul | **config.json** `offlineReasonTop` default `""` = tak dikirim (mirror asli); diisi = SATU-SATUNYA tombol dialog "server maintenance" |
| top-level `language` ABSEN di ACK asli; main.min: `a.language&&(ts.language=a.language)` | **config.json** `language` default `""` = tak dikirim (mirror asli) |
| server asli SELALU hidup — tidak kenal saklar | **config.json** `enabled` — satu-satunya field TANPA padanan asli; alasan lokal: co-exist dengan mode transport-only v19 (default false) |

Kesimpulan arsitektur: server asli menyimpan **data bisnis di DATABASE** dan
**kebijakan di KODE**. Folder kita meniru arsitektur itu — file DATA
(serverlist.json = padanan DB serverList, history.sqlite = padanan DB history)
+ file SAKLAR (config.json) + KODE untuk semua sisanya.

```
server/login-server/
├── config.json        ← SAKLAR & KEBIJAKAN (5 field): enabled(false =
│                         byte-identik v19), language(""),
│                         offlineReasonTop(""), historyMode(auto|persist),
│                         todayLoginCount(1)
├── serverlist.json    ← DATA GetServerList: serverList[] 12-field PERSIS asli
│                         (serverId STRING; url tetap http://127.0.0.1:9528)
├── data/
│   └── history.sqlite ← persist per-accountToken (mirror asli) — HANYA bila
│                         historyMode "persist"; TERPISAH dari
│                         sdk/data/db.sqlite (accounts milik SDK, L1)
└── logs/              ← NDJSON scope login-server — HANYA saat enabled:true
                          (folder kosong saat mati = bukan sampah)
```

File yang SENGAJA TIDAK ADA — jangan dihidupkan lagi:
- `README.txt` / `notes.md` / `captures/` — server asli TIDAK PERNAH mengirim
  dokumentasi ke client (tak ada padanan di tabel di atas). Ini persis
  pelajaran v16: SdkConfig.java:28 — index.txt DIHAPUS karena kata owner
  "berasa jadi sampah", dan kini dibersihkan otomatis tiap boot. Dokumentasi
  dan bukti kontrak tinggal di REPO (docs/, readme.md, verify-artifacts/),
  bukan di device.
- `announce.json` — asli DIAM; file kosong = sampah.
- `history.json` (mode "fixed") — asli persist; mode fixed = spekulasi, DIHAPUS
  (K3 kini auto|persist saja).
- `profile.json` — bentuk envelope = fakta KODE (§2.1), bukan konfigurasi;
  saklar K2/K4 = keputusan sekali di §5, dieksekusi sebagai konstanta kode.
- `errors.json` — pesan error asli = hardcode kode server → konstanta kode.

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
**2 file** = config.json, serverlist.json (padanan DB — harus ada sejak detik
pertama). Folder `data/` dibuat HANYA saat historyMode persist; `logs/` baru
berisi NDJSON saat enabled:true — kosong saat mati = bukan sampah. Nol file
dokumentasi pernah dibuat, di boot maupun di event. Nama kelas Java:
**LoginServerConfig.java** (konsistensi dgn nama folder).

### 3.4 Cara kerja runtime (enabled:false = v19 persis; enabled:true = ACK real)

```
Boot:  Application.onCreate → LocalServerStarter.start
       → logs/ siap → ExitInfo / CrashHook / OverlayFixer / SessionReset (SDK)
       → Db.open (sdk/data) → [BARU] LoginServerConfig.ensure() (login-server/:
         seed 2 file) → ekstraksi bs/ (thread, latch folder js/)
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

| Event masuk | Sumber jawaban | ACK keluar (live-verified) |
|---|---|---|
| `GetServerList` | serverlist.json + config(language, offlineReasonTop) + history (auto → `[]`; persist → baca history.sqlite) | `{errorCode:0, serverList:[…12-field…], history:[…]}` — 3 kunci top-level PERSIS asli [2] |
| `SaveHistory` | persist → tulis data/history.sqlite; auto → config(todayLoginCount) | `{"errorCode":0,"todayLoginCount":N}` [3] |
| `LoginAnnounce` | KODE — DIAM (mirror asli [4]; tanpa file) | tidak dijawab |
| `SaveLanguage` | KODE — whitelist konstanta en/fr/kr (mirror supportLang .bin) | 0 / 111 — JS tetap ganti bahasa walau 111 (fakta main.min, kedua cabang changeLanguage) |
| `SaveUserEnterInfo` | KODE | `{"errorCode":0}` [6] |
| `loginGame` | KODE (keputusan K2: success / mirror103) + AccountStore read-only | loginGameResponse sukses / identitas kosong 103 [1] |

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

1. Fresh install → skeleton sejak boot: config.json + serverlist.json (data/
   dan logs/ belum ada — kondisional); wire v19 byte-identik (enabled:false).
   AUDIT TAMBAHAN: nol file dokumentasi pernah dibuat (tidak ada README.txt,
   notes.md, captures/, announce.json, history.json, profile.json, errors.json).
2. Edit owner (enabled:true / serverlist.json) → berlaku tanpa restart.
3. Reboot → editan BERTAHAN (bs/ ditimpa ekstraksi; login-server/ TIDAK).
4. JSON mana pun diubah jadi teks sampah → boot berikut reseed + WARN; SDK aman.
5. historyMode persist → `login-server/data/history.sqlite` dibuat; mode auto →
   file tak pernah dibuat.
6. enabled:true → `login-server/logs/` mulai menerima NDJSON event login
   (URL penuh, tanpa pangkas); enabled:false → folder tetap kosong.

---

## 4. DESAIN CONFIG LOGIN v3 (menggantikan skema v4.2 yang ngawur)

### 4.1 File & lokasi — Bagian 61: `server/login-server/config.json` (5 field saklar/kebijakan) + `server/login-server/serverlist.json` (DATA serverList, padanan DB asli per §3.2); keduanya dibaca ulang tiap event. announce/whitelist/errors = konstanta kode (BUKAN file).

### 4.2 Skema baru (default seed — `enabled:false` = byte-identik v19)

config.json (5 field — semua ada padanan fakta di §3.2; serverList TIDAK lagi
di sini, pindah ke serverlist.json):

```json
{
  "enabled": false,
  "language": "",
  "offlineReasonTop": "",
  "historyMode": "auto",
  "todayLoginCount": 1
}
```

serverlist.json (DATA — skema entri = PERSIS server asli §2.2, default 1 entri):

```json
{
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
  ]
}
```

| Field / File | Default | Alasan (bukti) |
|---|---|---|
| `enabled` | false | satu-satunya field tanpa padanan asli (server asli selalu hidup); false = v19 persis (transport only) |
| `language` | "" | top-level `language` ACK — ABSEN di asli; JS: `a.language&&(ts.language=a.language)`; "" = field tak dikirim (mirror) |
| `offlineReasonTop` | "" | top-level `offlineReason` — ABSEN di asli; main.min `changeServerInfo` MENIMPA offlineReason semua entri dgn nilai ini (absen = dialog offline tak pernah muncul) |
| `historyMode` | `"auto"` | `auto` = ACK `history:[]` selalu (v17-proven: auto-enter); `persist` = tiru ASLI: per-accountToken di data/history.sqlite → history:[serverId terakhir] → tombol start manual. Mode `fixed` DIHAPUS Bagian 61 (spekulasi, tak ada di asli) |
| `todayLoginCount` | 1 | di-ACK-kan pada SaveHistory (konsumen JS: analytics `4===t`, `6===t`); mode persist kelak dihitung nyata dari history.sqlite |
| serverlist.json `serverList[]` | 1 entri 12 field | skema = PERSIS server asli (§2.2); `serverId` **STRING**; `url` tetap 9528 (fakta routing v3); `offlineReason` per-entry tetap diisi `""` seperti asli (efeknya ditimpa top-level — fakta changeServerInfo) |
| (dihapus) `announce` | — | LoginAnnounce asli DIAM [4] → konstanta kode, bukan file |
| (dihapus) `saveLanguageWhitelist` | — | asli = supportLang config .bin game; JS tetap ganti bahasa walau 111 → konstanta kode en/fr/kr, bukan file |

### 4.3 Perilaku wire saat `enabled:true` (per event — semua bentuk LIVE-verified)

| Event masuk | Respons lokal |
|---|---|
| `User/GetServerList` | ACK `{"ret":0,"data":"<JSON string {errorCode:0,serverList:[…12 field…],history:[…]>,language?}"}` — TANPA field compress (mirror asli); top-level keys = 3 PERSIS asli [2] |
| `User/SaveHistory` | ACK data `{"errorCode":0,"todayLoginCount":<config>}`; kalau `historyMode:"persist"` → simpan (accountToken→serverId) ke data/history.sqlite |
| `User/LoginAnnounce` | DIAM (no ACK) — mirror asli [4]; konstanta kode (announce.json dihapus Bagian 61) |
| `User/SaveLanguage` | whitelist konstanta kode en/fr/kr → `{"errorCode":0}`; di luar → `{"errorCode":111,"errorMessage":"不支持选择的语言<lang>"}` — client tetap ganti bahasa walau 111 (fakta main.min) |
| `User/SaveUserEnterInfo` | ACK data `{"errorCode":0}` |
| `User/loginGame` | ACK data `{"loginToken":"<uid>","userId":"<uid>","nickName":"Player","headerImage":"","isCreated":true,"errorCode":0,"errorMessage":"","channelCode":"<channel>"}` — server lokal MEMBIASAKAN sukses utk jalur origin (beda dari asli yang menolak 103; keputusan §5-K2, konstanta kode) |
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
| K3 | `historyMode`: `auto` (auto-enter, v17-proven) atau `persist` (mirror asli: tombol start manual) | `auto` utk sekarang; `persist` mudah ditambah kemudian (SQLite sudah ada); mode `fixed` DIHAPUS Bagian 61 |
| K4 | `LoginAnnounce` lokal: diam (mirror asli) atau ACK `{}` (kompatibel)? | Diam — mirror asli, UX sama |
| K5 | Bahasa SaveLanguage lokal: whitelist en/fr/kr (mirror asli, "in" → 111) atau terima "in"? | Mirror asli — UI game hanya punya aset en/fr/kr (supportLang config asli); "in" akan membuat teks game campur |

### 5.1 KEPUTUSAN FOLDER (revisi Bagian 61)

KEPUTUSAN OWNER — sudah FIX, tidak ditanya lagi:
- NAMA FOLDER = **`login-server/`** (koreksi langsung owner; `login/` salah nama).
- MULTI-FILE sungguhan ("masak ya gitu cuma 1 file doang") — TAPI isi folder =
  **TURUNAN FAKTA server asli + main.min.js** (koreksi #2 Bagian 61: "ULANGI YG
  BENER!! LIHAT FAKTA"); README.txt/notes.md/captures/ TIDAK KEMBALI LAGI ke
  device (pelajaran v16 yang sama).

| # | Keputusan | Rekomendasi (basis bukti) |
|---|---|---|
| L1 | DB history: file TERPISAH `login-server/data/history.sqlite` atau gabung `sdk/data/db.sqlite`? | **Terpisah** — scope SDK (tabel accounts) tidak tersentuh eksperimen login; Db.open masih hardcoded sdk/data (Bagian 30), memisah = nol risiko migrasi; DbSqlite dipakai ulang dgn dataDir berbeda |
| L2 | Waktu seed `login-server/`: BOOT atau lazy (event pertama)? | **Boot** — satu panggilan `LoginServerConfig.ensure()` tepat setelah `Db.open`; 2 file padanan-DB terlihat sejak detik pertama; event pertama dijamin punya config |
| L3 | Isi folder | **SELESAI Bagian 61**: 2 file (config.json = padanan kebijakan, serverlist.json = padanan DB serverList) + 2 folder kondisional (data/ persist saja, logs/ enabled saja) — SEMUA diturunkan dari tabel padanan §3.2 |
| L4 | `logs/` per-scope login-server (pisah dari logs/ global), hanya menulis saat enabled:true? | **Ya** — debug login tidak tercampur trafik SDK/CDN; folder kosong saat mati = bukan sampah |
| L5 | File dokumentasi di device (README.txt/notes.md/captures/)? | **TIDAK, PERMANEN** — server asli tak punya padanannya; v16 sudah menghukum index.txt ("berasa jadi sampah", SdkConfig.java:28); dokumentasi = REPO saja (docs/, readme.md, verify-artifacts/) |
| OPEN | K1–K5 (implementasi ACK, loginGame, historyMode, LoginAnnounce, bahasa) | Menunggu keputusan owner — struktur kini fakta-murni, tinggal persetujuan K1 |

## 6. YANG TETAP OPEN (jujur, tak dikarangan)
- loginGame SUKSES server asli: tak bisa direkam — semua channel uji ditolak 103
  ("hanya channel tertentu"); butuh akun SDK asli yang sudah tidak bisa dibuat
  (product DB qsdk dikosongkan — LIVE_CONTRACT_20261123.md).
- Data penuh `enterGame` sukses (UserDataParser): butuh loginToken sah — fase gameServer.
- Isi ACK `registChat` + `_chatServerUrl` dll: kontrak terbaca dari JS, respons asli
  belum terekam (butuh enterGame sukses dulu).

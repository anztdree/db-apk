# LOGIN SERVER — RENCANA v2: DESAIN CONFIG (PLAN ONLY, BELUM DIKERJAKAN)

> Status: **DOKUMEN RENCANA** — bukan implementasi. Nol baris ACK ditulis di APK.
> Arahan owner 25 Nov 2026 (kata-kata persis):
> "sekarang lihat baik-baik tugas SDK dan loginserver di game ini ..
>  lalu rencanakan untuk pembuatan config loginserver dan penyempurnaan sdk
>  jika diperlukan"
>
> Riwayat batasan yang TETAP berlaku:
> - "login server belum boleh di kerjakan!! hanya menentukan endpoint dan
>    merencanakan strategi struktur foldernya dulu !!" (25 Nov pagi)
> - kesalahan 2: "SDK ya fokus di SDK bukan mlah ngurusin loginserver" —
>   v19: SDK (9530) = SDK saja; listener socket.io = TRANSPORT ONLY, nol ACK.
>
> Basis bukti 100% nyata: config .bin ter-decode (XOR `DragonBall`), log device
> MOD NDJSON asli (v17 log-20261125-074226.ndjson — berisi ACK GetServerList
> terakhir sebelum dihapus; v18/v19 transport only), capture live server asli
> (`verify-artifacts/`), dan kontrak JS engine yang dibaca LANGSUNG dari
> `bs/js/main.min_777039fc.js` (kutipan persis di §2.4). NOL karangan —
> yang belum terbukti ditandai **OPEN**.

---

## 0. PEMBAGIAN TUGAS — 3 LAPIS (peta berbasis bukti)

Alur nyata dari log device v17 (urutan persis NDJSON, 25 Nov 07:42):

```
 1. 9527  GET /bs/db/android/setting_BS_Android.bin        → XOR decode (loginPort 9528, loginWebPort 5050)
 2. 9528  GET /bs/upgrade/* (base.version, upgrade.json, all.zip 19 MB) → CDN engine
 3. 9530  POST /v1/system/init                             → SDK: loginTypes ["1"] (email + TAMU)
 4. 9530  POST /v1/system/getNotice                        → SDK
 5. 9530  POST /v1/user/registerVisitor                    → SDK: akun TAMU dibuat (uid+token)
 6. 5050  GET /loginchecknative?uid=…&token=…              → SDK GLUE: thirdParams untuk JS
 7. 9528  GET /bs/index-native.html, manifest.json,
          serversetting.json, clientversion.json           → engine Egret boot
 8. 9528  GET /socket.io/?EIO=3&transport=polling          → engine.io OPEN+40 (TRANSPORT)
 9. 9528  POST socket.io  420["handler.process",
          {type:"User",action:"GetServerList",…}]          → (v17: ACK serverList; v18+: NO ACK)
10. 9528  POST socket.io  421[…"SaveHistory",…]            → (v17: ACK {}; v18+: NO ACK)
```

### 0.1 SDK (port 9530 + glue native 5050 `/loginchecknative`) — SELESAI sejak v13

Tugas SDK = **semua sebelum engine.io menyala**:
- 33 endpoint `/v1/…` (katalog verify_total.txt §C); 12 diimplement, sisanya
  `fallback.json` (result:false 20000) — rincian gap di docs/SDK-REFINEMENT-PLAN.md
- envelope `{"result":…,"data":…,"error":…}` + sign MD5 secret `0b2a18e45d7df321`
  (LIVE_CONTRACT_20261123.md — terverifikasi live server asli)
- `init.json` mirror PENUH (loginTypes menentukan UI login; productConfig 10 field)
- akun TAMU/email (SQLite `sdk/data/db.sqlite`)
- `/loginchecknative` (5050) = jembatan sesi native → JS (`window.getSdkLoginInfo()`)

**SDK TIDAK menyentuh serverlist.** Bukti v19: langkah 9–10 dikirim oleh JS ENGINE
(`clientRequestServerList` di main.min), bukan oleh SDK. SDK selesai di langkah 6.

### 0.2 loginServer (socket.io login: loginPort 9528 via JS; 5050 fallback native) — BELUM DIBUAT; CONFIG DIRANCANG DI §4

Tugas nyata loginServer (semua dari kontrak JS + log):
| Tugas | Status sekarang (v19) |
|---|---|
| transport engine.io (open/poll/WS/probe/ping-pong 1:1 capture live) | SUDAH — transport only |
| `GetServerList` → ACK serverList | TIDAK ADA (dihapus v18) — **dirancang jadi isi config** |
| event fire-and-forget `SaveHistory` `SaveUserEnterInfo` `SaveLanguage` `saveHistory` `LoginAnnounce` `getBulletin` | TIDAK ADA — ACK `{}` cukup (v13–v17 bukti) |
| `loginGame` | TIDAK ADA — HANYA jalur akun-origin JS (password "game_origin"); jalur SDK TAMU/EMAIL TIDAK PERNAH mengirimnya (bukti log: GetServerList → langsung SaveHistory, nol loginGame) |

### 0.3 gameServer (serverItem.url → socket.io game) — LUAR SCOPE DOKUMEN INI

- `enterGame` → payload user data PENUH (kontrak UserDataParser: currency,
  userInfo, heros, backpack, equip, superSkill, signIn, team, mainTask, …)
- `getBulletinBrief` → `{_brief:{}}`
- notify stream (`startListenNotify`)

Dokumen ini hanya menyiapkan PINTUNYA (`serverList[].url`). Isi enterGame =
rencana terpisah — disiplin tugas (pelajaran kesalahan 2: jangan campur scope).

---

## 1. SIAPA MENUNJUK ENDPOINT LOGIN (fakta config, bukan asumsi)

| File (pohon `bs/` yang kita kontrol) | Field | ORIGINAL (live 21 Nov) | MOD (v13–v19) | Dipakai siapa |
|---|---|---|---|---|
| `bs/db/android/setting_BS_Android.bin` (XOR `DragonBall`, 711 B live / 687 B mod) | `loginServer` | `https://login.popoh5.com` | `http://127.0.0.1` | smali `LaunchActivity.java` |
| idem | `loginPort` | `610` | `9528` | **socket.io JS engine** |
| idem | `loginWebPort` | `510` | `5050` | **native Java SDK** (`/loginchecknative`) |
| `bs/resource/properties/serversetting.json` | `loginserver` | login.popoh5.com | `http://127.0.0.1:9528` | JS engine |
| `bs/resource/properties/clientversion.json` | `clientVersion` | — | `202109150935` | cek versi native |

Client menelepon sendiri sejak boot karena config di atas menunjuk 127.0.0.1
sejak v13 — itulah kenapa "loginserver sudah jalan padahal belum diberi rencana".

---

## 2. PETA ENDPOINT LOGIN (semua terbukti dari log device)

### 2.1 Endpoint yang DIPANGGIL client (URL penuh, contoh nyata log v17)

| # | Endpoint PENUH | Pemanggil | Penjawab v19 |
|---|---|---|---|
| 1 | `GET http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=Q8G9v7T` → `96:0{"sid":"…","upgrades":["websocket"],"pingInterval":25000,"pingTimeout":5000}2:40` | JS engine | transport (SocketIoHandler) |
| 2 | `GET http://127.0.0.1:9528/socket.io/?EIO=3&transport=websocket&sid=…` (101) | JS engine | transport |
| 3 | `POST http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=…&sid=…` body `<len>:42<ackId>["handler.process",{…}]` | JS engine | transport — event dicatat VERBATIM, nol ACK |
| 4 | `GET http://127.0.0.1:5050/loginchecknative?uid=…&usermode=1&username=…&token=…&appVersion=1&osVersion=31&sourceVersion=20200418&model=itel+S665L&packageName=com.db.local&brand=Itel&os=Android&sdk=BS` | native SDK | SDK glue (ACC, scope SDK) |
| 5 | listener 5050 `/socket.io/…` langsung | fallback native jalur lama | transport |

### 2.2 Wire event yang PERNAH MUNCUL di log device (verbatim)

Wire: client `42<ackId>["handler.process",{type,action,…}]` → ACK server
`43<ackId>[{ret,data,compress}]` (socket.io v2 — bukti JS `TSLoginClient.sendToServer`).

ACK real v17 yang TERAKHIR dilayani (log seq 53, PENUH tanpa pangkas):
```
326:430[{"ret":0,"data":"{\"serverList\":[{\"serverId\":1,\"name\":\"DragonBall Local\",\"online\":true,\"hot\":true,\"new\":false,\"url\":\"http://127.0.0.1:9528\",\"state\":1,\"recommend\":true,\"offlineReason\":\"\",\"registerTime\":1795567358,\"worldRoomId\":\"w1\",\"guildRoomId\":\"g1\"}],\"history\":[]}","compress":false}]
```
Event real lain (log v17): `421[…"SaveHistory",{accountToken,channelCode,serverId:1,securityCode,subChannel:"",version:"1.0"}]`.
Event era v13–v16 (rekap docs lama): `loginGame {userId,fromChannel,…}` (jalur
akun-origin), `LoginAnnounce`, `SaveUserEnterInfo`, `SaveLanguage`, `saveHistory`,
`GetNotice`/`getBulletin` — semuanya cukup ACK `{}`.

### 2.3 Yang dipakai server ASLI tapi belum terbukti dibutuhkan (OPEN)

| Item | Fakta yang ADA | Yang belum terbukti |
|---|---|---|
| `loginKey` + verify TEA | field `loginKey` ADA di JSON config live | belum ada satu pun log device yang memanggilnya |
| `loginWebPort` original `:510` | live: `{"code":404,"msg":"没有开通"}` | — (native MOD pakai 5050) |
| `tsLoginServer` `http://118.24.68.91:7003` | MATI live (timeout) | tak pernah dipanggil di log device |
| battle-log `log.sjflaregame.com:10000` | DIBLOKIR live (302 webblock) | tak pernah muncul di log device MOD |

### 2.4 KONTRAK JS — FIELD SERVERLIST DIBACA OLEH SIAPA (kutipan persis main.min_777039fc.js)

Ini dasar skema config §4 — setiap field config ketahui konsumennya:

| Field | Konsumen JS (kutipan persis) | Efek kalau salah/absen |
|---|---|---|
| `serverList[]` | `selectNewServer`: `t.serverList[0].serverId` | array kosong → crash selectServer |
| `serverList[].serverId` | `clientEnterGame`: `o=parseInt(ts.loginInfo.serverItem.serverId)` → param enterGame | identitas server; masuk event enterGame |
| `serverList[].name` | `onLoginSuccess`: `i=r(t.serverId)||t.name` → `o.loginUser.text=i` | label tombol server |
| `serverList[].online` | `onLoginSuccess`: `t.online?t.hot?ServerHotIcon:…`; `startPlay`: `if(!ts.loginInfo.serverItem.online) return ErrorHandler.ShowNormalErrror(offlineReason)` | `false` = TERKUNCI + tampilkan alasan |
| `serverList[].hot` / `.new` | `onLoginSuccess`: ServerHotIcon / ServerNewIcon | ikon badge (kosmetik) |
| `serverList[].url` | `clientStartGame`: `a=ts.loginInfo.serverItem.url; this.mainClient.connectToServer(a,…)` | **alamat socket.io GAME** — lihat §4.5 |
| `serverList[].offlineReason` | `changeServerInfo`: `t[n].offlineReason=e.offlineReason` — field TOP-LEVEL `offlineReason` MENIMPA nilai per-entry | pesan saat server offline |
| `history[]` | `selectNewServer`: `a=t.history.length>0?t.history[0]:t.serverList[0].serverId`; `onLoginSuccess(e,r,o)` dengan `o=!t.history.length` → `startBtnTap()` OTOMATIS | history kosong = auto-pilih server pertama + AUTO-ENTER |
| top-level `language` | `processHandlerWithLogin`: `a.language&&(ts.language=a.language)` | opsional — override bahasa game |
| `compress` (ACK envelope) | `processHandlerWithLogin`: `e.compress&&(t=LZString.decompressFromUTF16(t))` | `false` = data JSON polos (bukti live + v17) |
| `ret` (ACK envelope) | `processHandlerWithLogin`: `if(0===e.ret){…} else Logger.showInfoLog("登录出错",e.ret)` | `ret≠0` = gagal login, popup error |
| `registerTime` `.state` `.recommend` | NOL konsumen ditemukan di jalur login (grep) | kosmetik — mirror apa adanya |
| `worldRoomId` `.guildRoomId` | chat: `ts.loginInfo.serverItem.worldRoomId` / `.guildRoomId` (room chat world/guild) | default room chat; nanti ditimpa data enterGame |
| `filterByWhiteList` | `window.serverList` (bridge native) — kita TIDAK inject | no-op untuk kita |

Alur JS setelah ACK (semua terbukti): `selectNewServer` → `onLoginSuccess`
(userInfo + serverItem tersimpan) → `showLoginSuccessStatus(isNew)` →
`startBtnTap()` (auto bila history kosong) → `SaveHistory` → `clientStartGame`
→ `mainClient.connectToServer(url)` → `clientEnterGame` (type `user`).

---

## 3. STRATEGI STRUKTUR FOLDER (refine v2 —Proposal)

### 3.1 Device (`Android/data/com.db.local/server/`) — kondisi NYATA sekarang

```
server/
├── bs/      ← 2759 file engine (ekstraksi assets tiap boot; JANGAN diedit manual)
│   ├── db/android/setting_BS_Android.bin      ← loginPort 9528 / loginWebPort 5050
│   ├── resource/properties/serversetting.json ← loginserver http://127.0.0.1:9528
│   └── resource/properties/clientversion.json
├── sdk/     ← config SDK (profile.json, v1/system/init.json, …) — pola TERBUKTI
├── db/      ← SQLite tambahan (AccountStore/UserAccounts)
└── logs/    ← NDJSON log-*.ndjson + crash-*.txt
```

### 3.2 Proposal folder login (DIBUAT hanya setelah owner oke)

```
server/
└── login/                       ← BARU (seed default enabled:false — nol efek runtime)
    ├── config.json              ← SATU sumber kebenaran (skema §4.2)
    ├── notes.md                 ← log keputusan owner (append-only, tanggal)
    └── captures/                ← capture MENTAH asli (bukti kontrak, tak pernah dikarang)
        ├── socketio-open-live-610-20261122.txt      (= live_login_handshake_capture)
        └── getserverlist-ack-v17-20261125.txt       (= ACK 326 byte di §2.2)
```

**Perubahan dari proposal v1:** folder `payloads/` DIBUANG. Dulu diusulkan
`payloads/GetServerList.json` dll — sekarang `config.json` sudah memuat
serverList/history/announce, jadi payloads/ = sumber kebenaran kedua = sampah
(pelajaran anti-sampah v16). Satu event yang butuh payload besar KELAK milik
gameServer (`server/game/`), bukan login.

Aturan folder:
1. Satu file config, dibaca ulang tiap request (pola SdkConfig terbukti).
2. `captures/` hanya capture asli + tanggal di nama file.
3. Yang tidak dipakai dihapus, bukan ditumpuk.
4. JSON rusak → server seed ulang default (`enabled:false`) + log WARN.

### 3.3 Repo

```
rebuild-src/javasrc/com/db/local/server/   ← LoginConfig.java KELAK di sini (pola SdkConfig)
docs/LOGIN-SERVER-PLAN.md                   ← dokumen ini
docs/SDK-CONFIG.md · SDK-STUDY.md · SDK-REFINEMENT-PLAN.md
verify-artifacts/                           ← capture live asli
```

---

## 4. DESAIN CONFIG LOGIN SERVER (inti v2 — menunggu oke owner)

### 4.1 File & lokasi

```
/storage/emulated/0/Android/data/com.db.local/server/login/config.json
```
Dibaca ulang server pada TIAP event (bukan di-cache saat boot) → edit = berlaku
langsung tanpa restart, tanpa rebuild APK — persis pola `sdk/` yang sudah
terbukti (SdkConfig: baca tiap request, rusak → seed default).

### 4.2 Skema `config.json` (default seed — enabled:false)

```json
{
  "enabled": false,
  "compress": false,
  "language": "",
  "offlineReason": "",
  "serverList": [
    {
      "serverId": 1,
      "name": "DragonBall Local",
      "url": "http://127.0.0.1:9528",
      "online": true,
      "hot": true,
      "new": false,
      "recommend": true,
      "state": 1,
      "offlineReason": "",
      "registerTime": 0,
      "worldRoomId": "w1",
      "guildRoomId": "g1"
    }
  ],
  "history": [],
  "announce": {},
  "fireAndForgetAck": true
}
```

| Field | Tipe | Default | Alasan (bukti) |
|---|---|---|---|
| `enabled` | bool | `false` | saklar utama — `false` = perilaku v19 persis (transport only, nol ACK). Nol risiko saat seed |
| `compress` | bool | `false` | kontrak `e.compress` §2.4; live+v17 selalu `false` |
| `language` | string | `""` | kosong = jangan override `ts.language` (field tak dikirim bila kosong) |
| `offlineReason` | string | `""` | top-level — menimpa offlineReason tiap entry (kontrak changeServerInfo) |
| `serverList[]` | array | 1 server (nilai = ACK v17 real) | kontrak §2.4 — WAJIB non-kosong |
| `serverList[].url` | string | `http://127.0.0.1:9528` | lihat §4.5 — JANGAN diganti sembarangan |
| `serverList[].registerTime` | number | `0` | nol konsumen JS di jalur login (§2.4) — mirror saja |
| `history` | array of number | `[]` | kosong = auto-pilih server pertama + AUTO-ENTER (kontrak selectNewServer). Isi `[1]` = masuk tanpa auto-tap? — **OPEN**: perlu log device untuk memastikan perilaku `o=false` (startBtn manual) |
| `announce` | object | `{}` | ACK `LoginAnnounce` (v13–v17: `{}` cukup) |
| `fireAndForgetAck` | bool | `true` | ACK `{}` untuk SaveHistory/SaveUserEnterInfo/SaveLanguage/saveHistory/getBulletin — `false` = no-ack verbatim saja |

### 4.3 Perilaku kawat saat `enabled:true` (per event)

| Event masuk | Respons | Format persis |
|---|---|---|
| `User/GetServerList` | ACK | `<len>:43<ackId>[{"ret":0,"data":"<JSON string {serverList:[…],history:[…],offlineReason?,language?}>","compress":false}]` — `data` = JSON.stringify isi config (DOUBLE-encoded — bukti §2.2) |
| `User/SaveHistory`, `SaveUserEnterInfo`, `SaveLanguage`, `saveHistory`, `getBulletin` | ACK kosong | `<len>:43<ackId>[{}]` (bila `fireAndForgetAck:true`) |
| `User/LoginAnnounce` | ACK | `<len>:43<ackId>[<isi announce — `{}` = tak ada jendela pengumuman>]` |
| `User/loginGame` | ACK | `<len>:43<ackId>[{"ret":0,"data":"{\"userId\":\"…\",\"channelCode\":\"quick\",\"sign\":\"local\"}","compress":false}]` — kontrak minimal JS: `o.userId`,`o.channelCode` (doOriginLoginRequest); userId diambil dari sesi loginchecknative aktif |
| event lain APAPUN | **NO ACK** | verbatim di log — batas disiplin tetap (ketahuan bila muncul event baru) |

Catatan: `GetServerList.userId` dari event dicek silang dengan akun aktif
(loginchecknative) — hanya untuk log; nilai ACK murni dari config.

### 4.4 Perilaku saat `enabled:false` (= default seed)

Byte-identik dengan v19: transport only + `/loginchecknative` + verbatim
`EVENT (no ack) … loginServer scope — not implemented (plan only)`. Game
berhenti sebelum layar server list. Ini yang membuat folder seed AMAN dibuat
tanpa menyentuh perilaku yang sudah di-oke owner.

### 4.5 FAKTA ROUTING KRITIS — `serverList[].url` JANGAN SEMBARANGAN

- `clientStartGame` menyambungkan socket.io GAME ke `serverItem.url` (§2.4).
- Fact v3 FIX STUCK (ServerCore.java:73-85): runtime XHR Egret TERBUKTI hanya
  mencapai origin halaman (9528) — `io.connect` ke 127.0.0.1:5050 tidak pernah
  menghasilkan satu pun request di 6 log device.
- ServerCore chain: 9528 `/socket.io/` → handler login; 5051 = listener game
  transport-only (fallback). Artinya kedua socket (login DAN game) mendarat di
  chain yang sama — discriminasinya NAMA EVENT (GetServerList vs enterGame),
  bukan port.
- **Kesimpulan config**: `url` default `http://127.0.0.1:9528` (satu-satunya
  jalur yang terbukti dicapai runtime). Nilai lain = risiko stuck loading.

### 4.6 Konsumsi server (rencana implementasi KELAK — tidak dieksekusi sekarang)

1. `LoginConfig.java` (pola SdkConfig): baca `serverRoot/login/config.json`
   tiap event; file hilang/rusak → seed default `enabled:false` + log WARN.
2. `SocketIoHandler`: ack-gate per EVENT sesuai tabel §4.3 — hanya bila
   `LoginConfig.enabled`. Semua event lain tetap verbatim no-ack.
3. Seed folder `server/login/` (config.json + notes.md; captures/ diisi dari
   `verify-artifacts/` yang SUDAH ADA — bukan karangan).
4. Dashboard TIDAK berubah — tombol tetap hanya filter error + salin; tiap
   ACK tampil dengan URL penuh (disiplin log v17 berlaku).
5. Smoke desktop baru: `enabled:false` = byte-identik v19; `enabled:true` =
   string ACK persis §4.3; JSON rusak → seed ulang; event tak dikenal → no-ack.

### 4.7 Kriteria terima (uji mode pesawat — offline penuh)

1. `enabled:false`: boot pesawat → v19 seperti sekarang — nol dialog network,
   berhenti sebelum serverlist. (sudah di-oke owner)
2. `enabled:true`: boot pesawat → TAMU → serverlist tampil sesuai config →
   auto-enter (history kosong) → socket game tersambung (transport OK) →
   `enterGame` NO ACK → game berhenti di loading (BATAS — gameServer belum
   dibuat; ini HARUS dianggap perilaku normal tahap ini).
3. Nol dialog network di SEMUA tahap (patch v19 tetap).
4. Log: URL penuh tanpa pangkas, data real saja, gaya Inggris.

---

## 5. URUTAN KERJA BILA OWNER OKE (TIDAK dieksekusi sekarang)

1. Tambah `LoginConfig.java` + seed folder (nol efek — enabled:false).
2. Ack-gate di `SocketIoHandler` (tabel §4.3) + smoke guard.
3. Uji device: pesawat enabled:false → v19; enabled:true → sampai batas §4.7.2.
4. Baru setelah itu bicara gameServer (`enterGame` payload) — rencana terpisah.

## 6. PENYEMPURNAAN SDK

→ dokumen terpisah **docs/SDK-REFINEMENT-PLAN.md** (gap 20 endpoint fallback,
paket kosmetik 404 layar login, keputusan loginTypes migrasi) — semuanya
OPSIONAL dan dijalankan HANYA bila log device memperlihatkan kebutuhan nyata.

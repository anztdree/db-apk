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
>
> **Bagian 62 (26 Nov 2026) — KOREKSI OWNER #3**: "JANGAN KAU SAMAKAN SERVER
> KITA DENGAN MEREKA!! MEREKA DIAM YA HANYA DIAM — KALAU KITA DIAM JANGAN IKUT
> DIAM!! HARUS TAU ALASANNYA!! CONFIG ASLI GIMANA — MAU PAKAI ISI ATAU DIAM!!
> JANGAN SEMUA DISAMAKAN TANPA KEJELASAN" + peringatan fase gameplay ("ini
> baru tahap depan — masuk tahap gameplay nanti bingung lagi karena cuma asal
> niru server asli padahal gak punya dasar"). AKIBATNYA: (1) metodologi
> "mirror asli" DIGANTI prinsip 3 kolom — KONTRAK JS (dasar teknis, wire
> wajib ikut parser client) / FAKTA ASLI (informasi, bukan perintah) /
> KEPUTUSAN KITA + ALASAN (dilarang "karena asli begitu"); (2) announce.json
> KEMBALI sebagai FILE PILIHAN OWNER ({} = diam karena BELUM ADA ISI — visual
> identik dgn tak menjawab, terbukti kontrak getNotice; diisi → pengumuman
> tampil, bisa berbahasa Indonesia via text["in"]); format entry
> DIDOKUMENTASIKAN dari bedah getNotice main.min (text/title per-bahasa,
> version, orderNo, alwaysPopup) — inilah "config asli" yang diminta owner;
> (3) SEMUA alasan "mirror asli" di-rebase: bahasa en/fr/kr krn ASET game
> (bukan krn asli menolak), loginGame sukses krn TUJUAN lokal (103 = kebijakan
> bisnis mereka, bukan kebutuhan teknis kita), envelope/compress krn PARSER
> JS strict. Seed boot kini 3 file. Catatan fase gameplay ditulis di §6.
>
> **Bagian 63 (26 Nov 2026) — USULAN ULANG**: owner minta struktur folder
> login-server di-usulkan ULANG dgn benar ("yg jelas saya mau usulan ulang
> struktur folder login-server yg benar"). §3.0 BARU = usulan FINAL satu
> layar: SATU ATURAN "ISI / DIAM" (kosong = diam, diisi = pakai isi — file
> TIDAK pernah dihapus untuk diam) + tabel per-file (peran / pilihan /
> default / dasar kontrak main.min) + 3 fakta verbatim baru hasil verifikasi
> ulang main.min (pesan 111 = console.log saja TIDAK pernah tampil; daftar
> bahasa tombol = window.supportLang aset game — client UI hanya pernah
> kirim en/fr/kr; text[lang]||"" TANPA fallback). STRUKTUR TIDAK berubah
> dari Bagian 62 (3 file + 2 folder kondisional) — yang baru = kejelasan
> penyajian + penguat bukti. Menunggu oke owner (bareng K1–K5).
>
> **Bagian 64 (26 Nov 2026) — USULAN Bagian 63 DITOLAK OWNER; BEDAH TOTAL**:
> owner: "usulan di tolak!! saya sudah bilang bedah total dulu login-server
> yg ada di main.min.js!!" → dokumen BARU `docs/LOGIN-SERVER-BEDAH-TOTAL.md`
> = atlas verbatim lengkap: kelas TSSocketClient (ack = ACK socket.io bawaan;
> maks 10 reconnect; offline ≥10 menit = login ulang), dispatcher
> processHandlerWithLogin (`a.language` dibaca di SEMUA ACK; autoConnect=false
> + belum connect = event dibuang senyap; TANPA timeout), sumber URL
> (bridge getLoginServer > serversetting.json), INVENTARIS TOTAL 6 event
> (GetServerList/loginGame/SaveHistory/LoginAnnounce/SaveLanguage/
> SaveUserEnterInfo — tidak ada ke-7), PINTU INJEKSI loginToken di ACK
> SaveHistory (fakta baru), rantai pemilih server + 2 bridge native
> `window.serverList` (WHITELIST+rename) dan `window.replaceServerName`
> (nama per-range — bisa menimpa isi serverlist kita SEBELUM pemain lihat),
> 2 wadah identitas (loginUserInfo vs loginInfo.userInfo), localStorage
> last_game_server, wire end-to-end, peta konsekuensi WAJIB/BEBAS (§11),
> 7 nol-hasil (§12). STRUKTUR TIDAK diusulkan di Bagian 64 — usulan
> berikutnya wajib merujuk baris atlas.
>
> **Bagian 65 (26 Nov 2026) — STRUKTUR DITENTUKAN DARI ATLAS**: owner: "lalu
> tentukan struktur yg benar" → struktur DITURUNKAN dari atlas Bedah Total
> (aturan §13 atlas: tanpa baris atlas = dilarang masuk struktur). HASIL:
> (a) struktur 62/63 DIKONFIRMASI bedah — 3 file + 2 folder kondisional TIDAK
> berubah; (b) SATU field BARU config.json `loginTokenReturn` (default "") —
> pintu injeksi loginToken di ACK SaveHistory (atlas §4.3/§11 baris 7) yang
> belum pernah dipetakan repo mana pun; keputusan K6; (c) aturan wire
> diperketat dari atlas: kunci data ACK = HANYA kunci yang DIBACA parser —
> `errorCode` dibuang dari ACK yang tak dibaca client (GetServerList,
> SaveUserEnterInfo, loginGame — atlas §4.1/§4.2/§12.5); SaveLanguage TETAP
> membawa `errorCode` karena parser MEMBACANYA (atlas §4.5 `0===t.errorCode`);
> baris SaveHistory dapat cabang `loginToken`. STATUS: menunggu oke owner
> K1–K6.
>
> **Bagian 66 (26 Nov 2026) — TREE LAMA DITOLAK OWNER KEDUA KALI; STRUKTUR =
> TABEL EVENT ATLAS DIBUAT FILE**: owner: "ini bukankah struktur folders yg
> saya tolak tadi anda usulkan lagi — parah" → benar: Bagian 65 hanya
> mengganti LABEL usulan→ditentukan tanpa mengubah isi tree (config.json /
> serverlist.json / announce.json = pengelompokan karangan saya, bukan
> kontrak). Struktur ditulis ulang DARI TABEL 6 EVENT yang owner tempel
> sendiri: **1 baris atlas §4 = 1 file jawaban**, nama file = action string
> PERSIS yang client kirim (GetServerList.json / loginGame.json /
> SaveHistory.json / LoginAnnounce.json / SaveLanguage.json), isi file =
> kunci PERSIS yang dibaca client baris itu; `config.json` DIHAPUS (field
> menyebar ke file event yang MEMBACANYA); `enabled` DIHAPUS (K7: file ada
> = dijawab, file kosong `{}` = DIAM/NO ACK per event — atlas §11 baris 9);
> SaveUserEnterInfo = SATU-SATUNYA event TANPA file (atlas §4.6/§11 baris 11:
> isi tak dibaca → konstanta kode, selalu dijawab `{}` supaya socket login
> di-destroy rapi). STATUS: menunggu oke owner K1–K7.

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
| `SaveLanguage` | `{type:"User",userid,sdk,appid,language}` (userid huruf kecil semua) | `43[{"ret":0,"data":"{\"errorCode\":111,\"errorMessage\":\"不支持选择的语言in\"}"}]` utk bahasa tak didukung | bahasa sah = `supportLang` config .bin (en/fr/kr); "in" ditolak 111; VERIFIKASI ULANG Bagian 63: callback client utk 111 = `console.log` + `window.changeLanguage` — **pesan 111 TIDAK pernah tampil ke pemain** (console saja); daftar bahasa tombol = `window.supportLang` → client UI hanya pernah kirim en/fr/kr |
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

## 3. STRUKTUR FOLDER + CARA KERJA DI ANDROID/DATA — FINAL (diperbarui Bagian 62: prinsip 3 kolom, bukan jiplakan)

> Riwayat §3: Bagian 59 salah nama + terlalu minimal → Bagian 60 menghidupkan
> sampah v16 (README.txt/notes.md/captures/) → Bagian 61 membuang sampah tapi
> SALAH METODE: "server asli diam → kita diam" = JIPLAKAN TANPA DASAR (kritik
> owner) → **KOREKSI OWNER Bagian 62**: prinsip 3 kolom + announce.json
> kembali sebagai PILIHAN owner → **Bagian 63**: USULAN ULANG final (§3.0).
> Semua tetap berpijak pada kode v13–v19.

### 3.0 STRUKTUR SATU LAYAR — 1 EVENT ATLAS = 1 FILE JAWABAN (Bagian 66; tree Bagian 63/65 DITOLAK OWNER KEDUA KALI)

> Kesalahan diakui (readme 66.1): tree `config.json`/`serverlist.json`/
> `announce.json` = pengelompokan KARANGAN saya (saklar/data/pengumuman) —
> pengelompokan itu TIDAK ada di kontrak. Struktur yang benar = TABEL 6 EVENT
> atlas itu sendiri, dibuat file: nama file = action string PERSIS yang
> client kirim (atlas §4 verbatim), isi file = kunci PERSIS yang dibaca
> client di baris itu. Tidak ada file yang melintasi event.

**SATU ATURAN "ISI / DIAM" — SEKARANG PER EVENT (dari atlas):**

- File **BERISI** = event **DIJAWAB** dgn isi file (dibaca ulang tiap event;
  edit berlaku tanpa restart).
- File **DIKOSONGKAN** (`{}` / 0 byte) = event **DIAM** (NO ACK — atlas §11
  baris 9: client diam TANPA crash; efek diam tiap event = tabel di bawah,
  semua dari atlas).
- Perilaku TIDAK PERNAH berubah karena kode — hanya karena isi yang owner tulis.
- Dasar tiap baris = ATLAS Bedah Total (offset verbatim, Bagian 64).

**TREE (device):**

```
Android/data/com.db.local/server/
├── bs/  sdk/  logs/  ← SUDAH ADA (v13–v19) — login TAK menyentuh
│                         (bs/ ditimpa ekstraksi; sdk/data/db.sqlite = akun
│                          MILIK SDK — L1)
└── login-server/              ← self-seed BOOT; dibaca ulang tiap event
    ├── GetServerList.json     ← event 1 (atlas §4.1, offset 793433)
    ├── loginGame.json         ← event 2 (atlas §4.2, offset 792556)
    ├── SaveHistory.json       ← event 3 (atlas §4.3, offset 1453497)
    ├── LoginAnnounce.json     ← event 4 (atlas §4.4, offset 1458983)
    ├── SaveLanguage.json      ← event 5 (atlas §4.5, offset 790564)
    ├── data/
    │   └── history.sqlite     ← state history per-akun — HANYA bila
    │                             historyMode:"persist" (atlas §6/§12.7)
    └── logs/                  ← NDJSON per event yang DIJAWAB
    (event 6 SaveUserEnterInfo — TANPA FILE: konstanta kode `{}`; atlas §4.6/
     §11 baris 11: isi TIDAK dibaca client; SELALU dijawab supaya socket
     login di-destroy rapi — destroy itu SATU-SATUNYA, atlas §4.6)
```

**TABEL EVENT → FILE — 1:1 dengan tabel atlas yang owner tempel (efek DIAM
tiap event juga dari atlas):**

| Event (offset atlas §4) | File | Isi file = kunci yang DIBACA client | DIAM (`{}` = NO ACK) — efek dari atlas | PAKAI ISI |
|---|---|---|---|---|
| GetServerList (793433, §4.1) | `GetServerList.json` | `serverList[]` 12 field (urutan array = prioritas auto — §11.3), `history[]` (§11.4), `offlineReason` top-level (§6 changeServerInfo), `language` top-level (§2 — dibaca di SEMUA ACK); `errorCode` TIDAK dibaca (§4.1/§12.5) | client berhenti SEBELUM daftar server muncul (§11.9: diam tanpa crash) | serverList diisi (1 entri lokal cukup); `historyMode`:"auto"\|"persist" (kunci server KITA — parser mengabaikan kunci asing); `offlineReason`/`language` "" = tak dikirim |
| loginGame (792556, §4.2) | `loginGame.json` | cuma `userId` + `channelCode` (echo dari request — sisanya diabaikan) | jalur origin/manual MACET di tombol login (callback tak jalan) | `{"jawab":"sukses"}` / `{"jawab":"tolak103"}` (K2 — kini bisa dibalik owner via edit; userId echo diisi server dari request) |
| SaveHistory (1453497, §4.3) | `SaveHistory.json` | `loginToken` = PINTU INJEKSI (§11.7) + `todayLoginCount` (§11.8); onErr=onOk → ACK gagal pun game mulai | client MACET di LoadingPanel (onOk/onErr tak pernah jalan — atlas §4.3) | `{"todayLoginCount":1,"loginToken":""}` — loginToken "" = tak kirim (token SDK dipertahankan); diisi = inject ke sesi game (K6) |
| LoginAnnounce (1458983, §4.4) | `LoginAnnounce.json` | `{kunci:entri}` — per entri `text[lang]`, `title[lang]` (tanpa fallback `\|\|""`), `version`, `orderNo`, `alwaysPopup` (§11.5) | TIDAK APA-APA — visual identik dgn jawab `{}`: tombol pengumuman tak muncul (atlas §4.4) | diisi entri → tombol muncul; `alwaysPopup:true` → popup OTOMATIS; teks `in` TAMPIL (minimal isi `en`+`in`) |
| SaveLanguage (790564, §4.5) | `SaveLanguage.json` | `errorCode` 0/111 — DUA-DUANYA ganti bahasa; pesan server tak pernah tampil (§11.6) | dialog bahasa TIDAK tertutup (callback = satu-satunya yang memanggil closeWindow — atlas §4.5) → praktis WAJIB berisi | `{"whitelist":["en","fr","kr"]}` → di luar daftar = 111 (K5 — kini bisa dibalik owner via edit: `[]` = terima semua) |
| SaveUserEnterInfo (794977, §4.6) | — TANPA FILE | TIDAK dibaca apa pun → `destroy()` login socket (SATU-SATUNYA destroy) | — | konstanta kode: SELALU dijawab `{}` supaya socket login ditutup rapi (§4.6/§11 baris 11) |

**FAKTA VERBATIM BARU — verifikasi ulang main.min Bagian 63 (penguat, bukan pengubah struktur):**

1. `saveLanguage` callback utk 111: `console.log("failed save language",t)` +
   `window.changeLanguage(e)` — **errorMessage TIDAK PERNAH tampil ke pemain**
   (console saja; dialog lain pakai teks ASET game `ToolCommon.getLanguageWithEditor`,
   bukan pesan server) → teks 111 kita bebas ditulis; TIDAK ada dasar buat
   errors.json.
2. Daftar bahasa tombol = `window.supportLang` (aset .bin game: en/fr/kr) —
   client UI hanya PERNAH mengirim en/fr/kr → kode 111 di server lokal praktis
   tak pernah terjadi lewat UI asli; whitelist = kerapian server, bukan UX (K5).
3. `text[lang]||""` **TANPA fallback** — bahasa client yang tak ada di entri =
   string KOSONG (bukan bahasa lain) → LoginAnnounce.json minimal isi `en` + `in`
   supaya tidak ada panel kosong (§4.2b).

**YANG SENGAJA TIDAK ADA (permanen — SEMUA dgn dasar, bukan asal buang):**
`config.json` — DIHAPUS sebagai nama (Bagian 66): pengelompokan "saklar/data/
pengumuman" = karangan saya yang DITOLAK owner dua kali; setiap field kini ada
di file event yang MEMBACANYA — tidak ada file kebijakan yang melintasi event ·
`enabled` — saklar global DIHAPUS (K7): file ada = dijawab, file kosong `{}` =
DIAM — saklar granular per event tanpa meta-file (atlas §11 baris 9: DIAM aman) ·
`serverlist.json`/`announce.json` — NAMA LAMA DIHAPUS → `GetServerList.json`/
`LoginAnnounce.json` (nama = action string verbatim yang client kirim, atlas §4) ·
`README.txt`/`notes.md`/`captures/` (nol konsumen kode; pelajaran v16
SdkConfig.java:28; dokumentasi = REPO) · `history.json` (atlas §6+§12.7: history
murni server-side → sqlite saja) · `profile.json` (atlas §2+§11 baris 1: bentuk
envelope = milik parser `0===ret` strict) · `errors.json` (atlas §4.5+§11 baris 6:
pesan server TAK PERNAH tampil) · `whitelist.json` (whitelist ada DI DALAM
SaveLanguage.json) · file utk bridge native `window.serverList`/
`window.replaceServerName` (atlas §8+§11 baris 12: layer NATIVE — "di luar scope
folder login") · file utk localStorage `last_game_server` (atlas §9: cache CLIENT).

**STATUS (Bagian 66):** struktur = TABEL EVENT ATLAS dibuat file — 5 file
(GetServerList/loginGame/SaveHistory/LoginAnnounce/SaveLanguage.json) +
data/history.sqlite (persist saja) + logs/; SaveUserEnterInfo = konstanta
kode (tanpa file). Menunggu oke owner (K1–K7 §5) → `LoginServerConfig.java`
seed 5 file → ack-gate §4.3 → smoke §3.6. Nol kode sampai owner oke.

### 3.1.1 PRINSIP KEPUTUSAN — 3 KOLOM, DILARANG "KARENA ASLI BEGITU" (Bagian 62)

Setiap perilaku login-server wajib dijawab dgn TIGA kolom ini — satu kolom
pun kosong = rencana belum matang:

1. **KONTRAK JS** (DASAR TEKNIS): apa yang main.min.js baca/terima — wire
   HARUS mengikuti parser client, di sinilah "dasar" yang diminta owner.
2. **FAKTA ASLI** (INFORMASI): apa yang server asli lakukan saat ini — status
   config MEREKA, bukan perintah untuk kita ("mereka diam ya hanya diam").
3. **KEPUTUSAN KITA + ALASAN**: pilihan sendiri dgn alasan eksplisit — dilarang
   memakai "karena server asli begitu" sebagai alasan.

Pembagian wewenang:
- BENTUK WIRE (envelope `ret/data`, data = JSON string, nama field, absen
  `compress`) = milik KONTRAK JS — parser strict (`0===e.ret`,
  `e.compress&&LZString…`, `JSON.parse(t)`) → kita ikut parser, bukan meniru.
- PERILAKU BISNIS (announce isi/diam, terima/tolak bahasa, terima/tolak
  loginGame, historyMode, nilai todayLoginCount, isi offlineReasonTop) =
  milik KEPUTUSAN KITA — diekspos di config/file supaya owner pegang kendali.

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

### 3.2 Struktur final `login-server/` — PRINSIP 3 KOLOM (Bagian 62), bukan jiplakan

Tabel keputusan PENUH — kolom 1 = DASAR teknis (parser client), kolom 2 =
status config server asli (INFORMASI SAJA), kolom 3 = KEPUTUSAN kita + alasan
(dilarang "karena asli begitu"):

| # | KONTRAK JS (dasar — main.min) | FAKTA ASLI (informasi saja) | KEPUTUSAN KITA + ALASAN + FILE |
|---|---|---|---|
| 1 | `selectNewServer`: baca `t.serverList[]` (12 field/entri; `matchServerUrl` banding `e==o.serverId`) + `t.history[]`; `onLoginSuccess` pakai `online/hot/new/name` | asli kirim 2.112 entri dari DB dinamis (openDate mingguan, merge) | **GetServerList.json `serverList[]`** — entri 12-field sesuai parser; jumlah entri BEBAS (lokal cukup 1+). Alasan: file = jawaban event yg sama nama (1 event = 1 file); owner edit langsung di file manager |
| 2 | `t.history` kosong → auto-enter; berisi → `history[0]` dicocokkan ke serverList → tombol start manual | asli persist per-akun di DB server (userId segar `[]`, akun lama `["2112"]`) | **GetServerList.json `historyMode`** = pilihan UX KITA: `auto` (masuk cepat, cocok server lokal) atau `persist` (resume server terakhir). Persist pakai **data/history.sqlite** TERPISAH dari accounts SDK (L1). Alasan: dua UX berbeda keduanya sah — bukan soal meniru |
| 3 | SaveHistory ACK dibaca `e.todayLoginCount` — hanya analytics (`4===t`, `6===t` → ReportToSdkCommon) | asli hitung per-hari di DB (contoh nyata `2`) | **SaveHistory.json `todayLoginCount`:1** — nilai bebas, owner bisa ubah; persist kelak hitung nyata. Alasan: konsumen cuma analytics, tidak memengaruhi alur |
| 4 | `getNotice` (LoginAnnounce): ACK data = OBJECT `{kunci: entri}`; baca `text[lang]||""`, `title[lang]||""`, `version`, `orderNo`, `alwaysPopup`; ≥1 entri → tombol pengumuman muncul; ada `alwaysPopup` → popup otomatis; data `{}` → tombol TIDAK muncul (visual identik dgn diam); server DIAM → callback tak jalan, hasil visual sama | asli DIAM (config pengumuman mereka kosong saat ini) | **LoginAnnounce.json (FILE PILIHAN OWNER)** — `{}` default = diam KARENA BELUM ADA ISI (bukan krn asli diam); diisi → pengumuman tampil, bisa **Bahasa Indonesia** via `text:{"in":…}` (client tetap ganti bahasa walau SaveLanguage 111 — fakta cabang changeLanguage). Alasan: fitur NYATA parser client + kebutuhan lokal menyampaikan info ke pemain |
| 5 | SaveLanguage: `0===errorCode` → changeLanguage; selain itu JUGA changeLanguage (kedua cabang sama); VERIFIKASI ULANG Bagian 63: cabang 111 = `console.log` saja — pesan server TIDAK pernah tampil ke pemain; daftar bahasa tombol = `window.supportLang` | asli tolak `in` dgn 111; whitelist mereka en/fr/kr (sumber: supportLang config .bin game) | **SaveLanguage.json `whitelist`** `["en","fr","kr"]` — alasan KITA: aset teks game hanya ada en/fr/kr; menerima `in` membuat UI game campur bahasa (rusak UX), padahal client tetap ganti bahasa walau ditolak; kini berbentuk FILE (1 event = 1 file) → bisa dibalik owner via edit: `[]` = terima semua |
| 6 | loginGame: JS TIDAK cek `data.errorCode` — lanjut dgn identitas apa pun yang dikirim | asli tolak semua channel uji dgn 103 (kebijakan bisnis "channel tertentu") | **loginGame.json `{"jawab":"sukses"}` (K2)** — alasan KITA: server lokal tidak punya kebijakan bisnis channel; 103 = aturan bisnis MEREKA; kini berbentuk FILE → bisa dibalik owner via edit (`tolak103`) tanpa sentuh kode |
| 7 | Parser envelope strict: `0===e.ret` → `data` = JSON string → `e.compress&&LZString…` (compress OPSIONAL) | asli tak pernah compress (618 KB pun polos) | **envelope polos tanpa compress** — alasan: ikut PARSER (compress:true hanya menambah kerja LZString tanpa manfaat lokal); fakta asli cuma memperkuat pilihan, bukan alasannya |
| 8 | `changeServerInfo`: `t[n].offlineReason=e.offlineReason` — top-level MENIMPA semua entri; absen → dialog offline tak pernah muncul | asli TIDAK mengirim top-level (status config mereka) | **GetServerList.json `offlineReason":""`** — default kosong = kita tidak mau dialog offline saat fresh boot; diisi = SATU-SATUNYA tombol dialog maintenance milik owner. Fitur parser client yang kita ekspos |
| 9 | `a.language&&(ts.language=a.language)` — top-level opsional | asli TIDAK mengirim | **GetServerList.json `language":""`** — default tak dikirim (biarkan client pakai bahasanya); diisi = paksa bahasa client dari server. Fitur parser client yang kita ekspos |
| 10 | (tidak ada konsumen JS utk saklar server) | asli SELALU hidup | **saklar `enabled` DIHAPUS (K7, Bagian 66)** — file event ADA = dijawab; file DIKOSONGKAN `{}` = DIAM (NO ACK, atlas §11 baris 9) → saklar granular per event tanpa meta-file; meta-saklar global = penemuan saya, bukan kontrak |
| 11 | pesan error tampil verbatim di dialog JS (`errorMessage`) | asli hardcode teks Tionghoa | **pesan error = konstanta kode** — alasan: bukan pilihan harian owner; teks baku cukup; file khusus = sampah kosong. Bisa dijadikan config kelak kalau ada kebutuhan nyata; ATLAS MENGUATKAN (§4.5/§11 baris 6): pesan server tak pernah tampil ke pemain |
| 12 | ACK SaveHistory: `e&&e.loginToken&&(ts.loginInfo.userInfo.loginToken=e.loginToken)` — server login BISA menanam token sesi game (atlas §4.3) | asli TIDAK PERNAH mengirim loginToken (selalu kosong → token SDK dipertahankan) | **SaveHistory.json `loginToken":""` [Bagian 65→66]** — default DIAM = tak pernah kirim (perilaku teruji; gameServer v19 belum punya konsumen token); diisi = owner menanam token via server login. Pintu parser DIEKSPOS di file event yang MEMBACANYA (K6) |

Kesimpulan arsitektur (tanpa jiplakan): wire mengikuti PARSER CLIENT;
setiap event punya FILE jawabannya sendiri — nama file = action string
verbatim (atlas §4), isi file = kunci yang parser baca di event itu;
konstanta kode hanya untuk event tanpa isi (SaveUserEnterInfo) dan bentuk
envelope. **Bagian 66**: struktur = TABEL EVENT ATLAS dibuat file —
pengelompokan config/data/announce karangan saya DIHAPUS.

```
server/login-server/
├── GetServerList.json     ← event 1: serverList[] 12-field + history[] +
│                             historyMode(auto|persist) + offlineReason("") +
│                             language("") — SEMUA kunci yg dibaca event ini
├── loginGame.json         ← event 2: {"jawab":"sukses"|"tolak103"} (K2)
├── SaveHistory.json       ← event 3: {"todayLoginCount":1,"loginToken":""}
├── LoginAnnounce.json     ← event 4: {} = diam; diisi = pengumuman tampil
│                             (format kontrak getNotice — bisa Bahasa Indonesia)
├── SaveLanguage.json      ← event 5: {"whitelist":["en","fr","kr"]} (K5)
├── data/
│   └── history.sqlite     ← persist per-accountToken — HANYA historyMode
│                             "persist"; TERPISAH dari sdk/data/db.sqlite (L1)
└── logs/                  ← NDJSON per event yang DIJAWAB
(event 6 SaveUserEnterInfo = TANPA FILE — konstanta kode `{}`: isi tak dibaca
 client, jawaban wajib supaya socket login di-destroy; atlas §4.6/§11.11)
```

File yang SENGAJA TIDAK ADA (dgn alasan, bukan asal buang):
- `config.json` / `serverlist.json` / `announce.json` — NAMA-nama pengelompokan
  karangan saya, DITOLAK owner dua kali (Bagian 63/65); diganti file
  per-event bernama action string verbatim (atlas §4).
- `README.txt` / `notes.md` / `captures/` — tak ada konsumen kode; pelajaran
  v16 (SdkConfig.java:28, index.txt "berasa jadi sampah"); dokumentasi = REPO.
- `history.json` — mode fixed DIHAPUS (spekulasi); K3 kini auto|persist.
- `profile.json` — bentuk envelope = kontrak parser (baris 7), bukan pilihan.
- `errors.json` — pesan error = konstanta kode (baris 11).

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
**5 file** = GetServerList.json (serverList 1 entri lokal + history[] +
historyMode:auto + offlineReason:"" + language:""), loginGame.json
({"jawab":"sukses"}), SaveHistory.json ({"todayLoginCount":1,
"loginToken":""}), LoginAnnounce.json `{}` (diam — kosong = pemain tidak
melihat apa pun, diisi = pengumuman tampil), SaveLanguage.json
({"whitelist":["en","fr","kr"]}). Folder `data/` dibuat HANYA saat historyMode
persist; `logs/` baru berisi NDJSON untuk event yang DIJAWAB — kosong = bukan
sampah. Nol file dokumentasi pernah dibuat, di boot maupun di event. Nama
kelas Java: **LoginServerConfig.java** (konsistensi dgn nama folder).

ATURAN BARU Bagian 66: file event DIKOSONGKAN (`{}` / 0 byte) oleh owner =
event DIAM (NO ACK) — dan seed TIDAK MENIMPA isi kosong itu (file ada, hanya
isinya kosong → dihormati). Menghapus file = kembali ke seed default.

### 3.4 Cara kerja runtime (file event kosong `{}` = NO ACK; file berisi = ACK real)

```
Boot:  Application.onCreate → LocalServerStarter.start
       → logs/ siap → ExitInfo / CrashHook / OverlayFixer / SessionReset (SDK)
       → Db.open (sdk/data) → [BARU] LoginServerConfig.ensure() (login-server/:
         seed 5 file) → ekstraksi bs/ (thread, latch folder js/)
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
  verbatim) → parse `{type,action}` → ambil file jawaban = `<action>.json`
  (self-seed folder login-server) → file KOSONG `{}`/absen = NO ACK (log
  "EVENT (no ack)" verbatim — v19 per event); file BERISI → `LoginAck.answer(...)`
  → `session.deliver("43<ackId>[{…}]")`. Infra kirim `Session.deliver` (WS
  langsung / antre polling) SUDAH ADA sejak v6 — tinggal dipakai.
- WS frame: titik yang sama di loop frame WS.
- SEMUA file kosong = wire PERSIS v19 (semua event NO ACK).
- `SaveUserEnterInfo` = TANPA file — SELALU dijawab `{}` (konstanta kode,
  atlas §4.6/§11 baris 11 — supaya socket login di-destroy rapi).

Envelope ACK = ikut PARSER client (§2): `43<ackId>[{"ret":0,"data":"<JSON string>"}]`
TANPA field `compress` (parser: `0===e.ret` → `JSON.parse(data)`, compress opsional —
polos lebih sederhana). Daftar ACK per event = §4.3 (semua LIVE-verified).

Peta EVENT → FILE (1:1 — nama file = action string; semua dibaca ulang tiap
event — edit tanpa restart):

| Event masuk | Sumber jawaban | ACK keluar (live-verified) |
|---|---|---|
| `GetServerList` | **GetServerList.json** (serverList + history + historyMode + offlineReason + language; persist → history dari data/history.sqlite) | data = `{serverList:[…12-field…], history:[…], language?, offlineReason?}` — kunci top-level = yang DIBACA parser; `errorCode` TIDAK dikirim (atlas §4.1/§12.5) |
| `SaveHistory` | **SaveHistory.json** (todayLoginCount + loginToken); persist → tulis data/history.sqlite | data = `{"todayLoginCount":N}` + `"loginToken":"<isi>"` bila diisi (pintu atlas §4.3 — K6); `errorCode` tidak dikirim (parser hanya baca todayLoginCount/loginToken) [3] |
| `LoginAnnounce` | **LoginAnnounce.json** — kosong `{}` → NO ACK (visual identik dgn jawab `{}` per kontrak getNotice); diisi → ACK data itu (format §4.2b) | tombol pengumuman muncul; `alwaysPopup` → popup otomatis |
| `SaveLanguage` | **SaveLanguage.json** (whitelist) | `{"errorCode":0}` / `{"errorCode":111,"errorMessage":"<teks bebas>"}` — `errorCode` WAJIB ADA di sini karena parser MEMBACANYA (atlas §4.5 `0===t.errorCode`) |
| `SaveUserEnterInfo` | KODE — TANPA file | data `{}` SELALU — isi ACK TIDAK dibaca client (atlas §11 baris 11); callback → destroy login socket [6] |
| `loginGame` | **loginGame.json** (`jawab`: sukses / tolak103) + AccountStore read-only | data `{"userId":"<uid>","channelCode":"<channel>"}` — dua kunci ini SATU-SATUNYA yang dibaca client (atlas §4.2); sisanya tidak dibaca → tidak dikirim [1] |

`login-server/logs/` menerima NDJSON untuk SETIAP event yang DIJAWAB (URL
penuh, gaya v17); event yang DIAM (file kosong) = baris "EVENT (no ack)"
verbatim — nol ACK = nol baris NDJSON.

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

1. Fresh install → skeleton sejak boot: 5 file event (GetServerList /
   loginGame / SaveHistory / LoginAnnounce / SaveLanguage .json) — data/ dan
   logs/ belum berisi (kondisional); seed = login AKTIF dgn isi default
   (server lokal 1 entri). AUDIT: nol file dokumentasi/pengelompokan lama
   pernah dibuat (tidak ada README.txt, notes.md, captures/, history.json,
   profile.json, errors.json, config.json, serverlist.json, announce.json).
2. Edit owner (isi file event mana pun) → berlaku tanpa restart.
3. Reboot → editan BERTAHAN (bs/ ditimpa ekstraksi; login-server/ TIDAK).
4. JSON mana pun diubah jadi teks sampah → boot berikut reseed + WARN; SDK aman.
5. historyMode persist → `login-server/data/history.sqlite` dibuat; mode auto →
   file tak pernah dibuat.
6. File event dikosongkan `{}` → event itu NO ACK (log "EVENT (no ack)");
   file berisi → NDJSON jawaban tercatat di `login-server/logs/`.
7. LoginAnnounce.json diisi 1 entri `alwaysPopup:true` → popup pengumuman
   tampil setelah login; tombol pengumuman terlihat; teks bahasa yang dipilih
   client yang ada di file yang tampil (termasuk `in`).
8. LoginAnnounce.json `{}` → tombol pengumuman TIDAK muncul (identik dgn
   perilaku diam — kontrak getNotice).
9. SaveUserEnterInfo SELALU dijawab `{}` TANPA file — setelah enterGame sukses,
   socket login tertutup (destroy — atlas §4.6).

---

## 4. DESAIN CONFIG LOGIN v3 (menggantikan skema v4.2 yang ngawur)

### 4.1 File & lokasi — Bagian 66: `server/login-server/` = **5 file event** bernama action string PERSIS yang client kirim (atlas §4): `GetServerList.json` + `loginGame.json` + `SaveHistory.json` + `LoginAnnounce.json` (pengumuman — pilihan isi/diam milik owner, format kontrak getNotice §4.2b) + `SaveLanguage.json`; SEMUA dibaca ulang tiap event; file kosong `{}` = event DIAM (NO ACK). Plus `data/history.sqlite` (persist saja) + `logs/`. SaveUserEnterInfo = TANPA file (konstanta kode — atlas §4.6/§11 baris 11). Pesan error / bentuk envelope = kontrak parser → konstanta kode.

### 4.2 Skema per file (default seed — 1 file = 1 event, kunci = yang dibaca parser)

**GetServerList.json** (event 1 — kunci kontrak: serverList/history/offlineReason/language;
historyMode = kunci server KITA, parser mengabaikan kunci asing — atlas §2/§6):

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
  ],
  "history": [],
  "historyMode": "auto",
  "offlineReason": "",
  "language": ""
}
```

**loginGame.json** (event 2 — kebijakan jawab K2; echo userId/channelCode diisi server dari request):

```json
{ "jawab": "sukses" }
```

**SaveHistory.json** (event 3 — analytics + pintu injeksi token K6):

```json
{ "todayLoginCount": 1, "loginToken": "" }
```

**LoginAnnounce.json** (event 4 — pengumuman; kosong `{}` = NO ACK, visual = diam):

```json
{}
```

**SaveLanguage.json** (event 5 — whitelist K5; `[]` = terima semua):

```json
{ "whitelist": ["en", "fr", "kr"] }
```

| File / kunci | Default | Alasan (bukti atlas) |
|---|---|---|
| GetServerList `serverList[]` | 1 entri 12 field | skema = PERSIS server asli (§2.2); `serverId` **STRING**; `url` tetap 9528 (fakta routing v3); urutan array = prioritas auto-enter (§11.3) |
| GetServerList `history[]` | `[]` | isi statis = resume untuk SEMUA akun; atlas §11.4: history[0] loose `==` cocok serverId |
| GetServerList `historyMode` | "auto" | kunci server KITA (parser abaikan kunci asing): auto = history `[]` → auto-enter instan; persist = per-accountToken dari data/history.sqlite (atlas §12.7) |
| GetServerList `offlineReason` | "" | fitur parser §6 (top-level menimpa offlineReason semua entri); "" = tak dikirim → tak ada dialog offline; diisi = papan maintenance owner |
| GetServerList `language` | "" | fitur parser §2 (dibaca SEMUA ACK); "" = tak dikirim; diisi = paksa bahasa client |
| loginGame `jawab` | "sukses" | K2 — kini FILE: owner bisa ganti `"tolak103"` via edit; ACK hanya echo userId+channelCode (§4.2) |
| SaveHistory `todayLoginCount` | 1 | analytics saja (`4===t`/`6===t` — §4.3/§11.8); persist kelak hitung nyata |
| SaveHistory `loginToken` | "" | pintu injeksi §4.3/§11.7: "" = tak kirim (token SDK dipertahankan); diisi = inject ke sesi game → enterGame (§7) — K6 |
| LoginAnnounce (isi) | `{}` | kosong = NO ACK; visual identik dgn jawab `{}` (tombol tak muncul — §4.4); diisi → pengumuman tampil, `in` TAMPIL |
| SaveLanguage `whitelist` | `["en","fr","kr"]` | K5: aset teks game hanya en/fr/kr (supportLang); `[]` = terima semua; client tetap ganti bahasa walau 111 (§4.5/§11.6) |

### 4.2b LoginAnnounce.json — format & pilihan (Bagian 62→66; dasar = kontrak getNotice main.min)

Parser client (main.min `getNotice`): ACK data = OBJECT `{kunci: entri}`;
per entri dibaca `text[bahasa]||""`, `title[bahasa]||""`, `version`,
`orderNo`, `alwaysPopup`. ≥1 entri → tombol pengumuman muncul di layar login;
ada `alwaysPopup` → panel pengumuman TERBUKA OTOMATIS setelah login. `{}` →
tombol tidak muncul (visual sama dgn diam).

```json
{}
```

Contoh TERISI (bila owner mau memakai pengumuman — tinggal edit file,
berlaku tanpa restart). Minimal isi `en` + `in` — `en` karena aman utk default
client, `in` utk pemain lokal; bahasa lain boleh ditambah (tanpa fallback: yang
tak ada = kosong):

```json
{
  "welcome": {
    "title": {"in": "Pengumuman", "en": "Notice", "kr": "공지"},
    "text": {"in": "Selamat datang di server lokal DragonBall!",
             "en": "Welcome to the local DragonBall server!",
             "kr": "로컬 서버에 오신 것을 환영합니다!"},
    "version": "1",
    "orderNo": 1,
    "alwaysPopup": true
  }
}
```

| Field | Wajib | Efek (dari parser) |
|---|---|---|
| `text` | ya | isi pengumuman PER-BAHASA — kunci = kode bahasa client (`en`/`fr`/`kr`/`in`…); ATURAN VERBATIM Bagian 63: parser baca `text[lang]||""` **TANPA fallback** — bahasa client yang tak ada di entri = string KOSONG (bukan bahasa lain) → minimal isi `en` + `in`; **`"in"` (Indonesia) TAMPIL** walau SaveLanguage ditolak 111 (client tetap ganti bahasa) |
| `title` | ya | judul per-bahasa, sama aturannya (`title[lang]||""` tanpa fallback) |
| `version` | ya | tampil sebagai versi pengumuman di panel (bulletinVersion) |
| `orderNo` | ya | urutan tampil (order) |
| `alwaysPopup` | opsional | true → panel TERBUKA OTOMATIS saat login; tanpa ini → hanya tombol |

KEPUTUSAN (kolom 3 prinsip §3.1.1): default `{}` karena BELUM ADA pengumuman
yang mau disampaikan — BUKAN karena server asli diam. Owner isi kapan saja →
fitur pengumuman (termasuk Bahasa Indonesia) langsung hidup.

### 4.3 Perilaku wire per event (semua bentuk LIVE-verified; sumber jawaban = file `<action>.json` — file kosong `{}` = NO ACK)

| Event masuk | Respons lokal |
|---|---|
| `User/GetServerList` | ACK `{"ret":0,"data":"<JSON string>"}` — data = `{serverList:[…12 field…], history:[…], language?, offlineReason?}` — tanpa compress (ikut parser: compress hanya menambah kerja LZString); ATURAN Bagian 65 (dari atlas): kunci data = HANYA kunci yang DIBACA parser — `errorCode` DIHAPUS (atlas §4.1/§12.5: client tidak pernah membacanya) |
| `User/SaveHistory` | ACK data `{"todayLoginCount":<SaveHistory.json>}` (+ `"loginToken":"<isi>"` bila `loginToken` diisi → pintu atlas §4.3/K6); kalau `historyMode:"persist"` (GetServerList.json) → simpan (accountToken→serverId) ke data/history.sqlite; `errorCode` tidak dikirim (parser hanya baca todayLoginCount/loginToken — atlas §4.3) |
| `User/LoginAnnounce` | baca LoginAnnounce.json — kosong `{}` → NO ACK (visual identik dgn jawab `{}` per kontrak getNotice); diisi → jawab data itu (format §4.2b) — tombol pengumuman muncul, `alwaysPopup` → popup otomatis, teks `in` tampil |
| `User/SaveLanguage` | baca SaveLanguage.json whitelist → dalam daftar: data `{"errorCode":0}`; di luar → `{"errorCode":111,"errorMessage":"<teks bebas — tak pernah tampil>"}` — `errorCode` WAJIB di sini karena parser MEMBACANYA (`0===t.errorCode`, atlas §4.5); client tetap ganti bahasa walau 111 |
| `User/SaveUserEnterInfo` | ACK data `{}` — isi ACK TIDAK dibaca client (atlas §11 baris 11; `errorCode:0` lama juga tak dibaca → aturan kunci-konsumen konsisten) → callback → destroy() login socket |
| `User/loginGame` | ACK data `{"userId":"<uid>","channelCode":"<channel>"}` — dua kunci ini SATU-SATUNYA yang dibaca client (atlas §4.2); server lokal MEMBIASAKAN sukses utk jalur origin (K2 tetap); loginToken/nickName/isCreated/errorCode/… tak dibaca → dibuang |
| event lain | NO ACK + log verbatim (disiplin tetap — atlas §11 baris 13: 6 event TOTAL, tidak ada ke-7) |

### 4.4 SEMUA file event kosong `{}` = wire PERSIS v19 (semua event NO ACK — atlas §11 baris 9); seed default = login AKTIF.

### 4.5 FAKTA ROUTING (tetap + penguat): `serverList[].url` = `http://127.0.0.1:9528`.
Server game asli pakai `https://sXXXX-bs.popoh5.com:8041` — pada device asli origin
halaman adalah HTTPS CDN sehingga bisa; device lokal origin halaman = 127.0.0.1:9528
(XHR runtime hanya terbukti capai origin — pelajaran v3 stuck). JANGAN diganti.

### 4.6 Implementasi kelak (urutan, saat owner oke)
1. `LoginServerConfig.java` (pola SdkConfig) + seed 5 file event.
2. Ack-gate `SocketIoHandler` per tabel §4.3 (file `<action>.json`; kosong = NO ACK) + history persist (SQLite, bila dipilih).
3. Smoke baru: string ACK persis §4.3 (bukan lagi bentuk v17); semua file kosong = v19.
4. Dashboard tak berubah (disiplin v17: URL penuh, nol pangkas).

### 4.7 Kriteria terima (pesawat, offline penuh) — diperbarui
1. SEMUA file event kosong `{}` → v19 persis (nol dialog network; berhenti sebelum serverlist — atlas §11 baris 9).
2. Seed default (file berisi) → TAMU → serverlist tampil dari GetServerList.json → history kosong =
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
| K2 | `loginGame.json`: `{"jawab":"sukses"}` atau `{"jawab":"tolak103"}`? | **Sukses (seed)** — alasan KITA: server lokal tidak punya kebijakan bisnis channel (103 = aturan bisnis MEREKA); tujuan lokal = semua jalur jalan; JS tidak cek errorCode jadi kedua pilihan aman secara teknis; kini FILE — owner bisa balik via edit tanpa sentuh kode |
| K3 | `historyMode` di GetServerList.json: `auto` (auto-enter instan) atau `persist` (resume server terakhir, tombol start manual)? | `auto` utk sekarang (cocok server lokal); `persist` mudah ditambah kemudian (SQLite sudah ada); mode `fixed` DIHAPUS (spekulasi) |
| K4 | `LoginAnnounce.json`: DIAM (kosong `{}` = NO ACK) atau ISI pengumuman? | **Kosong default** — alasan: BELUM ada pengumuman yang mau disampaikan (bukan krn server asli diam); visual kosong = diam (kontrak getNotice). ISI kapan saja via edit file → pengumuman tampil, bisa Bahasa Indonesia (§4.2b) |
| K5 | `SaveLanguage.json whitelist`: tolak bahasa di luar en/fr/kr (111) atau terima semua? | **Tolak (111) utk en/fr/kr saja (seed)** — alasan KITA: ASET teks game hanya en/fr/kr (supportLang .bin); terima `in` membuat UI game campur bahasa; client tetap ganti bahasa walau ditolak (kedua cabang changeLanguage) → tidak memengaruhi UX, hanya kerapian; kini FILE — `[]` = terima semua via edit |
| K6 | `SaveHistory.json loginToken`: kosong "" (tak kirim) atau diisi (inject sesi — atlas §4.3/§11 baris 7)? | **Kosong default** — DIAM: tak pernah kirim (= asli; token SDK dipertahankan; gameServer v19 belum punya konsumen token); ISI: owner menanam token sesi game via server login → client membawanya ke enterGame. Pintu parser DIEKSPOS di file event yang MEMBACANYA |
| K7 | Saklar global `enabled`: dipertahankan atau DIHAPUS? | **DIHAPUS (Bagian 66)** — file event ADA = dijawab; file kosong `{}` = DIAM (NO ACK per event — atlas §11 baris 9); meta-saklar global = pengelompokan karangan saya yang ditolak owner dua kali; matikan satu event = kosongkan file itu |
| P0 | PRINSIP (Bagian 62, mengikat semua keputusan): tiap perilaku wajib 3 kolom — KONTRAK JS (dasar wire) / FAKTA ASLI (informasi) / KEPUTUSAN KITA + ALASAN. Dilarang alasan "karena asli begitu"; fase gameplay kelak WAJIB mulai dari bedah kontrak (parser + capture), bukan jiplakan | Ditetapkan owner 26 Nov — "jangan semuanya disamakan tanpa kejelasan; jangan asal niru tanpa dasar" |

### 5.1 KEPUTUSAN FOLDER (revisi Bagian 62)

KEPUTUSAN OWNER — sudah FIX, tidak ditanya lagi:
- NAMA FOLDER = **`login-server/`** (koreksi langsung owner; `login/` salah nama).
- MULTI-FILE sungguhan ("masak ya gitu cuma 1 file doang") — TAPI isi folder
  wajib lewat prinsip 3 kolom.
- **DILARANG JIPLAK** (koreksi #3 Bagian 62): "mereka diam ya hanya diam —
  kalau kita diam jangan ikut diam, harus tau alasannya" → tiap perilaku =
  KONTRAK JS + KEPUTUSAN + ALASAN (§3.1.1, K-P0); LoginAnnounce.json = pilihan
  owner (isi/diam); README.txt/notes.md/captures/ tetap TIDAK KEMBALI (L5).

| # | Keputusan | Rekomendasi (basis bukti) |
|---|---|---|
| L1 | DB history: file TERPISAH `login-server/data/history.sqlite` atau gabung `sdk/data/db.sqlite`? | **Terpisah** — scope SDK (tabel accounts) tidak tersentuh eksperimen login; Db.open masih hardcoded sdk/data (Bagian 30), memisah = nol risiko migrasi; DbSqlite dipakai ulang dgn dataDir berbeda |
| L2 | Waktu seed `login-server/`: BOOT atau lazy (event pertama)? | **Boot** — satu panggilan `LoginServerConfig.ensure()` tepat setelah `Db.open`; 5 file event (GetServerList/loginGame/SaveHistory/LoginAnnounce/SaveLanguage .json) terlihat sejak detik pertama; event pertama dijamin punya file jawabannya |
| L3 | Isi folder | **Bagian 66 — DITURUNKAN DARI TABEL EVENT ATLAS** (koreksi kedua owner): 5 file event bernama action string verbatim (1 event = 1 file jawaban) + data/history.sqlite (persist saja) + logs/ (event yang dijawab saja); config.json/serverlist.json/announce.json DIHAPUS sebagai nama; SaveUserEnterInfo = tanpa file (konstanta kode) — SEMUA lolos prinsip 3 kolom §3.2 + tiap baris menunjuk atlas Bedah Total |
| L4 | `logs/` per-scope login-server (pisah dari logs/ global), menulis untuk event yang DIJAWAB? | **Ya** — debug login tidak tercampur trafik SDK/CDN; event yang DIAM = baris no-ack verbatim; nol ACK = nol NDJSON |
| L5 | File dokumentasi di device (README.txt/notes.md/captures/)? | **TIDAK, PERMANEN** — server asli tak punya padanannya; v16 sudah menghukum index.txt ("berasa jadi sampah", SdkConfig.java:28); dokumentasi = REPO saja (docs/, readme.md, verify-artifacts/) |
| OPEN | K1–K7 (implementasi ACK, loginGame, historyMode, LoginAnnounce, bahasa, loginToken, saklar-enabled) | Menunggu keputusan owner — struktur kini = tabel event atlas dibuat file (Bagian 66), tinggal persetujuan K1 |

## 6. YANG TETAP OPEN + TAHAP BERIKUTNYA (jujur, tak dikarangan)

PERINGATAN OWNER (Bagian 62) utk fase berikutnya: "ini baru tahap depan, belum
 gameplay — masuk tahap gameplay nanti bingung lagi karena cuma asal niru
server asli padahal gak punya dasar". MAKA fase gameServer/enterGame kelak
WAJIB mulai dari BEDAH KONTRAK dulu (parser main.min: UserDataParser,
enterGame/registChat; capture live server game + handshake verify XXTEA yang
sudah terbukti Bagian 58) — baru KEPUTUSAN per perilaku dgn prinsip 3 kolom
§3.1.1. Tidak ada implementasi dari jiplakan.

- loginGame SUKSES server asli: tak bisa direkam — semua channel uji ditolak 103
  ("hanya channel tertentu"); butuh akun SDK asli yang sudah tidak bisa dibuat
  (product DB qsdk dikosongkan — LIVE_CONTRACT_20261123.md).
- Data penuh `enterGame` sukses (UserDataParser): butuh loginToken sah — fase gameServer.
- Isi ACK `registChat` + `_chatServerUrl` dll: kontrak terbaca dari JS, respons asli
  belum terekam (butuh enterGame sukses dulu).

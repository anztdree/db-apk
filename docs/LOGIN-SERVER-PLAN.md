# LOGIN SERVER — RENCANA (PLAN ONLY, BELUM DIKERJAKAN)

> Status: **DOKUMEN RENCANA** — bukan implementasi.
> Arahan owner (kata-kata persis, 25 Nov 2026):
> "login server belum boleh di kerjakan!! hanya menentukan endpoint dan
>  merencanakan strategi struktur foldernya dulu !!"
>
> **PEMBARUAN v18 (25 Nov 2026)** — arahan owner:
> "muncul serverlist harusnya itu sudah bagian dari loginserver yg belum kita
>  buat , artinya anda tidak memisahkan tugas sebagai mana mestinya.. SDK ya
>  fokus di SDK bukan mlah ngurusin loginserver walaupun itu emang terkait .
>  dan loginserver pun belum kita rencanakan lebih lanjut ."
>
> **Tindak lanjut v18 di APK**: SEMUA ACK event bisnis socket.io DIHAPUS dari
> listener (routeEvent/GameData — termasuk ACK `GetServerList` serverList
> "DragonBall Local" — dihapus seluruhnya). Listener 5050/9528/5051 kini
> **TRANSPORT ONLY**: engine.io handshake/poll/WS/probe/ping-pong 1:1 capture
> live + `/loginchecknative` (scope SDK). Setiap frame event `42…` dicatat
> VERBATIM di logger dengan penanda `EVENT (no ack) … loginServer scope —
> not implemented (plan only)`. NOL balasan — callback JS tidak pernah
> dipanggil; game berhenti sebelum layar server list. Pemisahan tugas kini
> tegas: **SDK (9530) = SDK saja; loginServer = 0 kerja, hanya rencana.**
>
> Isi di bawah 100% dari data nyata: config .bin ter-decode (XOR `DragonBall`,
> uji ulang live 21 Nov 2026), smali APK, capture live server asli
> (`rebuild-src/scripts/capture_login*.py`), dan log device MOD v13–v18.
> NOL karangan — yang belum terbukti ditandai `OPEN`.

---

## 1. Siapa yang MENUNJUK endpoint login (fakta config, bukan asumsi)

Client membaca DUA file dari pohon `bs/` yang kita kontrol di
`server_root` (device: `Android/data/com.db.local/server/`):

| File | Field | Nilai ORIGINAL (live 21 Nov) | Nilai MOD (v13–v16 sekarang) | Dipakai siapa |
|---|---|---|---|---|
| `bs/db/android/setting_BS_Android.bin` (XOR `DragonBall`, 711 B live / 687 B mod) | `loginServer` | `https://login.popoh5.com` | `http://127.0.0.1` | dipetakan smali `LaunchActivity.java` |
| idem | `loginPort` | `610` | `9528` | **socket.io JS engine** (Egret) |
| idem | `loginWebPort` | `510` | `5050` | **native Java SDK** (`HttpURLConnection` → `/loginchecknative`) |
| `bs/resource/properties/serversetting.json` | `loginserver` | (live: login.popoh5.com) | `http://127.0.0.1:9528` | dibaca JS engine |
| `bs/resource/properties/serversetting.json` | `clientversion` | — | `{"clientVersion":"202109150935"}` | cek versi native (popup 404 sudah mati) |

Jawaban atas "kenapa loginserver sudah jalan padahal belum diberi rencana":
**client yang menelepon sendiri** sejak boot karena config di atas sudah
menunjuk `127.0.0.1` sejak v13. **v13–v17** server lokal menjawab minimal
(handshake + ACK `GetServerList` + `loginchecknative`) — owner menilai ini
melanggar batas tugas. **v18 memperbaikinya**: ACK `GetServerList` DIHAPUS —
listener kini transport saja + pencatatan verbatim. `loginchecknative` tetap
karena itu glue SDK native (bukan loginServer). Implementasi lengkap = dokumen
ini, menunggu keputusan owner.

---

## 2. PETA ENDPOINT (terbukti dari log device + capture live)

### 2.1 Yang SUDAH TERBUKTI dipanggil client ke login server (log device MOD)

| # | Endpoint PENUH | Sumber panggilan | Bukti | Penjawab sekarang |
|---|---|---|---|---|
| 1 | `GET http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=…` (OPEN → `96:0{"sid":…,"upgrades":["websocket"],"pingInterval":25000,"pingTimeout":5000}2:40`) | JS engine Egret (socket.io 2.1.2) | 6 log device + capture live `login.popoh5.com:610` | `SocketIoHandler` via chain 9528 (`ServerCore.java:77-83`) |
| 2 | `GET http://127.0.0.1:9528/socket.io/?EIO=3&transport=websocket&sid=…` (upgrade 101) | JS engine | log device | idem |
| 3 | `POST http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=…&sid=…` body `<len>:` frames | JS engine | log device | idem — packet & ACK kini PENUH di log (v17) |
| 4 | `GET http://127.0.0.1:5050/loginchecknative?uid=…&username=…&token=…&usermode=…&os=Android&sdk=…` | native SDK (smali `pursetruechild/feedtonight/singersmooth.onLoginFinished` → `HttpURLConnection`) | log device + smali | `SocketIoHandler` 5050 (`SocketIoHandler.java:197-217`) |
| 5 | listener `5050/socket.io/…` langsung (tanpa chain) | fallback native mode login (`loginServer:loginPort` jalur lama) | kontrak smali `loginPort` | `SocketIoHandler` 5050 |

### 2.2 Event socket.io `handler.process` yang SUDAH PERNAH MUNCUL (semua verbatim di log)

Wire: client `42<ackId>["handler.process",{type,action,…}]` → server ACK
`43<ackId>[{ret,data,compress}]` (socket.io v2, bukti JS `TSLoginClient.sendToServer`
+ `processHandlerWithLogin`).

**Status v18: SEMUA ACK DIHAPUS.** Kolom "ACK sekarang" = apa yang dulu
dijawab v13–v17 (sekarang menjadi bahan perencanaan, BUKAN yang dilayani).

| Action | type | Permintaan (field nyata) | ACK v13–v17 (DIHAPUS di v18) | Status v18 |
|---|---|---|---|---|
| `GetServerList` | `User` | `{userId, subChannel, channel}` | serverList "DragonBall Local" | **NO ACK** — dicatat verbatim `EVENT (no ack)…loginServer scope — not implemented (plan only)` |
| `loginGame` | `User` | `{userId, fromChannel,…}` | `{userId,channelCode,sign:"local"}` | **NO ACK** — verbatim di log |
| `LoginAnnounce` | `User` | — | `{}` | **NO ACK** — verbatim di log |
| `SaveUserEnterInfo` / `SaveLanguage` / `saveHistory` | `User` | — | `{}` | **NO ACK** — verbatim di log |
| `GetNotice` / `getBulletin` | — | — | `{}` | **NO ACK** — verbatim di log |
| (5051) `enterGame` | `user` | — | payload user penuh (UserDataParser kontrak) | **NO ACK** — verbatim di log |
| (5051) `getBulletinBrief` | `user` | — | `{_brief:{}}` | **NO ACK** — verbatim di log |
| **action tak dikenal** | — | — | `{ret:0,data:"{}"}` | **NO ACK** — verbatim di log (bahan belajar) |

Yang masih DIJAWAB di v18 (transport + scope SDK, bukan loginServer):
`0{sid,…}` open, `40` connect, `2probe`→`3probe`, `2`→`3` ping-pong, `41` close,
WS upgrade 101, dan `/loginchecknative` (glue SDK native).

### 2.3 Yang dipakai server ASLI tapi BELUM kita buktikan butuh (OPEN — jangan dikarang)

| Item | Fakta yang ADA | Yang belum terbukti |
|---|---|---|
| `loginKey` + verify TEA | field `loginKey` ADA di JSON config live (4.C) | apakah jalur login kita benar-benar melewati verify ini — belum ada satu pun log device yang menunjukkan panggilannya |
| `loginWebPort` original `:510` | live: `{"code":404,"msg":"没有开通"}` (belum dibuka server asli) | — (native MOD pakai 5050 untuk `/loginchecknative`, bukan 510) |
| `tsLoginServer` `http://118.24.68.91:7003` | MATI live (timeout) | jalur TS tidak pernah dipanggil di log device |
| battle-log `log.sjflaregame.com:10000/error/battle/client` | DIBLOKIR live (302 webblock) | tidak pernah muncul di log device MOD |

---

## 3. STRATEGI STRUKTUR FOLDER (PROPOSAL — belum dibuat, menunggu oke owner)

### 3.1 Device (`Android/data/com.db.local/server/`) — kondisi NYATA sekarang

```
server/
├── bs/                  ← 2759 file engine (ekstraksi assets, TIDAK disentuh)
│   ├── db/android/setting_BS_Android.bin      ← pointer loginPort/loginWebPort
│   ├── resource/properties/serversetting.json ← pointer loginserver
│   └── resource/properties/clientversion.json
├── db/                  ← SQLite akun (AccountStore/UserAccounts — sudah ada)
└── logs/                ← NDJSON arsip log-*.ndjson + crash-*.txt (sudah ada)
```

### 3.2 Proposal folder login (DIBUAT BARU hanya setelah owner oke)

```
server/
└── login/                       ← BARU (kosong saat dibuat — nol efek runtime)
    ├── endpoints.md             ← salinan peta §2 (snapshot saat itu)
    ├── captures/                ← capture MENTAH live server asli (bukti kontrak)
    │   ├── socketio-open.txt        (96:0{...} handshake asli :610)
    │   ├── getserverlist-ack.txt    (43<id>[{ret,data,compress}] asli)
    │   └── loginchecknative.json    (respons asli :510 — 404 未开通, sebagai contoh bentuk)
    ├── payloads/                ← payload JSON yang AKAN dilayani per event
    │   ├── GetServerList.json
    │   ├── loginGame.json
    │   └── enterGame.json
    └── notes.md                 ← log keputusan owner (append-only, tanggal)
```

Aturan folder (biar tidak jadi sampah — pelajaran v16 `index.txt` dihapus):
1. Satu file payload = satu event. NOL file indeks/summary otomatis.
2. `captures/` hanya berisi hasil capture asli (file + tanggal capture di
   nama/header) — TIDAK pernah diisi karangan.
3. Yang tidak dipakai dihapus, bukan ditumpuk.
4. Server membaca folder ini BARU pada iterasi berikutnya; sekarang payload
   masih hardcode di `SocketIoHandler.GameData` (kontrak JS, terdokumentasi).

### 3.3 Repo (struktur NYATA sekarang + tempat kerja login nanti)

```
rebuild-src/
├── javasrc/com/db/local/server/     ← SATU tempat logika server (SocketIoHandler,
│                                       SdkHandler, AccountStore, GameData di dalamnya)
├── scripts/capture_login.py         ← capture live :610 GetServerList (sudah ada)
├── scripts/capture_login2.py        ← capture lanjutan (sudah ada)
└── smoke/run_smoke.py               ← uji desktop (handshake/ACK/loginchecknative)
docs/
├── LOGIN-SERVER-PLAN.md             ← dokumen ini
├── SDK-CONFIG.md                    ← kontrak sdk/ (jangan dicampur login)
└── SDK-STUDY.md
```

Kerja login server KELAK tetap di `SocketIoHandler` (bukan folder baru di
javasrc) — satu process, satu class per handler, pola yang sudah terbukti.

---

## 4. URUTAN KERJA BILA OWNER OKE (tidak dieksekusi sekarang)

1. Isi `server/login/captures/` dari capture live yang SUDAH ADA
   (`capture_login*.py` hasil — tidak perlu re-capture kalau file backup logs
   masih ada).
2. Pindahkan isi `SocketIoHandler.GameData` (GetServerList/enterGame) ke
   `server/login/payloads/*.json` — server jadi pembaca file, isi bisa diedit
   owner tanpa rebuild APK.
3. Baru setelah itu: perlakuan `loginKey`/TEA — HANYA jika log device
   menunjukkan client benar-benar memanggilnya (OPEN di §2.3).

Langkah 1–3 TIDAK dilakukan di v17. v17 hanya: log URL penuh + dokumen ini.

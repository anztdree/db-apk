# GAME WORLD (SERVER UTAMA) — DOC TUNGGAL

> **Satu-satunya doc domain dunia game.** Tahap ini dimulai 2026-10-22 setelah tahap
> loginserver (1.0.3) terbukti hidup di perangkat (screenshot server list "DB-Local 1"
> + tombol PLAY = bukti `docs/login-server/doc.md` §8e).
> Doc loginserver: [`docs/login-server/doc.md`](../login-server/doc.md).

---

## 1. FAKTA ARSITEKTUR — KLIEN ASLI MEMAKAI 5 SERVER (bukti main.min_777039fc.js)

`TSUIController` membangun **4 instans `TSSocketClient`** + 1 gateway web:

| # | Server | Transport | Verify | URL sumber | Status lokal |
|---|--------|-----------|--------|-----------|--------------|
| 1 | gateway web SDK | HTTP (`HttpURLConnection`) | — | bin `loginServer:loginWebPort` | ✅ 28110 (sejak 1.0.2) |
| 2 | login-server | socket.io v2 polling | ❌ | bin `loginServer:loginPort` | ✅ 28112 (sejak 1.0.3) |
| 3 | **main-server** | socket.io v2 polling | ✅ XXTEA | `serverItem.url` (dari GetServerList) | ✅ **28113 (rev7)** |
| 4 | chat-server | socket.io v2 polling | ✅ XXTEA | `serverItem.chaturl` ← `registChat._chatServerUrl` | stub 28114 |
| 5 | dungeon-server | socket.io v2 polling | ✅ XXTEA | `serverItem.dungeonurl` ← `enterGame.myTeamServerSocketUrl` / `userData` tim | stub 28115 |

Instans persis di main.min.js: `new TSSocketClient("login-server",!1)`,
`("main-server",!0)`, `("chat-server",!0)`, `("dungeon-server",!0)`.

Alur setelah tombol PLAY (`clientStartGame`):
`mainClient.connectToServer(serverItem.url)` → verify → `clientEnterGame`
(`{type:"user",action:"enterGame",loginToken,userId,serverId:INT,version:"1.0",language,gameVersion}`)
→ `loginSuccessCallBack` (data → `UserDataParser.saveUserData`) → `setInterval(registChat,3s)`
→ bila `_success`: `chatClient.connectToServer(chaturl)` → chat `login` + `joinRoom` ×4
(world/guild/teamDungeon/team). Dungeon hanya untuk fitur tim.

---

## 2. BASIS BUKTI — PANEN SERVER ASLI HIDUP (2026-10-22)

Rantai penuh dipanen dari server asli yang MASIH HIDUP (satu-satunya sumber kebenaran);
alat: `work-re/panen-gameworld/` (probe1/2_sdk.py, eio3client.py, probe3_mainserver.py).

| Artefak (`panen-gameworld/original/`) | Isi |
|---|---|
| `qsdk-init.txt` / `qsdk-registerVisitor.txt` | init + sesi guest ASLI dari `qsdk.t4game.com` (sign dex-exact, secret `0b2a18e45d7df321`; sign = MD5(TreeMap `k=v&` tiap pasangan ber-`&` + secret); `authToken` WAJIB non-kosong) |
| `gate-loginchecknative.txt` | gerbang ASLI `login.popoh5.com:510/loginchecknative` → `code:0` + loginToken (uid 28506956) |
| `getserverlist.json` | `login.popoh5.com:610` GetServerList ASLI — **616 KB, 2107 server** |
| `entergame-raw.json` / `entergame-data.json` | **enterGame ASLI dari `s2107-bs.popoh5.com:8061`** — envelope `{"ret":0,"compress":true,"serverTime":…,"server0Time":25200000,"data":…}` → data user **79 kunci top-level** (user, heros, equip, currency, checkin, hangup, dungeon, guild, broadcastRecord, `teamServerHttpUrl`…) |
| `registchat-raw.json` | registChat ASLI utk akun baru = **`{"ret":60}`** (konsisten, ±5 s juga 60) |

Temuan kontrak kawat server utama asli:

1. **verify**: server emit `42["verify","<uuid-v4>"]` → klien balas
   `42<id>["verify","<XXTEA-b64>"]` (kelas `TEA` main.min.js = XXTEA, delta
   `0x9E3779B9`, putaran `6+52/n`, little-endian, key UTF8 `.slice(0,16)`,
   output Base64; **index key > array = 0** — JS undefined → ToInt32 0) → ack
   `43<id>[{"ret":0}]`.
2. **Aksi asing = DITAHAN** (tanpa ack — long-poll kosong), BEDA dari loginserver
   (yang menjawab `ret:901`). Klien punya timer sendiri: 600 ms LoadingPanel,
   10 dtk → toast timeout.
3. **Envelope** main: `ret`, `compress`, `serverTime`, `server0Time` (25200000 =
   UTC+7 ms), `data`. `compress:true` = LZString UTF-16 — lokal kirim
   `compress:false` + data polos (keduanya sah bagi klien; loginserver asli juga
   tanpa compress).
4. **Empty poll** dijawab NOOP (pola rev5 loginserver: `1:1` setelah tahan ±20 dtk).
5. PNG `Telegram/Instagram/Discord/Logo18+Full/denglujiemian…new21` **TIDAK ADA
   juga di CDN asli** → 404 + "Uncaught 1001" di log = perilaku ASLI, bukan cacat
   mirror. `com_db_local.bin` asli = stub "Page 500" (8 B) → 404 lokal setara.

---

## 3. IMPLEMENTASI LOKAL — kelas `com.db.local.MainServer` (rev7)

- Murni Java (`ServerSocket`, thread-per-conn + sweeper TTL 120 dtk), bind
  `127.0.0.1`; dipanggil dari `MiniServer.start` tepat setelah `LoginServer.start`
  (satu hook warisan; classes.dex tak disentuh).
- **3 port**: 28113 main (verify + enterGame + registChat + default), 28114 chat
  stub, 28115 dungeon stub — stub = verify sah lalu **hold** semua aksi (setia asli;
  stub tak pernah dihubungi selama registChat=60 & user belum gabung tim).
- Handshake: `0{"sid":"<17>","upgrades":           [],"pingInterval":25000,
  "pingTimeout":5000}` + pendamping `2:40` (pola rev5 yang terbukti di perangkat).
- **enterGame**: template 79 kunci = byte panen asli; adaptasi per-uid runtime:
  semua `_id` (1 UUID sama untuk 7 sub-objek — pola asli), `_nickName`
  `"New User"+4 char`, `_account`=uid, `serverId`/`_oriServerId` dari request,
  `teamServerHttpUrl` → `http://127.0.0.1:28115`, times → sekarang.
  **Persist per uid**: `files/server/mainserver/users/<uid>.json` — login pertama
  `newUser:true`, tersimpan `newUser:false` (login berikut = lanjutan, id tetap).
- **registChat** → `{"ret":60}` byte persis asli (chat aktif nanti mengikuti
  kondisi asli; klien membatasi 15 percobaan @3 dtk lalu berhenti sendiri).
- **Aksi asing**: default `noop` → `43<id>[{"ret":0,"data":"{}"}]` (tanpa toast
  error klien); mode `hold` (setia asli) bisa dipilih lewat
  `files/server/mainserver/mode.txt` berisi `hold`.
- Config owner-editable: `files/server/mainserver/entergame-template.json` (seed
  otomatis dari bawaan bila hilang/tidak sah) + `mode.txt`.
- Log: tag `MainServer` (handshake/verify/enterGame/hold/noop) + tag `PORT`
  (`28113 (MainServer) LISTENING ✓` dst.) — tag `MainServer` kini masuk filter
  **tab NET** panel DBG.

## 3a. FIX FLOATING (dibawa rev7, akar rev4–rev6)

`DLog.init()` dipanggil di `attachBaseContext` → `APP = ctx.getApplicationContext()`
**masih null** saat itu, tapi `inited` sudah `true` → semua `init` berikutnya kabur
→ `appCtx null` selamanya (bukti logcat 23:56:26). Fix: `DLog.rebindApp(Context)`
baru — dipanggil di `DbgAppApplication.onCreate` (application sudah attach) —
`APP` di-set ulang permanen. + `MainServer` masuk daftar `isNetTag`.

---

## 4. STATUS & UJI (build rev7-debug, 2026-10-22)

APK: `DB-Local-1.0.3-rev7-debug.apk` — 125.138.387 B, sha256
`5ef586a2e40761a6af3364286ac10804995d0a8e82799c47e1626071f6d452af`,
zipalign verified, sign v1+v2+v3. Delta vs rev6 (audit byte 3.720 entri):
**hanya classes2.dex (46.052→79.768 B: +MainServer +hook) dan classes3.dex
(51.080→51.168 B: rebindApp + filter NET)**; 3.715 entri identik, nol entri
hilang/baru; manifest & classes.dex byte-identik rev6.

**SERTIFIKAT BARU** (keystore rev4–6 hilang bersama sandbox) SHA256
`1e08a903…8187b5953` → **WAJIB UNINSTALL build lama dulu**, lalu pasang rev7.
Konsekuensi: DB akun guest mulai dari nol.

Uji JVM-kawat **21/21 PASS** (MainServer di JVM, klien = probe Python yang sama
yang sukses melawan server asli — bentuk kawat identik):
handshake `93:0{…}2:40` + `upgrades:[]`; verify UUID → `43[{"ret":0}]`;
enterGame envelope ret:0/compress:false/server0Time:25200000 + data 79 kunci;
adaptasi uid/serverId/dungeon-lokal/UUID/nick; persist `newUser true→false` +
id tetap; registChat `{"ret":60}`; noop `{"ret":0,"data":"{}"}`; ping→pong;
verify salah → sesi dibuang (`400 Session ID unknown`); stub 28114/28115
verify sah + hold tanpa ack (NOOP `1:1`).

### Protokol verifikasi perangkat (tanpa PC)

1. **UNINSTALL** build lama → pasang rev7-debug → buka app.
2. Bubble **DBG** kini mesti tayang DI ATAS game (appCtx fix). Ketuk → panel.
3. Tab **NET** wajib memuat: `MainServer 200 handshake`, `verify OK`,
   `PORT: 28113 (MainServer) LISTENING ✓` (+ 28114/28115 stub).
4. Login → pilih **DB-Local 1** → **PLAY** → kini mainClient konek **28113**
   (logcat lama: `Downloading http://127.0.0.1:28113/socket.io/… FAILED`) →
   verify → enterGame → dunia game dimuat (karakter "New User…").
5. Bila PLAY masih gagal → tab LIVE mencatat baris `MainServer` penyebabnya;
   kirim baris tersebut.

## 5. BATAS TAHAP INI (jujur & terukur)

- Gameplay aksi (build/battle/mail/checkin/dll.) = **noop** (klien anggap sukses
  kosong) → game masuk dunia & UI jalan, tapi **progres gameplay tidak
  dihitung server**. Naik kelas berikutnya = panen per-aksi dari server asli +
  persist penuh (tahap berikut).
- Chat/dungeon = stub (mengikuti kondisi asli utk akun baru).
- Progres mengikuti rantai asli: PLAY kini lanjut; pantau logcat/panel DBG utk
  aksi apa yang diminta klien setelah masuk — itu daftar kerja panen berikutnya.

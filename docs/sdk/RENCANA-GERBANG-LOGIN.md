# RENCANA GERBANG LOGIN — SDK 1.0.2 Standalone Lokal → Pintu Loginserver

> **Status: RENCANA — BELUM IMPLEMENTASI.**
> Dokumen ini menjawab satu pertanyaan saja: *dari SDK standalone lokal, bagaimana jalurnya
> sampai ke pintu gerbang loginserver — dan apa bukti fisik setiap lengkahnya.*
> Tidak ada satu langkah pun di sini yang berdiri di atas asumsi; setiap lengkah diberi
> **jangkar bukti fisik** (berkas APK, dex, tiket request, respons server hidup).
> Implementasi tetap ditahan sampai ada izin.

---

## 0. Aturan yang mengikat rencana ini

1. **Target versi = 1.0.2 saja** (basis 1.0.1). 1.0.3/1.0.4 batal.
2. **Aturan #1:** capture/HAR = **alat bantu/tiket petunjuk, BUKAN acuan pondasi** — berlaku juga
   untuk login/register/guest mode.
3. **KOREKSI USER 2026-10-20 (Tahap B):** kalau config benar → **token DIBANGKITKAN BARU
   secara lokal** (generator), **bukan dipungut dari sisa jejak panen.** Bukti sahnya:
   token opaque di seluruh rantai klien (bagian 2A).
4. **Tanpa jembatan (bridge):** server lokal harus **MENJAWAB** config, bukan meneruskan ke
   `qsdk.t4game.com`. Pola jembatan tes1 sudah ditinggalkan.
5. Config SDK di **LOCAL, bebas diatur user** (edit → langsung efektif, tanpa rebuild).

---

## 1. Rantai gerbang lengkap — 8 lengkah, semua terbukti

Alur fisik dari tekan tombol sampai masuk game (nama kelas/field persis dari dex):

```
LEG 0  Injeksi lokal
  LaunchActivity.onCreate (instruksi #2) → MiniServer.start(Context)
  → ServerSocket 127.0.0.1:28110 (daemon thread)
  → bootstrapSync: assets/mirror/ → getExternalFilesDir(null)/server/
    (copyDir: file yang SUDAH ADA → DILEWATI, tidak pernah ditimpa)

LEG 1  Config game diambil via HTTP (tiap boot)
  Game GET http://127.0.0.1:28110/bs/db/android/setting_BS_Android.bin?rnd=…
  (com_db_local.bin juga dicoba → 404 → fallback ke setting bin: aman, terbukti jalan)
  → MiniServer.resolve(): files/server/<path> DULU → fallback assets mirror/<path>

LEG 2  Isi bin menentukan alamat gerbang
  setting bin (XOR "DragonBall") mirror 1.0.1:
    url    = http://127.0.0.1:28110/bs/index-native.html   (LOKAL)
    update = http://127.0.0.1:28110/upgrade                 (LOKAL)
    loginServer    = https://login.popoh5.com               (INTERNET)
    loginWebPort   = 510
    loginPort      = 610
    tsLoginServer  = http://118.24.68.91:7003

LEG 3  Parser game membaca key bin
  LaunchActivity.worktopichardtails:
    getString("loginServer")   → field host   (pursetruechild)
    getInt("loginWebPort")     → field port   (pursetruechild)
    (juga membaca loginPort, tsLoginServer, loginKey)

LEG 4  Builder alamat gerbang
  pursetruechild.ironoriginhoblike.seventygenom():
    port > 0 → String.format("%s:%d", host, port)
  → "https://login.popoh5.com:510"
  TIDAK ADA hardcode "popoh5" di seluruh dex — satu-satunya kemunculan
  "loginchecknative" = format "%s/loginchecknative?%sos=Android&sdk=%s".

LEG 5  SDK menyusun sesi (bagian yang dilokalkan 1.0.2)
  onInitFinished(true) → SDK.login()
  → POST <QHinfo>/v1/user/registerVisitor  (QHinfo = satu-satunya penunjuk server SDK)
  → sesi: {authToken, userData{uid, username, token, isGuest}}
  → onLoginFinished(QGUserData, QGUserHolder)

LEG 6  Game menyusun panggilan gerbang
  feedtonight.singersmooth.onLoginFinished:
    HashMap {uid, username, token, usermode = isGuest?"1":"0"}
  → Thread baru → Runnable pursetruechild.nationalcommunitymissing.run()
    GET <base>/loginchecknative?uid=…&username=…&token=…&usermode=…
        &appVersion=…&osVersion=…&sourceVersion=…&model=…&packageName=…
        &brand=…&os=Android&sdk=BS
  → log tag asli "sdk登录验证url" / "sdk登录验证返回"

LEG 7  Respons gerbang
  code == 0 → JSON {code:0, sdk:"BSNative", nickName, loginToken, userId, sign,
                    sdkType:"BS", osType:"Android"} → MASUK GAME
  code != 0 → msg/message → dialog error → retry 3×
```

### Tiket fisik LEG 6 (request asli APK, perangkat itel S665L)

- Guest:  `https://login.popoh5.com:510/loginchecknative?uid=28486093&usermode=1&username=cz90406643&token=@154@171@…&appVersion=1&osVersion=31&sourceVersion=20200418&model=itel+S665L&packageName=com.db.local&brand=Itel&os=Android&sdk=BS`
- Login:  `…uid=28506361&usermode=0&username=vifen44624@juicehour.com&token=@146@174@…`
- Respons live (semua HTTP 200): `{"code":0,"sdk":"BSNative","nickName":"…","loginToken":"…","userId":"…","sign":"…","sdkType":"BS","osType":"Android"}`

→ **Port 510 pada tiket = `loginWebPort` di bin LEG 2. Host = `loginServer` di bin LEG 2.**
Bin dan tiket saling mengunci: alamat gerbang 100% bersumber dari setting bin.

---

## 2. Jangkar bukti fisik per lengkah

| LEG | Bukti fisik | Lokasi / cara cek |
|---|---|---|
| 0 | Diff classes.dex original→1.0.1: SATU method berubah (LaunchActivity.onCreate 234B→244B, injeksi `MiniServer.start`); classes2.dex 6.020 B = MiniServer/ConnRun | repo, worklog `study-101`; diverifikasi ulang dari `/tmp/DB-Local-1.0.1.apk` sha256 `a946ef6c…` |
| 0 | `copyDir`: `exists() → skip` (file lama tidak ditimpa); `resolve`: files/server dulu → assets fallback; `start`: flag `started` anti-ganda | Disassembly classes2.dex 1.0.1 (androguard) — verifikasi ulang |
| 1 | `config_BS.properties` 1.0.1: EntryPoint1/2 = `http://127.0.0.1:28110/bs/db/android` (original: `https://configus.sjmobilegame.com/bs/db/android`); md5 `20f8fedc95 → 3619a8fdf1` | Unzip 1.0.1 vs original — verifikasi ulang |
| 1 | HAR 1.0.1 (sesi 09:26, catatan worklog): GET setting bin → 200, com_db_local.bin → 404 lalu fallback | worklog `study-101` |
| 2 | Mirror bin didekrip XOR "DragonBall" → JSON dengan loginServer/loginWebPort seperti di atas | `assets/mirror/bs/db/android/setting_BS_Android.bin` (692 B) — verifikasi ulang |
| 3 | `worktopichardtails` membaca key `loginServer`, `loginWebPort`, `loginPort`, `tsLoginServer`, `loginKey` | Grep const-string dex game — verifikasi ulang |
| 4 | `seventygenom()` = format `"%s:%d"`; tidak ada hardcode host gerbang | `bukti/riset-login/dump-loginserver-baseurl.txt` + grep dex — verifikasi ulang |
| 5 | Parser sesi & registerVisitor: `psalmicvolleyball.cointhreat.cointhreat(String)`, `QGUserData.generateFromJson`, secret sign `0b2a18e45d7df321` tertanam dex | `bukti/dex-evidence/`, `bukti/riset-login/` |
| 6 | `onLoginFinished` menyusun HashMap persis `{uid, username, token, usermode}`; Runnable format `"%s/loginchecknative?%sos=Android&sdk=%s"` | `bukti/riset-login/dump-onLoginFinished-params.txt` — verifikasi ulang |
| 6 | Tiket URL + panen live 3 mode (guest/register/login) → respons code:0 | `bukti/panen-gerbang4/` (tiket + live + `_indeks-tiket.json`) |
| 7 | Format respons gerbang (10 field, `code:0`) | `live__game__loginchecknative-{guest,register,login}.bin` |

---

## 2A. Basis bukti GENERATOR — kenapa token bisa dibangkitkan baru (bukan sisa jejak)

Koreksi user: *"kalau config benar, artinya kita bisa generate baru token, dan bukan ambil
dari sisa jejak."* Ini terbukti benar dari dex — token adalah **string opaque di seluruh
rantai klien**:

| Titik | Pembuktian (dex original) | Konsekuensi |
|---|---|---|
| Bean sesi SDK `QGUserData.generateFromJson` | `token` & `authToken` hanya `getString` — disimpan apa adanya; TIDAK ada cek format/panjang/signature | nilai token boleh apa pun |
| Parser sesi SDK `psalmicvolleyball.cointhreat.cointhreat(String)` | pre-cek hanya input kosong/`[]`/`{}`; field `authToken` = `getString` | generator bebas menentukan isi |
| Penyusun request gerbang `onLoginFinished` | `HashMap {uid, username, token, usermode}` — langsung dari bean, tanpa transformasi | token generate dikirim apa adanya |
| Pemeriksaan gerbang game (Runnable `nationalcommunitymissing.run`) | `getInt("code") == 0` → LOLOS; selain itu hanya `msg`/`message` utk dialog; parse-gagal → `customreading()` | jawaban gerbang lokal cukup `code:0` |
| Konsumen lanjutan `MainActivity.hardlinerspare` | respons gerbang utuh diteruskan ke JS (payload event `startGame`/`startTsGame` + `loginServer` + `loginKey`) | jawaban lokal dipertahankan BENTUK 10 field live, nilai generate |

Polanya juga terbukti dari panen live: `sign` STABIL per uid (register & login uid sama →
sign sama `912c4ce7…`), `loginToken` BERUBAH per sesi, token antar uid berbeda —
struktur identitas server asli bisa ditiru perilakunya dengan nilai sendiri.
Bukti mentah: `bukti/riset-login/dump-generator-basis.txt`.

**Konsekuensi arah gerbang:** loginserver asli (`login.popoh5.com:510`) memvalidasi token
di sisi SERVER (klien tidak pernah mendekode token) → token generate lokal TIDAK akan
lolos di sana. Karena alamat gerbang adalah CONFIG (setting bin — sudah lokal sejak 1.0.1),
**gerbang diarahkan ke server lokal** → satu-satunya jalur yang konsisten dengan
koreksi user (generate) dan tanpa penerusan. Ini kini JALUR UTAMA (lihat Tahap C).

---

## 3. Baseline 1.0.1 vs pola jembatan (JANGAN TERTUKAR)

- **1.0.1 (baseline, benar):** config GAME lokal (mirror + upgrade). SDK config **masih internet**
  (`assets/QHinfo` = `http://qsdk.t4game.com`, TIDAK disentuh). classes2.dex 1.0.1 tidak punya
  string `v1/` atau `QHinfo` → MiniServer 1.0.1 murni static file server.
  Run perangkat 1.0.1: bootstrap → 7 request lokal → loginchecknative :510 = **200 OK** → game jalan.
- **tes1/jembatan (09:12, `repo-102/log/sdk.har`):** setiap request SDK muncul GANDA
  (lokal + qsdk) = pola bridge. Ini **BUKAN** 1.0.1 dan **BUKAN** pola tujuan.
  Nilai historisnya: menunjukkan isi respons asli & format request asli per perangkat.

---

## 4. Delta minimal 1.0.2 (apa yang ditambah, apa yang TIDAK disentuh)

### Ditambah (2 item saja di atas basis 1.0.1)
1. **`assets/QHinfo`** 22 B → `http://127.0.0.1:28110` (22→22 B).
   Satu-satunya pembaca terbukti: `QuickGameManager.initThirdDataSDK`
   → `bindcommunist.cointhreat.seventygenom[0]` (satu-satunya penulis base URL SDK).
   Tidak ada hardcode `qsdk` lain di dex/.so/asset (teraudit).
2. **`classes2.dex` v6** — MiniServer mampu **MENJAWAB** (bukan meneruskan):
   - `v1/system/init`, `v1/system/getNotice`, `v1/system/getAgreement`, `v1/user/ckRegVistor`
     → materi FINAL: `config-local/system/…`, `config-local/user/ckRegVistor.json`
   - `v1/user/registerVisitor` → materi perangkat (Tahap B)
   - cadangan materi live: `auth/getUserInfo`, `user/registerUser`, `user/loginByName`
   - drain POST body (kelemahan versi lama sudah difix di test10)

### TIDAK disentuh (bergantung pada 1.0.1 apa adanya)
- Injeksi LaunchActivity, mirror 23 file, `upgrade/all.zip`, `config_BS.properties`,
  dan **setting bin** (alamat gerbang tetap `login.popoh5.com:510` — tahap C memakai
  loginserver asli dulu, lihat Plan B bila perlu).

---

## 5. Rencana 3 tahap + kriteria lulus

### Tahap A — Config SDK lokal (tanpa sesi)
- MiniServer menjawab 4 endpoint config dari `config-local/`.
- **Kriteria lulus:** boot sampai layar login SDK tanpa dialog error; semua
  `v1/system/*` + `ckRegVistor` mendarat di 127.0.0.1:28110 dan 200 OK; **nol** trafik
  SDK keluar ke qsdk. (getNotice & getAgreement sudah terverifikasi byte-exact
  via HAR perangkat; init dkk. material live 2026-10-20 di `bukti/panen-live/`.)

### Tahap B — Sesi SDK lokal = GENERATOR (koreksi user — tanpa tiket panen)
- `registerVisitor.json` kini **TEMPLATE GENERATOR**: placeholder `{{UID}}`/`{{USERNAME}}`/
  `{{TOKEN}}`/`{{AUTH_TOKEN}}` diisi MiniServer saat request datang — **token dibangkitkan
  BARU**, bukan dipungut dari sisa jejak panen.
- Dasar sahnya (bukti dex, bagian 2A): parser SDK hanya `getString` tanpa validasi isi →
  nilai token bebas; panen live hanya jadi acuan FORMAT + pola perilaku (uid stabil per
  perangkat, token baru per sesi, sign stabil per uid).
- Aturan generate (default, semua diatur user via config — lihat `config-local/README.md`):
  `UID` persisten per perangkat (bisa dikunci user); `TOKEN`/`AUTH_TOKEN` baru per sesi;
  `SIGN` = `md5(uid + salt)` mengikuti pola live; `isGuest` sesuai mode.
- **STATUS 1.0.3 (TERBANGGAL 2026-10-20):** Tahap B LENGKAP dengan autoLogin —
  bug 404 `v1/user/autoLogin` dari HAR perangkat (BUILD 1.0.2) sudah diperbaiki:
  validasi authToken terbitan (Opsi A setia server asli) + persist sesi antar boot.
  Lihat `BUILD-1.0.3.md`.
- **Kriteria lulus:** `onLoginFinished` terpanggil; HashMap game terisi
  `{uid, username, token, usermode}` HASIL GENERATOR; `sdk登录验证url` mencetak alamat
  gerbang dengan token generate yang sama.

### Tahap C — Uji gerbang LOKAL (bukti akhir)
- Alamat gerbang = config (setting bin): `loginServer` → `http://127.0.0.1:28110` +
  `loginWebPort` → `28110` — **MURNI edit config** (dua pilihan eksekusi, keputusan user):
  (a) edit file external `files/server/bs/db/android/setting_BS_Android.bin`
  (dekrip XOR → ubah 2 nilai → enkrip balik; `copyDir` tidak menimpa file ada →
  edit bertahan antar boot); atau (b) mirror bin di APK 1.0.2 dibuat lokal sejak awal.
- MiniServer menjawab `/loginchecknative` dengan template generator (bentuk 10 field live,
  `loginToken`/`sign` baru, `userId` = uid generator) → `getInt("code") == 0` → **masuk game**.
- **Kriteria lulus:** masuk game tanpa dialog error; respons 200 + `code:0` terekam HAR;
  game lanjut memuat dunia game (JS menerima payload startGame).
- Log diagnose kalau gagal: tag `sdk登录验证url` / `sdk登录验证返回` (retry 3×).

### Catatan jalur lama (DIBATALKAN oleh koreksi)
- Jalur lama "gerbang tetap live dengan token panen" TIDAK dipakai lagi: bertentangan
  dengan koreksi user (bukan sisa jejak) dan token generate memang tidak akan lolos
  validasi server-side loginserver asli. Panen live tetap bernilai sebagai acuan format
  & referensi perilaku server (arsip di `bukti/panen-gerbang4/`).

---

## 6. Risiko & mitigasi (semua berjangkar bukti)

| # | Risiko | Bukti/mitigasi |
|---|---|---|
| R1' | JS dunia game menolak `loginToken`/`sign` hasil generate | Respons gerbang DITERUSKAN utuh ke JS (bukti: `hardlinerspare` → payload startGame). Mitigasi: bentuk field persis 10 field live + pola `sign` stabil-per-uid + `loginToken` baru-per-sesi (meniru perilaku server asli). Validasi internal JS (main.min.js 4,9 MB) BELUM dibedah → variabel di luar pintu gerbang; bila JS menolak → task lanjutan: bedah main.min.js. |
| R2 | SDK memanggil endpoint lain (getUserInfo/registerUser/loginByName/onNewToken/dmsg/getServerAgreement) | getUserInfo/registerUser/loginByName sudah ada materi live di `config-local/` (bisa di-generator-kan dengan pola sama bila user mau — keputusan lanjutan); onNewToken/dmsg/getServerAgreement tinggal 1 panen bila ternyata dipanggil (pola wrapper sama). |
| R3 | `com_db_local.bin` 404 dianggap error | Terbukti aman: 1.0.1 jalan normal dengan 404 itu (fallback ke setting bin). Jangan diperbaiki. |
| R4 | bootstrapSync menimpa editan user (termasuk ubahan gerbang lokal) | Terbukti tidak: `copyDir` melewati file yang sudah ada (`exists → skip`) — verifikasi ulang dex. |
| R5 | Gate gagal → pemain melihat dialog error | Perilaku asli APK (retry 3×); log tag tersedia untuk diagnose Tahap C. |
| R6 | Salah acuan baseline | Dokumen ini menegaskan: HAR 09:12 = build jembatan (BUKAN 1.0.1); HAR 09:26 = 1.0.1 murni (catatan worklog). |
| R7 | Konsistensi tipe field | `isGuest` angka di respons asli (getString mengkoersi); `code` harus ANGKA (`getInt`); ikuti bentuk template — sudah diatur README. |

---

## 7. Disiplin — yang TIDAK dilakukan

- Tidak menyentuh isi config server qsdk; tidak menjadikan capture sebagai isi config.
- Tidak ada bridge/forward dari MiniServer ke internet.
- Tidak ada replay request keluar; tidak ada penerusan kredensial.
- Tidak memakai sisa jejak panen sebagai identitas — sesi dibangkitkan baru (koreksi user).
- Implementasi (build 1.0.2) menunggu **green light** — rencana ini hanya peta.

---

## 8. Indeks berkas bukti

```
docs/sdk/config-local/                  ← THE CONFIG (config statis + template generator)
docs/sdk/bukti/panen-live/              ← 8 respons asli server hidup (boot)
docs/sdk/bukti/panen-gerbang4/          ← acuan format & perilaku server (tiket + respons live 3 mode)
docs/sdk/bukti/riset-login/             ← dump dex: onLoginFinished, parser endpoint, base URL,
                                          verifikasi-ulang-101, dump-generator-basis (basis generator)
docs/sdk/bukti/dex-evidence/            ← dump QHinfo/sign/bind/apply/model
docs/sdk/bukti/device-har/              ← bukti leg A=leg B (verbatim) + tiket HAR
docs/sdk/CONFIG-SDK-ASLI.md             ← dokumen lengkap (config + gerbang + field)
docs/sdk/RENCANA-GERBANG-LOGIN.md       ← dokumen ini
```

**Kesimpulan (setelah koreksi):** dari 1.0.1, jarak menuju gerbang = 2 item delta APK
(QHinfo + classes2.dex v6) + 1 edit config gerbang (setting bin) — sesi DIBANGKITKAN BARU
oleh generator lokal (sah: token opaque di seluruh rantai klien), 3 tahap uji, dan 1
variabel jujur di luar pintu (konsumsi JS dunia game — R1'). Tidak ada langkah yang
mengandalkan asumsi; tidak ada identitas dari sisa jejak.

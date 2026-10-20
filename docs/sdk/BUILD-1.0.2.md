# BUILD 1.0.2 — SDK lokal sampai gerbang loginserver (BUILT, SIGNED, TESTED 22/22)

> Status: **SELESAI DIBANGUN** — 2026-10-20.
> APK: `DB-Local-1.0.2.apk` (125.272.205 B, sha256 `f7f754ae…46aa`, v1+v2+v3,
> sertifikat IDENTIK 1.0.1 → bisa **install-over tanpa uninstall**).
> Prinsip dijalankan penuh: **tidak ada code tanpa bukti** — setiap bentuk respons
> di bawah punya jangkar ke materi server asli (`bukti/panen-live`, `bukti/panen-gerbang4`)
> dan setiap perilaku uji lulus di JVM (22 PASS / 0 FAIL, `build-1.0.2/TestServer2.java`).

---

## 1. Delta APK vs 1.0.1 (hanya ini — sisanya byte-identik)

| Entri APK | Perubahan | Isi |
|---|---|---|
| `classes2.dex` | 6.020 → 17.056 B | MiniServer 1.0.2 + **DbGen** (generator) + **DbJson** (mini JSON) |
| `assets/QHinfo` | 22 B | `http://127.0.0.1:28110` (penunjuk server SDK, sama seperti tes1) |
| `assets/mirror/bs/db/android/setting_BS_Android.bin` | 692 → 686 B | **Gerbang lokal**: `loginServer: http://127.0.0.1` + `loginWebPort: 28110` |
| `assets/mirror/VERSION` | BARU | `1.0.2` — penanda re-sync mirror (lihat §4) |
| `assets/mirror/generator.json` | BARU | Aturan generator — bisa diedit user (lihat §3) |
| `assets/mirror/qsdk/**` | BARU (12 file) | Config boot statik (7, byte-exact live) + template generator (5) |

`classes.dex` **TIDAK disentuh** — injeksi `MiniServer.start(Context)` di
`LaunchActivity.onCreate` sudah ada sejak 1.0.1. `AndroidManifest.xml`,
`resources.arsc`, dan 21 file mirror lain **byte-identik 1.0.1**.

**Tanpa identitas panen** — terverifikasi otomatis saat packaging: tidak ada satu
byte pun `28486093` / `28506361` / `cz90406643` / `vifen44624` / `KdddFRi` di
seluruh entri baru (koreksi user: generate, bukan sisa jejak).

## 2. Rute MiniServer 1.0.2 (semua di 127.0.0.1:28110)

| Request (dari SDK/game) | Dilayani oleh | Source config (file di mirror) |
|---|---|---|
| `GET /bs/db/android/setting_BS_Android.bin` | statik | `bs/db/android/setting_BS_Android.bin` |
| `POST /v1/system/init` | statik | `qsdk/v1/system/init` (767 B live) |
| `POST /v1/system/getNotice` | statik | `qsdk/v1/system/getNotice` (55 B live) |
| `POST /v1/system/getAgreement` | statik | `qsdk/v1/system/getAgreement` (188 B live) |
| `POST /v1/system/onNewToken` | statik | `qsdk/v1/system/onNewToken` (55 B live, asuransi R2) |
| `POST /v1/system/dmsg` | statik | `qsdk/v1/system/dmsg` (108 B live, asuransi R2) |
| `POST /v1/user/ckRegVistor` | statik | `qsdk/v1/user/ckRegVistor` (66 B live) |
| `POST /v1/user/getServerAgreement` | statik | `qsdk/v1/user/getServerAgreement` (104 B live) |
| `POST /v1/user/registerVisitor` | **GENERATOR** | template `qsdk/v1/user/registerVisitor.template` |
| `POST /v1/user/registerUser` | **GENERATOR** (baca envelope → echo username) | template `qsdk/v1/user/registerUser.template` |
| `POST /v1/user/loginByName` | **GENERATOR** (baca envelope → echo username) | template `qsdk/v1/user/loginByName.template` |
| `POST /v1/auth/getUserInfo` | **GENERATOR** | template `qsdk/v1/auth/getUserInfo.template` (nickName = username) |
| `GET /loginchecknative?uid=…&username=…&usermode=…&token=…` | **GERBANG LOKAL** | template `qsdk/game/loginchecknative.template` |

**KODE TIDAK MENULIS BENTUK RESPONS** — semua JSON berasal dari file template di
mirror; kode hanya mengisi placeholder. Edit file → langsung efektif (external files
menang atas assets; hapus file external → kembali ke bawaan APK).

### Aturan generator (DbGen — semua dari bukti, `bukti/riset-login/dump-generator-basis.txt`)

| Nilai | Aturan | Pola acuan (server asli) |
|---|---|---|
| `uid` | seed sekali (8 digit acak), **persisten per perangkat** di `generator.json` | 28486093 (8 digit) |
| `username` | default `Guest<uid>`; bisa dikunci user via generator.json | cz90406643 |
| `authToken` | baru per boot (sesi SDK) | — |
| `token` | baru per panggilan endpoint sesi | token berubah per sesi |
| `loginToken` | baru per panggilan gerbang | loginToken baru tiap panggilan |
| `sign` | `md5(uid + salt)` — **stabil per uid** | register & login uid sama → sign sama |

Placeholder template: `{{UID}}`, `{{USERNAME}}`, `{{TOKEN}}`, `{{AUTH_TOKEN}}`,
`{{LOGIN_TOKEN}}`, `{{SIGN}}`.

## 3. generator.json (external files — edit user)

```json
{
  "uid": "",            // kosong = auto-seed sekali lalu terkunci; isi manual = dikunci
  "username": "",       // kosong = "Guest<uid>"; isi manual = dikunci
  "salt": "dblocal-102" // garam md5 untuk sign (ganti = semua sign ikut berubah)
}
```

## 4. Re-sync versi (penutup lubang upgrade)

Masalah yang terlihat dari mekanisme 1.0.1: `copyDir` melewati file yang sudah ada →
di perangkat bekas tes1/1.0.1, config LAMA (panenan RAW, bin gerbang popoh5) akan
bertahan dan 1.0.2 tidak pernah aktif. Solusi di 1.0.2:

- Boot: baca `assets/mirror/VERSION` vs `<external>/server/VERSION`.
- **Sama** → perilaku 1.0.1 (edit user bertahan antar boot).
- **Beda/kosong** → `server/` di-sync penuh dari APK (file lama dibuang), kecuali
  `generator.json` (identitas lokal user) yang dijaga dan ditulis kembali.
- Log: `re-sync (kosong) -> 1.0.2: 37 file`.

## 5. Alur yang diharapkan di perangkat (semua LOKAL, tanpa internet)

```
boot → setting bin (lokal) ×2 → com_db_local.bin 404 (normal, biarkan)
     → upgrade versions → init → getNotice            ← config boot (Tahap A) ✅
     → [SDK.login guest] registerVisitor              ← sesi generator (Tahap B) ✅
     → game GET http://127.0.0.1:28110/loginchecknative?uid=… ← gerbang lokal (Tahap C) ✅
     → {code:0,…} → getInt("code")==0 → masuk game → payload startGame ke JS
     → getUserInfo (pasca-login)                      ← nickName generator
```

Tag logcat: `MiniServer: 200 …` / `200 gate loginchecknative` / `200 gen …`;
diagnosis gerbang: `sdk登录验证url` / `sdk登录验证返回` (retry 3× perilaku asli).

**Kriteria lulus**: masuk game tanpa dialog error; HAR/logcat menunjukkan seluruh
alur di atas mengarah ke 127.0.0.1:28110; tidak ada request ke qsdk.t4game.com /
popoh5 / 118.24.68.91 pada alur guest.

## 6. Jujur: apa yang belum (di luar pintu gerbang)

- **R1'** — dunia JS (`main.min.js` 4,9 MB) menerima payload startGame utuh.
  Bila JS ternyata memvalidasi `loginToken`/`sign` → task lanjutan: bedah main.min.js.
- `tsLoginServer` / `tsUrl` masih menunjuk internet (di luar tahap ini — user:
  "sampai titik masuk loginserver saja").
- registerUser/loginByName disajikan generator-konsisten (uid generator + username
  dari envelope); password tidak diverifikasi server-lokal (sifat lokal).

## 7. Provenance & cara rebuild

```
build-1.0.2/src/{MiniServer,DbGen,DbJson,ConnRun}.java   ← kode yang masuk APK
build-1.0.2/mirror/                                       ← staging config (qsdk/*, VERSION, generator.json)
build-1.0.2/staging_mirror.py                             ← bangun staging dari bukti repo
build-1.0.2/Compile102.java                               ← javac via ToolProvider (sandbox)
build-1.0.2/TestServer2.java                              ← uji JVM 22 kasus (22 PASS / 0 FAIL)
build-1.0.2/repackage102.py                               ← rakit APK (verifikasi otomatis anti-panen)
```

Urutan: `staging_mirror.py` → compile `src/` → `D8 --lib android.jar` →
`repackage102.py` (basis 1.0.1) → `uber-apk-signer` (debug key sama dengan 1.0.1).
Hasil: `DB-Local-1.0.2.apk` — sha256 `f7f754aebb9e48951595161864ec4d420a2a96771c4a777b22dcd968b20046aa`.

Uji JVM yang diluluskan (ringkas): setting bin lokal byte-exact · 7 statik byte-exact ·
registerVisitor generator (uid 8-digit, persisten) · gate echo + code:0 + 8 field live ·
sign stabil / loginToken berganti · registerUser+loginByName envelope ASLI (echo
username, tanpa kebocoran panen) · getUserInfo nickName generator · 404 + tolak `..` ·
generator.json persist · re-sync versi memulihkan config + uid bertahan + sign stabil
lintas boot.

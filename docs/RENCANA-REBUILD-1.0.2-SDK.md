# RENCANA REBUILD 1.0.2 (tes1) — Bagian SDK

> Tanggal: 2026-10-19 · Basis: hasil baca ulang release 1.0.1 (README §2/§4/§9 + server/VERIFIKASI.md)
> + riset fisik SDK (docs/RISET-FISIK-SDK.md) · Belum ada build — ini rencana eksekusi.

---

## 1. FONDASI 1.0.1 YANG DIREPLIKASI PERSIS (terbukti di perangkat)

Release 1.0.1 = **redirection murni**: satu titik kontrol (`config_BS.properties` → EntryPoint
base64 `http://127.0.0.1:28110/bs/db/android`) + MiniServer embedded + 23 file mirror 1:1.
Marker perangkat yang terbukti hidup:

```
客户端配置文件服务器地址: http://127.0.0.1:28110/bs/db/android   (Fase 1-2 OK)
CurrentMode: 1                                                  (Fase 3 OK)
开始处理bin文件 → bin文件解压完成                                  (Fase 4 OK)
开始检查更新 → 最新资源文件，无需更新                               (Fase 5 OK)
→ layar login → login SDK sukses → engine Egret jalan
```

Patch 1.0.1 yang direplikasi (dari original):

| File | Patch |
|---|---|
| `assets/config_BS.properties` | 4 EntryPoint → base64 `http://127.0.0.1:28110/bs/db/android` (CRLF→LF) |
| `classes.dex` | `LaunchActivity.onCreate` instruksi #2 + `invoke-static MiniServer;->start(Context)` dalam try/catch senyap (234B→244B) |
| `classes2.dex` (baru) | `com.db.local.MiniServer` + `ConnRun` — ServerSocket 127.0.0.1:28110, bootstrapSync mirror→files (deadline 8 dtk, `.tmp`+rename), resolve external→assets, mime map, reject `..`, `Connection: close`, `setSoTimeout(8000)` |
| `assets/mirror/{bs,upgrade}` | 23 file 1:1 (setting bin versi redirect: `url`+`update` → 127.0.0.1:28110, `loginServer` TETAP `https://login.popoh5.com`, testMode 1) |
| META-INF | re-sign (uber-apk-signer, cert debug embedded deterministik → `adb install -r` di atas 1.0.1 lama) |

Aturan emas diwarisi penuh: mirror 1:1 tanpa regenerasi nama (peta `vvcc.bin`), byte-preserving
kecuali penunjuk alamat, `testMode` selalu 1, version files = nilai APK (110/11220),
manifest & native (.so) tidak disentuh.

> **Status material**: binary 1.0.1 hilang bersama /tmp sandbox → rebuild dimulai dari
> `DB-Local.apk` original (sha256 `3650cbe5…`, terverifikasi LFS), lapis 1.0.1 + lapis SDK
> dikerjakan sekaligus, **SATU putaran uji perangkat** memverifikasi dua-duanya.

## 2. PEMBAGIAN MIRROR vs PROXY (aturan disepakati dengan user)

**Uji lolos mirror** = respons terbukti byte-identik antar pemanggil (IP/device beda) DAN
antar waktu → baru boleh jadi file lokal. Kalau tidak lolos → PROXY live ke server asli.
Isi capture HAR TIDAK PERNAH menjadi config.

| Endpoint | Putusan | Bukti |
|---|---|---|
| `v1/system/getNotice` | **MIRROR** (55 B) | byte-identik 2 hari berbeda, 0 field pemanggil |
| `v1/system/getAgreement` | **MIRROR** (188 B) | 9 field perjanjian kosong, 0 field pemanggil |
| `v1/system/init` | **PROXY** | mengandung `requestIp`+`countryCode` (gema IP pemanggil); arsip 766 B hanya fallback fase akhir (panen ulang dari HAR perangkat user, bukan sandbox) |
| `v1/user/registerVisitor` + semua endpoint /v1/ lain (30) | **PROXY** | sesi/state-bound (token per perangkat; registerUser replay → id 40008) |

Konsekuensi desain: **nol endpoint SDK bisa rusak di fase ini** — yang statis dilayani lokal,
sisanya diteruskan utuh ke `qsdk.t4game.com` (token asli → `loginchecknative` asli →
`loginToken` asli → socket :610 asli).

## 3. PATCH BARU LAPIS SDK (di atas fondasi 1.0.1)

### 3.1 `assets/QHinfo` — penunjuk alamat SDK (satu-satunya file asli yang isinya berubah)
- Asli (22 B): `http://qsdk.t4game.com` → tes1 (22 B): `http://127.0.0.1:28110`
- Pola identik redirect EntryPoint 1.0.1. Semua request SDK (`/v1/...`) kini ke mini server.

### 3.2 `assets/mirror/qsdk/v1/system/{getNotice,getAgreement}` — 2 file config asli live
- Byte-exact panenan live 2026-10-19 (docs/panen-live-2026-10-19/), CT `text/json`.
- Endpoint = nama file, nol route hardcode.

### 3.3 `classes2.dex` v3 — MiniServer diperkaya (perilaku 1.0.1 dipertahankan 100%)
1. **Drain body POST** — baca `Content-Length` byte (loop bounded) + dukung `Transfer-Encoding: chunked` (wajib: SDK kirim POST beruntun).
2. **Rute `v1/`** — resolve `qsdk/<path>` (external files → assets, urutan sama 1.0.1); file ada → serve byte-exact CT `text/json`.
3. **PROXY pass-through** — file tidak ada → buka `Socket("qsdk.t4game.com", 80)`:
   - tulis ulang request line & header: `Host: qsdk.t4game.com`, `Connection: close`;
     body diteruskan utuh (verbatim, tanpa edit `sv`/`data`/`sign`);
   - respons upstream diteruskan utuh ke klien (status + header + body; close-delimited);
   - connect timeout 5 dtk, read timeout 8 dtk (senada `setSoTimeout` 1.0.1);
   - gagal → 502 + log (perilaku = server asli tak terjangkau, jujur);
   - log tag `MiniServer`: `PROXY <status> <path>`.
4. **Close eksplisit** setelah respond (mencegah tumpukan FD).
5. TIDAK berubah: port 28110, bootstrapSync, urutan resolve, `parsePath` (buang query, reject `..`), `Connection: close`, mime map 1.0.1, log tag.

### 3.4 Mirror bs/upgrade — sama resep 1.0.1 (23 file) + 3 tambahan test7 yang aman
- 23 file: setting bin (redirect `url`+`update`), base.version `110`, resource.version `11220`
  (nilai APK → nol unduhan), size.json, upgrade.json, all.zip, index-native.html,
  manifest.json, 15 JS (union all.zip 11 + paket bawaan 4).
- Tambahan (harvest 1:1 CDN live, murni menambah jaring pengaman): `serversetting.json`,
  `clientversion.json`, `sound_click.mp3`.
- 5 file yang 404 di server asli (Telegram_png, dst) → dibiarkan 404 = perilaku asli (terbukti aman).

### 3.5 Re-sign + zipalign
- uber-apk-signer 1.3.0 (v1+v2+v3, keystore debug embedded deterministik) → cert sama dengan
  1.0.1 terpasang → `adb install -r` tanpa uninstall. Manifest tidak disentuh.

## 4. URUTAN KERJA BUILD

| Step | Kerja | Keluaran |
|---|---|---|
| B1 | Unduh toolchain (d8/r8 Maven, uber-apk-signer GitHub, stub compile-only) | toolchain siap |
| B2 | Lapis 1.0.1: patch config_BS.properties + classes.dex hook + mirror bs/upgrade | APK basis |
| B3 | Lapis SDK: QHinfo + mirror qsdk 2 file + classes2.dex v3 (drain+route+proxy) | APK lengkap |
| B4 | Re-sign + zipalign | `DB-Local-tes1.apk` |
| B5 | Uji statis + JVM | bukti otomatis |
| B6 | Serah ke perangkat (user) | HAR + logcat |

## 5. GERBANG VERIFIKASI

### Statis + JVM (sandbox, sebelum ke perangkat)
- [ ] unzip diff vs original = PERSIS {config_BS.properties, QHinfo, classes.dex, classes2.dex,
      mirror/** (28 file), META-INF} — tidak ada entri lain yang berubah
- [ ] decompile classes2.dex v3 → drain, route qsdk, mime text/json, PROXY terverifikasi
- [ ] QHinfo = `http://127.0.0.1:28110`; EntryPoint base64 terbaca ulang benar
- [ ] Uji JVM kelas persis dari APK:
      a) getNotice & getAgreement byte-exact + CT `text/json`;
      b) **PROXY REAL dari JVM ke qsdk.t4game.com** — init & registerVisitor diteruskan,
         respons asli kembali (sandbox punya internet → uji end-to-end sungguhan);
      c) query string OK, `..` ditolak, drain POST beruntun, jalur lama bs/upgrade utuh.

### Perangkat (user, satu putaran untuk dua lapis)
- [ ] 5 marker logcat lapis 1.0.1 (daftar §1) dari `127.0.0.1:28110`
- [ ] logcat MiniServer: `200 v1/system/getNotice` (mirror), `200 v1/system/getAgreement`
      (mirror, bila dipanggil), `PROXY 200 v1/system/init`, `PROXY 200 v1/user/registerVisitor`
- [ ] HAR perangkat: request /v1/ menuju 127.0.0.1:28110; koneksi qsdk.t4game.com yang
      tersisa berasal dari proxy mini server (bukan SDK langsung)
- [ ] loginchecknative 200 → login sukses → masuk game
- [ ] Semua hijau → beri label rilis **1.0.2**

## 6. RISIKO & MITIGASI (fokus lapis SDK)

| Risiko | Mitigasi |
|---|---|
| Proxy macet/koneksi menggantung | connect+read timeout eksplisit, `Connection: close` dua sisi, close eksplisit |
| Respons upstream chunked/tebal | forward byte-stream close-delimited (klien HTTP menangani); drain request wajib sebelum proxy |
| qsdk mengubah/putus | 502 → SDK mengalami "server tak terjangkau" = perilaku asli saat server mati; pantau logcat |
| Anti-tamper (libgavesec) | pola patch sama dengan 1.0.1 yang lolos (1 asset teks + dex tambahan); tidak strip SDK |
| init disajikan proxy mengandung IP perangkat | justru kemurnian yang diminta — perangkat menerima persis jawaban server asli utk dirinya |
| cert bentrok saat install | uber embedded debug deterministik = cert 1.0.1 → `install -r` |

## 7. YANG TIDAK DISENTUH

AndroidManifest (binary, versionName tetap) · `libegret.so`/`libgavesec.so`/`runtime-dex.jar` ·
`vvcc.bin`/`vvcc_tc.bin` + seluruh `assets/game/` · `testMode` (selalu 1) · `loginServer`
(tetap `https://login.popoh5.com` — lapis login masih hidup, dipakai apa adanya) ·
`config.properties` (channel default, tak dibaca channel BS) · `supplierconfig.json` ·
semua isi respons mirror (byte-exact panenan — nol edit).

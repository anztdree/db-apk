# Rencana 1.0.2 — SDK Lokal (QuickGame SDK, qsdk.t4game.com)

> **Arah user (2026-10-19)**: *"ada SDK sebelum menyentuh loginserver.. artinya lebih baik
> 100% fokus pada SDK ini"* → **1.0.2 = HANYA lapisan SDK.** `loginchecknative` (:510) dan
> login server (:610) **tetap perilaku asli** (online, tak disentuh) → dipindah ke **1.0.3**.
>
> **Posisi awal**: 1.0.1 stabil (rilis default) = boot → update → layar login → login SDK →
> engine jalan, dengan CDN 100% lokal. Urutan bukti HAR: setelah file game selesai, request
> pertama yang keluar justru **SDK** (`qsdk/system/init`), baru `:510`.

---

## 0. Disiplin (sama dengan 1.0.1 — tidak berubah)

1. **Redirection murni**: hanya ganti ALAMAT via mekanisme aplikasi sendiri; klien & pola client-server 100% utuh.
2. **Bukti dulu, implementasi kemudian**: semua respons yang disimulasikan HARUS panenan asli, bukan karangan.
3. **Satu fase = satu build test** (test10, test11, …), lulus kriteria verifikasi baru lanjut.
4. `check_invoke_regs.py` wajib lulus sebelum SETIAP build (Pelajaran 5).

---

## 1. Bukti baru (bedah smali SDK `com/quickgame/android/sdk/`, 2026-10-19)

### 1a. Alamat SDK = file asset `QHinfo` — BUKAN kode, BUKAN setting bin

- `assets/QHinfo` = **22 B plaintext**: `http://qsdk.t4game.com` (satu-satunya string t4game
  di seluruh APK — diverifikasi scan semua entry zip).
- Rantai pembaca (smali original):
  ```
  QuickGameManager.initThirdDataSDK():
    bindcommunist/singersmooth.cointhreat(ctx, "QHinfo")   // baca asset → String
    bindcommunist/cointhreat.seventygenom[0] = <hasil>      // array 1 elemen (clinit: {""})
  ```
- `bindcommunist/cointhreat.cointhreat(path, params)` menyusun URL dengan
  `String.format("%s%s", ACCESS_URLS[idx], path)` → **base + path digabung langsung**
  (base tanpa trailing slash; path selalu diawali `/v1/`).

> **Implikasi**: mengganti isi `QHinfo` = kelas perubahan yang SAMA dengan preseden
> `config_BS.properties` (1.0.1) — **ganti nilai asset config, nol baris kode**.
> Nilai redirect: `http://127.0.0.1:28110/qsdk` (**27 B** — koreksi hitung; semua request
> SDK menjadi `/qsdk/v1/…`, pemisahan path bersih dari mirror CDN).

### 1b. Kontrak HTTP SDK (dari `bindcommunist/cointhreat.cointhreat()`)

| Aspek | Bukti smali | Konsekuensi untuk MiniServer |
|---|---|---|
| Stack | `java.net.URL.openConnection()` → `HttpURLConnection` (UA Dalvik — cocok HAR) | Cleartext `http://127.0.0.1` diizinkan (preseden test9, stack sama) |
| Metode | POST, `setUseCaches(false)` | Route POST |
| Header | `Content-Type: application/x-www-form-urlencoded`, `Content-Length: <len>` | MiniServer **wajib drain body** per Content-Length sebelum menjawab (Pelajaran 6) |
| Body | `sv=v2&data=<urlencoded base64>` (bukti `req_*.txt`) | Tidak perlu di-parse — cukup dibuang |
| Sukses | HTTP 200 → `singersmooth.nationalcommunitymissing(InputStream)` = baca semua byte → `org.json.JSONObject` | 200 + body JSON; Content-Type respons diabaikan |
| Gagal | 3 percobaan → SDK menyusun sendiri `{"result":false,"error":"Connection failed…"}` | 404 route tak dikenal = perilaku server mati (aman, terdokumentasi) |
| Cabang khusus | base berisi `summertimegame/topgame/springgame` → path Google Play verify | Base kita tidak mengandung itu → jalur standar |

### 1c. Permukaan API SDK: 33 path di kode; 4 yang pernah terpanggil NYATA

Dari HAR test9 + h3 (asli), hanya 4 endpoint SDK yang benar-benar dipanggil:

| # | Path | Kapan | Panenan RAW |
|---|---|---|---|
| 1 | `POST /v1/system/init` | tiap peluncuran (setelah file game siap) | 764 B, sha256 `a6ab4470…` |
| 2 | `POST /v1/system/getNotice` | tiap peluncuran | 55 B, sha256 `424866f4…` |
| 3 | `POST /v1/user/registerVisitor` | peluncuran (akun tamu) — request = fingerprint device SAJA (tanpa kredensial tersimpan, bukti dekode `req_registerVisitor.txt`) → auth guest bersifat stateless, respons replay valid tiap peluncuran | 2.345 B, sha256 `95e8d974…` |
| 4 | `POST /v1/auth/getUserInfo` | situasional (terpanggil di sesi h3) | 280 B, sha256 `dfc72556…` |
| 5 | `POST /v1/user/registerUser` | registrasi akun baru via UI SDK (capture `sdk_register.har` dari user, 2026-10-19) | 2.397 B, sha256 `c4105fb9…` |

- **Stabilitas replay terbukti**: respons `init` (764 B) dan `getNotice` (55 B) **byte-identik antar sesi**
  (test9 vs sdk_register) → paling aman direplay. `registerVisitor`/`registerUser` berisi data dinamis
  (authToken, time) — diterima apa adanya oleh klien, diteruskan ke `:510` yang tetap online (terbukti
  server asli menerima KEDUA akun: uid 28486093 guest + uid 28505968 registered, `code:0`).
- Panenan pelengkap: `loginchecknative_usermode0.json` (209 B, `code:0`) — bukti `:510` menerima
  akun hasil registerUser (cadangan bukti untuk 1.0.3).

- 29 path lain (bind/pay/lupa password/FB-event/`postGooglePlayVerify`, dst.) = dipicu aksi UI
  khusus → **di luar lingkup test10** (404 = perilaku server mati; SDK punya pesan error bawaan).
- ⚠️ **Celah bukti tersisa (sempit & pulihable)**: `/v1/user/autoLogin` (`QuickGameSdkService`) dan
  `/v1/user/ckRegVistor` — baru relevan untuk pemulihan sesi **akun terdaftar** (username/password
  tersimpan). Auth guest (jalur utama) terbukti stateless via registerVisitor. Keputusan (arah user:
  capture sudah melewati SDK): **R0 digabung ke uji test10** — uji 2 peluncuran dgn capture; bila
  `autoLogin` muncul & 404, panen respons aslinya sesi singkat via 1.0.1/original (server masih hidup)
  → route ditambah di test11. Tidak ada bukti yang hilang permanen.

### 1d. Panenan RAW 1:1 (`docs/panenan-1.0.2/`)

Semua file = **byte persis respons asli** (koreksi: versi lama pretty-printed — kini diganti RAW).
Lampiran bukti kontrak request: `req_init.txt`, `req_getNotice.txt`, `req_registerVisitor.txt`,
`req_getUserInfo.txt` (format `sv=v2&data=…`), `req_loginchecknative_url.txt` (query penuh :510).

---

## 2. Fase R0 — digabung ke uji test10 (keputusan 2026-10-19)

Capture user (`sdk_register.har`, sampai `:510` `code:0`) melengkapi bukti alur SDK: guest
(registerVisitor) + registrasi (registerUser). Jalur startup terbukti lengkap; celah tinggal
`autoLogin`/`ckRegVistor` (pemulihan sesi akun terdaftar). Alih-alih capture terpisah:
- Uji test10 = **2 peluncuran** (pertama + kedua) dengan capture HAR + logcat.
- Bila peluncuran kedua memanggil `autoLogin` → HAR menunjukkan 404 kita → respons ASLI dipanen
  lewat sesi singkat 1.0.1/original (masih online) → route ditambah di test11.
- Bila tidak dipanggil → 5 route terbukti cukup, selesai.

---

## 3. Fase P — Prototipe test10

### P1 — APK: satu file data berubah

| File | Isi 1.0.1 | Isi 1.0.2 |
|---|---|---|
| `assets/QHinfo` (22 B) | `http://qsdk.t4game.com` | `http://127.0.0.1:28110/qsdk` (27 B) |

Yang lain TIDAK disentuh (config_BS.properties, setting bin, smali kode — semuanya persis 1.0.1).

### P2 — GANTI DESAIN (keputusan 2026-10-19, arah owner: file fisik config server)

Rencana lama (route hardcode di smali) **DIBATALKAN**. Owner minta endpoint & config berupa
**file fisik yang bisa diedit bebas** dan isi config harus **config server asli**. MiniServer
sudah file-driven (`resolve()`: path request → `files/server/<path>` → fallback
`assets/mirror/<path>` → 404), jadi cukup:

```
+5 file fisik di mirror (panenan RAW byte 1:1, endpoint = nama file):
  mirror/qsdk/v1/system/init          ← qsdk_v1_system_init.json          764 B  a6ab4470…
  mirror/qsdk/v1/system/getNotice     ← qsdk_v1_system_getNotice.json       55 B  424866f4…
  mirror/qsdk/v1/user/registerVisitor ← qsdk_v1_user_registerVisitor.json 2.345 B  95e8d974…
  mirror/qsdk/v1/user/registerUser    ← qsdk_v1_user_registerUser.json    2.397 B  c4105fb9…
  mirror/qsdk/v1/auth/getUserInfo     ← qsdk_v1_auth_getUserInfo.json      280 B  dfc72556…
TANPA route hardcode; TANPA perubahan resolve(). Menambah endpoint baru =
menambah file; mengganti respons = menimpa file; semua bisa di perangkat
(Android/data/…/files/server/) tanpa build ulang → docs/CONFIG-SERVER.md.
```

SMT smali yang tersisa HANYA fix drain body POST (Pelajaran 6 diperluas ke body):
`handle()` menangkap `Content-Length` saat drain header (case-insensitive, helper
`parseCL` dgn try/catch sendiri) lalu menghabiskan body per Content-Length sebelum
memilih response. Bukti HAR: SDK POST mengirim `Content-Length: 1249`.
Lab `apk/rstlab/lab_post.py` (body asli 1.249 B, respons panenan 764 B):
semantik lama **0/6** (BrokenPipe saat kirim = IOException jalur gagal SDK),
semantik baru **6/6 utuh tanpa error**.

### P3 — Verifikasi sebelum rilis test10 — SEMUA ✅ (realisasi 2026-10-19)

- `check_invoke_regs.py` lulus di source smali DAN baksmali dex final.
- Lab POST: lama 0/6 vs baru 6/6 (tabel di atas).
- Zip diff vs 1.0.1: 3.687/3.697 entry byte-identik; berubah hanya `classes2.dex`
  (6.020→6.316 B), `assets/QHinfo` (22→27 B), META-INF; +5 file `mirror/qsdk/…`
  (sha256 diekstrak balik dari APK final = panenan, 5/5 OK); `classes.dex` byte-identik.
- Sign v1+v2+v3 + zipalign verified. SHA-256:
  `bebddbf5d854d57c19dba7a91eaa57da019ff557823a8db73c0503005eae7342`

---

## 4. Fase T — Uji perangkat test10

**Kriteria lolos**:
1. HAR: **nol request** ke `qsdk.t4game.com`; 5 endpoint SDK dijawab `127.0.0.1` dengan 200.
2. Layar login SDK tampil normal (init → getNotice → registerVisitor lokal).
3. `loginchecknative` MASIH ke `login.popoh5.com:510` asli (perilaku 1.0.1, tidak berubah) dan `code 0`.
4. Peluncuran kedua juga normal (sesuai bukti R0).

**Catatan jujur**: dengan 1.0.2, app tetap butuh internet untuk `:510` (+telemetry noise).
Itu disengaja — satu lapisan per rilis, diagnosis pasti.

---

## 5. Risiko & mitigasi

| # | Risiko | Mitigasi |
|---|---|---|
| 1 | Peluncuran kedua memanggil `autoLogin`/`ckRegVistor` yang belum ada routenya | Auth guest terbukti stateless (registerVisitor); bila `autoLogin` muncul: 404 terlihat di HAR test10 → panen respons asli via 1.0.1 → test11. Pulihable, terdiagnosis jelas |
| 2 | `:510` suatu saat menolak token replay (kadaluarsa) | Terlihat jelas di logcat/HAR (`loginchecknative` code ≠ 0) → bukti baru → 1.0.3 (login server lokal) dipercepat |
| 3 | VerifyError kelas smali baru | `check_invoke_regs.py` wajib + pola instruksi terbukti |
| 4 | Body POST tidak didrain → koneksi rusak (keluarga bug test6) | Drain per Content-Length; lab curl POST sebelum rilis |
| 5 | Endpoint SDK tak terduga (aksi UI khusus user) | 404 = perilaku server mati; SDK menampilkan pesan bawaan; terdokumentasi di luar lingkup |

---

## 6. Definisi selesai 1.0.2

1. Dari app start sampai login SDK sukses (`code 0`), **satu-satunya traffic internet** =
   `:510/loginchecknative` (+ noise telemetry non-fungsional). Nol byte ke `qsdk.t4game.com`.
2. Panenan & bukti terarsip di repo; README/BUILD.md diperbarui; rilis `1.0.2` terbit.

---

## 7. 1.0.3 — Login server lokal (roadmap, BUKAN lingkup kini)

Bukti sudah terkumpul sebelumnya dan tetap valid:
- `loginServer`/`loginPort`/`loginWebPort` bersumber dari **setting bin** (sudah kita kuasai,
  byte-preserving) → redirect `:510`+`:610` tanpa sentuh kode.
- `:610` = engine.io v2 (handshake terbukti hidup); `loginClient` tanpa XXTEA.
- Butuh **Fase R besar** (sesi main penuh) untuk panen `loginGame`/`GetServerList` sebelum
  implementasi — jadwal menyusul setelah test10 stabil.

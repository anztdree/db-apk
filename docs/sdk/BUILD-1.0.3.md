# BUILD 1.0.3 — autoLogin lokal (bugfix dari HAR perangkat; Opsi A: setia server asli)

> Status: **SELESAI DIBANGUN** — 2026-10-20.
> APK: `DB-Local-1.0.3.apk` (125.272.393 B, sha256 `442e7df6…72db5`, v1+v2+v3,
> sertifikat IDENTIK 1.0.1/1.0.2 — serial `56c1a15`, SHA256 `1e08a903…b5953` →
> bisa **install-over tanpa uninstall**, `generator.json` perangkat bertahan).
> Prinsip dijalankan penuh: **tidak ada code tanpa bukti** — termasuk 2 template
> baru yang dibangun otomatis dari bukti repo oleh `staging_mirror103.py`.

---

## 1. Bug yang diperbaiki (bukti: `log/sdk.har` @ 5a580c3 — uji jalan 1.0.2 di perangkat)

1.0.2 lulus 90% (Tahap A config boot ✅, gerbang `loginchecknative code:0` ✅,
generator loginByName/registerUser ✅, nol kebocoran ✅) — SATU cacat:

| | |
|---|---|
| **Gejala** | `POST /v1/user/autoLogin → 404` (4×: entri 26,27 sesi-1; 75,76 sesi-2) |
| **Akar** | Rute tidak dipasang di `MiniServer.handle()` 1.0.2; mirror tanpa file autoLogin; `TestServer2` tanpa uji-nya. Padahal `CONFIG-SDK-ASLI.md` sudah mendokumentasikan *autoLogin (format = registerVisitor, parser sama)* — lupa di-wire. |
| **Dampak** | Sesi-1: SDK buka layar login → user ketik akun → `loginByName`. Sesi-2: autoLogin gagal 2× → HAR berhenti; SDK tak bisa masuk otomatis. |

**Rantai generator terbukti hidup dari HAR**: envelope autoLogin sesi-2 membawa
`authToken e963968f…` yang **diterbitkan sendiri oleh loginByName generator di
sesi-1** → SDK menyimpan dan mengirim-ulang token kita. Begitu rute ada, boot
berikutnya auto-masuk tanpa ketik manual.

**Bukti parser dex** (`bukti/riset-login/dump-login-endpoint-parser.txt` L159–186):
params autoLogin = **{authToken}** saja; parser = `getBoolean("result")` →
`getString("data")` → **parser sesi sama registerVisitor**; gagal → `p6.obj =
v5_1` → layar login.

## 2. Solusi 1.0.3 — kebijakan setia server asli (Opsi A)

Server asli memvalidasi authToken; yang kadaluarsa/asing ditolak → layar login.
Generator menerbitkan token, jadi generator berhak memvalidasi:

```
autoLogin(authToken)
  ├─ authToken == lastAuthToken (yang kita terbitkan sesi terakhir)
  │    → 200 result:true, sesi akun itu dari autoLogin.template
  │      (uid + lastUsername + isGuest sesi lama; token sesi BARU;
  │       authToken = token boot ini → rotasi seperti server asli)
  └─ token asing/kosong/envelope rusak
       → 200 result:false dari autoLogin.error.template (BENTUK error live 40008)
         → SDK membuka layar login  (persis server asli saat token kadaluarsa)
```

**Setiap respons sesi sukses** (registerVisitor/registerUser/loginByName/autoLogin)
mencatat sesi terakhir ke `generator.json`: `lastAuthToken`, `lastUsername`,
`lastIsGuest` → persist lintas boot dan **dijaga saat re-sync versi**.

### Perilaku di perangkat setelah upgrade 1.0.2 → 1.0.3

- Boot pertama: `generator.json` lama belum punya `lastAuthToken` → autoLogin
  token lama DITOLAK → layar login muncul **satu kali** (natural — seperti token
  kadaluarsa di server asli). Setelah satu login (loginByName/registerVisitor),
  semua boot berikutnya **auto-masuk**.
- `uid` yang dikunci user (mis. `16722253`) **bertahan** — re-sync menjaga
  `generator.json`.

## 3. Delta APK vs 1.0.2 (sisanya byte-identik 1.0.1 basis)

| Entri APK | Perubahan | Isi |
|---|---|---|
| `classes2.dex` | 17.056 → **18.840 B** | + rute autoLogin + validasi/persist sesi (`DbGen.autoLoginJson`, `rememberSession`, `authTokenFromEnvelope`) |
| `assets/mirror/VERSION` | `1.0.2` → **`1.0.3`** | pemicu re-sync penuh di perangkat |
| `assets/mirror/generator.json` | +3 field | `lastAuthToken:""`, `lastUsername:""`, `lastIsGuest:1` |
| `assets/mirror/qsdk/v1/user/autoLogin.template` | **BARU** (1.940 B) | registerVisitor.template (config-local) dgn `isGuest → {{IS_GUEST}}` — bentuk live, jenis sesi diisi DbGen |
| `assets/mirror/qsdk/v1/user/autoLogin.error.template` | **BARU** (86 B) | BENTUK dari panen error LIVE 40008 (`panen-gerbang4/live__user__registerUser.bin`); id/message → `{{ERROR_ID}}/{{ERROR_MESSAGE}}` |

`classes.dex` (injeksi `MiniServer.start`), 21 file mirror lama, manifest,
resources — byte-identik. Anti-panen diperluas: scan juga token device dari HAR
(`e963968f…`, `15ea6e27…`) → **TIDAK ADA kebocoran**.

### Jujur: apa yang TIDAK punya materi live

- ISI error autoLogin (`{{ERROR_ID}}=1`, `{{ERROR_MESSAGE}}="sesi telah berakhir,
  silakan login kembali"`) = **kebijakan server lokal** — TIDAK ada materi live
  error autoLogin (probe panen hanya 8 endpoint statik; tidak ada capture autoLogin
  error dari server asli). BENTUK envelope-nya sahih (panen 40008). SDK hanya
  membaca `result=false` (dex) → layar login; message sekadar tampilan bila ada.
- Envelope SDK bisa membawa authToken obfuscated `@NNN@…` (fixture panen era lama)
  — token yang KITA terbitkan selalu hex polos dan terbukti dikembalikan hex polos
  (HAR entri 75). Bila suatu saat SDK mengirim bentuk obfuscated → ditolak →
  layar login → degradasi aman, natural.

## 4. Rute MiniServer 1.0.3 (delta rute tabel §2 BUILD-1.0.2.md)

| Request | Dilayani oleh | Source config |
|---|---|---|
| `POST /v1/user/autoLogin` | **GENERATOR (validasi)** | `qsdk/v1/user/autoLogin.template` / `autoLogin.error.template` |

Rute lain = sama 1.0.2 (7 statik byte-exact live, registerVisitor/registerUser/
loginByName/getUserInfo generator, gerbang `loginchecknative` lokal).

## 5. Uji JVM — 29 PASS / 0 FAIL (`TestServer3.java`)

Warisan 1.0.2 (22): setting bin byte-exact · 7 statik byte-exact · registerVisitor
generator · gate echo code:0 + sign stabil + loginToken berganti · registerUser/
loginByName envelope ASLI (anti-kebocoran) · getUserInfo generator · 404 + tolak
`..` · generator.json persist · re-sync versi memulihkan config + uid bertahan.

BARU 1.0.3 (7):
1. `autoLogin-envelope-asli-ditolak` — envelope ASLI perangkat (token
   `e963968f…`) ditolak di server segar; result:false + error id/message; token asing tak di-echo
2. `autoLogin-cocok-sesi-akun` — token terbitan loginByName diterima → sesi akun
   itu (username echo, isGuest 0, uid generator) — **login bertahan (Opsi A)**
3. `autoLogin-rotasi-token-sesi` — panggil ulang → token sesi baru, authToken boot tetap
4. `autoLogin-sesi-guest` — registerVisitor lalu autoLogin → isGuest 1, Guest\<uid\>
5. `autoLogin-envelope-rusak-ditolak` — body sampah → result:false
6. `autoLogin-lintas-boot` — **replika skenario HAR sesi-2**: autoLogin = panggilan
   PERTAMA setelah boot (sebelum registerVisitor apa pun), token boot lama masih
   sah, sesi dipulihkan
7. `generator-json-lintas-boot` — lastAuthToken = token boot baru (rotasi persist)

## 6. Provenance & cara rebuild

```
build-1.0.3/src/{MiniServer,DbGen,DbJson,ConnRun}.java   ← kode yang masuk APK
build-1.0.3/staging_mirror103.py                          ← bangun staging dari bukti repo (39 file)
build-1.0.3/mirror/                                       ← staging config (hasil skrip, di-commit)
build-1.0.3/Compile103.java                               ← javac via ToolProvider (sandbox)
build-1.0.3/TestServer3.java                              ← uji JVM 29 kasus (29 PASS / 0 FAIL)
build-1.0.3/repackage103.py                               ← rakit APK (verifikasi otomatis anti-panen)
```

Urutan: `staging_mirror103.py` → compile `src/` (Java 21, classpath stubs) →
D8 (`r8.jar`, `--lib android.jar`) → `repackage103.py` (basis 1.0.1) →
`uber-apk-signer` (debug key bawaan = AOSP testkey yang sama dengan 1.0.1/1.0.2).
Hasil: `out/DB-Local-1.0.3.apk` — sha256 `442e7df6f51f53ab5da6816eb0965cf1c66937666b45c2d553aaa4a870772db5`.

Catatan sandbox: fixture uji (`envelopes.json` + envelope autoLogin ASLI dari HAR)
disimpan di workspace `work-re/build-103/fixture/` — TIDAK di-commit (berisi
identitas device/panen; repo tetap bersih identitas).

## 7. Kriteria lulus di perangkat

```
boot → setting bin (lokal) → init → getNotice         ← Tahap A ✅ (terbukti HAR)
     → autoLogin(authToken)                            ← 1.0.3 BARU
          ├─ cocok  → sesi dipulihkan → LANGSUNG masuk gerbang
          └─ ditolak → layar login (sekali) → loginByName → gerbang
     → game GET http://127.0.0.1:28110/loginchecknative?uid=… → code:0 ← Tahap C ✅
```

Tag logcat baru: `200 gen v1/user/autoLogin` (sukses) / `200 gen v1/user/autoLogin`
dengan body result:false (ditolak — layar login).

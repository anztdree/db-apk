# BUILD 1.0.5 — SDK PARUH A: AKUN & KEAMANAN PENUH (8 endpoint)

> Mandat user (2026-10-20): *"dari awal saya gak perlu jaring, cuma mau natural,
> artinya semua fitur SDK ini seharusnya wajib bisa di local karena kita sudah
> sepakat baik itu pembayaran atau CDK"*
> Revisi rencana oleh user: pembayaran → **website lokal** (fase C); SNS/Facebook
> → **diskip** (logis lokal). Fase A + B tetap dikerjakan penuh.

## 0. Bukti Fase 0 (dex asli, nol karangan)

- 23 caller class + wrapper + parser + bean dibedah dari `classes.dex` asli
  (`bukti/dex-evidence/dump105/`, 31 file) — kontrak 19 rute di RENCANA-1.0.5.md.
- Helper password klien = **MD5** (dump105/helper-password.txt:
  `MessageDigest.getInstance("MD5")`).
- Bentuk sesi refresh (parser `psalmicvolleyball`): `authToken` wajib + `userData`
  wajib (`username`, `uid`, `token`, `isGuest` — `getString`); `bindInfo` opsional.
  Bentuk = templat `registerUser` yang sudah TERBUKTI di perangkat.
- Android `org.json` lenient: `getString("data")` pada objek mengembalikan JSON
  text-nya → bentuk templat kita sah untuk parser dex manapun.

## 1. Yang baru di 1.0.5-A (8 endpoint — semua diterapkan sungguhan, tanpa jaring)

| Endpoint | Perilaku lokal (natural) |
|---|---|
| `v1/user/sendCodeByEmail` | kode 6 digit, TTL 10 menit, sekali pakai; "email keluar" = **`outbox.txt`** + log (bentuk natural pengganti SMTP) |
| `v1/user/findPassByEmail` | kode sah → password baru + **SESI REFRESH** (auto-login, parser dex 12) |
| `v1/auth/changePassword` | verifikasi password lama (md5) → ganti; salah → id 8 |
| `v1/auth/bindMail` | identitas akun MENJADI email (pola akun asli), isGuest→0, bindEmail terisi di sesi; email duplikat → id 6 |
| `v1/auth/viewMigCode` | kode migrasi `DB+8HEX`, dipakai ulang selama belum terpakai (pemakaian nyata di website fase C) |
| `v1/auth/cUserTrash` | tStatus=1 → akun trash (login/autoLogin DITOLAK id 7); tStatus=2 → pulih (dex: TrashAccountActivity=1, AccountRecoverActivity=2) |
| `v1/auth/unBindOtherInfo` | hapus baris binding; data.status |
| `v1/auth/userBindOtherSdk` | catat binding pihak ketiga (identitas lokal); data.status |

## 2. Database (di `external/server/` — semua DIJAGA saat re-sync versi)

```
accounts.txt : name<TAB>passHash<TAB>uid<TAB>isGuest<TAB>email<TAB>trash
               (kolom 5-6 BARU; baris 1.0.4 4-kolom tetap sah — install-over aman)
codes.txt    : email<TAB>code<TAB>sendType<TAB>createdMs<TAB>used
bindings.txt : uid<TAB>openType<TAB>userOpenId<TAB>otherAccountName
migcodes.txt : code<TAB>uid<TAB>createdMs<TAB>used
outbox.txt   : ts<TAB>email<TAB>code<TAB>sendType   (jurnal email keluar lokal)
```

## 3. Uji JVM — TestServer5: **32 PASS / 0 FAIL**

Fase A (segar): statik byte-exact; registerVisitor/gate/registerUser/loginByName
(salah ditolak id 2, cocok sesi akun); sendCode → outbox berisi kode; findPass
kode salah id 5 / sah → sesi refresh + rotasi authToken / kode sekali pakai;
autoLogin token baru OK; changePassword lama salah id 8 / sah → login baru ok +
lama ditolak; bindMail kode salah id 5 / email duplikat id 6 / guest → akun email
(username=email, isGuest 0, bindEmail isBind 1); login by email OK; guest BARU
otomatis (uid ≠ uid ter-bind); viewMigCode format + reuse; trash → login+autoLogin
ditolak id 7 → restore → login lagi OK; bind/unbind pihak ketiga tercatat.

Fase B (re-sync + boot ulang): akun, sesi (autoLogin), codes/bindings/migcodes/
outbox SEMUA bertahan; migcode reuse lintas boot; gate per akun.

## 4. Provenance APK

| Butir | Nilai |
|---|---|
| classes2.dex | 35.056 B (1.0.4: 23.312 B) — 8 rute + 5 file DB terverifikasi di dex |
| APK | `DB-Local-1.0.5.apk` — 125.280.859 B |
| sha256 | `b32352ce35d0006111a6a1ba766b61881ccd1e65399cf558ca62abf6be585300` |
| Sertifikat | `1e08a903…b5953` — IDENTIK 1.0.1/1.0.2/1.0.3/1.0.4 → **install-over aman** |
| Verifikasi repackage | mirror byte-exact, VERSION=1.0.5, gerbang lokal, NOL kebocoran panen/device |
| Sumber dex | `DB-Local-original.apk` classes.dex (6.697.104 B) via androguard 4.1.4 |

## 5. Kejujuran desain (diakui terbuka)

- authToken yang masuk pada 8 endpoint ini TIDAK divalidasi ketat (server asli
  memvalidasi; lokal memakai uid dari sesi SDK). autoLogin tetap Opsi A ketat.
- `isNewUser` di templat bindMail/findPass = 0 (bukan registrasi baru).
- Binding pihak ketiga = identitas LOKAL (tidak ada FB/Google asli — sesi
  keputusan user: SNS diskip).
- Kode email TIDAK dikirim via internet — `outbox.txt` adalah bentuk lokalnya.

## 6. Lanjutan (rencana user)

- **1.0.5-B**: CDK (`actCdKey` + sesi refresh) & `setGameRoleInfo`.
- **Fase C**: website lokal = developer config + pembayaran (order tampil di web,
  konfirmasi via web; memakai DB order + migcode dari server).

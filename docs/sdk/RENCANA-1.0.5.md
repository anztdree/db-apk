# RENCANA BUILD 1.0.5 — SDK PARUH PENUH (mandat user: "semua fitur SDK wajib bisa di local, termasuk pembayaran & CDK")

> Prinsip user (2026-10-20): **bukan jaring anti-stuck** — semua 18 endpoint terpendam harus
> HIDUP secara natural di lokal. Tanpa catch-all error sebagai tujuan akhir.

## 1. Bukti Fase 0 (selesai — build-105/dump105/, 27 file dari classes.dex asli)

Metode: androguard 4.1.4 → 23 caller class + wrapper + parser + bean. Nol kelas hilang.

### 1a. Amplop request BERSAMA (wrapper-request.txt — terbukti, bukan tebakan)

Setiap endpoint memakai wrapper `bindcommunist.singersmooth.singersmooth(...)`:

- Field umum **di dalam JSON**: `sdkVersion`(223), `gameVersion`, `deviceId`, `serialNum`,
  `devIDShort`, `platform`, `productCode`, `channelCode`, **`authToken`** (bila ada sesi),
  `clientLang`, `suggestCurrency`, `time` + field spesifik endpoint.
- Yang lewat kabel: POST body `sv=v2&data=Base64(JSON)&sign=…` — **SAMA dengan yang sudah
  di-decode MiniServer 1.0.4** (`valueFromEnvelope`: form `data` → Base64 → JSON → ambil key).
  → Mesin decode yang sudah terbukti di perangkat DIPAKAI ULANG untuk semua endpoint baru.
- `authToken` hadir di hampir semua request = **identitas pemanggil** untuk operasi
  per-akun (bind/changePassword/CDK/order) → dipetakan ke `accounts.txt` via generator.

### 1b. Kontrak per endpoint (req = field di dalam JSON; resp = bentuk yang dibaca SDK)

| Endpoint | Request (field spesifik) | Respons yang dibaca SDK |
|---|---|---|
| `/v1/user/actCdKey` | `cdkey` | `result`; **sukses → `data` = SESI REFRESH** (lihat 1c), callback 14 |
| `/v1/auth/setGameRoleInfo` | `uid, gameRoleId, gameRoleName, serverName, gameRoleLevel, vipLevel, partyName, gameRoleBalance` | `result` (+`error` string bila gagal); fire-and-forget |
| `/v1/auth/createOrder` | `uid, goodsId, orderSubject, payType, productOrderNo, roleLevel, roleName, serverName, callbackUrl, extrasParams` | `result`; `data.orderNo` |
| `/v1/auth/jianchaLayLx` | `amount, currency, goodsId, roleLevel` | `result`, `data`(string), `apiUrlH5`, `firstPy`, `othpy_type`, `error`(string) — **pemilih kanal bayar** |
| `/v1/user/postGooglePlayVerify` | map verifikasi purchase (URL string ber-spasi di dex) | shaped result |
| `/v1/user/sendCodeByEmail` | `email, sendType` | `result`, `data`/`error` object |
| `/v1/user/findPassByEmail` | `email, code, newPassword` | `result`, `data`(string) |
| `/v1/auth/changePassword` | `uid, oldPassword, newPassword` | `result` |
| `/v1/auth/bindMail` | `uid, email, code, password` | `result`, `data`/`error` object |
| `/v1/auth/viewMigCode` | `uid` | `result`, `data`(object), `error` |
| `/v1/auth/cUserTrash` | `uid, tStatus` | `result` |
| `/v1/auth/unBindOtherInfo` | `uid, openType, userOpenId` | `result`, `data`/`error` object |
| `/v1/auth/userBindOtherSdk` | `uid, appToken, access_token, openType, otherAccountName, userOpenId` | `result`, `data`/`error` object |
| `/v1/auth/cksnslogin` + `/v1/user/userLoginByOtherSdk` | `uid, authToken, appToken, access_token, openType, userOpenId` | `result`, `data`(string) |
| `/v1/auth/fbActInit` | `uid, roleInfo, serverInfo` | `result`, data array, `error` object |
| `/v1/auth/fbInviteEvent` | `uid, roleInfo, serverInfo, totalUser` | idem |
| `/v1/auth/fbLikeEvent` | `uid, roleInfo, serverInfo, edgeType, fburl` | idem |
| `/v1/auth/fbShareEvent` | `uid, roleInfo, serverInfo, fburl, shareStatus` | idem |
| `/v1/auth/fbUserClaimEvent` | `uid, roleInfo, serverInfo, actId, claimStatus` | idem |

Catatan: pasangan `.put("id"/.put("message"` di beberapa dump = pembangun objek error lokal
untuk callback (bukan field request) — dikonfirmasi saat implementasi per-endpoint.

### 1c. Temuan kunci: `data` actCdKey = SESI REFRESH (parser-cdk-data.txt)

Parser `psalmicvolleyball.cointhreat` membaca `data` sebagai:

```json
{ "authToken": "...", "checkRealName": "0", "tips": "...", "userData": { ... "bindInfo": {...} } }
```

- Sukses CDK → SDK **mengganti sesi aktif** dengan payload ini + callback 14.
- Bentuk `userData` = bentuk yang SUDAH dihasilkan generator kita (registerVisitor/login)
  → template sesi dipakai ulang; CDK sukses = token baru + userData akun.

## 2. Tahapan eksekusi (bertahap, tiap tahap: uji JVM → rebuild → push release, versioning tetap DB-Local-1.0.2.apk)

- **1.0.5-A — Akun & keamanan**: sendCodeByEmail, findPassByEmail, changePassword, bindMail,
  viewMigCode, cUserTrash, unBindOtherInfo, userBindOtherSdk
  (DB baru: tabel kode-email, tabel binding, kolom email/updated; email dilokalkan — kode
  masuk DB + jurnal, bukan SMTP)
- **1.0.5-B — CDK & peran**: actCdKey (DB kode CDK + sesi refresh bentuk live),
  setGameRoleInfo (tabel role per akun — fondasi tahap belakang)
- **1.0.5-C — Pembayaran**: jianchaLayLx (kanal lokal), createOrder (DB order, orderNo generator),
  postGooglePlayVerify (verifikasi lokal terkendali)
- **1.0.5-D — SNS/Facebook**: fbActInit/fbShareEvent/fbLikeEvent/fbInviteEvent/fbUserClaimEvent,
  cksnslogin, userLoginByOtherSdk (identitas SNS lokal yang berfungsi)

## 3. Status

- [x] Fase 0 bukti (dump105 lengkap, kontrak 19 rute terpetakan)
- [ ] 1.0.5-A  [ ] 1.0.5-B  [ ] 1.0.5-C  [ ] 1.0.5-D

# RISET FISIK — Alur SDK & Panen Config Asli (qsdk.t4game.com)

> Tanggal: 2026-10-19 (regenerasi: /tmp terhapus, ditarik ulang dari repo + panen ulang live)
> Metode: bukti fisik (DB-Local.apk + HAR fisik + logcat fisik) + probe langsung ke server.
> Semua file panenan = **respons asli server hidup**, bukan capture, bukan karangan.

## 1. Alur SDK 4 langkah (logcat fisik urutan harfiah + kronologi HAR)

```
1. POST /v1/system/init          → CONFIG OTAK (19 saklar productConfig, dll)
2. POST /v1/system/getNotice     → CONFIG pengumuman (data:[])
3. POST /v1/user/registerVisitor → SESI: uid + username + token (@149@...)
4. GET  login.popoh5.com:510/loginchecknative?uid=..&token=..&sdk=BS
                                 → loginToken (per sesi) → socket :610
```

## 2. Struktur request

```
POST http://qsdk.t4game.com/<ep>
CT: application/x-www-form-urlencoded
body: sv=v2 & data=<base64 JSON> & sign=<32 hex MD5>
```
- `data` init = fingerprint perangkat penuh (~35 field, productCode=75880787318053043365515388491588, sdkVersion=223)
- `data` call lanjutan = ringkas (12 field, authToken client-side)
- Server menolak payload termodifikasi sedikit pun → panen wajib verbatim
- `sign` tervalidasi per-body, tidak terikat path (terbukti: body init diterima endpoint lain)

## 3. Inventaris config asli layer SDK — LENGKAP (3 file)

| File (panen-live-2026-10-19/) | Isi | Sifat |
|---|---|---|
| init 766-767 B | payTypes[] , payFast[] , version(semua "empty"), productConfig{useServiceCenter:0, isShowFloat:0, showShare:1, useSms:0, skinStyle:0, guestShowBind:1, single_play:0, adZhifuSwt:0, noticeNode:0, regMailVerify:0, pycenttype:0, onGoToPUrl:0, trashTime:0, noFirebaseLog:0, logEvtReport:0, title/logo/serviceInfo/onGoToPUrlData:""}, otherpayType:2, isNotGift:0, showShare:"1", showDiscount:0, nodes{deleteAccount:0,serverInfo:0,oneYeaerTips:0,removeEmail:0}, loginTypes:["1","13"], first_install:0 | CONFIG + requestIp/countryCode (ikut IP pemanggil) |
| getNotice 55 B | `{"result":true,"data":[],"error":{"id":0,"message":""}}` | CONFIG murni — byte-identik antar hari |
| getAgreement 188 B | 9 field perjanjian, semua "" | CONFIG murni |

Bukti sesi (bukan config): registerVisitor 2.345 B (token per perangkat);
registerUser replay → id 40008 "pengguna ini telah digunakan" (state-bound);
getServerAgreement butuh agmId; dmsg butuh func_code; onNewToken = push.
→ Sisa 32 endpoint di dex = aksi akun/pembayaran/event, bukan config.

## 4. Verifikasi hidup 2026-10-19
- init/getNotice/getAgreement: result:true (respons asli live)
- loginchecknative dengan token arsip: HTTP 200 code:0, loginToken BARU diterbitkan → lapisan login hidup
- loginToken = per sesi; field sign respons = stabil antar sesi

## 5. Status material build
- Basis: DB-Local.apk original (sha256 3650cbe5... = LFS pointer, ditarik utuh 105.112.524 B)
- APK tes1 lama HILANG bersama /tmp (dan pendekatan capture-nya sudah digugurkan user)
- Panenan config + arsip sesi: tersedia kembali di docs/panen-live-2026-10-19/

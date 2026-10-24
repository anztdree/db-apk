# Perbandingan HAR — Original online vs Custom DB-Local 1.0.1-C

Sumber: `log running/original-apk.har` (72 req, 22-10-2026 21:05–21:08 WIB, pkg com.db.teslocal)
vs `log running/db-local.har` (52 req, 24-10-2026 13:12–13:14 WIB, pkg com.db.local, build 1.0.1-C-03d23bae).

## 1. Pemetaan domain → lokal (SEMUA endpoint inti sudah lokal)
| Domain (original) | Jml | Custom | Status |
|---|---|---|---|
| configus.sjmobilegame.com + …cos.myqcloud.com (setting bin) | 12 | 127.0.0.1:10100 | ✓ lokal |
| dragonh5cdn.popoh5.com (upgrade incl. all.zip) | 10 | 127.0.0.1:10100 | ✓ lokal |
| qsdk.t4game.com (v1/system, v1/user, v1/auth) | 13 | 127.0.0.1:10101 | ✓ lokal |
| login.popoh5.com:510 (loginchecknative) | 3 | 127.0.0.1:10102 | ✓ lokal |
| 119.29.29.29 (HTTPDNS) | 1 | — (tidak muncul) | ✓ |

## 2. Urutan alur 1:1 IDENTIK (kedua HAR)
FB bootstrap (mobile_sdk_gk, profile, activities) → setting_BS_Android.bin ×2 (+ com_db_*.bin 404 — file opsional, original juga 404) → upgrade base/resource.version, upgrade.json, size.json, **all.zip 19.385.098 byte (ukuran sama persis dgn original)** → FB me → system/init → getNotice → AppsFlyer → registerVisitor → **loginchecknative** → getUserInfo → putaran ke-2 (setting ×2, versi upgrade, init, getNotice) → registerUser → **loginchecknative** → egret plugin-check.

## 3. Respons lokal diterima klien (semua 200 di endpoint inti)
- system/init 767 B (original 765 B) — productConfig lengkap
- getNotice 55 B — identik bentuk
- registerVisitor 1.972 B — authToken + userData{uid,username,token,isGuest}
- registerUser 1.069 B — + bindInfo (original 2.395 B)
- loginchecknative 281/282 B (original 195/206 B) — code/sdk/nickName/loginToken/userId/sign
- Perbedaan ukuran = isi offline sendiri; klien tidak retry, tidak hang.

## 4. Yang MASIH ke internet (sesuai scope "hanya port ke lokal", belum diambil alih)
| Domain | Jml | Hasil |
|---|---|---|
| graph.facebook.com | 14 | activities & mobile_sdk_gk 200; me/model_asset 400 |
| *.appsflyer.com (gcdsdk/inapps/launches/conversions) | 11 | **semua 400** |
| plugin-check.egret.com | 2 | status 0 (gagal, di original juga status 0) |

Fakta penting: **di ORIGINAL pun AppsFlyer ditolak server-nya (400 semua)** — jadi perilaku custom = original; bukan regresi. Egret plugin-check gagal di keduanya, game tetap lanjut.

## 5. Catatan
- Kedua HAR berakhir setelah loginchecknative putaran ke-2 — traffic world/socket (WebSocket) tidak tertangkap proxy Reqable, jadi perbandingan mencakup alur sampai login sukses.
- Tidak ada request yang menggantung/hang di custom; status 0 hanya egret plugin-check (sama seperti original).

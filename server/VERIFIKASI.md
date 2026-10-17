# ✅ VERIFIKASI Panen Mirror Server (2026-10-18 06:01 WIB)

Semua file di folder `server/` diunduh langsung dari server asli dan diverifikasi
**23/23 cek VALID** — tidak ada 404, tidak ada file korup.

## Hasil verifikasi isi

| File / Objek | Cek | Status | Detail |
|---|---|---|---|
| `configus/setting_BS_Android.bin` | XOR dekrip + JSON valid | ✅ VALID | JSON valid, 10 key |
| — key `testMode` | ada | ✅ VALID | `1` |
| — key `url` | ada | ✅ VALID | `https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935` |
| — key `update` | ada | ✅ VALID | `https://dragonh5cdn.popoh5.com/bs/upgrade` |
| — key `loginServer` | ada | ✅ VALID | `https://login.popoh5.com` |
| — key `loginPort` | ada | ✅ VALID | `610` |
| — key `checkTime` | ada | ✅ VALID | `300` |
| `upgrade/base.version` | angka murni | ✅ VALID | `110` |
| `upgrade/resource.version` | angka murni | ✅ VALID | `11390` |
| `upgrade/size.json` | JSON + key `all` | ✅ VALID | 6 key |
| `upgrade/upgrade.json` | JSON + key `all` | ✅ VALID | 5 key |
| `upgrade/all.zip` | ZIP utuh (CRC test) | ✅ VALID | semua CRC ok |
| `upgrade/all.zip` | jumlah entri | ✅ VALID | 652 entri |
| `all.zip` → `resource.version` | terbaca di dalam zip | ✅ VALID | `11390` (== remote) |
| `all.zip` → `js/` | ada | ✅ VALID | 12 file JS di dalam zip |
| `upgrade/all.zip` | ukuran == `size.json["all"]` | ✅ VALID | 19385098 == 19385098 |
| `bs/manifest.json` | initial + game | ✅ VALID | 7 + 8 = **15 file** |
| `bs/js/` | semua file manifest ada di mirror | ✅ VALID | 15/15 ada |
| `bs/js/main.min_777039fc.js` | ukuran wajar | ✅ VALID | 4.914.149 B |
| `bs/index-native.html` | fetch `manifest.json` | ✅ VALID | ditemukan |
| `bs/index-native.html` | callback `startGame` | ✅ VALID | ditemukan |
| `bs/index-native.html` | `egret.runEgret` | ✅ VALID | ditemukan |
| `bs/index-native.html` | baca `loginServer` | ✅ VALID | ditemukan |

## File yang TIDAK panen (sesuai perilaku aplikasi)

| File | URL | Hasil | Keputusan |
|---|---|---|---|
| `com_db_local.bin` | `{configus}/com_db_local.bin` | **HTTP 404** | diabaikan aplikasi (terverifikasi di kode) → tidak perlu di mirror |

## SHA-256 semua file (untuk pengecekan ulang kapan pun)

| File | Ukuran (B) | SHA-256 |
|---|---|---|
| `configus/setting_BS_Android.bin` | 711 | `ad6fd64cc166abb463cb777d6b47289670e8f01e8dacf6a9ae2b5fa02f12cf4a` |
| `upgrade/all.zip` | 19.385.098 | `6b838206bfa148ed85c1e8529b20facca3518f84bcd5e462512ebc7667ff85cf` |
| `upgrade/base.version` | 4 | `e00e6031fc5e7ef95c526573343419859e112cebd550f10e2089bf393f5ed2bd` |
| `upgrade/resource.version` | 5 | `e0047685602673409ca394f72c551c5f0d13a0bd05f65dcb67531ed013ea03e9` |
| `upgrade/size.json` | 105 | `31880406c619cd5a4e19f3508571cc7de051877eabcf66888e09008b005ae96d` |
| `upgrade/upgrade.json` | 130 | `fe3a47ce19525e3ec22150c3a387d2aca7dcf1ffe6d4e44031ce990e26280b3d` |
| `bs/index-native.html` | 10.792 | `59dba3e460045cf89f0e12247442e1a499b9ee830ae871af9f3405340cda36b3` |
| `bs/manifest.json` | 533 | `53c1a26bec74ddcc36df9c762e70bf763b70c890d85f860056e6a5f4516307d3` |
| `bs/js/ThinkingAnalyticsSDK.min_72d3fad5.js` | 64.625 | `b5a48c07068df61bf7dc4e03f08f988ee497e7dd4a885e6e9ed4bea77be986e7` |
| `bs/js/assetsmanager.min_2ef412b7.js` | 28.574 | `6929c7b57d8334d0a0cebe2cf8bfd3c2f4689b1ea30f9a5e1d5ae519f44d9d29` |
| `bs/js/battlelogic.min_d446437e.js` | 335.265 | `513ab08cbb9b0fab75fa233faede9aa95190ebdfeb087258e018b6349417e6ae` |
| `bs/js/blockmessage.min_7dc7bcec.js` | 466.179 | `d55348a0af9fecdda3d81e86bd844432f4f4163daab43d999d17283fe5d2efda` |
| `bs/js/default.thm_4d02a3c6.js` | 15.882 | `0888ddbf496061685919c4fb52e24940009efa6b86c5c891c9617f06a64e8e46` |
| `bs/js/dragonBones.min_6252b9c4.js` | 258.742 | `bfec4f9c59bfe6d0ae088dadddbe921cb9e39a68f420a9bee61a5360b2f002d2` |
| `bs/js/egret.min_d9413192.js` | 202.067 | `76f8628eecf3a483ecc65a4bb815819e783f50a043d50ee7ba2063aa3647f303` |
| `bs/js/egret.web.min_b66ab113.js` | 133.267 | `36ebd9abeb5cba686da49a10691c82ded80529277353d29fb6dee2ea04e4e337` |
| `bs/js/eui.min_493403ce.js` | 211.320 | `056c73a840527220f739b0ebc2525a21e506d62e4059a28faf761c903ed1b281` |
| `bs/js/game.min_16249d0f.js` | 40.395 | `44a0329fe96c0b793abd2340f0eb3415e3aa8b939f10fde718159bf723f975d9` |
| `bs/js/lz-string.min_896bee8b.js` | 4.720 | `3bd8685ba939395aa62bb5e14872591312a4948e65047eae612372fc70666509` |
| `bs/js/main.min_777039fc.js` | 4.914.149 | `2a14dca311e87b7203012017baeb4b702bbb78c3d1ffd10f0455a749f5986065` |
| `bs/js/md5.min_97e969ef.js` | 7.209 | `6d0295632c35ee62ad866678369e1184893562c39bec66db59519b1875808393` |
| `bs/js/socket.io.min_57ea9db7.js` | 62.402 | `5c0ab9ea3339a5857ff1273a5433a5fc80150ef8bc78ca5084b63da5451f0132` |
| `bs/js/tween.min_6c5a88f9.js` | 12.481 | `6cdc901fdb97af34c3dc1be4fb256d05f4ccd086982fafda40a0fdc90f8b9445` |

## Catatan versi (penting)

Server saat panen melayani `resource.version` = **11390** (riset sebelumnya: 11389).
`all.zip` yang diunduh berisi `resource.version` = **11390** juga → **set konsisten**.
Konsekuensi perilaku aplikasi (instalasi baru): lokal 11220 → unduh `all.zip` → lokal jadi 11390 →
bukaan berikutnya: remote 11390 == lokal 11390 → **tidak ada unduhan lagi**.

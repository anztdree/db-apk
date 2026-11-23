# KONTRAK LIVE SERVER ASLI qsdk.t4game.com — direkam 23 Nov 2026 (server masih hidup)

Metode: POST form `sv=v2&data=<urlenc(base64(JSON))>&sign=<md5>` dengan sign
dikonstruksi dari smali `unnecessarysperrylites.seventygenom.nationalcommunitymissing`:
TreeMap sort -> `k=v&` per entry (null -> "") -> append secret `0b2a18e45d7df321` -> MD5 lowercase.
SIGN KITA DITERIMA SERVER (bukti: error yang kembali adalah param, bukan 20002).

## Header respons (semua kasus, terverifikasi ulang)
HTTP/1.1 200 OK
Content-Type: text/json
Transfer-Encoding: chunked
X-Powered-By: PHP/5.4.37

## Katalog error REAL (live):
- 20000 {"result":false,"data":[],"error":{"id":20000,"message":"Params error"}}
  (data kosong / tidak bisa diparse)
- 20002 "Params sign error" (capture 14.A#8 sebelumnya)
- 20003 {"id":20003,"message":"LostSomeMustParams:<namaParam>"} — param wajib kosong/absen,
  disebut SATU PERSATU dalam urutan: sdkVersion -> productCode -> authToken
  (semantik PHP empty(): "" = kosong)
- 30001 {"id":30001,"message":"Product doesn't exist"} — productCode tidak ada di DB server
  (per 23 Nov product DB SUDAH DIKOSONGKAN — 10 kandidat productCode semuanya 30001,
  termasuk default client "會員帳戶註冊" (hw_tomato_register 0x7f0c0143) dan
  porkactorcompanion 9fb6f896c2422d160ad512b8ac73a041. Sukses kontrak init TIDAK
  bisa direkam lagi dari live server.)

## Param universal (builder bindcommunist.singersmooth, smali):
sdkVersion=223 (0xdf), gameVersion (long, versionCode=1), deviceId, serialNum, devIDShort,
platform=1, productCode, channelCode, authToken (fresh install = MD5(currentMillis)),
clientLang, suggestCurrency="", time (ms) + param device per-endpoint (init: deviceName,
ismobiledevice, isjailbroken, pushToken, gaid, flashversion, countryCode, bluetoothMac,
osVersion, javasupport, osName, defaultbrowser, osLanguage, screenWidth, screenHeight,
dpi, imsi, netType, longitude, latitude, imei, androidId, adId).

## Konsekuensi untuk server lokal:
- Urutan validasi lokal = decode data(20000) -> sign(20002) -> must-params(20003)
  -> product(30001) -> logika endpoint. Merekap SEMUA respons live yang terobservasi.
- Sukses session: kontrak parser smali psalmicvolleyball/cointhreat (TERBUKTI v11: tamu
  masuk game) — tidak lagi terekam dari live (product DB kosong).
- Body file: qsdk_live_contract_probe_20261123.json (raw respons), qsdk_v1_system_init_raw.txt

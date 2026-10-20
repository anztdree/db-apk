# DB-Local 1.0.2 (tes1) — SDK lokal

File: `DB-Local-1.0.2.apk` (125.262.972 B)
SHA-256: `7b9ff17d5cb57156d8906ccd46ffbe60e1ed5a141e0ce0f28375daeec37670e6`
Basis: APK 1.0.1 dari release GitHub (a946ef6c…) — 3.692 entri warisan, **3.690 byte-identik**.

## Perubahan (4 entri saja)

| # | Entri | 1.0.1 → 1.0.2 | Ukuran |
|---|---|---|---|
| 1 | `assets/QHinfo` | `http://qsdk.t4game.com` → **`http://127.0.0.1:28110`** | 22 → 22 B |
| 2 | `classes2.dex` | v2 → **v3**: drain body POST (Content-Length) + rute `v1/` = mirror-or-**proxy verbatim** ke `qsdk.t4game.com` + CT `text/json` utk mirror | 6.020 → 8.556 B |
| 3 | `assets/mirror/v1/system/getNotice` | BARU — panen live 19 Okt (byte-identik antar hari) | 55 B |
| 4 | `assets/mirror/v1/system/getAgreement` | BARU — panen live 19 Okt | 188 B |

**Tidak tersentuh**: `classes.dex` (hook +56 B tetap), `AndroidManifest.xml`, `config_BS.properties`,
setting bin (692 B), semua mirror layer 1 (23 file), `resources.arsc`, semua `lib/*.so`, kode game.

## Titik baca SDK = LOKAL

`QHinfo` adalah satu-satunya sumber base URL SDK di dex (QuickGameManager.initThirdDataSDK →
`bindcommunist.cointhreat.seventygenom[0]`). Setelah flip, SEMUA `/v1/*` dari client mendarat di
`127.0.0.1:28110` — client tidak pernah lagi menyusun URL ke luar sendiri.

Di dalam server lokal:
- `v1/system/getNotice`, `v1/system/getAgreement` → **dijawab dari mirror** (byte config murni, CT `text/json`).
- Endpoint lain (`init`, `registerVisitor`, `loginchecknative`, dll.) → **proxy verbatim** ke
  `qsdk.t4game.com`: body `sv/data/sign` diteruskan persis, status+body+CT diteruskan balik apa adanya,
  server asli melihat IP perangkat sendiri (proxy jalan di perangkat). String `qsdk.t4game.com`
  yang tersisa di dex hanyalah alamat proxy ini — bukan titik baca client.
- `init` tidak di-mirror (mengandung `requestIp`/`countryCode` per-pemanggil) — sesuai rencana disetujui.
- Upstream mati/gagal → HTTP 502 → client SDK menerima null tanpa crash (perilaku executor terbukti).

Bootstrap & resolusi tidak berubah: `bootstrapSync` menyalin mirror (kini 25 file) ke
`/storage/emulated/0/Android/data/com.db.local/files/server/` (8 dtk, skip existing, .tmp+rename);
`resolve` membaca situ dulu, fallback asset APK. Edit/hapus/tambah file di Android/data tetap
dipatuhi (edit dipertahankan, hapus → disalin ulang, tambah → langsung disajikan).

## Uji pra-rilis (sandbox)

- Kompilasi ecj 3.33 + D8 8.5.35 (min-api default), 2 kelas, 8.556 B.
- Uji fungsional JVM: POST `v1/system/getNotice` body tes → **PROXY 200, CT `text/json`, body
  verbatim dari server hidup** (server balas `id:20002` utk sign tes = rantai forward-passthrough
  utuh); GET tak dikenal → **404** (perilaku v2 terjaga).
- Sign: uber-apk-signer 1.3.0 — zipalign verified, **signature verified [v1, v2, v3]**
  (debug cert deterministik, sama dgn 1.0.1).

## Gerbang verifikasi perangkat (harus hijau sebelum label resmi)

1. logcat: `客户端配置文件服务器地址` → `CurrentMode: 1` → `bin文件解压完成` (layer config lokal)
2. logcat: `post request===http://127.0.0.1:28110/v1/system/init` — marker QGConnectManager:
   base-nya kini LOKAL
3. MiniServer log: `200 v1/system/getNotice (mirror)` / `PROXY 200 v1/user/registerVisitor`
4. Login SDK sukses → masuk game → konten 100% lokal

## Cara pasang

1. Uninstall `DB-Local-1.0.1.apk` (aman; kalau cert sama, update langsung juga bisa)
2. Install `DB-Local-1.0.2.apk`
3. Jalankan — internet hanya dipakai proxy SDK; konten game 100% lokal

## Peta jalan setelah ini (fase akhir, atas arahan)

Emulasi sesi SDK (sign/token dari dex) → emulasi `loginchecknative` + socket :610 →
semua endpoint lokal → proxy ditiadakan.

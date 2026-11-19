# db-apk — GAME LOCAL STANDALONE (1x REBUILD)

Repo ini berisi dua file APK:

| File | Apa |
|---|---|
| `db-local(Original-DO NOT DELETE!).apk` | objek kerja asli — JANGAN DIHAPUS (sha256 `b308c44de6ec082a70c39e388a2cef4e9cdc1cee30b38c14a30bef86a7bc2218`) |
| `db-local-rebuild-miniserver-03.apk` | **APK HASIL REBUILD — FINAL (v03)** — sha256 `ece2a840f8397d064b1e999424a2331e5d6fd76a8ee675ac17613332b8ea9d9d` (116.224.711 B) |

Satu APK, dua sistem:
1. **SISTEM 1 — client:** apa adanya. Satu-satunya sentuhan: EDIT ENDPOINT
   (base64 EntryPoint → `127.0.0.1:4000`). `classes.dex` sha256 IDENTIK
   original — NOL satu byte gameplay diubah.
2. **SISTEM 2 — mini-server kernel** (`classes2.dex` baru): hidup di
   `Android/data/com.db.local/server` — **SEBELAH folder `files/`**, kernel
   NOL PERNAH menulis ke dalam `files/`. Dashboard owner: browser device →
   `http://127.0.0.1:4000/dash` (atau `http://127.0.0.1:4000/`).

## ALUR GAME (ringkas — bukti lengkap ada di catatan build)

1. APK dibuka → ContentProvider kernel boot dulu: seed config kecil →
   engine hidup di `127.0.0.1:4000`.
2. `LaunchActivity` baca EntryPoint → fetch **native_setting** dari
   mini-server (XOR DragonBall): loginServer=127.0.0.1, loginPort=4000,
   url=`http://127.0.0.1:4000/game/index-native.html`, nativeRender=1,
   version=110/11220 (= assets APK → Egret tidak download resource).
3. Runtime Egret native ambil **shell** `index-native.html` (shell ASLI
   DragonBall dari cache device) → `manifest.json` (daftar JS build-kita:
   14 file JS ASLI client, diekstrak dari `pschent_full_tired.bin` pakai
   dekriptor client sendiri — skema `onepieceencrypts`) → JS jalan.
4. JS panggil `startGame` → native balas JSON → socket.io v2 konek
   `127.0.0.1:4000` → `User.GetServerList` → `User.loginGame` → game jalan.
5. Resource tekstur besar TIDAK lewat internet — layer native libegret.so
   menyajikannya dari assets APK (peta vvcc.bin). Semua komunikasi
   konfig/login ke 127.0.0.1:4000; endpoint yang belum siap di-HOLD
   (NOL popup).

## Pasang

1. Uninstall APK lama bila terpasang.
2. Install `db-local-rebuild-miniserver-03.apk` (sign v1+v2+v3, keystore
   `serverkernel-v2`).
3. Buka game. Boot pertama kernel menanam zona ke
   `Android/data/com.db.local/server/`.
4. Dashboard owner: browser → `http://127.0.0.1:4000/dash`
   (dari PC: `adb forward tcp:4000 tcp:4000` lalu alamat yang sama).

## Struktur root di device

```
Android/data/com.db.local/
|-- files/            <-- zona CLIENT (game) — kernel tidak pernah menyentuh
`-- server/           <-- zona SERVER — SEBELAH files/
    |-- engine.json, config/, handlers/, static/, data/
```

Semua config/handler/static bisa diedit kapan saja (dashboard atau manual)
— hot-swap ≤ 2 detik. **APK tidak perlu dibangun ulang untuk perubahan
server-side.**

# db-apk — GAME LOCAL STANDALONE (1 APK, 2 SISTEM)

Satu APK `com.db.local`: (1) client **FROZEN** — satu-satunya sentuhan adalah
EDIT ENDPOINT (base64 di `assets/config.properties` dan
`assets/config_BS.properties` → `http://127.0.0.1:4000/...`); (2) mini-server
lokal (kernel `s2.kernel.*` di `classes2.dex`) yang menghidupkan game offline
+ dashboard owner di **port 4000** (`http://127.0.0.1:4000/dash`).

## FILE DI REPO

| File | Apa |
|---|---|
| `db-local(Original-DO NOT DELETE!).apk` | ORIGINAL mentah — objek kerja tunggal, jangan pernah diubah |
| `db-local-rebuild-miniserver-05.apk` | **HASIL REBUILD v05 (FINAL)** — pasang ini (uninstall dulu APK lama jika ada: ganti signature) |
| `kernel/` | Seluruh sumber build reproducible: `build-dex.sh`, `assemble-apk.py`, `gen-seed.py`, `src/s2/kernel/` (15 kelas), `zone-seed/` (zona server — SUMBER TUNGGAL), `zone-static/` (shell + 14 JS asli + TS), `libs/` |

## ALUR 1:1 (buka APK → main) — ringkas

1. Buka ikon → **ContentProvider `s2.kernel.KernelProvider`** boot SEBELUM
   Application: seed 14 file ke `Android/data/com.db.local/server/`
   (**SEBELAH folder `files/`** — client tidak tersentuh) → engine bind
   `127.0.0.1:4000` → dashboard hidup.
2. `LaunchActivity` baca `config.properties` EntryPoint1 (base64, SATU edit
   client) → GET `127.0.0.1:4000/lzceshi/native_setting/Android` → XOR
   DragonBall → setting (nativeRender=1, shell lokal, loginServer+loginPort=4000).
3. Runtime native → ambil shell `index-native.html` dari 4000 → manifest.json
   → 14 JS asli → `startGame` bridge → `egret.runEgret()`.
4. Socket.io v2/EIO3 ke 127.0.0.1:4000 → **verify TEA (ACK)** → `loginGame` →
   `GetServerList {serverList,history}` → `user.enterGame` (save lengkap) →
   `registChat` + `chat.*` → game jalan. Belum siap = HOLD (NOL popup).
5. Owner dashboard: `http://127.0.0.1:4000/dash` — status / database
   lihat+edit / config edit (hot-swap 2 dtk) / handlers / logs.

## BUILD (reproducible)

```
cd kernel
python3 gen-seed.py        # zone-seed/ -> Seed.java (validasi JSON dulu)
bash build-dex.sh          # ECJ + D8 -> out/classes2.dex
python3 assemble-apk.py out/vXX-unsigned.apk   # dari ORIGINAL.apk
java -jar uber-apk-signer.jar --apks out/vXX-unsigned.apk \
  --ks keystore/serverkernel-v3.keystore --ksAlias serverkernel \
  --ksPass dblocal-kernel-2026 --ksKeyPass dblocal-kernel-2026
```

Rencana lengkap + bedah 1:1 + status eksekusi: README.md di sumber proyek
(`PONDASI APK — SATU SISTEM MINI SERVER`).

## SIGNATURE

- Keystore `serverkernel-v3` (password terdokumentasi di §8 README rencana).
- APK lama v01–v03 pakai keystore lain → **WAJIB uninstall dulu** sebelum
  pasang v04.

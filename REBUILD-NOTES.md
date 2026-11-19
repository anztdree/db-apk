# REBUILD 1x — MINI SERVER DALAM APK — v02 (2026-11-19)

Koreksi besar dari v01: **ROOT SERVER KINI SEBELAH FOLDER `files/`** dan
**NOL SATU BYTE pun ditulis ke dalam `files/` milik client**.

## File
- `db-local-rebuild-miniserver-02.apk` — APK hasil rebuild v02 (FINAL)
- `serverkernel-v2.keystore` — kunci v02 (alias `serverkernel`, pass `s2kernel-2026`)
  — WAJIB untuk rebuild berikutnya agar update-in-place tetap bisa (v01
  keystore password hilang, maka v02 keystore baru: install v02 perlu
  UNINSTALL v01 dulu sekali saja)
- `kernel/` — SELURUH sumber kernel (14 kelas Java + JSON internal + UI tanam)
- `kernel/build-dex.sh` + `kernel/assemble-apk.py` — build reproducible
- `extract/` — AndroidManifest.xml + config endpoint (hasil edit terverifikasi)

## Perbedaan v02 vs v01 (poin persoalan user)
| | v01 | v02 |
|---|---|---|
| Root server | `Android/data/com.db.local/files/server` (DALAM files/ — SALAH) | `Android/data/com.db.local/server` (SEBELAH files/ — BENAR) |
| Zona seed | `assets/server/**` di APK (bisa ikut ter-extract client) | **TERTANAM DI DALAM classes2.dex** — NOL `assets/server` di APK |
| Tulis ke files/ | ya (seed masuk files/server) | **NOL PERNAH** — kernel hanya menulis di root sendiri |
| Dashboard port 4000 | hanya API JSON | **UI dashboard penuh di `http://127.0.0.1:4000/dash`** + API |

## Perubahan APK v02 (HANYA ini — 3.662 entri asli identik byte-per-byte)
1. **TAMBAH** `classes2.dex` (978.124 B) — 14 kelas `s2.kernel.*` + Rhino 1.7.10 core:
   - Entrypoint `s2.kernel.KernelProvider` (ContentProvider, sebelum Application.onCreate)
   - Boot: seed zona tertanam → `Android/data/com.db.local/server` (SEBELAH files/) →
     KV SQLite + logger → build handler set → engine bind `127.0.0.1:4000`
   - Seed HANYA mengisi file yang BELUM ADA (device menang — editan owner tidak ditimpa)
   - Wire engine.io v3 + socket.io v2 + upgrade WS RFC6455, verify XXTEA `"verification"`
   - Codec: XXTEA (vektor client dua arah MATCH), XOR DragonBall, LZString UTF16, md5
   - Hot-swap handler 2 dtk; gagal build → set lama tetap aktif; NOL popup (HOLD-connection)
   - **Dashboard UI tanam** di `GET /dash` (Status/Database/Config/Handlers/Logs) + API `/dash/api/*`
2. **EDIT** `AndroidManifest.xml`: +1 entri `<provider s2.kernel.KernelProvider
   authorities="com.db.local.kernel" exported="false"/>` (reuse hasil edit v01 terverifikasi)
3. **EDIT ENDPOINT** (satu-satunya sentuhan client):
   - `assets/config.properties` EntryPoint1(_debug) → `http://127.0.0.1:4000/lzceshi/native_setting/Android`
   - `assets/config_BS.properties` EntryPoint1/2(_debug) → `http://127.0.0.1:4000/bs/db/android`
4. **NOL** perubahan lain. NOL `assets/server/**`. NOL edit code gameplay
   (classes.dex sha256 identik ORIGINAL). resources.arsc tetap STORED.

## Identitas
| | |
|---|---|
| Input | `db-local(Original-DO NOT DELETE!).apk` sha256 `b308c44de6ec082a70c39e388a2cef4e9cdc1cee30b38c14a30bef86a7bc2218` (104.945.181 B) |
| Output | `db-local-rebuild-miniserver-02.apk` sha256 `cfc869fc18ac8434f5221d85fc6ea0d134bea9f4fe0c09936a49f5d232732a3f` (105.444.360 B) |
| Package | com.db.local — versionName 1.0.1 (tidak berubah) |
| Sign | v1+v2+v3 + zipalign verified (uber-apk-signer, keystore `serverkernel-v2`) |

## Root di device (v02)
```
Android/data/com.db.local/
|-- files/            <-- zona client (GAME) — KERNEL NOL PERNAH MENULIS DI SINI
`-- server/           <-- zona mini-server (SISTEM 2) — SEBELAH files/
    |-- engine.json
    |-- config/  (serverList, setting, versionbin, version/*)
    |-- handlers/ (tier-1 JSON, tier-2 JS, lib/)
    |-- static/
    `-- data/    (state.db, logs.jsonl, seed.json — dibuat runtime)
```

## Pasang
1. Uninstall APK lama (v01) bila terpasang — signature v02 berbeda.
2. Install `db-local-rebuild-miniserver-02.apk`.
3. Boot pertama: kernel seed zona ke `Android/data/com.db.local/server` → engine hidup di `127.0.0.1:4000`.
4. **Dashboard owner:** buka browser di device → `http://127.0.0.1:4000/dash`
   (atau via PC: `adb forward tcp:4000 tcp:4000` lalu buka alamat yang sama).

## Build ulang dari sumber
```
bash kernel/build-dex.sh          # -> kernel/out/classes2.dex
python3 kernel/assemble-apk.py    # -> out/...-unsigned.apk
# sign:
java -jar uber-apk-signer.jar --apks <unsigned.apk> \
  --ks serverkernel-v2.keystore --ksAlias serverkernel \
  --ksPass s2kernel-2026 --ksKeyPass s2kernel-2026
```

# REBUILD 1x — MINI SERVER DALAM APK (2026-11-19)

Hasil rebuild sesuai README.md proyek (rencana tunggal, tanpa fase).

## File
- `db-local-rebuild-miniserver-01.apk` — APK hasil rebuild 1x
- `serverkernel.keystore` — kunci tanda tangan (WAJIB dipakai untuk rebuild berikutnya agar signature tetap sama / update-in-place di device)

## Identitas
| | |
|---|---|
| Input | `db-local(Original-DO NOT DELETE!).apk` sha256 `b308c44de6ec082a70c39e388a2cef4e9cdc1cee30b38c14a30bef86a7bc2218` (104.945.181 B) |
| Output | `db-local-rebuild-miniserver-01.apk` sha256 `47b69a2898a2b5642a47a93237fe07e6eb6588b88a74a0f6e5225d66bc5980cd` (105.420.859 B) |
| Package | com.db.local — versionName 1.0.1 (tidak berubah) |

## Perubahan (HANYA ini — 3.662 entri asli terverifikasi identik byte-per-byte)
1. **TAMBAH** `classes2.dex` (906.900 B) — kernel mini-server (11 kelas s2.kernel.* + Rhino 1.7.10 core)
   - Entrypoint: ContentProvider `s2.kernel.KernelProvider` (jalan sebelum Application.onCreate)
   - Engine: bind 127.0.0.1:4000 — engine.io v3 + socket.io v2 (EIO3), verify XXTEA "verification"
   - Codec: XXTEA (vektor client dua arah MATCH), XOR DragonBall, LZString UTF16, md5
   - Hot-swap handler 2 dtk; gagal build -> set lama tetap aktif; NOL popup (HOLD-connection)
   - Dashboard API /dash/api/* : status / config view+edit / database KV view+edit / logs / handlers / reload
2. **TAMBAH** `assets/server/**` (13 file zona seed ~7 KB) -> benih android/data/com.db.local/files/server
3. **EDIT ENDPOINT** (satu-satunya sentuhan client):
   - assets/config.properties EntryPoint1(_debug) -> `http://127.0.0.1:4000/lzceshi/native_setting/Android`
   - assets/config_BS.properties EntryPoint1/2(_debug) -> `http://127.0.0.1:4000/bs/db/android`
4. **EDIT** AndroidManifest.xml: +1 entri `<provider android:name="s2.kernel.KernelProvider" android:authorities="com.db.local.kernel" android:exported="false"/>`
5. **SIGN** ulang v1+v2+v3 (uber-apk-signer + zipalign, keystore serverkernel)

## NOL sentuh (bukti)
- classes.dex sha256 IDENTIK dengan ORIGINAL
- Semua assets game / res / lib / resources.arsc (tetap STORED) identik byte-per-byte: 3662/3662
- network_security_config: cleartextTrafficPermitted=true (asli) — http 127.0.0.1 diizinkan

## Pasang
Install APK biasa. Boot pertama: kernel seed zona -> android/data/com.db.local/files/server ->
engine hidup di 127.0.0.1:4000. Dashboard owner: `adb forward tcp:4000 tcp:4000` lalu buka
http://127.0.0.1:4000/dash/api/status (atau UI Next.js port 3000 pada prototipe).

## Catatan kunci (keystore)
- storepass / keypass / alias: simpan catatan owner secara aman (file keystore JANGAN dihapus;
  kalau hilang, update di device perlu uninstall-install).

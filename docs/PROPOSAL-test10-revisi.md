# PROPOSAL — REBUILD test10 DENGAN CONFIG YANG BENAR (`test10-revisi`)

> **Status: DISETUJUI OWNER (2026-10-19, "yaa setuju") — eksekusi §5 dimulai.**
>
> Dasar arah owner (2026-10-19): *"kalau anda perlakukan HAR sebagai verifikasi saja,
> bukan alat pembuatan config — artinya tes10 bisa di ulangi dengan config yg benar.
> tapi tulis proposal dlu untuk di setujui."*

---

## 1. Diagnosis singkat — kenapa perlu diulang

Rebuild lama `e6e8ad4d` (test10) = **prematur**, dua pelanggaran terhadap aturan yang
kini terkunci (§6a, §12):

1. `QHinfo` 22→27 B di-flip ke server lokal yang **mesinnya nihil** → alamat mati.
2. 5 file `mirror/qsdk/` = **replay byte HAR** → memakai HAR sebagai pola/pembuat
   respons, melanggar "HAR = verifikasi saja".

## 2. Prinsip yang dipegang proposal ini

1. **HAR = alat VERIFIKASI saja** (aturan owner) — tidak ada byte HAR masuk build.
2. **SDK endpoint tetap ke server asli** (`qsdk.t4game.com`) sampai mesin akun siap
   (§12b #6 — flip ditahan).
3. **Satu fase = satu build test** (disiplin BUILD.md).
4. **Drain fix ≠ imitasi HAR** — itu perbaikan socket MiniServer (membaca body POST
   per `Content-Length` agar koneksi tidak putus); *prasyarat mesin* (§7c-1), dan
   **dorman** saat tidak ada POST lokal (karena SDK tidak diarahkan ke lokal).

## 3. Delta persis vs release 1.0.1 (asset `a946ef6c`)

| Item | Keadaan lama (`e6e8ad4d`) | Keadaan baru (`test10-revisi`) |
|---|---|---|
| `assets/QHinfo` | 27 B flip prematur | **22 B asli** `http://qsdk.t4game.com` (1 baris, tanpa newline; rekreasi dari entry APK release — terverifikasi audit `a2f7ace`) |
| `apk/mirror/qsdk/` (5 file replay) | ikut build | **DIHAPUS dari repo & build** (arsip panenan tetap aman di `docs/panenan-1.0.2/` sebagai dokumen VERIFIKASI) |
| `classes2.dex` | test9 + drain fix | **test9 + drain fix — TETAP**, assemble ulang dari `apk/smali` (drain fix terkonfirmasi ada: `parseCL` + `drain_loop`); `check_invoke_regs` wajib BERSIH |
| `classes.dex` | release asli | **release asli, byte-identik (`e45b3044`)** — tidak disentuh |
| Asset lain (`bs/`, `upgrade/`, `config_BS.properties`, `setting_BS_Android.bin`) | 1:1 release | 1:1 release — tidak disentuh |

## 4. Yang TIDAK dilakukan fase ini (anti overshoot)

- ❌ Tidak ada flip QHinfo ke `127.0.0.1:28110/qsdk`
- ❌ Tidak ada file config SDK / respons SDK dari file
- ❌ Tidak ada mesin akun (fase berikutnya — blueprint §7c/§8b)
- ❌ Tidak menyentuh loginserver (`popoh5:510` — ranah 1.0.3)

## 5. Langkah eksekusi (hanya setelah disetujui)

0. Pulihkan toolchain (sudah 2× pernah: smali/baksmali 2.5.2 + dexlib2/util/guava/
   jcommander dari Maven Central, uber-apk-signer) + unduh ulang asset release
   `DB-Local-1.0.1.apk` (`a946ef6c`, diverifikasi SHA).
1. `git rm` 5 file `apk/mirror/qsdk/v1/...`; tulis ulang `apk/QHinfo` = 22 B asli.
2. Assemble `classes2.dex` dari `apk/smali` → `check_invoke_regs` BERSIH →
   baksmali verifikasi hasil (`resolve` tanpa `getAssets`, drain helper ada).
3. Zip + zipalign + sign v1/v2/v3 → catat SHA-256 final di BUILD.md.
4. Tag + release **`test10-revisi`** (APK saja; nama `offline-testN` baru setelah lulus uji).
5. Update docs: §13 status koreksi selesai + BUILD.md blok build baru.

## 6. Rencana uji di perangkat owner (HAR = alat verifikasi)

1. Install → **konten game jalan lokal** (`bs/db`, `upgrade`) = perilaku 1.0.1 yang terbukti.
2. **HAR baru**: SEMUA trafik SDK harus menuju `qsdk.t4game.com` — nol request ke
   `127.0.0.1:28110/qsdk` → bukti flip prematur sudah dibatalkan.
3. Login tamu via server asli sukses (`isNewUser:0`) → main normal.
   > Catatan jujur: pada tahap ini SDK **masih butuh internet** (server asli).
   > Offline penuh = setelah mesin + flip (fase berikutnya), lalu loginserver (1.0.3).
4. (Opsional) uji drain: POST ke MiniServer tidak memutus koneksi — fondasi mesin, dorman.

## 7. Kriteria SELESAI (acceptance)

- [x] Asset diff vs 1.0.1: **nol perubahan** kecuali `classes2.dex` (delta disengaja §3 —
      test9+drain fix, 6.316 B `6e1f1e0f…`) + META-INF (re-sign); QHinfo byte sama 22 B
- [x] `classes.dex` byte-identik `e45b3044`; `classes2.dex` = test9 + drain fix, regs bersih
- [x] **0 file `qsdk` di mirror** build tree
- [ ] Uji 1–3 lulus di perangkat owner; HAR baru terlampir di docs sebagai VERIFIKASI
- [x] SHA final + tag `test10-revisi` tercatat; docs/§13 diperbarui

> Eksekusi selesai 2026-10-19 — APK `DB-Local-test10-revisi.apk`
> SHA `8f3e5597d38b76ac94c4fc7a6e2cfd41b3e59f89ad4550ce8d89e66ad38b152d` terbit di
> release `test10-revisi`. Sisa satu checkbox = uji perangkat owner (HAR baru).

## 8. Risiko & mitigasi

| Risiko | Mitigasi |
|---|---|
| Drain fix mengubah perilaku socket | sudah lulus `lab_post.py` 6/6 (semantik lama 0/6) + `check_invoke_regs` + uji perangkat |
| Rekreasi QHinfo 22 B salah byte | dibuat dari string audit `a2f7ace`; cek ukuran 22 B sebelum build; HAR baru wajib menunjukkan host `qsdk.t4game.com` |
| Toolchain hilang pasca reset sandbox | resep BUILD.md; sudah pernah diunduh ulang 2× |
| Bentrok nama rilis lama | `test10-revisi`; rilis `offline-test10` lama terbukti sudah tidak ada |

## 9. Persetujuan owner

- [x] **SETUJU** — eksekusi sesuai §5 (owner: "yaa setuju", 2026-10-19)
- [ ] Setuju dengan perubahan: ______________
- [ ] Tidak setuju — alasan: ______________

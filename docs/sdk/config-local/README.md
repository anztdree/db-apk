# config-local/ — INI CONFIG-nya (bukan jejak, ini materi yang dilayani lokal)

Folder ini berisi **config SDK final yang akan dilayani MiniServer 1.0.2 secara lokal**
(127.0.0.1:28110). Semua file adalah JSON utuh siap-layani: MiniServer membaca file ini
dan mengirim persis isinya ke SDK di dalam APK original. **Edit file di sini → langsung
efektif di APK** (tanpa penerusan ke server mana pun, bukan hasil capture).

| File | Endpoint yang dilayani | Peran |
|---|---|---|
| `system/init.json` | `POST /v1/system/init` | **OTAK** — semua saklar UI/login/update/pembayaran |
| `system/getNotice.json` | `POST /v1/system/getNotice` | Pengumuman (popup notice) |
| `system/getAgreement.json` | `POST /v1/system/getAgreement` | Teks perjanjian (semua kosong = tak ada dialog) |
| `user/ckRegVistor.json` | `POST /v1/user/ckRegVistor` | Cek status registrasi guest per perangkat |
| `user/registerVisitor.json` | `POST /v1/user/registerVisitor` | **SESI LOGIN guest** (Gerbang 4) |

## Aturan edit (terbukti dari dex original — salah format = SDK tolak)

1. **Jangan hapus kunci wajib.** `init.json`: `payTypes` (boleh `[]`, tapi kuncinya harus
   ada), `productConfig`, `version`, `loginTypes`, `nodes`. `registerVisitor.json`:
   `authToken`, dan `userData.{uid, username, token, isGuest}` — hilang salah satu →
   parser SDK lempar exception → login gagal.
2. **`productConfig` dan `version` dibaca sebagai STRING.** Tulis `"0"`/`"1"` (kutip),
   bukan angka. `isShowFloat` kebalikan: `"0"` = tampil, `"1"` = sembunyi.
3. **`getNotice.json`:** `data` harus berupa **OBJECT** `{content, showNode}`.
   Kalau `data` berupa `[]` (array) SDK abaikan (tanpa notice) — itu perilaku normal.
4. **`version` = "empty" untuk semua** → cek update dianggap tidak ada (aman).
   Kalau mau memaksa dialog update: isi `versionNo` (angka > versionCode APK) + `isMust`.
5. **`loginTypes`:** `"1"` = guest (WAJIB ADA untuk login guest), `"13"` = akun cepat.
   Hapus elemen lain bila ingin menyembunyikan FB/Google/dll dari layar login.
6. **`requestIp` bebas diisi** — tidak dipakai koneksi; hanya muncul di API game
   `getCountryInfo()` sebagai `"<channel>|<requestIp>"`.
7. **JSON boleh di-pretty-print** seperti di folder ini — parser SDK pakai `org.json`,
   whitespace tidak berpengaruh. Nilai `ISI-...` pada `registerVisitor.json` adalah
   PLACEHOLDER: diisi dari **material panen live Tahap 2** (sesi asli per perangkat).

## Status material per file

| File | Status material | Sumber |
|---|---|---|
| `system/init.json` | ✅ FINAL — isi = respons asli server hidup (2026-10-20), `requestIp` disunting jadi `127.0.0.1` (tidak dipakai koneksi) | panen live |
| `system/getNotice.json` | ✅ FINAL — versi kosong = identik respons server hidup (55 B) | panen live |
| `system/getAgreement.json` | ✅ FINAL — semua field kosong = identik respons server hidup (188 B) | panen live |
| `user/ckRegVistor.json` | ✅ FINAL — `isReged:1` identik respons server hidup | panen live |
| `user/registerVisitor.json` | ⏳ TEMPLATE — format terbukti dari parser dex; **nilai diisi dari panen live Tahap 2** (butuh 1 capture login dari perangkat user sebagai tiket) | dex + (menunggu tiket) |

Detail field-per-field & bukti instruksi: lihat [`../CONFIG-SDK-ASLI.md`](../CONFIG-SDK-ASLI.md).

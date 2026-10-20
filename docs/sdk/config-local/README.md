# config-local/ — INI CONFIG-nya (bukan jejak, ini materi yang dilayani lokal)

Folder ini berisi **config & sesi SDK final yang dilayani MiniServer 1.0.2 secara lokal**
(127.0.0.1:28110). Semua file adalah JSON utuh siap-layani: MiniServer membaca file ini
dan mengirim persis isinya ke SDK di dalam APK original. **Edit file di sini → langsung
efektif di APK** (tanpa penerusan ke server mana pun).

> Prinsip (per user): **capture = alat bantu saja, bukan acuan pondasi — termasuk
> login/register/guest.** Pondasinya = jawaban ASLI server hidup. Semua nilai di bawah
> dipanen LIVE dari server asli (2026-10-20) memakai tiket request asli buatan APK;
> bukti leg A ≡ leg B byte-identik (8/8) di `../bukti/panen-gerbang4/`.

| File | Endpoint yang dilayani | Peran |
|---|---|---|
| `system/init.json` | `POST /v1/system/init` | **OTAK** — semua saklar UI/login/update/pembayaran |
| `system/getNotice.json` | `POST /v1/system/getNotice` | Pengumuman (popup notice) |
| `system/getAgreement.json` | `POST /v1/system/getAgreement` | Teks perjanjian (semua kosong = tak ada dialog) |
| `user/ckRegVistor.json` | `POST /v1/user/ckRegVistor` | Cek status registrasi guest per perangkat |
| `user/registerVisitor.json` | `POST /v1/user/registerVisitor` | **SESI GUEST** (Gerbang 4) — materi LIVE |
| `user/registerUser.json` | `POST /v1/user/registerUser` | **SESI REGISTER** (guest → akun) — materi server asli, aturan satu-kali |
| `user/loginByName.json` | `POST /v1/user/loginByName` | **SESI LOGIN** (username+password) — materi LIVE |
| `auth/getUserInfo.json` | `POST /v1/auth/getUserInfo` | Info profil user bersesi — materi LIVE |
| `game/loginchecknative.json` | `GET <loginServer>/loginchecknative` | **Jawaban verifikasi game** — format terbukti LIVE |

## Aturan edit (terbukti dari dex original — salah format = SDK tolak)

1. **Jangan hapus kunci wajib.** `init.json`: `payTypes` (boleh `[]`, tapi kuncinya harus
   ada), `productConfig`, `version`, `loginTypes`, `nodes`.
   Sesi (`registerVisitor`/`registerUser`/`loginByName`): `authToken` dan
   `userData.{uid, username, token, isGuest}` — hilang salah satu → parser SDK lempar
   exception → login gagal (bukti parser `QGUserData.generateFromJson`).
2. **`productConfig` dan `version` dibaca sebagai STRING.** Tulis `"0"`/`"1"` (kutip),
   bukan angka. `isShowFloat` kebalikan: `"0"` = tampil, `"1"` = sembunyi.
   (Catatan: `isGuest` di sesi justru berupa ANGKA di respons asli — `getString`
   mengkoersi otomatis; ikuti format asli.)
3. **`getNotice.json`:** `data` harus berupa **OBJECT** `{content, showNode}`.
   Kalau `data` berupa `[]` (array) SDK abaikan (tanpa notice) — itu perilaku normal.
4. **`version` = "empty" untuk semua** → cek update dianggap tidak ada (aman).
   Kalau mau memaksa dialog update: isi `versionNo` (angka > versionCode APK) + `isMust`.
5. **`loginTypes`:** `"1"` = guest (WAJIB ADA untuk login guest), `"13"` = akun cepat.
   Hapus elemen lain bila ingin menyembunyikan FB/Google/dll dari layar login.
6. **`requestIp` bebas diisi** — tidak dipakai koneksi; hanya muncul di API game
   `getCountryInfo()` sebagai `"<channel>|<requestIp>"`.
7. **JSON boleh di-pretty-print** — parser SDK pakai `org.json`, whitespace tidak
   berpengaruh.

## Sifat sesi (TERBUKTI dari panen live 2026-10-20)

| Sifat | Bukti | Konsekuensi utk local |
|---|---|---|
| **Sesi guest TERIKAT PERANGKAT & DAPAT DIULANG** | tiket registerVisitor sama → server jawab sesi SAMA (uid `28486093`, token sama persis) | aman dilayani lokal statis per perangkat |
| **loginchecknative MENERIMA token asli berulang kali** | GET sama → `code:0` tiap panen (3 mode semua lolos); `loginToken` berubah tiap panggil, `sign` stabil per user | sesi asli = tiket sah menuju server login game |
| **registerUser = SATU-KALI (state-bound)** | panen ulang → `{"result":false,"error":{"id":40008,"message":"pengguna ini telah digunakan"}}` | utk local: layani format sukses sekali; panggilan berikutnya boleh jawab sesi (sudah terdaftar) |
| **loginByName DAPAT DIULANG** | panen ulang → sesi akun sama (uid `28506361`, isGuest 0) | aman dilayani lokal statis |
| **uid antar perangkat berbeda** | `28486093` (device lama) vs `28506361` (akun register) | nilai di file ini = milik perangkat uji user; perangkat lain → panen tiket sendiri (metode bertahap) |

## Status material per file

| File | Status material | Sumber |
|---|---|---|
| `system/init.json` | ✅ FINAL | panen live 2026-10-20 (requestIp disunting `127.0.0.1`, tidak dipakai koneksi) |
| `system/getNotice.json` | ✅ FINAL | identik respons server hidup (55 B) |
| `system/getAgreement.json` | ✅ FINAL | identik respons server hidup (188 B) |
| `user/ckRegVistor.json` | ✅ FINAL | identik respons server hidup |
| `user/registerVisitor.json` | ✅ FINAL — materi LIVE Gerbang 4 | panen live (2345 B, leg A≡B) |
| `user/registerUser.json` | ✅ FINAL — respons sukses asli (satu-kali, leg A≡B) | sesi perangkat user |
| `user/loginByName.json` | ✅ FINAL — materi LIVE | panen live (2429 B) |
| `auth/getUserInfo.json` | ✅ FINAL — materi LIVE (varian guest; varian terdaftar ada di bukti) | panen live |
| `game/loginchecknative.json` | ✅ FINAL — format jawaban `code:0` (live, 3 mode lolos) | panen live |

Detail field-per-field & bukti instruksi: [`../CONFIG-SDK-ASLI.md`](../CONFIG-SDK-ASLI.md).

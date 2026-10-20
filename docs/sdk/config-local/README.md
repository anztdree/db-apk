# config-local/ — INI CONFIG-nya (config + GENERATOR sesi yang dilayani lokal)

Folder ini berisi **config & template sesi SDK yang dilayani MiniServer 1.0.2 secara
lokal** (127.0.0.1:28110). Dua jenis isi:

1. **Config statis** (`system/…`, `ckRegVistor`): JSON utuh siap-layani — MiniServer
   mengirim persis isinya. **Edit → langsung efektif.**
2. **Template GENERATOR** (`registerVisitor.json`, `loginchecknative.json`):
   berisi placeholder `{{…}}` yang diisi MiniServer saat melayani —
   **token/uid DIBANGKITKAN BARU**, bukan dipungut dari sisa jejak panen
   *(koreksi user 2026-10-20: kalau config benar → generate token baru).*

> Prinsip (per user): **capture = alat bantu saja, bukan acuan pondasi — termasuk
> login/register/guest.** Panen live server asli (2026-10-20, `../bukti/panen-gerbang4/`)
> berfungsi sebagai ACUAN FORMAT + referensi perilaku server; isi sesi = hasil generator
> lokal. Basis sahnya: token opaque di seluruh rantai klien — parser SDK hanya `getString`
> tanpa validasi isi, gerbang game hanya membaca `code`
> (bukti: `../bukti/riset-login/dump-generator-basis.txt`).

| File | Endpoint yang dilayani | Peran |
|---|---|---|
| `system/init.json` | `POST /v1/system/init` | **OTAK** — semua saklar UI/login/update/pembayaran |
| `system/getNotice.json` | `POST /v1/system/getNotice` | Pengumuman (popup notice) |
| `system/getAgreement.json` | `POST /v1/system/getAgreement` | Teks perjanjian (semua kosong = tak ada dialog) |
| `user/ckRegVistor.json` | `POST /v1/user/ckRegVistor` | Cek status registrasi guest per perangkat |
| `user/registerVisitor.json` | `POST /v1/user/registerVisitor` | **SESI GUEST** (Gerbang 4) — **TEMPLATE GENERATOR** (uid/token dibangkitkan) |
| `user/registerUser.json` | `POST /v1/user/registerUser` | **SESI REGISTER** (guest → akun) — materi server asli, aturan satu-kali |
| `user/loginByName.json` | `POST /v1/user/loginByName` | **SESI LOGIN** (username+password) — materi LIVE |
| `auth/getUserInfo.json` | `POST /v1/auth/getUserInfo` | Info profil user bersesi — materi LIVE |
| `game/loginchecknative.json` | `GET <loginServer>/loginchecknative` | **Jawaban verifikasi game** — **TEMPLATE GENERATOR** (bentuk 10 field live, nilai baru) |

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

## Aturan placeholder generator (token BARU, bukan sisa jejak)

Placeholder `{{…}}` diisi MiniServer saat request datang — semua bisa diatur user lewat
config:

| Placeholder | Diisi dengan | Aturan (terbukti dari dex) |
|---|---|---|
| `{{UID}}` | uid lokal per perangkat | angka; dibangkitkan sekali per perangkat (persisten di external files) supaya progres konsisten; bisa DIKUNCI user dengan nilai tetap di config |
| `{{USERNAME}}` | nama user | pola bebas; default `player{{UID}}` (mesin asli: `cz90406643` gaya acak — tidak diwajibkan) |
| `{{TOKEN}}` | **token BARU per sesi** | format BEBAS — parser SDK hanya `getString`, tanpa validasi isi (bukti `generateFromJson`) |
| `{{AUTH_TOKEN}}` | token sesi SDK | format BEBAS — disimpan apa adanya oleh parser sesi |
| `{{LOGIN_TOKEN}}` | token jawaban gerbang | baru per request (mis. md5 acak); game tidak membacanya di jalur gerbang, JS menerimanya via event startGame |
| `{{SIGN}}` | tanda jawaban gerbang | pola live: STABIL per uid (register & login uid sama → sign sama) → default `md5(uid + salt)`, salt diatur user |

**Kunci wajib tetap tidak boleh hilang:** `authToken`, `userData.{uid, username, token,
isGuest}` (parser melempar exception bila absen) — nilai boleh apa pun (hasil generator).

## Sifat sesi (TERBUKTI dari panen live 2026-10-20)

| Sifat | Bukti | Konsekuensi utk local |
|---|---|---|
| **Sesi guest TERIKAT PERANGKAT & DAPAT DIULANG** | tiket registerVisitor sama → server asli jawab sesi SAMA (uid `28486093`, token sama persis) | konsep identitas per-perangkat STABIL diadopsi generator lokal (uid persisten) — nilainya dibangkitkan sendiri, bukan disalin |
| **loginchecknative MENERIMA token asli berulang kali** | GET sama → `code:0` tiap panen (3 mode semua lolos); `loginToken` berubah tiap panggil, `sign` stabil per user | validasi gerbang bersifat per-sesi → generator lokal memegang sesinya sendiri: token BARU per sesi, uid stabil, jawaban `code:0` |
| **registerUser = SATU-KALI (state-bound)** | panen ulang → `{"result":false,"error":{"id":40008,"message":"pengguna ini telah digunakan"}}` | utk local: layani format sukses sekali; panggilan berikutnya boleh jawab sesi (sudah terdaftar) |
| **loginByName DAPAT DIULANG** | panen ulang → sesi akun sama (uid `28506361`, isGuest 0) | aman dilayani lokal statis |
| **uid antar perangkat berbeda** | `28486093` (device lama) vs `28506361` (akun register) | generator lokal juga membangkitkan uid sendiri per perangkat — tidak perlu panen per perangkat lagi |

## Status material per file

| File | Status material | Sumber |
|---|---|---|
| `system/init.json` | ✅ FINAL | panen live 2026-10-20 (requestIp disunting `127.0.0.1`, tidak dipakai koneksi) |
| `system/getNotice.json` | ✅ FINAL | identik respons server hidup (55 B) |
| `system/getAgreement.json` | ✅ FINAL | identik respons server hidup (188 B) |
| `user/ckRegVistor.json` | ✅ FINAL | identik respons server hidup |
| `user/registerVisitor.json` | ✅ **TEMPLATE GENERATOR** (placeholder terisi runtime) | bentuk field = persis respons live 2345 B (leg A≡B); isi = generate |
| *(referensi sesi asli)* | 📎 Arsip panen live | `../bukti/panen-gerbang4/live__user__registerVisitor.bin` |
| `user/registerUser.json` | ✅ FINAL — respons sukses asli (satu-kali, leg A≡B) | sesi perangkat user |
| `user/loginByName.json` | ✅ FINAL — materi LIVE | panen live (2429 B) |
| `auth/getUserInfo.json` | ✅ FINAL — materi LIVE (varian guest; varian terdaftar ada di bukti) | panen live |
| `game/loginchecknative.json` | ✅ **TEMPLATE GENERATOR** — bentuk 10 field `code:0` (live, 3 mode lolos); nilai generate | panen live sebagai acuan bentuk |

Detail field-per-field & bukti instruksi: [`../CONFIG-SDK-ASLI.md`](../CONFIG-SDK-ASLI.md).

# BUILD 1.0.4 — DATABASE AKUN + VALIDASI PASSWORD (setia server asli)

## 0. Pemicu (keberatan user, kutipan)

> "astagaaa ini gak asal work lah .. ini kan masuk ranah database !! password cocok login !!"

Keberatan BENAR. Audit kode 1.0.3 (jujur): `loginByNameJson`/`registerUserJson`
hanya mengambil `username` dari envelope — **password dibaca lalu dibuang**,
tidak ada penyimpanan akun, satu uid global untuk "semua akun". Username apa
pun + password apa pun = "sukses". Itu TIDAK setia server asli. 1.0.4
memperbaiki dengan bukti, bukan karangan.

## 1. Bukti (lihat dulu, baru tulis)

| # | Bukti | Isi |
|---|-------|-----|
| B1 | dex `com/quickgame/android/sdk/service/dishbesideshockey$ironoriginhoblike.run` | const-string params `registerUser`: **`vcode`, `username`, `password`** |
| B2 | dex `dishbesideshockey$gangclothing.run` | const-string params `loginByName`: **`username`, `password`** |
| B3 | Envelope live panen `fixture/envelopes.json` (registerUser + loginByName) | `password` = `22dc0c1e8cb89b5d906baf15a8fe3fbb` — **MD5 hex klien** |
| B4 | HAR perangkat #28 (`log/sdk.har`) | `loginByName` → `password` = `e10adc3949ba59abbe56e057f20f883e` = md5("123456"); konfirmasi B3 |
| B5 | HAR #32 gerbang | `uid=16722253&username=yihonew48…` — **uid PER AKUN**, sign = md5(uid+salt) |
| B6 | HAR #42 `getUserInfo` | params `{authToken, uid}` → server bisa jawab per sesi/akun |
| B7 | Panen error live `panen-gerbang4/live__user__registerUser.bin` (40008) | bentuk penolakan: `{"result":false,"data":[],"error":{"id":…,"message":…}}` |
| B8 | dex parser `registerVisitor`/`autoLogin` | `result==false` → response utuh ke UI error → SDK tampilkan pesan |

Kesimpulan desain: server asli = database akun (username+password), uid per
akun, login membandingkan password. SDK mengirim password sebagai MD5 → server
lokal berhak menyimpan/membandingkan hash, bukan plaintext.

## 2. Database akun — `accounts.txt` (external/server/, TSV)

```
# DB akun lokal (1.0.4) — username<TAB>passHash<TAB>uid<TAB>isGuest
Guest66912875		66912875	1
vifen44624@juicehour.com	dc9253870e1856efc64fd7de97f765b3	21027390	0
```

- **passHash = md5(password_dari_SDK + salt)** — password klien tidak pernah
  disimpan mentah (bukan plaintext, bukan MD5 klien apa adanya).
- passHash kosong = akun guest (tidak bisa login by name).
- **uid 8-digit UNIK per akun** (pola server asli, B5).
- Seed pertama: 1 baris guest (uid/username generator) → uid guest stabil
  antar boot, persis 1.0.1–1.0.3.
- Edit user dihormati; `copyDir` re-sync tidak menimpa file ada; **re-sync
  versi (full wipe) kini MENJAGA accounts.txt** + generator.json (bug potensial
  ditemukan & diperbaiki di 1.0.4 — sebelumnya full wipe menghapus segalanya
  kecuali generator.json).

## 3. Kebijakan login/register (setia server asli)

| Kasus | Jawaban |
|---|---|
| `loginByName` username tak terdaftar | `result:false` id=2 "username atau password salah" (bentuk B7) → SDK tampilkan pesan |
| `loginByName` password salah | sama (id=2) — pesan SAMA, tidak membocorkan mana yang salah |
| `loginByName` username guest | ditolak (id=2) — guest tak punya password |
| `loginByName` username+password cocok | sesi akun itu: uid AKUN, isGuest=0, token baru |
| `registerUser` username sudah ada | `result:false` id=3 "username sudah terdaftar, silakan login" |
| `registerUser` username/password kosong | `result:false` id=4 "data tidak lengkap" |
| `registerUser` baru | baris akun baru + uid unik + sesi akun (isNewUser=1) |
| `autoLogin` authToken = terbitan terakhir | sesi akun itu dipulihkan **termasuk lastUid** (Opsi A 1.0.3 utuh) |
| `autoLogin` token asing/kadaluarsa | `result:false` id=1 → layar login (1.0.3) |
| `getUserInfo` | per sesi: authToken cocok last* → lastUsername; else uid → akun; else generator |
| gerbang `loginchecknative` | tetap echo uid/username query + sign md5(uid+salt) — kini uid per akun |

Catatan jujur: ISI pesan error (id 1–4) = **kebijakan lokal**; BENTUKNYA dari
panen error live 40008 (B7) via template `qsdk/v1/user/error.template` (baru)
dan `autoLogin.error.template` (1.0.3). Tidak ada materi live error
login/register — SDK hanya membaca `result` + menampilkan `message`.

## 4. Perubahan teknis

- `mirror/qsdk/v1/user/error.template` — BARU: bentuk live 40008, id/message → placeholder.
- `mirror/generator.json` — + `lastUid` (sesi akun antar boot).
- `mirror/VERSION` — `1.0.4` (pemicu re-sync penuh).
- `DbGen.java` — database akun (`accounts.txt`), validasi password
  (double-hash + salt), uid per akun, sesi per akun (authToken BARU per
  respons sesi — rotasi seperti server asli), `lastUid`, getUserInfo per sesi.
- `MiniServer.java` — rute `getUserInfo` kini meneruskan body; full-wipe
  re-sync menjaga `generator.json` + `accounts.txt`.
- Migrasi 1.0.2/1.0.3 → 1.0.4: `lastUid` kosong + lastUsername ada →
  `lastUid = uid` (era satu-uid); accounts.txt hilang → seed guest row.

## 5. Uji JVM — TestServer4: 42 PASS / 0 FAIL

Warisan (tetap lulus): statik byte-exact ×7, setting bin gerbang lokal, gate
echo+sign, uid guest stabil lintas boot/re-sync, autoLogin Opsi A lintas boot,
404/tolak `..`, envelope rusak ditolak.

BARU 1.0.4 (inti):
- registerUser akun baru → **uid baru ≠ uid guest**; duplikat ditolak (id=3);
  tanpa password ditolak (id=4)
- loginByName password cocok → uid AKUN; **password salah → ditolak (id=2)**;
  username asing → ditolak; username guest → ditolak
- autoLogin token terbitan → sesi akun (uid akun) + rotasi; token asing → id=1
- getUserInfo per akun (uid path) + fallback generator
- accounts.txt: seed 2 baris, TSV 4 kolom, **passHash = md5(klien+salt)**
  terverifikasi recompute, bukan nilai mentah klien
- re-sync penuh: **DB akun BERTAHAN** (bug full-wipe diperbaiki)
- lintas boot: autoLogin sesi guest & sesi AKUN dipulihkan; loginByName akun
  → uid SAMA
- **REPLIKA HAR #28 PERSIS**: `yihonew485@juicehour.com` + md5("123456") di
  server segar → DITOLAK (dulu 1.0.3 "sukses" palsu) → register → login ulang
  → sukses (perilaku server asli dgn DB baru)
- gerbang per akun: uid vifen → sign = md5(uid vifen + salt) ≠ sign guest

## 6. Provenance APK

| | |
|---|---|
| File | `DB-Local-1.0.4.apk` (internal; **nama rilis tetap `DB-Local-1.0.2.apk`** — versioning tetap permintaan user) |
| SHA256 | `05d17aaf9d6c76071b1739f0f0fbe58138819cab8f226acae18b22e6d5942ad2` |
| Sertifikat | `1E:08:A9:03:AE:F9:C3:A7:21:51:0B:64:EC:76:4D:01:D3:D0:94:EB:95:41:61:B6:25:44:EA:8F:18:7B:59:53` — IDENTIK 1.0.1/1.0.2/1.0.3 → install-over aman |
| classes2.dex | 23.312 B (1.0.3: ~6 KB → tumbuh oleh database akun) |
| Kebocoran identitas panen/device | NOL (dicek otomatis saat packaging) |
| classes.dex | TIDAK disentuh (injeksi MiniServer.start() warisan 1.0.1) |

## 7. Yang TIDAK berubah

Port 28110, gerbang lokal via setting bin, dunia game JS di luar pintu (R1'
pending), config boot SDK, statik live byte-exact.

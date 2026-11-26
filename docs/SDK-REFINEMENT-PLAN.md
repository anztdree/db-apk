# SDK REFINEMENT — RENCANA (PLAN ONLY, MENUNGGU KEPUTUSAN OWNER)

> Arahan owner 25 Nov 2026: "…dan penyempurnaan sdk jika diperlukan" —
> dokumen ini = analisis gap + daftar penyempurnaan OPSIONAL dengan prioritas.
> Prinsip (pelajaran kesalahan 2): **SDK fokus di SDK** — tidak menyentuh
> loginServer/serverlist sama sekali. Prinsip REAL-ONLY: item dikerjakan
> HANYA bila ada bukti kebutuhan (log device), bukan karangan fitur.
>
> Basis bukti: SdkHandler.java v19 (kode nyata), verify_total.txt §C (katalog
> 33 endpoint dari smali), LIVE_CONTRACT_20261123.md (server asli masih hidup
> 23 Nov — katalog error 20000/20002/20003/30001), SDK-STUDY.md (peta kelas +
> crash NPE productConfig 23 Nov), log device v17 (init → getNotice →
> registerVisitor → loginchecknative — semua SUKSES).

---

## 1. KONDISI SDK SEKARANG (v19) — YANG SUDAH BUKTI JALAN

Rantai nyata log device (v17, urutan persis):
`/v1/system/init` (loginTypes ["1"]) → `/v1/system/getNotice` →
`/v1/user/registerVisitor` (Guest6299 + token) →
`5050/loginchecknative` (thirdParams lengkap) → JS engine lanjut.

Fitur hidup yang sudah terimplement (SdkHandler.java):
| Kelompok | Endpoint | Status |
|---|---|---|
| Gerbang | `/v1/system/init` | mirror PENUH original (init.json bisa diedit owner) |
| Tamu | `/v1/user/ckRegVistor`, `/v1/user/registerVisitor` | kontrak file + SQLite |
| Email | `/v1/user/registerUser`, `loginByName`, `autoLogin`, `/v1/auth/getUserInfo`, `bindMail`, `changePassword`, `findPassByEmail`, `sendCodeByEmail` | jalan (log device 23 Nov 06:08 sukses) |
| Kosong-kontrak | `getNotice`, `dmsg`, `onNewToken`, `getAgreement`, `setGameRoleInfo`, `getServerAgreement` | OK_EMPTY (cukup — kontrak UI tidak butuh isi) |
| Gerbang kontrak | data→sign→must-params→product (20000/20002/20003/30001 urutan live) | mirror live |
| Sisanya (20 path) | `fallback.json` = result:false 20000 | aman — UI SDK menampilkan error, game tidak crash |

Crash-safety sudah digarap sejak v2: seed DEFAULT init.json = mirror PENUH
(NPE productConfig 23 Nov 21:49 sudah akar: model/d.worktopichardtails),
SessionReset (info.qg + qg_Users) putus siklus crash auto-login overlay
(window 2008), OverlayFixer banner izin.

**Kesimpulan: untuk alur yang di-oke owner (TAMU + email), SDK TIDAK
butuh penyempurnaan wajib. Semua di bawah = OPSIONAL.**

---

## 2. GAP — 20 ENDPOINT YANG MASIH KE FALLBACK (20000)

Dari katalog 33 (verify_total.txt §C) dikurangi yang terimplement = 20:

### 2.1 Prioritas RENDAH — sekadar pesan error yang lebih jelas (kosmetik error)
Endpoint ini punya tombol di UI SDK/game, tapi gagal-nya SUDAH graceful
(result:false → pesan error di UI, tanpa crash):
| Endpoint | Pemicu UI | Respons fallback sekarang | Penyempurnaan OPSIONAL bila owner mau |
|---|---|---|---|
| `/v1/user/actCdKey` | tombol CDKey game | 20000 Params error | pesan lokal jelas: "CDKey tidak tersedia di server lokal" (id 20005 lokal, pola findPassByEmail) |
| `/v1/auth/unBindOtherInfo` | Pusat Akun (lepas ikatan) | 20000 | pesan: tidak ada ikatan lain di server lokal |
| `/v1/auth/cUserTrash` | hapus akun | 20000 | pesan: gunakan clear data untuk reset akun lokal |
| `/v1/user/getServerAgreement` | perjanjian server | OK_EMPTY sudah | — (sudah cukup) |

### 2.2 Prioritas RENDAH — hanya relevan bila owner mengubah `loginTypes`
| Endpoint | Kapan dipanggil | Syarat jadi perlu |
|---|---|---|
| `/v1/auth/viewMigCode` | tombol Kode Migrasi (loginTypes `["1","13"]` — mirror server asli) | owner ganti loginTypes + mau alur migrasi; **OPEN**: format kode migrasi lokal belum ada bukti (tak pernah sukses di live — product DB kosong 30001) |
| `/v1/user/userLoginByOtherSdk`, `/v1/auth/cksnslogin` | login pihak ketiga | hanya bila loginTypes ditambah tipe SNS — tidak direncanakan |
| `/v1/auth/fbActInit`, `fbInviteEvent`, `fbLikeEvent`, `fbShareEvent`, `fbUserClaimEvent` | event Facebook | tidak direlevanikan (offline lokal) |

### 2.3 TIDAK PERLU — memang mati by design (bukan gap)
| Endpoint | Alasan |
|---|---|
| `/v1/auth/createOrder`, `/v2/purchase`, `/v1/user/postGooglePlayVerify` | pembayaran — productConfig lokal sudah mematikan jalannya (`adZhifuSwt:"0"`, `useSms:"0"`, `single_play:"0"`, `initCZurl:""`) |
| `/v1/auth/jianchaLayLx` | tak pernah muncul di log device MOD (**OPEN** — biarkan fallback sampai ada log nyata) |
| `/v1/auth/userBindOtherSdk` | gabungan SNS — idem §2.2 |

---

## 3. KOSMETIK DI LUAR PROTOKOL SDK — 404 LAYAR LOGIN (fakta log v17)

Log v17 mencatat 6 file 404 di pohon `bs/` saat layar login tampil:
`denglujiemianxuanzhejiemiannew21_png` (gambar layar pilih login),
`Instagram_png`, `Telegram_png`, `Discord_png` (ikon sosial — JS openUrlStr
discord.gg/t.me AlphaGamingIran), `Logo18+Full_png`, dan
`resource/assets/image/public/sound/sound_click.mp3` (suara klik UI).

Dampak: gambar kosong/pecah + suara klik tidak bunyi. TIDAK menghalangi login.
File ini TIDAK ada di assets APK (ekstraksi tiap boot menimpa `bs/` — edit
manual `bs/` hilang saat reboot).

PEMBUKTIAN ULANG 26 Nov (Bagian 69, jawaban pertanyaan owner): file-file ini
juga TIDAK ADA di DALAM all.zip (652 entri di-parse via HTTP Range dari
dalam APK — isi: js/ 12 + resource/ 639 + resource.version; nol
Instagram_png/Telegram_png/Discord_png/denglujiemian…new21/Logo18) dan
TIDAK ADA di manapun di repo/capture = file yang TIDAK PERNAH kita punya.
Sesuatu yang tidak pernah ada tidak bisa dilayani 200 oleh server manapun
(asli maupun lokal) — 404 ini bukan cacat server, ini file yang hilang dari
sumbernya sejak awal (perlu DIBUAT placeholder bila mau diisi).

Dua opsi penyempurnaan (menunggu keputusan owner, keduanya butuh iterasi build):
1. **Opsi A (APK)**: suntik 6 file placeholder PNG/MP3 ke assets APK pada path
   persis di atas → ikut ekstraksi otomatis tiap boot.
2. **Opsi B (server, tanpa APK)**: StaticHandler memetakan path 404 image/audio
   itu ke placeholder yang SUDAH ada di `bs/` (aturan statis di kode — daftar
   pathnya dari log nyata, bukan pattern-match liar).

Rekomendasi: **tunda** — kosmetik murni; kerjakan sekali dengan iterasi build
v20 bila owner mau.

---

## 4. KEPUTUSAN YANG DIMINTAK KE OWNER

| # | Keputusan | Default bila tidak diputuskan |
|---|---|---|
| D1 | Lanjut bangun config loginServer sesuai docs/LOGIN-SERVER-PLAN.md §4? (ini kerja loginServer, bukan SDK) | tidak — tetap plan |
| D2 | SDK: kerjakan §2.1 (pesan error CDKey/unbind/trash)? | tidak — fallback sudah aman |
| D3 | SDK: ganti `loginTypes` `["1"]` → `["1","13"]` (tombol Kode Migrasi seperti server asli)? | tidak — TAMU+email sudah cukup; migrasi butuh bukti format kode |
| D4 | Kosmetik 404 layar login (§3) — Opsi A atau B? | tunda ke iterasi berikutnya (Bagian 69 menambah bukti: file TIDAK ADA juga di all.zip — kalau dikerjakan = HARUS buat placeholder, bukan "mengambil" file) |
| D5 | `history` config login: kosong (auto-enter) vs diisi (perilaku **OPEN** — perlu uji device dulu) | kosong (perilaku terbukti v17: auto-enter) |

## 5. ATURAN YANG TETAP (disiplin berjalan)

1. Nol implementasi tanpa oke owner (loginServer DAN penyempurnaan SDK).
2. Setiap perubahan SDK baru dikerjakan setelah ada log device yang
   memperlihatkan kebutuhan nyata (REAL-ONLY).
3. Log tetap: URL penuh tanpa pangkas, gaya Inggris natural, data real saja.
4. Dashboard tetap: hanya filter error + salin, SVG icon, nol emoji, detail
   inline — TIDAK ada tombol baru untuk SDK maupun login.

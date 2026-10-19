# Rencana 1.0.2 — SDK & Login Server Lokal (Roadmap README §9 Tahap E)

> **Posisi awal**: 1.0.1 stabil (rilis default) = boot → unduh/ekstraksi → layar login → login SDK → engine jalan,
> dengan CDN 100% lokal. Yang masih ke internet = **perilaku asli** yang kebetulan masih hidup:
> `qsdk.t4game.com` (SDK), `login.popoh5.com:510` (SDK login), `login.popoh5.com:610` (login server socket.io), telemetry.
>
> **Tujuan 1.0.2**: lapisan SDK + login server **dijawab mini server lokal**, tanpa mengubah satu byte klien/game.
> Telemetry (appsflyer/facebook) **tidak masuk lingkup** — tidak memengaruhi fungsi.

---

## 0. Disiplin (sama dengan 1.0.1 — tidak berubah)

1. **Redirection murni**: hanya ganti ALAMAT via mekanisme aplikasi sendiri; klien & pola client-server 100% utuh.
2. **Bukti dulu, implementasi kemudian**: semua respons yang disimulasikan HARUS panenan asli (atau hasil bedah protokol), bukan karangan.
3. **Satu fase = satu build test** (test10, test11, …), lulus kriteria verifikasi baru lanjut.
4. `check_invoke_regs.py` wajib lulus sebelum SETIAP build (Pelajaran 5).

---

## 1. Bukti baru (riset 2026-10-19, langsung dari smali original + HAR test9)

### 1a. Alamat SDK login (:510) BERSUMBER dari setting bin — milik kita

Rantai bukti smali (original `classes.dex`, obfuscated):

```
LaunchActivity (parser JSON setting bin):
  "loginServer"   → ironoriginhoblike.customreading     (String host)
  "loginPort"     → ironoriginhoblike.hoboismrelationbelow (int)
  "loginWebPort"  → ironoriginhoblike.tidyleadership    (int)
  "tsLoginServer" → ironoriginhoblike.gangclothing      (override mode-2/test)

ironoriginhoblike.seventygenom()  // = base URL SDK web
  = String.format(Locale.ENGLISH, "%s:%d", loginServer, loginWebPort)

nationalcommunitymissing (SDK login runner):
  url = String.format("%s/loginchecknative?%sos=Android&sdk=%s",
                      seventygenom(), query(uid,usermode,username,token,…), "BS")
  → dibuktikan logcat test9: "sdk登录验证url:https://login.popoh5.com:510/loginchecknative?uid=…"
```

### 1b. Alamat login server (:610) JUGA dari setting bin

`MainActivity` menyuntik `loginServer` + `loginPort` ke JS → logcat test9:
`JS:INFO:serverurl:https://login.popoh5.com:610` (= `loginServer`:`loginPort`).

> **Implikasi**: mengubah NILAI `loginServer` / `loginPort` / `loginWebPort` di setting bin
> (satu file yang memang sudah kita ganti arahnya — preseden `url` + `update` di 1.0.1)
> otomatis mengarahkan seluruh lapisan SDK + login server ke mini server.
> **Tidak ada kode yang disentuh.** Inilah jalan redirection murni untuk 1.0.2.

### 1c. Cleartext ke 127.0.0.1 diizinkan (tanpa menyentuh manifest)

- `res/xml/network_security_config.xml` original: `base-config cleartextTrafficPermitted` (= true —
  wajib, karena config server original pun HTTP).
- Preseden terbukti: test9 sukses `http://127.0.0.1:28110/...` dari stack native.
- JS/socket.io jalan di runtime native yang sama → cleartext `http://127.0.0.1` diperbolehkan.

### 1d. Panenan respons nyata (sudah disimpan di `docs/panenan-1.0.2/`)

| File | Endpoint | Ukuran | Sifat |
|---|---|---|---|
| `qsdk_v1_system_init.json` | POST `qsdk/v1/system/init` | 1.139 B | konfigurasi SDK statis (payTypes kosong, productConfig) |
| `qsdk_v1_system_getNotice.json` | POST `qsdk/v1/system/getNotice` | 83 B | `{"result":true,"data":[],…}` — trivial statis |
| `qsdk_v1_user_registerVisitor.json` | POST `qsdk/v1/user/registerVisitor` | 3.266 B | `authToken` @-encoded (format sama dgn token di query logincheck) |
| `qsdk_v1_auth_getUserInfo.json` | POST `qsdk/v1/auth/getUserInfo` | 280 B | profil akun SDK (nickName, regIp, lastLoginTime) — panenan ke-4, diekstrak dari HAR h3 2026-10-18 |
| `loginchecknative.json` | GET `:510/loginchecknative?…` | 228 B | `{"code":0,…,"loginToken":"…","sign":"…",…}` |

### 1e. :610 = socket.io v2 (engine.io) — HIDUP hari ini

Probe ulang 2026-10-19: handshake `96:0{"sid":…,"upgrades":["websocket"],"pingInterval":25000,…}2:40` sukses.
- `loginClient` dibuat dengan `verifyEnable=false` (README §5a) → **tidak butuh handshake XXTEA** untuk tahap ini.
- engine.io: client polling dulu; upgrade websocket bersifat OPSIONAL — bila upgrade gagal, client **tetap polling**
  (HTTP murni) → server polling-only di MiniServer CUKUP secara desain.
- Rate-limit per IP pada koneksi berulang (terbukti §6 README) → recon cukup 1 sesi.

---

## 2. Fase R — Recon (peran Anda; **prioritas — jendela waktu server terbatas**)

**Anda (1 sesi bermain penuh, seperti biasa, dengan capture HAR + logcat):**
1. Buka app → tunggu update → login SDK → **masuk sampai lobby, main 3–5 menit**
   (biar loginClient/mainClient/chatClient/dungeonClient sempat connect).
2. Keluar → stop capture → push ke `log/` dengan nama jelas (mis. `2026_10_20_sesi_game.har` + logcat).

**Saya (bedah):**
- Ekstrak transport :610 yang nyata dipakai (polling di HAR? websocket?).
- Panen `loginGame` / `GetServerList` (params + respons) + ack berikutnya; simpan ke `docs/panenan-1.0.2/`.
- Petakan urutan event sampai `enterGame`; identifikasi titik yang butuh main-server (verify XXTEA).

**Kriteria lolos**: semua endpoint & payload nyata tersimpan; SHA panenan tercatat;
keputusan transport (:610) berbasis bukti, bukan asumsi.

*(✅ SELESAI 2026-10-19, di luar Fase R: panen file fisik CDN dilakukan langsung — `base.zip`
31.015.076 B (file pondasi base v110), 4 delta zip `113XX_11390.zip` @252.562 B, dan snapshot kedua
`all.zip` state CDN terkini (11389) — semua terarsip di repo. Detail: README §9.4 Tahap A.)*

---

## 3. Fase P — Prototipe test10 (implementasi mini server)

### P1 — Setting bin: ganti 3 nilai (byte-preserving, alat `make_setting_bin.py`)

| Field | Nilai 1.0.1 (asli) | Nilai 1.0.2 |
|---|---|---|
| `loginServer` | `https://login.popoh5.com` | `http://127.0.0.1` |
| `loginPort` | `610` | `28110` |
| `loginWebPort` | `510` | `28110` |

`url`/`update`/field lain **tidak disentuh**. Validasi: dekrip-ulang = JSON valid;
diff field-per-field vs 1.0.1 = hanya 3 nilai itu. (Port tunggal 28110; pemisahan endpoint via PATH.)

### P2 — MiniServer (smali, classes2.dex): routing tambahan by-path

```
/loginchecknative          GET  → JSON panenan (uid/username di-echo dari query; token diterima apa adanya)
/qsdk/v1/system/init       POST → panenan statis
/qsdk/v1/system/getNotice  POST → panenan statis
/qsdk/v1/user/registerVisitor POST → panenan statis
/socket.io/?EIO=3&transport=polling  → engine.io polling minimal:
    handshake 96:0{...} → 40 (connect) → 42["handler.process",{type:"user",action:"loginGame"},ack]
    → ack panenan; GetServerList → ack panenan (serverItem.url/chaturl/dungeonurl → http://127.0.0.1:28110/…)
    → SaveHistory/SaveUserEnterInfo/SaveLanguage → ack ret 0; ping/pong 2↔3
Semua endpoint lain → perilaku 1.0.1 (file mirror 1:1) — TIDAK BERUBAH.
```

Catatan desain: engine.io polling = HTTP GET/POST biasa → implementasi smali realistis
(envelope `4X`, bukan websocket). Upgrade websocket dari klien akan gagal → klien lanjut polling
(perilaku standar engine.io). **Keputusan final tetap menunggu bukti Fase R.**

### P3 — Verifikasi sebelum rilis test10
- `check_invoke_regs.py` lulus (source + hasil baksmali dex final).
- Lab: curl handshake socket.io; simulasi klien polling; diff setting bin dekrip-ulang.
- Zip diff vs 1.0.1: hanya `classes2.dex` + `setting_BS_Android.bin` + META-INF.

---

## 4. Fase T — Uji perangkat bertahap

| Build | Target | Kriteria lolos |
|---|---|---|
| test10 | SDK + loginGame + GetServerList lokal | layar **pilih server** tampil; HAR: **nol byte** ke internet sejak app start (setelah cache hangat) |
| test11 (opsional) | enterGame + data awal | lobby tampil; bila ternyata wajib main-server → STOP, batas aman 1.0.2 = pilih server |

**Batas jujur 1.0.2**: main/chat/dungeon server (verify XXTEA + 200+ action gameplay) = **1.0.3**,
dikerjakan per-modul setelah 1.0.2 stabil. Uji total: mode pesawat setelah cache hangat.

---

## 5. Risiko & mitigasi

| # | Risiko | Mitigasi |
|---|---|---|
| 1 | Server asli mati sebelum recon | **Fase R diprioritaskan**; probe harian oleh saya. File fisik CDN 100% sudah diarsip (2026-10-19, termasuk bukti CDN pernah rollback 11390→11389) |
| 2 | `enterGame` menuntut main-server | Batas 1.0.2 = layar pilih server (tetap bernilai: SDK & login lokal penuh) |
| 3 | Respons SDK berisi data dinamis (authToken, IP) | Replay netral terbukti cukup — klien hanya meneruskan ke layanan KITA |
| 4 | Rate-limit :610 saat recon | 1 sesi capture, tanpa pengulangan |
| 5 | VerifyError kelas baru smali | `check_invoke_regs.py` wajib + pola instruksi terbukti |
| 6 | Event socket.io dikarangan | HANYA dari capture Fase R; tanpa bukti → tidak diimplement |

---

## 6. Definisi selesai 1.0.2

1. App dari nol (uninstall → install) sampai **layar pilih server** tanpa satu pun request ke domain asli
   (bukti HAR kosong + logcat MiniServer lengkap).
2. Panenan & bukti terarsip di repo; BUILD.md + README diperbarui; rilis `1.0.2` jadi default.

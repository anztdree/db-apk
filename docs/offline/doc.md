# MODE PESAWAT / OFFLINE PENUH — DOC TUNGGAL

> **Satu-satunya doc domain offline (mode pesawat).** Revisi ini lahir dari laporan owner
> (2026-10-22): *"local running tapi kenyataannya saat mode pesawat game ini perlu koneksi
> internet nyata … log CAPTURE dari SERVER aslinya banyak sekali server. sedangkan kita baru
> di gerbang update, sdk, login-server."*
> Bukti penuh: HAR sejarah repo (log/2026_10_18_11_03_22.har dkk.) + logcat perangkat rev7
> (`log running/logcat_2026-10-22_11-22-37.txt`).
> Doc keluarga server lokal: [`docs/game-world/doc.md`](../game-world/doc.md),
> [`docs/login-server/doc.md`](../login-server/doc.md), [`docs/sdk/CONFIG-SDK-ASLI.md`](../sdk/CONFIG-SDK-ASLI.md).

---

## 1. FAKTA PANGKAL — 5 SERVER UTAMA SUDAH LOKAL, MODE PESAWAT MASIH GAGAL

rev7 sudah memetakan **5 server** klien (28110 gateway/SDK/CDN, 28112 login-server,
28113 main-server, 28114 chat stub, 28115 dungeon stub) dan logcat rev7 perangkat
membuktikan semuanya LISTENING ✓ dan dihubungi klien (autoLogin 200, loginchecknative
200 gate, `serverurl:http://127.0.0.1:28112`, handshake 200).

Mode pesawat **tidak mematikan loopback 127.0.0.1** — semua server lokal tetap hidup.
Jadi kegagalan mode pesawat **bukan** di server lokal, melainkan di **dua gerbang jaringan
yang tertanam di dalam dex SDK** yang menolak lanjut sebelum sampai ke server lokal mana pun.

---

## 2. AKAR MASALAH — DUA GERBANG JARINGAN DI classes.dex (transkrip dex asli)

Bedah `classes.dex` (e45b3044…, tak tersentuh sejak 1.0.1) menemukan **tepat 4** pemakai
`ConnectivityManager.getActiveNetworkInfo()`. Dua di antaranya adalah gerbang penghalang:

### 2a. Gerbang SDK init — `com.quickgame.android.sdk.feedtonight;->hoboismrelationbelow()`

```java
NetworkInfo ni = ((ConnectivityManager) activity.getSystemService("connectivity")).getActiveNetworkInfo();
boolean ok = (ni != null && ni.isConnected());
if (!ok) {                                   // ← MODE PESAWAT MASUK SINI
    new AlertDialog.Builder(activity)
        .setTitle(R.string.hw_network_dialog_title)
        .setMessage(R.string.hw_network_dialog_message)
        .setPositiveButton(R.string.hw_network_dialog_ok, 0)
        .create().show();                    // dialog + OnDismissListener → mati
} else {
    Log.d("GameSDKImpl", "begin init");      // ← SELURUH SDK BARU MULAI DI SINI
    // thread productInfo → system/init → getNotice → registerVisitor/autoLogin …
}
```

**Efek mode pesawat:** dialog "jaringan tidak tersedia"; `begin init` TIDAK PERNAH jalan →
SDK mati total → tidak ada satu request pun yang sampai ke 28110. Ini penyebab owner
menyimpulkan "perlu koneksi internet nyata".

### 2b. Gerbang layar login — `com.quickgame.android.sdk.activity.HWLoginActivity;->onCreate()`

```java
ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
boolean ok = (cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected());
if (!ok) {                                   // ← MODE PESAWAT MASUK SINI
    new AlertDialog.Builder(this)
        .setTitle(R.string.hw_network_dialog_title)…show();
    // TIDAK ADA bindService → layar login tidak pernah siap
} else {
    Intent i = new Intent(this, QuickGameSdkService.class);
    i.setAction("com.quickgame.android.sdk.ACCOUNT_SERVICE");
    bindService(i, rewakenssupertoward, BIND_AUTO_CREATE);   // ← jalur login
}
```

### 2c. Dua pemakai lainnya = TIDAK berbahaya (tidak dipatch)

| Metode | Fungsi | Kenapa aman |
|---|---|---|
| `unnecessarysperrylites.seventygenom;->influencecomparisonrestore(Context)` | klasifikasi operator (SIM 46000/46001/… atau WiFi) → int utk telemetri | tanpa dialog, tanpa gate; return 0 saat offline |
| `com.adjust.sdk.Util;->getConnectivityType` | internal telemetri Adjust | async, gagal senyap |

### 2d. Bukti pelengkap dari panen HAR/logcat

| Bukti | Isi |
|---|---|
| HAR 18-10 (`log/2026_10_18_11_03_22.har`) | "banyak sekali server" yang owner lihat: graph.facebook.com ×70, appsflyer ×~20, qsdk.t4game.com, login.popoh5.com:510, plugin-check.egret.com — kini qsdk+popoh5 sudah lokal; sisanya telemetri |
| HAR 21-10 (`log running/loginserver.har`) | tersisa graph.facebook.com ×44, appsflyer ×3, plugin-check ×2 — **semua 400/gagal BAHKAN saat online** dan game tetap sampai list server (screenshot 21-10 22:41) |
| `plugin-check.egret.com/pkg-check.php` | status 0 (gagal koneksi) **walau online** → bukti keras non-blocking |
| JS game (15 file mirror) | nol `navigator.onLine`; nol URL server produksi selain dari setting bin (tsUrl/tsLoginServer hanya untuk testMode 2) |
| libegret.so / libgavesec.so | nol simbol ConnectivityManager / NetworkInfo |

---

## 3. PATCH REVISI 8-OFFLINE — 2 BYTE, DETERMINISTIK, SERTIFIKAT SAMA

Kedua gerbang memakai pola bytecode yang sama; perbaikannya juga sama — **flag dipaksa 1**
sehingga dispatcher selalu memilih jalur "online" (`begin init` / `bindService`), dengan
atau tanpa jaringan nyata. Tidak ada logika lain yang tersentuh; kode dialog menjadi dead
code namun tetap sah bagi verifier.

| # | Metode | Instruksi | File off | Sebelum | Sesudah |
|---|---|---|---|---|---|
| 1 | `feedtonight.hoboismrelationbelow` | `const/4 v0, 0` (0x36 dlm method) | `0x5E816F` | `12 00` | `12 10` |
| 2 | `HWLoginActivity.onCreate` | `const/4 v12, 0` (0x326 dlm method) | `0x5D2427` | `12 0C` | `12 1C` |

(11n: byte ke-2 = register nibble rendah + literal nibble tinggi → literal 0→1.)
Header dex ikut dibenahi: SHA-1 @12..32 + checksum adler32 @8; **ukuran dex tetap
6.697.160 B**.

Alat: [`apk/patch-rev8-offline/patch_rev8_offline.py`](../../apk/patch-rev8-offline/patch_rev8_offline.py)
— menolak basis lain (whitelist sha256 classes.dex e45b3044…), mengaudit delta zip,
zipalign+sign via uber-apk-signer. **Deterministik**: dua kali jalan → APK identik.

---

## 4. RILIS — `DB-Local-1.0.3-rev8-offline.apk`

| Properti | Nilai |
|---|---|
| Basis | `DB-Local-1.0.3-rev7-debug.apk` (sha256 `5ef586a2…`) |
| Delta zip | **4 entry**: `classes.dex` (2 byte) + `MANIFEST.MF`, `ANDROIDD.SF`, `ANDROIDD.RSA` (re-sign) |
| Entry lain | 3.716 byte-identik (termasuk classes2.dex v7 MainServer, classes3.dex DLog, manifest) |
| classes.dex | `e45b3044…` → `4482fcb923f1707a705d4fd05641aca1036b05e8f84fecdf25b2004971e6dee0` |
| APK sha256 | `7d2fb238f1eed807a797d92fad045ba79f3f63d0eaf9be8ec9ed0ff42d081e23` |
| Ukuran | 125.138.339 B |
| Sertifikat | **SAMA dgn rev7** (debug uber-apk-signer, SHA256 `1e08a903…`, exp 2044) → **`adb install -r` TANPA uninstall; DB akun guest ikut** |
| Versi | **tetap 1.0.3** (revisi, bukan versi baru — disiplin versi dijaga; versi baru hanya keputusan owner setelah stabil) |

---

## 5. PROTOKOL VERIFIKASI PERANGKAT (tanpa PC)

1. `adb install -r DB-Local-1.0.3-rev8-offline.apk` (dari rev7, tanpa uninstall).
2. **Aktifkan MODE PESAWAT** (WiFi & data mati total).
3. Buka app → wajib **tanpa dialog "jaringan tidak tersedia"**:
   - SDK init jalan (bukti logcat/panel NET: `200 v1/system/init`, `200 v1/system/getNotice`);
   - autoLogin 200; loginchecknative 200 gate; `gameUrl http://127.0.0.1:28110/...`.
4. Layar login game tampil (gambar 404 Instagram/Telegram/Discord/Logo18+/denglujiemian
   = **perilaku asli**, server CDN asli juga 404 — lihat doc game-world §2 poin 5).
5. Masuk → `serverurl:http://127.0.0.1:28112` → handshake 200 → **list "DB-Local 1" + PLAY**.
6. PLAY → main-server 28113: verify XXTEA → enterGame (panen asli 79 kunci) → masuk dunia game.
7. Panel DBG (bubble) tetap hidup di atas surface game; tab NET memperlihatkan semua request
   **hanya 127.0.0.1**; traffic ke internet yang tersisa (facebook/appsflyer/egret plugin-check)
   gagal senyap — sama seperti perilakunya saat online (400 / status 0).

**Kriteria LULUS**: langkah 3–6 tuntas dengan mode pesawat aktif penuh.

---

## 6. BATAS TAHAP (jujur & tereksplicit)

- Patch ini **hanya** membuka dua gerbang jaringan. Ia tidak (dan tidak perlu) mematenkan
  telemetri pihak-3: facebook/appsflyer/adjust/plugin-check tetap mencoba keluar dan gagal
  senyap saat offline — bukti non-blocking ada di §2d. Penetrasi nol-request-internet untuk
  telemetri bisa jadi tahap tersendiri (meta-data manifest same-length) bila owner minta.
- `influencecomparisonrestore` (SIM) & `Adjust.Util` sengaja tidak disentuh — tanpa dialog,
  tanpa gate, return-offline aman.
- Sudah diuji lab (bedah bytecode + determinisme build + audit zip); **menunggu uji mode
  pesawat di perangkat owner** sesuai §5.

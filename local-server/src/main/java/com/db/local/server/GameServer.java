package com.db.local.server;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;

/**
 * Server port GAME — inilah yang "berperan sebagai server" bagi client.
 * Endpoint di assets APK menunjuk ke http://127.0.0.1:&lt;gamePort&gt;, jadi semua
 * permintaan bootstrap client lewat sini. Dua mode (config/server.json):
 *
 * - LOCAL : layani file dari &lt;root&gt;/www/ — hasil ekstraksi OTOMATIS mirror
 *           server ASLI yang dibawa APK (assets/server/www/); struktur folder =
 *           path URL server asli. File yang ditulis-ulang (PR1/PR3 0.4.0):
 *           1) setting bin — host H5 + endpoint LOGIN (SettingBin)
 *           2) serversetting.json — "loginserver" → server login lokal
 *           (dua-duanya satu-satunya konten yang berubah). File yang belum
 *           ada di mirror dicoba diambil SEKALI dari server asli lalu disimpan
 *           ke www/ (mirror melengkapi dirinya sendiri); bila server asli juga
 *           tidak bisa dihubungi → 404 — client melanjutkan boot (fallback dex
 *           terbukti §2.8: "Server base.version is Not Exist").
 * - ONLINE: teruskan permintaan ke server asli (passthrough) — 0.5.1:
 *           setting bin & serversetting.json ditulis-ulang dulu (HANYA jalur
 *           login + url H5 → server lokal; sisanya asli). Semua koneksi login
 *           masuk ke server login lokal yang meneruskan utuh ke server asli
 *           (tuntutan owner: config setelah SDK login HARUS ke loginserver
 *           lokal di SEMUA mode — bukan lagi "file asli apa adanya").
 *
 * 0.5.3 — DUA JALUR 100% TERPISAH (owner: "online tebaca itu cuma jadikan jejak
 * arah bukan untuk dijadikan config asli ke offline" + "config offline murni
 * disiapkan manual"):
 *   LOCAL  = NOL sentuh jaringan luar. Setting bin & serversetting.json dilayani
 *           APA ADANYA dari paket MANUAL <root>/config-offline/ (setting.json,
 *           bin XOR DragonBall, serversetting, servers.json 1 entri "Server
 *           Lokal" url 127.0.0.1). TIDAK ada penulisan-ulang runtime, TIDAK ada
 *           mirror-melengkapi-diri dari CDN. www/ hanya resource H5 (statis).
 *   ONLINE = passthrough ke server asli (configus/dragonh5cdn/dragon) +
 *           penulisan-ulang jalur login (SettingBin.rewrittenOnline) — jalur
 *           kode terpisah, tidak berbagi config dengan LOCAL.
 *
 * Peta passthrough (HANYA mode ONLINE — persis hasil dekode EntryPoint §2.8):
 *   /bs/db/android/… → configus.sjmobilegame.com (fallback COS)
 *   /bs/…            → dragonh5cdn.popoh5.com        (H5 game + folder upgrade)
 *   /lzceshi/…       → dragon.sjmobilegame.com:82
 *
 * Query anti-cache (?rnd= / ?v=) diteruskan ke server asli apa adanya,
 * dan diabaikan saat mencari file lokal.
 *
 * FORMAT LOG REQUEST (0.5.4 — koreksi owner "kalau sedang local log ya isinya
 * local sesuai, kalau log online ya log aslinya seperti httpcanary" + "log isi
 * config di ikutkan ngapain, kalau isi config besar ya bisa show hide"):
 *   - MODE ONLINE : setiap request passthrough = SATU baris gaya HttpCanary —
 *     🌐 GET dragonh5cdn.popoh5.com/bs/... → 200 · 412,3 KB · 340 ms
 *     (config/protokol & galat = PENTING; aset statis = TEKNIS — RINCI).
 *   - MODE LOCAL  : setiap serve = SATU baris gaya lokal — 📄 GET /bs/... ·
 *     mirror www/ · 412,3 KB · 12 ms (aset TEKNIS; config PENTING).
 *   - ISI CONFIG  : TIDAK dibuang ke feed lagi (dulu 📦 AKAR CONFIG belasan
 *     baris × 4 ulang = sampah). Bedah field-per-field ConfigAkar pindah ke
 *     seksi CONFIG dasbor (ConfigView — show/hide); feed hanya SATU baris 📦
 *     padat (dedupe 10 mnt per file).
 *   - Batch tetap masuk GRUP LogGroups (statistik Σ di dasbor).
 *
 * 0.5.4 RELAY socket game :8080 (penutup "loading muter-muter tanpa kejelasan"):
 * url entri daftar server manual = http://127.0.0.1:8080 → klik Play membuat
 * klien io.connect KE PORT INI memakai engine.io v3 polling (persis jalur login).
 * Dulu :8080 hanya HTTP statis → klien retry selamanya, log DIAM. Kini
 * /socket.io/ di mode LOCAL diteruskan utuh (byte-per-byte) ke UPSTREAM RELAY =
 * SATU baris di config/game-upstream.txt — LoginData.gameUpstream() — HANYA
 * isian pemilik sendiri (dasbor seksi CONFIG / file di perangkat).
 * KOREKSI FATAL (tegasan owner 2026-11-14: "online itu cuma jejak agar kita gak
 * tersesat.. bukan malah murni dipakai mentah-mentah"): field "upstream" paket
 * manual TIDAK lagi membawa alamat server game ASLI — dulu 0.6.3 mengisi
 * s2111-bs.popoh5.com:8141 mentah dari capture = LOCAL diam-diam bergantung ke
 * server online (bukti log owner 19:41:37–19:42:23: enterGame diteruskan ke
 * server asli, Play mati saat upstream tak terjangkau). Paket kini kosong;
 * LOCAL tanpa isian pemilik = tidak ada kontak keluar sama sekali — kebutuhan
 * protokol game yang belum punya jawaban lokal dicatat UTUH di log (log =
 * nyawa) sebagai bahan handcraft berikutnya, bukan dilempar ke server asli.
 * Di mode ONLINE /socket.io/ dijawab 404 jelas — gameplay langsung ke server
 * asli (url daftar ASLI), server lokal tidak berada di jalur itu.
 *
 * Halaman root port ini (http://127.0.0.1:&lt;gamePort&gt;/ di browser) = STATUS
 * MINI tanpa tombol apa pun (koreksi owner 0.3.2 — "website pindah mode itu
 * salah cuma menuhin tempat"): switch mode DIHAPUS dari semua halaman web.
 * Satu-satunya cara ganti mode = tombol melayang di layar game (FloatingToggle).
 * Endpoint POST /api/mode juga dihapus karena tidak ada lagi pemakainya.
 */
public final class GameServer extends NanoHTTPD {

    /** Peta passthrough — persis nilai EntryPoint hasil dekode §2.8 + setting bin. */
    private static final String BS_PRIMARY = "https://configus.sjmobilegame.com";
    private static final String BS_FALLBACK = "https://configus-1257066086.cos.na-siliconvalley.myqcloud.com";
    private static final String H5CDN = "https://dragonh5cdn.popoh5.com";
    private static final String DRAGON = "http://dragon.sjmobilegame.com:82";

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final Context appCtx;
    private final File wwwRoot;
    /** 0.5.3 — folder paket CONFIG OFFLINE manual (saudara www/): &lt;root&gt;/config-offline. */
    private final File offlineRoot;
    /** Dedupe peringatan port bin manual ≠ server.json — cukup sekali. */
    private static volatile boolean portWarned = false;

    public GameServer(Context appCtx, String bind, int port, File wwwRoot) throws java.io.IOException {
        super(bind, port);
        this.appCtx = appCtx;
        this.wwwRoot = wwwRoot;
        this.offlineRoot = (wwwRoot != null && wwwRoot.getParentFile() != null)
                ? new File(wwwRoot.getParentFile(), "config-offline")
                : null;
        setTempFileManagerFactory(new TempFileManagerFactory() {
            public TempFileManager create() {
                return new DefaultTempFileManager();
            }
        });
    }

    @Override
    public Response serve(IHTTPSession session) {
        long t0 = System.currentTimeMillis();
        Method m = session.getMethod();
        String uri = session.getUri(); // path saja, tanpa query
        String query = session.getQueryParameterString();
        String mode = Config.mode();
        Response resp;
        try {
            boolean post = (Method.POST == m);
            if (!post && Method.GET != m && Method.HEAD != m) {
                resp = textResp(Response.Status.METHOD_NOT_ALLOWED,
                        "Metode " + m + " tidak didukung — kontrak bootstrap hanya GET");
            } else if ("/".equals(uri) || "/index.html".equals(uri)) {
                resp = rootPage();
            } else if ("/socket.io/".equals(uri) || "/socket.io".equals(uri)) {
                // 0.5.4 — klik Play: klien io.connect ke url entri daftar server.
                // LOCAL = relay ke UPSTREAM ISIAN PEMILIK (bila diisi); ONLINE = 404
                // jelas (gameplay langsung ke server asli, bukan lewat port ini).
                resp = socketIo(session, m, mode);
            } else if ("local".equals(mode)) {
                resp = serveLocal(uri, query, t0);
            } else {
                resp = servePassthrough(uri, query, t0);
            }
        } catch (Throwable t) {
            StatusStore.countErr();
            try {
                HumanLog.ev("⚠️", "Port game gagal memproses " + gameUrl(uri) + " — dijawab 500", String.valueOf(t));
            } catch (Throwable ignored) {
            }
            resp = textResp(Response.Status.INTERNAL_ERROR,
                    "Kesalahan internal port game: " + t);
        }
        int code = 500;
        try {
            code = resp.getStatus().getRequestStatus();
        } catch (Throwable ignored) {
        }
        long ms = System.currentTimeMillis() - t0;
        StatusStore.countReq();
        StatusStore.recordGame(uri, code, ms);
        if (StatusStore.requestTick()) {
            StatusStore.persist();
        }
        return resp;
    }

    // ---------------- halaman root port game: status mini tanpa tombol ----------------

    /**
     * Halaman http://127.0.0.1:&lt;gamePort&gt;/ — status mini (koreksi owner 0.3.2:
     * "website pindah mode itu salah cuma menuhin tempat"). TANPA tombol, TANPA
     * JavaScript — cuma tiga baris keterangan supaya developer bisa memastikan
     * port game hidup. Ganti mode = tombol melayang di layar game.
     */
    private Response rootPage() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("<!doctype html><html lang=\"id\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>DB LOCAL · Port Game</title><style>")
                .append("body{font:15px/1.6 -apple-system,\"Segoe UI\",Roboto,Helvetica,Arial,sans-serif;")
                .append("color:#1c1917;background:#f5f5f4;margin:0;padding:24px}")
                .append("@media(prefers-color-scheme:dark){body{background:#0c0a09;color:#fafaf9}}")
                .append("</style></head><body>")
                .append("Server lokal hidup — mode <b>").append(Config.mode().toUpperCase())
                .append("</b> · port game ").append(Config.gamePort())
                .append(" · endpoint client menunjuk ke sini.<br>")
                .append("Login lokal: http://127.0.0.1:").append(Config.loginPort())
                .append(" (engine.io v3 + socket.io v2) · dasbor developer: http://127.0.0.1:")
                .append(Config.debugPort())
                .append(" · ganti mode: tombol melayang di layar game.")
                .append("</body></html>");
        return htmlResp(sb.toString().getBytes());
    }

    // ---------------- 0.5.4 RELAY SOCKET GAME :8080 → upstream ----------------

    /** Dedupe baris sesi game (10 mnt — sama dengan 📡/📋 server login). */
    private static volatile long lastGameSessLogAt = 0L;

    // ---------------- 0.6.0 — tabel upstream daftar server ASLI ----------------

    /** Entri {serverId, url ASLI} utuh dari respons GetServerList passthrough. */
    private static final Object UPTABLELOCK = new Object();
    private static volatile java.util.ArrayList<String[]> onlineUpstreamTable
            = new java.util.ArrayList<String[]>();
    private static volatile String onlineChosenId = null;

    /** Isi tabel dari jalur passthrough login (serverId, url ASLI — utuh). */
    public static void setOnlineUpstreamTable(java.util.ArrayList<String[]> table) {
        synchronized (UPTABLELOCK) {
            onlineUpstreamTable = table;
        }
    }

    /** Catat serverId pilihan pemain (dari paket login socket). Null/kosong = hapus pilihan. */
    public static void setOnlineChosenId(String serverId) {
        onlineChosenId = (serverId != null && serverId.length() > 0) ? serverId : null;
    }

    /**
     * Pilih upstream mode ONLINE: entri dgn serverId terpilih → bila tidak ada,
     * entri pertama. Return {url, sumber} atau null bila tabel kosong.
     */
    private static String[] onlineUpstreamPick() {
        synchronized (UPTABLELOCK) {
            if (onlineUpstreamTable.isEmpty()) {
                return null;
            }
            if (onlineChosenId != null) {
                for (int i = 0; i < onlineUpstreamTable.size(); i++) {
                    String[] e = onlineUpstreamTable.get(i);
                    if (onlineChosenId.equals(e[0])) {
                        return new String[]{e[1],
                                "daftar server ASLI · serverId " + e[0] + " (pilihan pemain)"};
                    }
                }
            }
            String[] f = onlineUpstreamTable.get(0);
            return new String[]{f[1],
                    "daftar server ASLI · serverId " + f[0] + " (entri pertama)"};
        }
    }

    /**
     * 0.6.0 — resolusi upstream relay game (dipakai relay + Monitor probe).
     * KOREKSI FATAL 2026-11-14 (owner: "config online itu cuma jejak agar kita
     * gak tersesat.. bukan malah murni di pakai mentah-mentah"): sumber satu-satunya
     * = ISIAN PEMILIK — config/game-upstream.txt (dasbor seksi CONFIG / file di
     * perangkat). Field "upstream" di paket manual TIDAK DIBACA LAGI — paket
     * tidak pernah membawa alamat server asli (dulu 0.6.3 mengisi
     * s2111-bs.popoh5.com:8141 mentah = pelanggaran yang sudah dikoreksi).
     * Mode ONLINE tetap lanjut ke tabel daftar server ASLI (jalur passthrough).
     * Return {url, sumber} atau null (LOCAL = tidak ada tujuan → tidak ada
     * kontak keluar; Play berhenti dengan log yang jujur dan utuh).
     */
    public static String[] resolveGameUpstream(String mode) {
        String[] upEx = LoginData.gameUpstreamEx();
        if ("file".equals(upEx[1]) && upEx[0].length() > 0) {
            return new String[]{upEx[0], "config/game-upstream.txt — isian pemilik"};
        }
        if (!"local".equals(mode)) {
            return onlineUpstreamPick();
        }
        return null;
    }

    /**
     * Klik Play: klien io.connect(url entri daftar). Di LOCAL url itu = port ini
     * → /socket.io/ diteruskan UTUH ke UPSTREAM ISIAN PEMILIK (mesin relay
     * yang sama dgn passthrough login 0.5.1). Di ONLINE: gameplay LANGSUNG ke
     * server asli — port ini tidak dipakai; dijawab 404 dengan penjelasan.
     */
    /**
     * Klik Play: klien io.connect(url entri daftar) → /socket.io/ diteruskan
     * UTUH ke UPSTREAM ISIAN PEMILIK (mesin relay sama dgn passthrough
     * login 0.5.1). 0.6.0: relay AKTIF DI SEMUA MODE — di ONLINE, url daftar
     * server ASLI ditulis-ulang ke port ini (jalur passthrough login), jadi
     * Play online pun tertangkap penuh di log; di LOCAL, upstream = ISIAN
     * PEMILIK saja (config/game-upstream.txt — koreksi fatal 2026-11-14:
     * field "upstream" paket manual tidak dibaca lagi, paket tidak pernah
     * membawa alamat server asli).
     */
    private Response socketIo(IHTTPSession session, Method m, String mode) {
        String query = session.getQueryParameterString();
        String qs = (query == null || query.length() == 0) ? "" : query;
        String transport = session.getParms() == null ? null : session.getParms().get("transport");
        if ("websocket".equals(transport)) {
            // klien mencoba upgrade websocket → ditolak → engine.io tetap polling
            // (perilaku terbukti di device pada passthrough login 0.5.1).
            return textResp(LoginServer.safeStatus(400),
                    "websocket tidak diteruskan — klien engine.io tetap di polling");
        }
        // REBUILD 0.0.1 — MESIN GAME LOKAL (GameEngine): LOCAL tanpa isian
        // upstream = protokol game dijawab SERVER INI SENDIRI (kontrak dari
        // kode klien, nilai dari dragonball.db — NOL kontak keluar). Play LOKAL
        // HIDUP. Isian pemilik config/game-upstream.txt = relay OPSIONAL yang
        // tetap dihormati (jalur lama).
        String[] res;
        if ("local".equals(mode)) {
            String[] upEx = LoginData.gameUpstreamEx();
            if (!"file".equals(upEx[1]) || upEx[0].length() == 0) {
                return GameEngine.serve(session, m);
            }
            res = new String[]{upEx[0], "config/game-upstream.txt — isian pemilik"};
        } else {
            res = resolveGameUpstream(mode);
        }
        if (res == null) {
            long now = System.currentTimeMillis();
            if (now - lastGameSessLogAt >= 60000L) {
                lastGameSessLogAt = now;
                // 0.6.1 — pesan berbeda per jalur (owner: sub judul harus jelas):
                // LOCAL = isian manual belum ada; ONLINE = tabel daftar server ASLI
                // masih kosong (passthrough login belum memberi GetServerList).
                if ("local".equals(mode)) {
                    HumanLog.ev("❌", "Play berhenti — jalur LOCAL dengan isian upstream tidak berlaku (pakai mesin game lokal)",
                            "Jalur     : LOCAL — relay :8080 → upstream isian pemilik\n"
                                    + "io.connect : http://127.0.0.1:" + Config.gamePort() + "\n"
                                    + "Query     : " + (qs.length() > 0 ? qs : "(kosong)") + "\n"
                                    + "Catatan   : isian upstream hanya berlaku mode ONLINE; di LOCAL\n"
                                    + "             protokol game dijawab mesin lokal (tanpa relay).");
                } else {
                    HumanLog.ev("❌", "Play berhenti — jalur ONLINE belum punya url ASLI",
                            "Jalur     : ONLINE — passthrough ke server asli\n"
                                    + "io.connect : http://127.0.0.1:" + Config.gamePort() + "\n"
                                    + "upstream   : (tabel daftar server ASLI masih kosong — passthrough "
                                    + "login belum memberi daftar GetServerList)");
                }
            }
            return textResp(LoginServer.safeStatus(502),
                    "local".equals(mode)
                            ? "relay isian pemilik tidak berlaku di LOCAL — mesin game lokal menangani Play"
                            : "tabel daftar server ASLI masih kosong — jalur ONLINE belum bisa relay");
        }
        String up = res[0];
        String src = res[1];
        long t0 = System.currentTimeMillis();
        String body = null;
        if (Method.POST == m) {
            try {
                body = LoginServer.readBody(session);
            } catch (Throwable t) {
                body = "";
            }
        }
        String target = up + "/socket.io/" + (qs.length() > 0 ? ("?" + qs) : "");
        LoginServer.UpResp u;
        try {
            u = LoginServer.forwardLogin(target, body, Method.POST == m,
                    Method.POST == m ? 30000 : 45000);
        } catch (Throwable t) {
            long ms = System.currentTimeMillis() - t0;
            HumanLog.ev("⚠️", "Upstream relay (isian pemilik) tidak terjangkau — Play tidak bisa lanjut",
                    "Jalur    : " + ("local".equals(mode)
                            ? "LOCAL — relay :8080 → upstream isian pemilik (bukan daftar server)"
                            : "ONLINE — relay :8080 → url ASLI dari daftar server") + "\n"
                            + "Upstream : " + up + "\n"
                            + "Sebab    : " + t + "\n"
                            + "Waktu    : " + HumanLog.fmtMs(ms) + " — klien akan mencoba ulang sendiri");
            return textResp(LoginServer.safeStatus(502),
                    "upstream tidak terjangkau — " + t);
        }
        long ms = System.currentTimeMillis() - t0;
        String respText = new String(u.body, java.nio.charset.StandardCharsets.UTF_8);
        // ---- log relay: satu baris per arah (gaya HttpCanary, tanpa isi payload) ----
        if (Method.GET == m) {
            boolean handshake = (qs.indexOf("sid=") < 0) && respText.indexOf("\"sid\"") >= 0;
            if (handshake) {
                long now = System.currentTimeMillis();
                if (now - lastGameSessLogAt >= 600000L) {
                    lastGameSessLogAt = now;
                    HumanLog.ev("🎮", "Play → socket game lokal :" + Config.gamePort()
                                    + ("local".equals(mode)
                                            ? " — relay ke upstream isian pemilik"
                                            : " — diteruskan ke SERVER GAME ASLI"),
                            "Jalur    : " + ("local".equals(mode)
                                    ? "LOCAL — relay :8080 meneruskan ke upstream isian pemilik (config/game-upstream.txt)"
                                    : "ONLINE — relay :8080 meneruskan ke url ASLI dari daftar server") + "\n"
                                    + "Upstream : " + up + " (sumber: " + src + ")\n"
                                    + "Query    : " + (qs.length() > 0 ? qs : "(kosong)") + "\n"
                                    + "Sesi     : sid dari "
                                    + ("local".equals(mode) ? "upstream" : "server asli") + " · relay polling");
                }
            } else if (u.body.length > 0 && ms > 0) {
                if (respText.indexOf("42[") >= 0) {
                    // 0.6.0 — jawaban berisi event game → isi UTUH di blok ISI
                    // (aturan owner: log panjang wajib tampil & txt wajib menyimpan)
                    HumanLog.evIsi("🎮", "← upstream " + hostOf(up) + " · "
                                    + HumanLog.fmtBytes(u.body.length) + " · " + HumanLog.fmtMs(ms),
                            null, "jawaban upstream (utuh)", respText);
                } else {
                    HumanLog.evTech("🎮", "← upstream " + hostOf(up) + " · "
                            + HumanLog.fmtBytes(u.body.length) + " · " + HumanLog.fmtMs(ms), null);
                }
            }
        } else if (Method.POST == m && body != null && body.length() > 0) {
            String act = ringkasAksi(body);
            if (act != null) {
                // 0.6.0 — payload POST yang membawa aksi game: isi UTUH di blok ISI
                HumanLog.evIsi("🎮", "→ " + act + ("local".equals(mode)
                                                ? " diteruskan ke upstream isian pemilik"
                                                : " diteruskan ke server game ASLI") + " · "
                                + HumanLog.fmtBytes(body.length()) + " · " + HumanLog.fmtMs(ms),
                        null, "payload POST (utuh — diteruskan byte-per-byte)", body);
            } else {
                HumanLog.evTech("🎮", "→ upstream · " + HumanLog.fmtBytes(body.length())
                        + " · " + HumanLog.fmtMs(ms), null);
            }
        }
        Response r = newFixedLengthResponse(LoginServer.safeStatus(u.status == -1 ? 502 : u.status),
                u.contentType, new ByteArrayInputStream(u.body), u.body.length);
        r.addHeader("Cache-Control", "no-store");
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    /**
     * Ringkas nama aksi game dari flush POST (framing engine.io → event
     * socket.io "42...[\"handler.process\",{...action...}]"). Null bila hanya
     * ping/keepalive — tidak banjir log.
     */
    private static String ringkasAksi(String body) {
        try {
            java.util.ArrayList<String> pkts = LoginServer.parseFrames(body);
            StringBuilder sb = new StringBuilder(48);
            for (int i = 0; i < pkts.size(); i++) {
                String pkt = pkts.get(i);
                if (pkt.length() < 3 || pkt.charAt(0) != '4' || pkt.charAt(1) != '2') {
                    continue;
                }
                int j = 2;
                while (j < pkt.length() && pkt.charAt(j) >= '0' && pkt.charAt(j) <= '9') {
                    j++;
                }
                String msg = LoginServer.firstArgJson(pkt.substring(j));
                String action = LoginServer.strField(msg, "action");
                String type = LoginServer.strField(msg, "type");
                if (action != null && action.length() > 0) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(action);
                    if (type != null && type.length() > 0) {
                        sb.append('(').append(type).append(')');
                    }
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------- mode LOCAL: layani dari www/ ----------------

    private Response serveLocal(String uri, String query, long t0) {
        // 0.5.3 — setting bin = paket MANUAL config-offline/, dilayani APA ADANYA
        // (byte-per-byte, TANPA penulisan-ulang runtime). Berlaku untuk kedua jalur
        // path lama: bs/db/android (configus) & lzceshi/native_setting/Android (dragon).
        if ((uri.startsWith("/bs/db/android/") || uri.startsWith("/lzceshi/native_setting/Android/"))
                && uri.endsWith(".bin")) {
            return serveSettingBin(uri, query, t0);
        }
        // serversetting.json = peta login yang dibaca H5 — paket MANUAL, apa adanya.
        if (uri.endsWith("/serversetting.json")) {
            return serveServersetting(uri, query, t0);
        }
        File f = resolveLocal(uri);
        long ms = System.currentTimeMillis() - t0;
        if (f == null) {
            // 0.5.3 — LOCAL = NOL sentuh jaringan luar: TIDAK ADA lagi "mirror
            // melengkapi diri" dari CDN (trySeed dihapus dari jalur ini). 404 =
            // perilaku normal (client lanjut boot — fallback terbukti §2.8).
            StatusStore.count404();
            LogGroups.item("404", "404 fallback", "file", true,
                    "GET " + uri + " — normal (LOCAL tidak mengambil dari luar)", 0);
            return textResp(Response.Status.NOT_FOUND,
                    "Tidak ada di mirror www/" + uri + " — mode LOCAL melayani mirror statis bawaan APK "
                            + "+ paket config-offline/ manual. Game melanjutkan boot saat jawaban tidak ada.");
        }
        return serveFile(f, uri, query, t0, false);
    }

    // ---------------- 0.5.4 CONFIG: baris padat + isi penuh ke seksi CONFIG dasbor ----------------

    /**
     * 0.5.4 — dedupe baris 📦 config per file: 10 mnt (owner: isi config yang
     * sama muncul 4× beruntun = sampah). Isi penuh (bedah field-per-field +
     * teks config) masuk ConfigView → seksi CONFIG dasbor dengan show/hide.
     */
    private static final java.util.HashMap<String, Long> CFG_LOG_SEEN =
            new java.util.HashMap<String, Long>();

    private static void cfgLog(String uri, String sentence, String detail) {
        cfgLog(uri, sentence, detail, null, null);
    }

    private static void cfgLog(String uri, String sentence, String detail,
                               String isiLabel, String isi) {
        long now = System.currentTimeMillis();
        Long prev;
        boolean tampil;
        synchronized (CFG_LOG_SEEN) {
            if (CFG_LOG_SEEN.size() > 64) {
                CFG_LOG_SEEN.clear();
            }
            prev = CFG_LOG_SEEN.get(uri);
            tampil = (prev == null || now - prev.longValue() >= 600000L);
            CFG_LOG_SEEN.put(uri, Long.valueOf(now));
        }
        if (tampil) {
            HumanLog.evIsi("📦", sentence, detail, isiLabel, isi);
        }
    }

    /**
     * 0.5.3 — setting bin mode LOCAL: dilayani APA ADANYA dari paket MANUAL
     * config-offline/setting_BS_Android.bin (byte-per-byte — TANPA penulisan-ulang
     * runtime). Bin manual sudah menunjuk 127.0.0.1 sejak disiapkan (lihat
     * setting.json di paket + manifest.txt sha256). Isi dibedah di log (📦) dari
     * file yang sama — yang diterima klien PERSIS paket manual.
     */
    private Response serveSettingBin(String uri, String query, long t0) {
        File f = (offlineRoot == null) ? null : new File(offlineRoot, "setting_BS_Android.bin");
        if (f == null || !f.exists() || !f.isFile()) {
            ReqLog.ev("⚠️", "404 " + uri,
                    "GET " + gameUrl(uri) + " → 404 · paket config-offline/ TIDAK berisi setting bin manual "
                            + "— cek ekstraksi paket config OFFLINE",
                    nullSafe(query));
            return textResp(Response.Status.NOT_FOUND, "Paket config-offline/ tidak berisi setting_BS_Android.bin");
        }
        try {
            byte[] b = readAllBytes(f);
            long ms = System.currentTimeMillis() - t0;
            // Pengaman konfigurasi (sekali per proses): bin manual memakai port
            // DEFAULT 0.0.2 (game 8080 · login socket 8611 · SDK 8610) — bila
            // server.json mengubah port, paket manual harus dibuat ulang (bukan
            // ditulis-ulang saat runtime — keputusan 0.5.3).
            if (!portWarned && (Config.gamePort() != 8080 || Config.loginPort() != 8611
                    || Config.sdkPort() != 8610)) {
                portWarned = true;
                HumanLog.ev("⚠️", "Port server.json beda dari bin manual (8080/8611/8610) — paket config-offline "
                        + "harus dibuat ulang agar cocok", "gamePort=" + Config.gamePort()
                        + " · loginPort=" + Config.loginPort()
                        + " · sdkPort=" + Config.sdkPort());
            }
            // 0.5.4 — feed: SATU baris padat (dedupe 10 mnt); ISI PENUH (bedah
            // field-per-field + teks setting.json) → seksi CONFIG dasbor (show/hide).
            // 0.5.6 — baris 📦 sekarang MEMBAWA isi komunikasinya (capture HttpCanary
            // di dasbor :4000): bedah + teks config persis yang dilayani.
            String plain = SettingBin.decryptedPlain(b);
            String breakdown = ConfigAkar.settingBin(plain, plain);
            String isiConfig = breakdown
                    + "\n\n— teks config (setting.json dienkripsi XOR “DragonBall”) —\n" + plain;
            ConfigView.put("setting-bin", "setting_BS_Android.bin",
                    "OFFLINE · paket manual config-offline/ · " + HumanLog.fmtBytes(b.length)
                            + " · " + HumanLog.fmtMs(ms) + " · apa adanya (tanpa tulis-ulang)",
                    isiConfig);
            cfgLog("setting-bin", "CONFIG setting bin disajikan — paket manual config-offline/ · "
                            + HumanLog.fmtBytes(b.length) + " · " + HumanLog.fmtMs(ms),
                    "Menunjuk : 127.0.0.1 (game :" + Config.gamePort() + " · login :"
                            + Config.loginPort() + ") — sejak disiapkan manual",
                    "ISI · bedah field-per-field + teks config · " + HumanLog.fmtBytes(b.length),
                    isiConfig);
            Response r = newFixedLengthResponse(Response.Status.OK, "application/octet-stream",
                    new ByteArrayInputStream(b), b.length);
            r.addHeader("Cache-Control", "no-store");
            LogGroups.item("req", "Request game", "request", true,
                    "GET " + uri + " — " + HumanLog.fmtBytes(b.length) + " · " + HumanLog.fmtMs(ms)
                            + " · paket config-offline/ manual (apa adanya)",
                    b.length);
            return r;
        } catch (Throwable t) {
            HumanLog.ev("⚠️", "GET " + gameUrl(uri) + " gagal membaca setting bin manual", String.valueOf(t));
            return textResp(Response.Status.INTERNAL_ERROR, "Gagal membaca setting bin manual: " + uri);
        }
    }

    /**
     * 0.5.3 — serversetting.json mode LOCAL: dilayani APA ADANYA dari paket MANUAL
     * config-offline/serversetting.json (loginserver = http://127.0.0.1:&lt;loginPort&gt;
     * sejak disiapkan — TANPA penulisan-ulang runtime).
     */
    private Response serveServersetting(String uri, String query, long t0) {
        File f = (offlineRoot == null) ? null : new File(offlineRoot, "serversetting.json");
        if (f == null || !f.exists() || !f.isFile()) {
            StatusStore.count404();
            LogGroups.item("404", "404 fallback", "file", true,
                    "GET " + uri + " — paket config-offline/ tidak berisi serversetting.json", 0);
            return textResp(Response.Status.NOT_FOUND, "Paket config-offline/ tidak berisi serversetting.json");
        }
        try {
            String text = readFileText(f);
            byte[] out = text.getBytes("UTF-8");
            long ms = System.currentTimeMillis() - t0;
            Response r = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8",
                    new ByteArrayInputStream(out), out.length);
            r.addHeader("Cache-Control", "no-store");
            // 0.5.4 — feed: SATU baris padat; isi penuh → seksi CONFIG dasbor.
            // 0.5.6 — baris membawa isi komunikasinya (capture).
            String isiSrv = ConfigAkar.serversetting(text, text)
                    + "\n\n— teks config —\n" + text;
            ConfigView.put("serversetting", "serversetting.json",
                    "OFFLINE · paket manual config-offline/ · " + HumanLog.fmtBytes(out.length)
                            + " · " + HumanLog.fmtMs(ms) + " · apa adanya (tanpa tulis-ulang)",
                    isiSrv);
            cfgLog("serversetting", "CONFIG serversetting.json disajikan — paket manual config-offline/ · "
                            + HumanLog.fmtBytes(out.length) + " · " + HumanLog.fmtMs(ms),
                    "Menunjuk : loginserver 127.0.0.1:" + Config.loginPort() + " — sejak disiapkan manual",
                    "ISI · bedah + teks config · " + HumanLog.fmtBytes(out.length),
                    isiSrv);
            LogGroups.item("req", "Request game", "request", true,
                    "GET " + uri + " — " + HumanLog.fmtBytes(out.length) + " · " + HumanLog.fmtMs(ms)
                            + " · paket config-offline/ manual (apa adanya)",
                    out.length);
            return r;
        } catch (Throwable t) {
            HumanLog.ev("⚠️", "GET " + gameUrl(uri) + " gagal membaca serversetting.json manual", String.valueOf(t));
            return textResp(Response.Status.INTERNAL_ERROR, "Gagal membaca serversetting.json manual: " + uri);
        }
    }

    private static String readFileText(File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream((int) Math.max(32, f.length()));
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    /** Baca seluruh isi file (byte) — paket config-offline dilayani byte-per-byte. */
    private static byte[] readAllBytes(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.max(32, f.length()));
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return bos.toByteArray();
    }

    private Response serveFile(File f, String uri, String query, long t0, boolean seeded) {
        try {
            long len = f.length();
            InputStream in = new FileInputStream(f);
            Response r = newFixedLengthResponse(Response.Status.OK, contentType(uri), in, len);
            r.addHeader("Cache-Control", "no-store");
            long ms = System.currentTimeMillis() - t0;
            try {
                // 0.5.7 — LAPORAN owner "log cacat gak bisa nangkap jelas .. loading
                // update sampai sdk loading selesai cuma muncul 1 log": alur loading
                // update (base.version/resource.version/upgrade.json/size.json dst)
                // dulu TEKNIS → tersembunyi di dasbor bersih. HttpCanary = SEMUA
                // traffic tercapture — file CONFIG/VERSION/TXT/BIN kini PENTING
                // (selalu tampil, membawa isi komunikasinya); aset besar (js/zip/
                // html) tetap TEKNIS + Σ grup agar alur tetap terbaca.
                String isiLbl = "ISI · " + HumanLog.fmtBytes(len)
                        + (isTextExt(uri) ? " (teks)" : " (binary)");
                String isiTxt = isiFile(f, uri, len);
                if (isConfigExt(uri)) {
                    HumanLog.evIsi("📄", "GET " + uri + " · mirror www/ · "
                            + HumanLog.fmtBytes(len) + " · " + HumanLog.fmtMs(ms), null,
                            isiLbl, isiTxt);
                } else {
                    HumanLog.evTechIsi("📄", "GET " + uri + " · mirror www/ · "
                            + HumanLog.fmtBytes(len) + " · " + HumanLog.fmtMs(ms), null,
                            isiLbl, isiTxt);
                }
            } catch (Throwable ignored) {
            }
            LogGroups.item("req", "Request game", "request", true,
                    "GET " + uri + " — " + HumanLog.fmtBytes(len) + " · " + HumanLog.fmtMs(ms),
                    len);
            return r;
        } catch (Throwable t) {
            HumanLog.ev("⚠️", "GET " + gameUrl(uri) + " — file ada tapi gagal dibaca", String.valueOf(t));
            return textResp(Response.Status.INTERNAL_ERROR, "File ada tapi gagal dibaca: " + uri);
        }
    }

    /** Resolve file di wwwRoot; path aman (tanpa naik direktori). null bila tidak ada. */
    private File resolveLocal(String uri) {
        File f = safePath(uri);
        if (f == null || !f.exists() || !f.isFile()) {
            return null;
        }
        return f;
    }

    /** Path aman di dalam wwwRoot (blokir ../); boleh belum ada. */
    private File safePath(String uri) {
        try {
            if (wwwRoot == null) {
                return null;
            }
            String path = uri;
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.length() == 0) {
                return null;
            }
            File f = new File(wwwRoot, path);
            String canF = f.getCanonicalPath();
            String canRoot = wwwRoot.getCanonicalPath();
            if (!canF.startsWith(canRoot)) {
                return null; // percobaan keluar folder (../)
            }
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------- mode ONLINE: teruskan ke server asli ----------------

    private Response servePassthrough(String uri, String query, long t0) {
        String[] base = upstreamBase(uri); // [0]=utama, [1]=fallback (boleh null)
        if (base == null) {
            ReqLog.evIsi("⚠️", "404 " + uri,
                    "GET " + gameUrl(uri) + " → 404 · path tidak dipetakan ke server asli — peta: "
                            + "/bs/db/android, /bs, /lzceshi",
                    nullSafe(query),
                    "ISI · jawaban 404",
                    "Path tidak dipetakan ke server asli: " + uri + " — peta passthrough: bs/db/android, "
                            + "bs (dragonh5cdn), lzceshi.");
            return textResp(Response.Status.NOT_FOUND,
                    "Path tidak dipetakan ke server asli: " + uri + " — peta passthrough: bs/db/android, "
                            + "bs (dragonh5cdn), lzceshi.");
        }
        String q = (query != null && query.length() > 0) ? ("?" + query) : "";
        Upstream up = fetchUpstream(base[0] + uri + q);
        String usedHost = base[0];
        if (up.status < 0 || up.status >= 500) {
            if (base[1] != null) {
                HumanLog.ev("⚠️", "Server utama gagal (" + (up.status < 0 ? "tak terjangkau" : "HTTP " + up.status)
                        + ") → mencoba fallback COS", null);
                Upstream alt = fetchUpstream(base[1] + uri + q);
                if (alt.status >= 0 && alt.status < 500) {
                    up = alt;
                    usedHost = base[1];
                }
            }
        }
        long ms = System.currentTimeMillis() - t0;
        if (up.status < 0) {
            ReqLog.evIsi("⚠️", "502 " + uri,
                    "GET " + gameUrl(uri) + " → 502 · server asli tidak terjangkau · " + HumanLog.fmtMs(ms)
                            + " — coba mode LOCAL bila www/ sudah terisi",
                    nullSafe(query),
                    "ISI · jawaban 502",
                    "Server asli tidak terjangkau untuk " + uri + " — coba mode LOCAL bila www/ sudah terisi.");
            return textResp(LoginServer.safeStatus(502),
                    "Server asli tidak terjangkau untuk " + uri + " — coba mode LOCAL bila www/ sudah terisi.");
        }
        // 0.4.6: jawaban server asli = GRUP "Passthrough ONLINE" — 5xx naik ⚠.
        // 0.5.1: dua config kunci ditulis-ulang dulu (setting bin / serversetting) —
        // catatan grup menyebutnya agar owner melihat config LEWAT server lokal.
        boolean loginCfg = uri.endsWith("setting_BS_Android.bin") || uri.endsWith("/serversetting.json");
        LogGroups.item("pass", "Passthrough ONLINE", "request", up.status < 500,
                "GET " + uri + " — HTTP " + up.status
                        + (up.length >= 0 ? " · " + HumanLog.fmtBytes(up.length) : "")
                        + " · " + HumanLog.fmtMs(ms) + " · dari " + hostOf(usedHost)
                        + (loginCfg ? " · endpoint login ditulis-ulang ke server lokal" : ""),
                Math.max(0, up.length));
        Response r;
        long sentBytes = Math.max(0, up.length);
        String rewriteNote = "";
        // 0.5.6 — capture isi: config (json/version/txt/bin) dan file teks kecil
        // dibuffer agar isi persis yang disajikan ikut pada baris ⇄ (HttpCanary
        // di dasbor :4000); binary/besar diteruskan streaming + catatan jujur.
        String isiLabel = null;
        String isiText = null;
        if (up.status == 200 && isTextExt(uri) && up.length >= 0 && up.length <= 512L * 1024L && up.in != null) {
            // 0.5.4 — config asli dibuffer agar bisa ditulis-ulang (bin/serversetting)
            // lalu disajikan dari memori. Isi penuh → seksi CONFIG dasbor (show/hide);
            // feed hanya baris ⇄ padat.
            try {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream((int) up.length);
                byte[] buf = new byte[8192];
                int n;
                while ((n = up.in.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                up.in.close();
                byte[] data = bos.toByteArray();
                String txt = new String(data, "UTF-8");
                // 0.5.1 CONFIG — jalur login selalu ke server lokal (SEMUA mode):
                // setting bin (HANYA login + url H5) & serversetting.json (loginserver)
                // ditulis-ulang SEBELUM disajikan — klien tidak pernah langsung ke
                // login.popoh5.com; server login lokal yang meneruskan utuh.
                if (uri.endsWith("setting_BS_Android.bin")) {
                    byte[] rw = SettingBin.rewrittenOnline(data, Config.gamePort(), Config.loginPort(),
                            Config.sdkPort());
                    String originalPlain = SettingBin.decryptedPlain(bos.toByteArray());
                    if (rw != data) {
                        data = rw;
                        String plain = SettingBin.plainOnline(bos.toByteArray(), Config.gamePort(),
                                Config.loginPort(), Config.sdkPort());
                        txt = (plain == null) ? txt : plain;
                        rewriteNote = " · login + url H5 ditulis-ulang ke server lokal";
                    }
                    // 0.5.4 — isi penuh (bedah ASLI → klien) ke seksi CONFIG dasbor.
                    // 0.5.6 — string yang sama = ISI pada baris ⇄ (capture).
                    String isiCfg = ConfigAkar.settingBin(originalPlain, txt)
                            + "\n\n— teks config yang diterima klien —\n" + txt;
                    ConfigView.put("setting-bin", "setting_BS_Android.bin",
                            "ONLINE · dari server asli " + hostOf(usedHost) + " · "
                                    + HumanLog.fmtBytes(data.length) + " · " + HumanLog.fmtMs(ms)
                                    + rewriteNote,
                            isiCfg);
                    isiLabel = "ISI · bedah ASLI → klien + teks config · " + HumanLog.fmtBytes(data.length);
                    isiText = isiCfg;
                } else if (uri.endsWith("/serversetting.json")) {
                    String rw = SettingBin.setJsonValue(txt, "loginserver",
                            "http://127.0.0.1:" + Config.loginPort());
                    String originalText = txt;
                    if (!rw.equals(txt)) {
                        txt = rw;
                        data = rw.getBytes("UTF-8");
                        rewriteNote = " · loginserver ditulis-ulang ke server lokal";
                    }
                    String isiCfg = ConfigAkar.serversetting(originalText, txt)
                            + "\n\n— teks config yang diterima klien —\n" + txt;
                    ConfigView.put("serversetting", "serversetting.json",
                            "ONLINE · dari server asli " + hostOf(usedHost) + " · "
                                    + HumanLog.fmtBytes(data.length) + " · " + HumanLog.fmtMs(ms)
                                    + rewriteNote,
                            isiCfg);
                    isiLabel = "ISI · bedah + teks config · " + HumanLog.fmtBytes(data.length);
                    isiText = isiCfg;
                } else {
                    // 0.5.6 — file teks biasa: isi persis yang dilayani (≤16 KB,
                    // potongan lebihnya diberi catatan jujur oleh HumanLog.capIsi).
                    isiLabel = "ISI · " + HumanLog.fmtBytes(data.length);
                    isiText = txt;
                }
                sentBytes = data.length;
                r = newFixedLengthResponse(Response.Status.OK, up.contentType,
                        new ByteArrayInputStream(data), data.length);
            } catch (Throwable io) {
                closeQuietly(up.in);
                r = newChunkedResponse(Response.Status.OK, up.contentType, new ByteArrayInputStream(new byte[0]));
            }
        } else {
            if (up.length >= 0 && up.in != null) {
                r = newFixedLengthResponse(LoginServer.safeStatus(up.status), up.contentType, up.in, up.length);
            } else if (up.in != null) {
                r = newChunkedResponse(LoginServer.safeStatus(up.status), up.contentType, up.in);
            } else {
                r = newFixedLengthResponse(LoginServer.safeStatus(up.status), up.contentType, "");
            }
            // 0.5.6 — binary/besar: catatan jujur, bukan isi karangan.
            isiLabel = "ISI · " + (sentBytes > 0 ? HumanLog.fmtBytes(sentBytes) : "?");
            isiText = "// binary/besar — isi tidak di-capture; diteruskan utuh dari "
                    + hostOf(usedHost);
        }
        // 0.5.4 — SATU baris gaya HttpCanary per request passthrough (owner:
        // "log online ya log aslinya seperti httpcanary"): config/protokol &
        // galat = PENTING; aset statis = TEKNIS (RINCI). Statistik Σ tetap di grup.
        try {
            String qStr = (query != null && query.length() > 0) ? ("?" + query) : "";
            String line = "⇄ GET " + hostOf(usedHost) + uri + qStr + " → HTTP " + up.status
                    + (sentBytes > 0 ? " · " + HumanLog.fmtBytes(sentBytes) : "")
                    + " · " + HumanLog.fmtMs(ms) + rewriteNote;
            if (isConfigExt(uri) || up.status >= 400) {
                HumanLog.evIsi("🌐", line, null, isiLabel, isiText);
            } else {
                HumanLog.evTechIsi("🌐", line, null, isiLabel, isiText);
            }
        } catch (Throwable ignored) {
        }
        r.addHeader("Cache-Control", "no-store");
        return r;
    }

    private static final class Upstream {
        int status = -1;
        long length = -1;
        String contentType = "application/octet-stream";
        InputStream in;
    }

    private Upstream fetchUpstream(String target) {
        Upstream up = new Upstream();
        HttpURLConnection conn = null;
        try {
            URL u = new URL(target);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            up.status = conn.getResponseCode();
            up.contentType = conn.getContentType();
            if (up.contentType == null || up.contentType.length() == 0) {
                up.contentType = contentType(new URL(target).getPath());
            }
            up.length = conn.getContentLength();
            up.in = (up.status >= 400) ? conn.getErrorStream() : conn.getInputStream();
        } catch (Throwable t) {
            up.status = -1;
            up.in = null;
            try {
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (Throwable ignored) {
            }
        }
        return up;
    }

    private static void closeQuietly(InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (Throwable ignored) {
        }
    }

    /** 0.5.0 REKAM — ekstensi yang dianggap CONFIG (dibaca utuh untuk perekaman). */
    private static boolean isConfigExt(String uri) {
        String u = uri.toLowerCase();
        return u.endsWith(".json") || u.endsWith(".version") || u.endsWith(".properties")
                || u.endsWith(".cfg") || u.endsWith(".txt") || u.endsWith(".bin");
    }

    /** 0.5.6 — ekstensi teks: isi capture boleh tampil sebagai teks. */
    private static boolean isTextExt(String uri) {
        String u = uri.toLowerCase();
        return isConfigExt(uri) || u.endsWith(".js") || u.endsWith(".css")
                || u.endsWith(".html") || u.endsWith(".htm") || u.endsWith(".xml")
                || u.endsWith(".csv") || u.endsWith(".log");
    }

    /**
     * 0.6.0 — ISI capture untuk file mirror www/: teks = byte persis UTUH
     * (aturan owner: log panjang wajib tampil & txt wajib menyimpan);
     * binary = catatan jujur. TIDAK ada rekonstruksi — yang tampil adalah
     * byte yang memang dilayani.
     */
    private static String isiFile(File f, String uri, long len) {
        try {
            if (!isTextExt(uri)) {
                return "// binary " + HumanLog.fmtBytes(len)
                        + " — tidak ditampilkan sebagai teks";
            }
            long n = len;
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[(int) n];
            int read = 0;
            while (read < n) {
                int k = in.read(buf, read, (int) n - read);
                if (k < 0) {
                    break;
                }
                read += k;
            }
            in.close();
            return new String(buf, 0, read, "UTF-8");
        } catch (Throwable t) {
            return "// isi tidak bisa dibaca: " + t;
        }
    }

    /** Peta path → server asli, sesuai EntryPoint (README §2.8) + isi setting bin. */
    private static String[] upstreamBase(String uri) {
        if (uri.startsWith("/bs/db/android")) {
            return new String[]{BS_PRIMARY, BS_FALLBACK};
        }
        if (uri.startsWith("/bs/")) {
            return new String[]{H5CDN, null};
        }
        if (uri.startsWith("/lzceshi/")) {
            return new String[]{DRAGON, null};
        }
        return null;
    }

    // ---------------- util ----------------

    /** URL utuh yang diminta client — dipakai di SEMUA baris log request (koreksi 0.3.1). */
    private static String gameUrl(String uri) {
        return "http://127.0.0.1:" + Config.gamePort() + uri;
    }

    private static String hostOf(String base) {
        try {
            return new URL(base).getHost();
        } catch (Throwable t) {
            return base;
        }
    }

    private static String nullSafe(String q) {
        return (q == null || q.length() == 0) ? "-" : q;
    }

    /**
     * Dedupe baris request PENTING kembar (404 tak terpetakan / 502 — koreksi
     * 0.3.1): kunci sama dalam jendela 10 dtk tidak dicatat ulang; saat jendela
     * lewat, jumlah yang ditenyapkan dilaporkan sekali. 0.4.6: hanya baris
     * penting — baris batch sudah pindah ke LogGroups (grup dasbor).
     */
    private static final class ReqLog {
        private static final HashMap<String, long[]> SEEN = new HashMap<String, long[]>();
        private static final long WINDOW_MS = 10000L;

        /** Baris request PENTING (404 tak terpetakan, 502) — selalu tampil. */
        static void ev(String icon, String key, String sentence, String detail) {
            evIsi(icon, key, sentence, detail, null, null);
        }

        /** 0.5.6 — baris request penting yang membawa ISI jawaban (capture). */
        static void evIsi(String icon, String key, String sentence, String detail,
                          String isiLabel, String isi) {
            long now = System.currentTimeMillis();
            long[] e;
            boolean suppress;
            synchronized (SEEN) {
                if (SEEN.size() > 256) {
                    SEEN.clear(); // pengaman memori — kunci hanya status+path
                }
                e = SEEN.get(key);
                if (e == null) {
                    e = new long[2];
                    SEEN.put(key, e);
                }
                suppress = (e[0] > 0 && now - e[0] < WINDOW_MS);
                if (suppress) {
                    e[1]++;
                    return;
                }
                e[0] = now;
            }
            long n = e[1];
            e[1] = 0;
            if (n > 0) {
                sentence = sentence + " · (+" + n + " request kembar dalam 10 dtk tidak dicatat)";
            }
            HumanLog.evIsi(icon, sentence, detail, isiLabel, isi);
        }
    }

    private static String contentType(String uri) {
        String u = uri.toLowerCase();
        if (u.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (u.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (u.endsWith(".version") || u.endsWith(".txt") || u.endsWith(".properties") || u.endsWith(".cfg")) {
            return "text/plain; charset=utf-8";
        }
        if (u.endsWith(".html") || u.endsWith(".htm")) {
            return "text/html; charset=utf-8";
        }
        if (u.endsWith(".png")) {
            return "image/png";
        }
        if (u.endsWith(".jpg") || u.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (u.endsWith(".zip")) {
            return "application/zip";
        }
        return "application/octet-stream";
    }

    private static Response textResp(Response.IStatus st, String msg) {
        Response r = newFixedLengthResponse(st, "text/plain; charset=utf-8", msg);
        r.addHeader("Cache-Control", "no-store");
        return r;
    }

    private static Response jsonResp(Response.IStatus st, String json) {
        Response r = newFixedLengthResponse(st, "application/json; charset=utf-8", json);
        r.addHeader("Cache-Control", "no-store");
        return r;
    }

    private static Response htmlResp(byte[] d) {
        Response r = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8",
                new ByteArrayInputStream(d), d.length);
        r.addHeader("Cache-Control", "no-store");
        return r;
    }
}

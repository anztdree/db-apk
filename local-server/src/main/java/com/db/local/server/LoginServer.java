package com.db.local.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;

/**
 * SERVER LOGIN LOKAL — port 8610 (config loginPort). Menerapkan protokol yang
 * SAMA dengan server asli login.popoh5.com:610, hasil rekayasa balik + probe
 * langsung 2026-11-02 (README §2.10):
 *
 *   engine.io v3 (EIO=3) transport polling + socket.io v2.1 (client game memakai
 *   socket.io.min v2.1.0 di bundle H5). Framing polling: "<panjang>:<paket>"
 *   berurutan. Event: "handler.process" (pesan, callback-ack) — jawaban lewat
 *   ACK socket.io "43<id>[{ret,data}]".
 *
 *   TIDAK ada websocket: handshake mengumumkan "upgrades":[] — client v2.1
 *   tetap di polling (di server asli, POST polling malah 502 dari nginx; di
 *   lokal POST polling sehat karena server kita sendiri).
 *
 *   Action yang dilayani (semua yang dipakai H5 — hasil bedah main.min):
 *     loginGame          → masuk/daftar otomatis (akun lokal, LoginData)
 *     GetServerList      → daftar server (config/servers.json — snapshot asli)
 *     LoginAnnounce      → pengumuman (kosong)
 *     SaveHistory / SaveLanguage / SaveUserEnterInfo → diterima, dijawab ret 0
 *
 * MODE ONLINE (0.5.1 — tuntutan owner "CONFIG yg jelas bukan rekam jejak"):
 *   socket.io TIDAK dilayani lokal, melainkan DITERUSKAN UTUH (byte-per-byte)
 *   ke server asli https://login.popoh5.com:610 (HTTPS — hasil probe 2026-11-05:
 *   TLSv1.2, sertifikat DigiCert *.popoh5.com; HTTP polos ditolak "plain HTTP
 *   request sent to HTTPS port"). Alasannya: 0.5.1 config setting bin &
 *   serversetting.json menulis-ulang jalur login ke server lokal di SEMUA mode
 *   (GameServer/SettingBin) — maka server ini WAJIB punya jalur passthrough,
 *   kalau tidak login online mati. Token & daftar server tetap dikeluarkan
 *   SERVER ASLI (progres pemain tetap di sana), tetapi seluruh alur login kini
 *   LEWAT server lokal — terlihat di log (📡 handshake, ✅ loginGame, 📋 daftar
 *   server ASLI, 🔌 pindah) dan terekam REKAM lapisan login.
 *   Websocket TIDAK diteruskan: klien engine.io v3 gagal upgrade → tetap di
 *   polling (perilaku terbukti di device: "polling, tanpa websocket").
 *
 * Catatan desain:
 * - Sesi engine.io = sid 16 karakter; kedaluwarsa 90 dtk tanpa aktivitas
 *   (client ping tiap 25 dtk — jawab PONG "3").
 * - Long-poll GET menunggu antrean sampai 20 dtk; jawaban boleh kosong
 *   (client mengulang sendiri — perilaku engine.io standar).
 * - Semua jawaban dibungkus {ret:0, data:"<json-string>"} — H5 mem-parsing
 *   data sebagai string JSON (processHandlerWithLogin di main.min).
 * - Log: hanya kejadian nyata (login/daftar server) — ping & koneksi senyap.
 */
public final class LoginServer extends NanoHTTPD {

    private static final long SESSION_TTL_MS = 90000L;
    private static final long POLL_WAIT_MS = 20000L;
    private static final int MAX_SESSIONS = 64;
    /** Dedupe milestone GetServerList (0.4.8): ulangan < 10 menit tidak dilog ulang. */
    private static volatile long lastServerListLogAt = 0L;
    /** 0.4.9: handshake socket naik ke log penting — dedupe 10 mnt (H5 bisa
     *  menyambung ulang beberapa kali; yang dilaporkan = koneksi BARU setelah
     *  jeda, bukan polling yang sama). */
    private static volatile long lastConnectLogAt = 0L;
    private static final String HANDSHAKE_JSON =
            "{\"sid\":\"%SID%\",\"upgrades\":[],\"pingInterval\":25000,\"pingTimeout\":5000}";

    private final ConcurrentHashMap<String, Sess> sessions = new ConcurrentHashMap<String, Sess>();
    private final Object SWEEPLOCK = new Object();

    /** Sesi engine.io satu client — antrean paket keluar + waktu terakhir hidup.
     *  0.5.0 REKAM: + waktu lahir & aksi terakhir — bahan baris 🔌 disconnect. */
    private static final class Sess {
        final String sid;
        final LinkedBlockingQueue<String> out = new LinkedBlockingQueue<String>();
        final long createdAt = System.currentTimeMillis();
        volatile long lastSeen = System.currentTimeMillis();
        volatile boolean closed = false;
        volatile String lastAction = null;

        Sess(String sid) {
            this.sid = sid;
        }
    }

    public LoginServer(String bind, int port) throws java.io.IOException {
        super(bind, port);
        setTempFileManagerFactory(new TempFileManagerFactory() {
            public TempFileManager create() {
                return new DefaultTempFileManager();
            }
        });
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method m = session.getMethod();
        String uri = session.getUri();
        Map<String, String> parms = session.getParms();
        try {
            // 0.4.2 ALUR SDK: rute API quickgame + loginchecknative — dibaca dulu agar
            // tidak jatuh ke 404 socket.io. Body POST dibaca SEKALI di sini (stream
            // tidak bisa dibaca dua kali) lalu diteruskan ke SdkApi.
            if ((uri.startsWith("/v1/") || uri.equals("/loginchecknative")
                    || uri.equals("/loginchecknative/")) && m == Method.POST) {
                String sdkBody = readBody(session);
                Response r = SdkApi.handle(m, uri, parms, sdkBody);
                if (r != null) {
                    return r;
                }
            } else if (uri.startsWith("/v1/") || uri.equals("/loginchecknative")
                    || uri.equals("/loginchecknative/")) {
                Response r = SdkApi.handle(m, uri, parms, "");
                if (r != null) {
                    return r;
                }
            }
            if (!"/socket.io/".equals(uri) && !"/socket.io".equals(uri)) {
                return text(Response.Status.NOT_FOUND,
                        "Server login lokal melayani /socket.io/ (protokol game) + API SDK (/v1/*, "
                                + "/loginchecknative). Dasbor developer ada di port " + Config.debugPort() + ".");
            }
            // 0.5.1 — mode ONLINE: passthrough utuh ke server login ASLI. Config
            // (bin/serversetting) sudah menulis-ulang jalur login ke server lokal
            // di SEMUA mode — di sini tempatnya diteruskan (byte-per-byte).
            if ("online".equals(Config.mode())) {
                return proxyOnline(session, m);
            }
            if (Method.GET == m) {
                String sid = parm(parms, "sid");
                if (sid == null || sid.length() == 0) {
                    return handshake();
                }
                return poll(sid);
            }
            if (Method.POST == m) {
                return receive(session, parm(parms, "sid"));
            }
            return text(Response.Status.METHOD_NOT_ALLOWED, "Metode tidak didukung — hanya GET/POST polling");
        } catch (Throwable t) {
            try {
                HumanLog.ev("⚠️", "Server login lokal gagal memproses permintaan — dijawab 500", String.valueOf(t));
            } catch (Throwable ignored) {
            }
            return text(Response.Status.INTERNAL_ERROR, "kesalahan server login lokal: " + t);
        }
    }

    // ---------------- engine.io v3 polling ----------------

    /** Handshake: buat sesi + kirim OPEN engine.io dan OPEN socket.io (framing v3). */
    private Response handshake() {
        sweepExpired();
        String sid = newSid();
        Sess s = new Sess(sid);
        if (sessions.size() >= MAX_SESSIONS) {
            sessions.clear(); // pengaman — client akan menyambung ulang sendiri
        }
        sessions.put(sid, s);
        String open = HANDSHAKE_JSON.replace("%SID%", sid);
        String body = frame("0" + open) + frame("40");
        try {
            // 0.5.0 REKAM — handshake terekam utuh (bahan bangun protokol):
            Capture.login("→", "handshake", "GET /socket.io/ · engine.io v3 · polling", null, 0, 0);
            Capture.login("←", "open", "sid " + sid + " · upgrades [] · ping 25 dtk",
                    body, body.length(), 0);
            // 0.4.9 — koneksi klien = bagian ALUR yang harus terlihat (owner:
            // "log harus menceritakan sampai serverlist"): handshake naik dari
            // TEKNIS ke PENTING dengan rincian sesi. Dedupe 10 mnt — sambung
            // ulang dalam sesi yang sama tidak mengulang baris.
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastConnectLogAt >= 600000L) {
                lastConnectLogAt = nowMs;
                HumanLog.ev("📡", "Klien game tersambung ke server login lokal — jalur LOCAL",
                        "Jalur    : LOCAL — login & sesi dikelola server ini (dragonball.db)\n"
                                + "Sesi     : " + sid + " (engine.io v3 · polling, tanpa websocket)\n"
                                + "Tujuan   : 127.0.0.1:" + Config.loginPort()
                                + " (endpoint login dari paket MANUAL config-offline — "
                                + "setting bin, dilayani apa adanya)\n"
                                + "Lanjutan : menunggu loginGame + GetServerList dari klien H5");
            }
        } catch (Throwable ignored) {
        }
        return text(Response.Status.OK, body);
    }

    /** Long-poll: tunggu paket keluar sampai 20 dtk; boleh jawab kosong. */
    private Response poll(String sid) {
        Sess s = sessions.get(sid);
        if (s == null || s.closed) {
            return unknownSession();
        }
        s.lastSeen = System.currentTimeMillis();
        StringBuilder body = new StringBuilder(256);
        try {
            String first = s.out.poll(POLL_WAIT_MS, TimeUnit.MILLISECONDS);
            if (first != null) {
                body.append(frame(first));
                String next;
                while ((next = s.out.poll(20, TimeUnit.MILLISECONDS)) != null) {
                    body.append(frame(next));
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        Response r = text(Response.Status.OK, body.toString());
        r.setKeepAlive(false);
        return r;
    }

    /** Terima flush paket client (POST) — proses lalu antrekan jawaban. */
    private Response receive(IHTTPSession session, String sid) {
        Sess s = (sid == null) ? null : sessions.get(sid);
        if (s == null || s.closed) {
            return unknownSession();
        }
        s.lastSeen = System.currentTimeMillis();
        String body;
        try {
            body = readBody(session);
        } catch (Throwable t) {
            return text(Response.Status.BAD_REQUEST, "bad request");
        }
        int i = 0;
        final int len = body.length();
        while (i < len) {
            int c = body.indexOf(':', i);
            if (c < 0) {
                break;
            }
            int plen;
            try {
                plen = Integer.parseInt(body.substring(i, c));
            } catch (NumberFormatException nfe) {
                break;
            }
            if (plen < 0 || c + 1 + plen > len) {
                break;
            }
            String pkt = body.substring(c + 1, c + 1 + plen);
            handlePacket(s, pkt);
            i = c + 1 + plen;
        }
        return text(Response.Status.OK, "ok");
    }

    /**
     * Baca body POST LANGSUNG dari stream (persis Content-Length) — TIDAK lewat
     * parseBody. Alasan: vendored NanoHTTPD memakai aturan form untuk
     * Content-Type "application/x-www-form-urlencoded" (isi masuk ke parms,
     * files["postData"] TIDAK diisi) sehingga framing engine.io bisa hilang.
     * Client game asli mengirim text/plain, tetapi dengan baca langsung server
     * ini tahan SEMUA Content-Type (curl, debugger, WebView varian apa pun).
     */
    static String readBody(IHTTPSession session) throws Exception {
        String cl = session.getHeaders().get("content-length");
        int n = 0;
        if (cl != null) {
            try {
                n = Integer.parseInt(cl.trim());
            } catch (NumberFormatException nfe) {
                n = 0;
            }
        }
        if (n <= 0) {
            return "";
        }
        if (n > 1_048_576) {
            n = 1_048_576; // pengaman — flush engine.io polling tak perlu lebih besar
        }
        java.io.InputStream in = session.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(n);
        byte[] buf = new byte[8192];
        int left = n;
        while (left > 0) {
            int r = in.read(buf, 0, Math.min(buf.length, left));
            if (r < 0) {
                break;
            }
            bos.write(buf, 0, r);
            left -= r;
        }
        return new String(bos.toByteArray(), "UTF-8");
    }

    // ---------------- socket.io v2 paket ----------------

    private void handlePacket(Sess s, String pkt) {
        if (pkt == null || pkt.length() == 0) {
            return;
        }
        char t = pkt.charAt(0);
        switch (t) {
            case '2': // PING engine.io → PONG
                s.out.offer("3");
                try {
                    // 0.5.0 REKAM — keepalive satu entri (dua arah dalam satu kejadian)
                    Capture.login("→", "ping→pong", "keepalive engine.io · interval 25 dtk · sid "
                            + s.sid, null, 1, 0);
                } catch (Throwable ignored) {
                }
                return;
            case '3': // PONG (tidak diharapkan dari client v3) — abaikan
                return;
            case '4': // engine.io MESSAGE → payload socket.io
                if (pkt.length() < 2) {
                    return;
                }
                handleSio(s, pkt.substring(1));
                return;
            default: // 0/1/5/6 dari client → abaikan (tanpa websocket/binary)
                return;
        }
    }

    private void handleSio(Sess s, String p) {
        if (p.length() == 0) {
            return;
        }
        char type = p.charAt(0);
        if (type == '0') {
            return; // CONNECT namespace "/" — sudah terbuka lewat OPEN
        }
        if (type == '1') {
            // DISCONNECT — klien menutup sendiri. 0.5.0: TIDAK diam lagi —
            // ini titik ketika klien pindah ke server game (atau layar ditutup).
            s.closed = true;
            sessions.remove(s.sid);
            onSessionEnded(s, true);
            return;
        }
        if (type != '2') {
            return; // 3=ACK dari client? 4=ERROR? — tidak dipakai game
        }
        long sioT0 = System.currentTimeMillis();
        // EVENT: "42<ackid>[\"handler.process\",{pesan}]"
        int i = 1;
        while (i < p.length() && p.charAt(i) >= '0' && p.charAt(i) <= '9') {
            i++;
        }
        String ackId = p.substring(1, i);
        String json = p.substring(i);
        String msg = firstArgJson(json);
        String action = strField(msg, "action");
        s.lastAction = (action == null) ? "(tanpa action)" : action;
        try {
            // 0.5.0 REKAM — event masuk TEREKAM UTUH (payload penuh):
            Capture.login("→", action == null ? "handler.process" : action,
                    "sid " + s.sid
                            + (ackId.length() > 0 ? " · ack#" + ackId : ""),
                    msg, msg.length(), 0);
        } catch (Throwable ignored) {
        }
        String data;
        if ("loginGame".equals(action)) {
            String userId = strField(msg, "userId");
            if (userId == null || userId.length() == 0) {
                userId = LoginData.localUserId();
            }
            String password = strField(msg, "password");
            long t0 = System.currentTimeMillis();
            data = LoginData.loginGameResponse(userId, password);
            long ms = System.currentTimeMillis() - t0;
            boolean baru = data.indexOf("\"isCreated\":true") >= 0;
            // 0.4.9: hasil + rincian proses penuh (TIDAK dipangkas).
            HumanLog.ev("✅", "Login game sukses — " + (baru ? "akun BARU dibuat" : "akun lama"),
                    "Akun     : " + userId + "\n"
                            + "Proses   : " + HumanLog.fmtMs(ms) + " · jawaban " + HumanLog.fmtBytes(data.length())
                            + " (ret 0, skema asli login.popoh5.com:610)\n"
                            + "Sumber   : dragonball.db (accounts) — identitas mengikuti pilihan layar SDK");
        } else if ("GetServerList".equals(action)) {
            long t0 = System.currentTimeMillis();
            data = LoginData.serverListData();
            long ms = System.currentTimeMillis() - t0;
            // 0.5.4 — isi paket manual (servers.json) masuk seksi CONFIG dasbor.
            try {
                String up = LoginData.gameUpstream();
                ConfigView.put("servers", "servers.json (daftar server)",
                        "OFFLINE · paket manual config-offline/ · " + HumanLog.fmtBytes(data.length())
                                + " · " + HumanLog.fmtMs(ms)
                                + (up.length() > 0 ? " · upstream relay: " + up
                                        : " · upstream relay: BELUM DIISI"),
                        data + "\n\n— catatan —\n"
                                + "url entri = tujuan io.connect klien saat klik Play (127.0.0.1:"
                                + Config.gamePort() + " = server game lokal).\n"
                                + "Field \"upstream\" = server game ASLI tujuan relay :8080.\n"
                                + "Isi di perangkat: config/game-upstream.txt (1 baris URL, tanpa build ulang)");
            } catch (Throwable ignored) {
            }
            // 0.4.9 — MILESTONE TUNGGAL + RINCIAN PENUH (permintaan owner:
            // "kurang rinci" + "gak boleh disingkat"):
            //  1) daftar = bawaan APK yang DISAJIKAN server lokal (bukan diambil
            //     dari server asli) — mematikan dugaan berulang "config loginserver
            //     masih mengarah ke server aslinya";
            //  2) baris Lanjutan menjelaskan kenapa log "diam" sesudahnya: koneksi
            //     gameplay langsung ke server game asli, tidak lewat server lokal.
            //  Dedupe 10 menit (H5 bisa meminta daftar lagi saat bolak-balik layar).
            long nowMs = System.currentTimeMillis();
            boolean dup = (nowMs - lastServerListLogAt) < 600000L;
            lastServerListLogAt = nowMs;
            if (!dup) {
                String up = LoginData.gameUpstream();
                HumanLog.ev("📋", "Daftar server terkirim — " + LoginData.serverCount() + " server (paket MANUAL)",
                        "Sumber   : config-offline/servers.json — paket CONFIG OFFLINE MANUAL bawaan APK\n"
                                + "           (bukan snapshot online — owner: online cuma jejak arah, bukan config asli offline)\n"
                                + "Tujuan   : url tiap entri = 127.0.0.1:" + Config.gamePort()
                                + " — klik Play masuk SERVER GAME LOKAL\n"
                                + "Upstream : " + (up.length() > 0
                                        ? up + " — socket game :8080 diteruskan ke sini (relay)"
                                        : "BELUM DIISI — isi config/game-upstream.txt (1 baris URL server game asli)"
                                                + " atau field \"upstream\" di config-offline/servers.json") + "\n"
                                + "Ukuran   : " + HumanLog.fmtBytes(data.length()) + " · " + HumanLog.fmtMs(ms)
                                + " · pemain sekarang di layar pilih server");
            }
        } else if ("LoginAnnounce".equals(action)) {
            data = "{}";
        } else if ("SaveHistory".equals(action) || "SaveLanguage".equals(action)
                || "SaveUserEnterInfo".equals(action)) {
            data = "{}";
        } else {
            // aksi tak dikenal — jawab ret 0 kosong agar game tidak menggantung
            data = "{}";
            HumanLog.ev("ℹ️", "Aksi login tak dikenal: " + (action == null ? "(kosong)" : action)
                    + " — dijawab ret 0 kosong (game tidak menggantung)", null);
        }
        if (ackId.length() > 0) {
            String resp = "{\"ret\":0,\"data\":" + quote(data) + "}";
            long ms = System.currentTimeMillis() - sioT0;
            try {
                // 0.5.0 REKAM — ACK keluar TEREKAM UTUH (jawaban yang dikirim klien;
                // daftar server 520 KB ikut tersimpan file — config asli):
                Capture.login("←", "ack#" + ackId + " → "
                        + (action == null ? "handler.process" : action),
                        "sid " + s.sid, resp, resp.length(), ms);
            } catch (Throwable ignored) {
            }
            s.out.offer("43" + ackId + "[" + resp + "]");
        }
    }

    /**
     * 0.5.0 — penutup sesi login dilaporkan (dulu DIAM total — pemain melihat
     * log berhenti di 📋 tanpa tahu klien sudah pindah). Klien menutup sendiri
     * (socket.io DISCONNECT) atau sesi kedaluwarsa 90 dtk (sweepExpired).
     * Dedupe 60 dtk — sambung-ulang cepat tidak membanjiri.
     */
    private static volatile long lastEndLogAt = 0L;

    private static void onSessionEnded(Sess s, boolean byClient) {
        try {
            Capture.login("→", byClient ? "disconnect" : "expired",
                    "sid " + s.sid, null, 0, 0);
        } catch (Throwable ignored) {
        }
        long now = System.currentTimeMillis();
        if (now - lastEndLogAt < 60000L) {
            return; // dedupe 60 dtk
        }
        lastEndLogAt = now;
        long age = now - s.createdAt;
        String act = s.lastAction;
        if (byClient) {
            String up = LoginData.gameUpstream();
            HumanLog.ev("🔌", "Klien menutup sesi login",
                    "Sesi     : " + s.sid + " · hidup " + HumanLog.fmtMs(age) + "\n"
                            + "Terakhir : " + (act == null ? "(belum ada aksi — hanya handshake)" : act) + "\n"
                            + "Lanjutan : klien io.connect ke url entri daftar server — http://127.0.0.1:"
                            + Config.gamePort() + " (relay socket game lokal)");
        } else {
            HumanLog.evTech("🔌", "Sesi login kedaluwarsa (90 dtk tanpa aktivitas) — klien sudah pergi",
                    "Sesi     : " + s.sid + " · terakhir " + (act == null ? "-" : act));
        }
    }

    // ---------------- 0.5.1: mode ONLINE — passthrough ke server login asli ----------------

    /** Server login ASLI (hasil probe 2026-11-05 — HTTPS wajib, TLSv1.2 DigiCert). Publik — diuji harness. */
    public static final String UPSTREAM_LOGIN = "https://login.popoh5.com:610";

    private static final int PROXY_TIMEOUT_GET_MS = 45000;  // long-poll upstream ~25-30 dtk
    private static final int PROXY_TIMEOUT_POST_MS = 30000;

    /** Jawaban upstream yang diteruskan — byte utuh + status + content-type. */
    static final class UpResp {
        final int status;
        final String contentType;
        /* 0.6.0 — tidak lagi final: respons GetServerList passthrough diganti
         * dgn versi hasil rewrite url entri (relay game semua mode). */
        byte[] body;
        UpResp(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = (contentType == null || contentType.length() == 0)
                    ? "text/plain; charset=utf-8" : contentType;
            this.body = body;
        }
    }

    /**
     * Teruskan GET/POST polling engine.io ke login.popoh5.com:610 apa adanya
     * (query sama, body sama, jawaban byte-per-byte sama). Setiap arah terekam
     * REKAM; kejadian penting naik ke log (dedupe sama dengan mode LOCAL).
     */
    private Response proxyOnline(IHTTPSession session, Method m) {
        String query = session.getQueryParameterString();
        String qs = (query == null || query.length() == 0) ? "" : query;
        String transport = parm(session.getParms(), "transport");
        if ("websocket".equals(transport)) {
            // Klien mencoba upgrade websocket → kita tidak meneruskan → engine.io
            // client akan tetap di polling (perilaku terbukti di device).
            return text(safeStatus(400),
                    "websocket tidak diteruskan — klien engine.io kembali ke polling");
        }
        long t0 = System.currentTimeMillis();
        String body = null;
        if (Method.POST == m) {
            try {
                body = readBody(session);
            } catch (Throwable t) {
                body = "";
            }
        }
        String target = UPSTREAM_LOGIN + "/socket.io/" + (qs.length() > 0 ? ("?" + qs) : "");
        UpResp up;
        try {
            up = forwardLogin(target, body, Method.POST == m,
                    Method.POST == m ? PROXY_TIMEOUT_POST_MS : PROXY_TIMEOUT_GET_MS);
        } catch (Throwable t) {
            long ms = System.currentTimeMillis() - t0;
            try {
                Capture.login("→", "proxy-gagal", "ke login.popoh5.com:610", body == null ? null : body,
                        body == null ? 0 : body.length(), ms);
            } catch (Throwable ignored) {
            }
            HumanLog.ev("⚠️", "Server login ASLI tidak terjangkau lewat passthrough — login online gagal",
                    String.valueOf(t));
            return text(safeStatus(502),
                    "upstream login.popoh5.com:610 tidak terjangkau — " + t);
        }
        long ms = System.currentTimeMillis() - t0;
        String respText = new String(up.body, StandardCharsets.UTF_8);
        // 0.6.1 — JEJAK ASLI (HttpCanary): teks respons dari upstream SEBELUM
        // tulis-ulang disimpan khusus — REKAM + blok ISI menampilkan nilai ASLI
        // utuh (nama/url apa adanya dari server = bahan diskusi/diagnosis).
        // Tulis-ulang = ROUTING saja dan dicatat TERPISAH di field Routing.
        // Dulu 0.6.0: Capture/ISI ikut memakai teks HASIL tulis-ulang → nilai
        // ASLI hilang dari jejak (komentar 0.6.0 tidak cocok kode — temuan
        // owner: "isi log anda yg ngawur"; contoh: Gerbang tampil 127.0.0.1).
        String respAsli = respText;
        int asliLen = up.body.length;
        // 0.6.0 — GetServerList: tulis-ulang url entri → relay game :8080 + isi
        // tabel upstream ASLI (ROUTING agar Play mode ONLINE tertangkap penuh
        // di log — BUKAN sumber config; paket config-offline tetap manual).
        // Capture di bawah merekam respons ASLI (sebelum tulis-ulang); yang
        // dilayani klien = teks hasil tulis-ulang (routing).
        // 0.6.2 — tulis-ulang kini frame-per-frame dgn prefix panjang engine.io
        // DIHITUNG ULANG (rewriteServerListFramed) — dulu prefix lama tertinggal
        // → parser klien menolak payload → SERVERLIST kosong.
        int rewrote = 0;
        int servedLen = asliLen;
        String framing = null;
        if (Method.GET == m && respText.indexOf("serverList") >= 0) {
            java.util.ArrayList<String[]> table = parseServerTable(respText);
            if (table != null && table.size() > 0) {
                // 0.6.2 — tulis-ulang url dgn framing engine.io DIHITUNG ULANG.
                // Bukti log perangkat 0.6.1 (2026-11-13 19:33:56): respons ASLI
                // upstream = frame "618016:430[...]" (REKAM events.jsonl: prefix
                // 618016 = panjang paket, selisih 0 — framing upstream sempurna);
                // tulis-ulang 2111 url memangkas paket → 597094, prefix TIDAK
                // dihitung ulang → parser polling klien (decodePayload: "a !=
                // s.length → r(w,0,1)") MENOLAK seluruh payload → ACK
                // GetServerList tak pernah sampai ke UI → SERVERLIST kosong
                // (versi original tak menyentuh payload → daftar muncul).
                String r = rewriteServerListFramed(respText);
                if (r != null) {
                    framing = firstFramePrefix(respText) + " → " + firstFramePrefix(r);
                    respText = r;
                    rewrote = table.size();
                    up.body = respText.getBytes(StandardCharsets.UTF_8);
                    servedLen = up.body.length;
                    GameServer.setOnlineUpstreamTable(table);
                }
            }
        }
        try {
            // REKAM — jejak upstream ASLI dua arah (pembanding utama utk versi
            // lokal; 0.6.1: arah ← = respons ASLI SEBELUM tulis-ulang):
            Capture.login("→", Method.POST == m ? "handler.process (passthrough)" : "polling (passthrough)",
                    "ke login.popoh5.com:610" + (qs.length() > 0 ? " · " + ringkasQuery(qs) : ""),
                    body, body == null ? 0 : body.length(), ms);
            Capture.login("←", "HTTP " + up.status + " (passthrough)",
                    "dari login.popoh5.com:610 — respons ASLI (sebelum tulis-ulang)",
                    respAsli, asliLen, ms);
        } catch (Throwable ignored) {
        }
        // ----- log alur (dedupe = persis mode LOCAL) -----
        if (Method.POST == m && body != null && body.length() > 0) {
            logOnlineEvents(body);
        }
        if (Method.GET == m) {
            // 0.6.1 — teks ASLI yang dilog (Gerbang + ISI = nilai asli); routing
            // dilaporkan sebagai field terpisah di dalam logOnlineResponse.
            // 0.6.2 — ukuran yang diterima klien + prefix framing ikut dinyatakan
            // (log gaya HttpCanary: nilai ASLI utuh DI SEBELAH apa yang klien
            // terima — tidak ada kalimat ambigu).
            logOnlineResponse(qs, respAsli, asliLen, ms, rewrote, servedLen, framing);
        }
        Response r = newFixedLengthResponse(safeStatus(up.status == -1 ? 502 : up.status),
                up.contentType, new ByteArrayInputStream(up.body), up.body.length);
        r.addHeader("Cache-Control", "no-store");
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    /** POST passthrough: urai framing → event socket.io → baris log alur. */
    private void logOnlineEvents(String body) {
        try {
            java.util.ArrayList<String> pkts = parseFrames(body);
            for (String pkt : pkts) {
                if (pkt.length() >= 2 && pkt.charAt(0) == '4' && pkt.charAt(1) == '1') {
                    // socket.io DISCONNECT — klien pindah ke server game
                    onlineDisconnect();
                }
                if (pkt.length() >= 3 && pkt.charAt(0) == '4' && pkt.charAt(1) == '2') {
                    int i = 2;
                    while (i < pkt.length() && pkt.charAt(i) >= '0' && pkt.charAt(i) <= '9') {
                        i++;
                    }
                    String msg = firstArgJson(pkt.substring(i));
                    String action = strField(msg, "action");
                    if ("loginGame".equals(action)) {
                        String userId = strField(msg, "userId");
                        String pwd = strField(msg, "password");
                        String sidChosen = strField(msg, "serverId");
                        if (sidChosen != null && sidChosen.length() > 0) {
                            GameServer.setOnlineChosenId(sidChosen);
                        }
                        HumanLog.ev("✅", "Login game diteruskan ke server ASLI (passthrough)",
                                "Jalur    : ONLINE — passthrough utuh ke server asli\n"
                                        + "Akun     : " + (userId == null ? "-" : userId) + "\n"
                                        + "Sandi    : " + (pwd == null ? "-"
                                                : pwd.length() + " karakter — diteruskan UTUH") + "\n"
                                        + "Proses   : permintaan klien diteruskan utuh ke login.popoh5.com:610\n"
                                        + "Sumber   : SERVER ASLI — token & identitas dikeluarkan di sana "
                                        + "(progres pemain tetap tersimpan di server asli)");
                    } else if (action != null && action.length() > 0
                            && !"GetServerList".equals(action)) {
                        // 0.6.0 — catat serverId pilihan pemain bila terbaca di paket
                        if ("SaveUserEnterInfo".equals(action) || "enterGame".equals(action)
                                || "loginGame".equals(action)) {
                            String sidChosen = strField(msg, "serverId");
                            if (sidChosen == null || sidChosen.length() == 0) {
                                sidChosen = strField(msg, "serverid");
                            }
                            if (sidChosen != null && sidChosen.length() > 0) {
                                GameServer.setOnlineChosenId(sidChosen);
                            }
                        }
                        HumanLog.evTech("ℹ️", "Aksi login (passthrough): " + action, null);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** GET passthrough: OPEN handshake / ACK daftar server → baris log alur. */
    private void logOnlineResponse(String qs, String respText, int len, long ms, int rewrote,
            int servedLen, String framing) {
        try {
            boolean handshake = (qs == null || qs.indexOf("sid=") < 0);
            if (handshake && respText.indexOf("\"sid\"") >= 0) {
                long now = System.currentTimeMillis();
                if (now - lastConnectLogAt >= 600000L) {
                    lastConnectLogAt = now;
                    HumanLog.ev("📡", "Klien tersambung — jalur ONLINE: passthrough ke server login ASLI",
                            "Jalur    : ONLINE — passthrough utuh ke server asli\n"
                                    + "Sesi     : sid dari server asli (passthrough utuh)\n"
                                    + "Tujuan   : klien → 127.0.0.1:" + Config.loginPort() + " → login.popoh5.com:610"
                                    + " (HTTPS, TLS 1.2)\n"
                                    + "Config   : setting bin + serversetting.json menunjuk server lokal "
                                    + "(semua mode) — server ini yang meneruskan\n"
                                    + "Lanjutan : loginGame + GetServerList dijawab SERVER ASLI (progres tetap di sana)");
                }
                return;
            }
            if (respText.indexOf("serverList") >= 0) {
                int count = countServerIds(respText);
                // 0.6.1 — gerbang dibaca dari teks ASLI (dulu 0.6.0: teks hasil
                // tulis-ulang → Gerbang tampil 127.0.0.1:8080 dua kali — nilai
                // asli hilang; laporan owner: isi log ngawur).
                String gw = firstAndLastGateway(respText);
                // 0.6.1 — ROUTING dicatat terpisah: apa yang diterima klien
                // (url = relay lokal) vs apa yang dijawab upstream (url ASLI).
                String routing = rewrote > 0
                        ? "klien menerima url entri = http://127.0.0.1:" + Config.gamePort()
                                + " (relay game lokal) — relay meneruskan ke url ASLI per entri"
                                + " · framing engine.io UTUH: prefix panjang dihitung ulang per frame ("
                                + framing + ") — parser klien memvalidasi panjang frame; dulu 0.6.1"
                                + " prefix tertinggal → payload ditolak parser klien → SERVERLIST kosong"
                                + " · tulis-ulang = routing, BUKAN sumber config"
                        : "passthrough utuh — klien menerima respons ASLI apa adanya";
                long now = System.currentTimeMillis();
                if (now - lastServerListLogAt >= 600000L) {
                    lastServerListLogAt = now;
                    HumanLog.evIsi("📋", "Daftar server ASLI terkirim — " + count + " server · jalur ONLINE"
                                    + (rewrote > 0 ? " (klien lewat relay lokal)" : " (passthrough utuh)"),
                            "Sumber   : SERVER ASLI login.popoh5.com:610 — respons utuh dari upstream"
                                    + " (jejak capture — nilai asli apa adanya, bahan diskusi/diagnosis)\n"
                                    + "Gerbang  : " + (gw == null
                                            ? "(tidak ada http(s):// terbaca — payload utuh di blok ISI)"
                                            : gw) + "\n"
                                    + "Routing  : " + routing + "\n"
                                    + "Ukuran   : ASLI " + HumanLog.fmtBytes(len)
                                    + (rewrote > 0 ? " · ke klien " + HumanLog.fmtBytes(servedLen) : "")
                                    + " · " + HumanLog.fmtMs(ms)
                                    + " · pemain sekarang di layar pilih server",
                            "respons ASLI dari login.popoh5.com:610 (utuh — jejak capture)", respText);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 0.5.2 AKAR — gerbang PERTAMA dan TERAKHIR dari daftar server ASLI.
     * 0.6.0 — scanner regex: menangani JSON polos maupun bentuk ter-escape di
     * dalam framing socket.io; url ditampilkan UTUH.
     * Null bila tidak ada http(s):// yang terbaca.
     */
    public static String firstAndLastGateway(String respText) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\\\?\"url\\\\?\"\\\\?:\\\\?\"(https?://[^\"\\\\]+)\\\\?\"")
                    .matcher(respText);
            String first = null;
            String last = null;
            while (m.find()) {
                String v = m.group(1);
                if (first == null) {
                    first = v;
                }
                last = v;
            }
            if (first == null) {
                return null;
            }
            return first.equals(last) ? first : "pertama " + first + " · terakhir " + last;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 0.6.0 — pasangkan (serverId, url ASLI) dari respons daftar server ASLI.
     * Menangani JSON polos maupun ter-escape. Pasangan disusun berurutan;
     * bila jumlah serverId ≠ jumlah url (format tak dikenal) → null
     * (pemanggil memakai passthrough utuh tanpa rewrite — tetap aman).
     */
    public static java.util.ArrayList<String[]> parseServerTable(String respText) {
        try {
            java.util.ArrayList<String> ids = new java.util.ArrayList<String>();
            java.util.regex.Matcher mi = java.util.regex.Pattern
                    .compile("\\\\?\"serverId\\\\?\"\\\\?:\\\\?\"([^\"\\\\]*)\\\\?\"")
                    .matcher(respText);
            while (mi.find()) {
                ids.add(mi.group(1));
            }
            java.util.ArrayList<String> urls = new java.util.ArrayList<String>();
            java.util.regex.Matcher mu = java.util.regex.Pattern
                    .compile("\\\\?\"url\\\\?\"\\\\?:\\\\?\"(https?://[^\"\\\\]+)\\\\?\"")
                    .matcher(respText);
            while (mu.find()) {
                urls.add(mu.group(1));
            }
            if (ids.isEmpty() || ids.size() != urls.size()) {
                return null;
            }
            java.util.ArrayList<String[]> out = new java.util.ArrayList<String[]>();
            for (int i = 0; i < ids.size(); i++) {
                out.add(new String[]{ids.get(i), urls.get(i)});
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 0.6.0 — tulis-ulang url SETIAP entri daftar server ke relay game lokal
     * (http://127.0.0.1:gamePort). Field lain (urlInner, chatInner, dst.)
     * dibiarkan apa adanya. Return null bila tidak ada url yang cocok →
     * pemanggil memakai respons ASLI utuh.
     */
    public static String rewriteServerListUrls(String respText) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern
                    .compile("(\\\\?\"url\\\\?\"\\\\?:\\\\?\")https?://[^\"\\\\]+(\\\\?\")");
            java.util.regex.Matcher m = p.matcher(respText);
            if (!m.find()) {
                return null;
            }
            String localUrl = "http://127.0.0.1:" + Config.gamePort();
            StringBuffer sb = new StringBuffer(respText.length() + 64);
            m.reset();
            while (m.find()) {
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                        m.group(1) + localUrl + m.group(2)));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 0.6.2 — tulis-ulang url daftar server DENGAN framing engine.io utuh.
     * Body polling = urutan frame "<panjang>:<paket>" — panjang prefix WAJIB
     * dihitung ulang setelah isi paket berubah.
     * Bukti 0.6.1 (log perangkat 2026-11-13 19:33:56, REKAM events.jsonl):
     * respons ASLI upstream = satu frame "618016:430[...]" — prefix 618016 =
     * panjang paket, selisih 0 (framing upstream sempurna); tulis-ulang 2111
     * url (https://sXXXX-bs.popoh5.com:NNNN → http://127.0.0.1:8080) memangkas
     * paket 618016 → 597094 (−20.922 char) TANPA memperbarui prefix → parser
     * polling klien (engine.io decodePayload di socket.io.min.js bawaan game:
     * "s = t.substr(c+1,i); a != s.length → return r(w,0,1)") MENOLAK seluruh
     * payload → ACK GetServerList tidak pernah sampai ke UI game → SERVERLIST
     * kosong. Versi original tidak menyentuh payload → daftar muncul (laporan
     * owner). Perbaikan: urai frame-per-frame (logika sama dgn parseFrames),
     * tulis-ulang di dalam paket, susun ulang dgn prefix BARU. Semantik
     * panjang = UTF-16 char (Java String.length() == substr/length parser JS).
     * Frame tanpa url diteruskan byte-identik. Return null bila framing tidak
     * valid atau tidak ada url berubah → pemanggil memakai respons ASLI.
     */
    public static String rewriteServerListFramed(String body) {
        if (body == null || body.length() == 0) {
            return null;
        }
        StringBuilder out = new StringBuilder(body.length() + 32);
        int i = 0;
        final int len = body.length();
        int changed = 0;
        while (i < len) {
            int c = body.indexOf(':', i);
            if (c < 0) {
                return null;
            }
            int plen;
            try {
                plen = Integer.parseInt(body.substring(i, c));
            } catch (NumberFormatException nfe) {
                return null;
            }
            if (plen < 0 || c + 1 + plen > len) {
                return null;
            }
            String pkt = body.substring(c + 1, c + 1 + plen);
            String rw = rewriteServerListUrls(pkt);
            if (rw != null) {
                pkt = rw;
                changed++;
            }
            out.append(pkt.length()).append(':').append(pkt);
            i = c + 1 + plen;
        }
        if (changed == 0) {
            return null;
        }
        return out.toString();
    }

    /** 0.6.2 — prefix panjang frame PERTAMA (utk field Framing di log). Null bila tidak terbaca. */
    public static String firstFramePrefix(String body) {
        try {
            if (body == null) {
                return null;
            }
            int c = body.indexOf(':');
            if (c <= 0) {
                return null;
            }
            return body.substring(0, c);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 🔌 klien menutup sesi (passthrough) — dedupe 60 dtk, gaya 0.5.0. */
    private void onlineDisconnect() {
        long now = System.currentTimeMillis();
        if (now - lastEndLogAt < 60000L) {
            return;
        }
        lastEndLogAt = now;
        HumanLog.ev("🔌", "Klien menutup sesi login (passthrough)",
                "Jalur    : ONLINE — passthrough ke server asli\n"
                        + "Sesi     : passthrough ke login.popoh5.com:610\n"
                        + "Lanjutan : klien io.connect ke url entri — dilayani relay game lokal dan "
                        + "diteruskan ke url ASLI entri (0.6.1)");
    }

    /**
     * Kirim satu permintaan HTTP(S) ke upstream login — GET (long-poll) atau
     * POST (flush paket). Jawaban dibaca UTUH lalu diteruskan (byte-per-byte).
     * 0.5.4: package-visible — dipakai juga relay socket game :8080 (GameServer).
     */
    static UpResp forwardLogin(String target, String body, boolean post, int timeoutMs) throws Exception {
        HttpURLConnection c = null;
        try {
            URL u = new URL(target);
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(timeoutMs);
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod(post ? "POST" : "GET");
            if (post) {
                byte[] b = (body == null) ? new byte[0] : body.getBytes("UTF-8");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
                c.setRequestProperty("Content-Length", String.valueOf(b.length));
                java.io.OutputStream os = c.getOutputStream();
                os.write(b);
                os.flush();
                os.close();
            }
            int status = c.getResponseCode();
            String ct = c.getContentType();
            java.io.InputStream in = (status >= 400) ? c.getErrorStream() : c.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (in != null && (n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            if (in != null) {
                in.close();
            }
            return new UpResp(status, ct, bos.toByteArray());
        } finally {
            try {
                if (c != null) {
                    c.disconnect();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** Ringkas query utk info Capture (t param bisa panjang). */
    private static String ringkasQuery(String qs) {
        return qs; // 0.6.0 — tanpa singkatan (owner: log = nyawa, utuh apa adanya)
    }

    // ---------------- util bersama (mode LOCAL + ONLINE) ----------------

    /**
     * Urai framing polling engine.io v3 "<panjang>:<paket>" berurutan → daftar
     * paket. Publik — diuji harness (0.5.1: dipakai juga passthrough ONLINE).
     */
    public static java.util.ArrayList<String> parseFrames(String body) {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        if (body == null) {
            return out;
        }
        int i = 0;
        final int len = body.length();
        while (i < len) {
            int c = body.indexOf(':', i);
            if (c < 0) {
                break;
            }
            int plen;
            try {
                plen = Integer.parseInt(body.substring(i, c));
            } catch (NumberFormatException nfe) {
                break;
            }
            if (plen < 0 || c + 1 + plen > len) {
                break;
            }
            out.add(body.substring(c + 1, c + 1 + plen));
            i = c + 1 + plen;
        }
        return out;
    }

    /** Hitung jumlah server dalam payload daftar (kunci "serverId") — passthrough.
     *  Dicari TANPA tanda kutip: di wire, data ACK = string JSON ter-escape
     *  (\"serverId\") — bentuk escape & polos sama-sama terhitung. */
    public static int countServerIds(String data) {
        if (data == null) {
            return 0;
        }
        int n = 0;
        int i = 0;
        while ((i = data.indexOf("serverId", i)) >= 0) {
            n++;
            i += 8;
        }
        return n;
    }

    // ---------------- parser JSON mini (cukup untuk pesan game) ----------------

    /** Ambil objek pesan dari array JSON event '["handler.process",{pesan}]' — args[1]. */
    static String firstArgJson(String jsonArray) {
        if (jsonArray == null) {
            return "{}";
        }
        int a = jsonArray.indexOf('[');
        int b = jsonArray.lastIndexOf(']');
        if (a < 0 || b <= a) {
            return "{}";
        }
        String inner = jsonArray.substring(a + 1, b);
        // cari '{' pertama DI LUAR string (arg[0] = nama event "handler.process")
        int start = -1;
        boolean inStr = false;
        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (inStr) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '"') {
                    inStr = false;
                }
            } else if (ch == '"') {
                inStr = true;
            } else if (ch == '{') {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return "{}";
        }
        // ekstrak objek berimbang dari start
        int depth = 0;
        boolean inStr2 = false;
        for (int i = start; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (inStr2) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '"') {
                    inStr2 = false;
                }
            } else if (ch == '"') {
                inStr2 = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return inner.substring(start, i + 1);
                }
            }
        }
        return "{}";
    }

    /** Nilai string level atas dari objek JSON — cukup untuk kunci pesan game. */
    static String strField(String obj, String key) {
        if (obj == null || key == null) {
            return null;
        }
        String needle = "\"" + key + "\"";
        int i = obj.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int c = obj.indexOf(':', i + needle.length());
        if (c < 0) {
            return null;
        }
        int j = c + 1;
        final int len = obj.length();
        while (j < len && obj.charAt(j) == ' ') {
            j++;
        }
        if (j >= len || obj.charAt(j) != '"') {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        j++;
        while (j < len) {
            char ch = obj.charAt(j);
            if (ch == '\\' && j + 1 < len) {
                char nx = obj.charAt(j + 1);
                if (nx == 'n') {
                    sb.append('\n');
                } else if (nx == 't') {
                    sb.append('\t');
                } else if (nx == 'u' && j + 5 < len) {
                    try {
                        sb.append((char) Integer.parseInt(obj.substring(j + 2, j + 6), 16));
                    } catch (Throwable ignored) {
                    }
                    j += 6;
                    continue;
                } else {
                    sb.append(nx);
                }
                j += 2;
                continue;
            }
            if (ch == '"') {
                break;
            }
            sb.append(ch);
            j++;
        }
        return sb.toString();
    }

    private static String quote(String s) {
        return "\"" + Json.esc(s) + "\"";
    }

    // ---------------- util ----------------

    /** Framing polling engine.io v3: "<panjang>:<paket>". */
    private static String frame(String pkt) {
        return pkt.length() + ":" + pkt;
    }

    private static String newSid() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    private static String parm(Map<String, String> parms, String key) {
        if (parms == null) {
            return null;
        }
        String v = parms.get(key);
        return (v == null || v.length() == 0) ? null : v;
    }

    private Response unknownSession() {
        return text(safeStatus(400), "{\"code\":1,\"message\":\"Session ID unknown\"}");
    }

    private void sweepExpired() {
        synchronized (SWEEPLOCK) {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, Sess>> it = sessions.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Sess> e = it.next();
                if (e.getValue().closed || now - e.getValue().lastSeen > SESSION_TTL_MS) {
                    boolean report = !e.getValue().closed && e.getValue().lastAction != null;
                    e.getValue().closed = true;
                    it.remove();
                    if (report) {
                        onSessionEnded(e.getValue(), false);
                    }
                }
            }
        }
    }

    private static Response text(Response.IStatus st, String msg) {
        Response r = newFixedLengthResponse(st, "text/plain; charset=utf-8", msg);
        r.addHeader("Cache-Control", "no-store");
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    /**
     * 0.5.8 — STATUS HTTP AMAN. Response.Status.lookup(int) mengembalikan NULL
     * untuk kode yang tidak ada di enum NanoHTTPD (502, 203, 302, 504, 511, …).
     * Dulu Response ber-status null → send() lempar java.lang.Error → thread
     * NanoHTTPD mati → Android membunuh SELURUH proses (force close persis saat
     * serverlist muncul — bukti crash.log 2026-11-12 13:46:54). Kini kode apa
     * pun tetap punya status sah (kode asli dipertahankan — jujur ke klien).
     */
    public static Response.IStatus safeStatus(final int code) {
        Response.IStatus s = Response.Status.lookup(code);
        if (s != null) {
            return s;
        }
        final int c = (code <= 0 || code > 599) ? 502 : code;
        return new Response.IStatus() {
            public int getRequestStatus() {
                return c;
            }

            public String getDescription() {
                return String.valueOf(c) + " Relay";
            }
        };
    }
}

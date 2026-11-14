package com.db.local.server;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * MESIN GAME LOKAL — jantung rebuild LOCAL yang benar-benar berdiri sendiri.
 * Bagian handcraft murni: kontrak protokol dibaca dari KODE KLIEN SENDIRI
 * (main.min_777039fc.js di mirror www — bagian APK proyek), nilai dibuat
 * server sendiri (GamePlayer + dragonball.db). NOL nilai dari capture online.
 *
 * TEGASAN OWNER (2026-11-14): "online itu cuma jejak agar kita gak tersesat..
 * bukan malah murni dipakai mentah-mentah". Capture dipakai HANYA untuk
 * mengetahui URUTAN alur (verify → enterGame → aksi lanjutan); bentuk jawaban
 * diambil dari kategori kontrak klien berikut:
 *
 *   1) ACK (jawaban event dgn ack id — klien: "43<id>[{...}]"):
 *        {ret:0, compress:false, data:"<JSON-string>",
 *         serverTime:<ms>, server0Time:<offset zona waktu ms>}
 *      ret==0 sukses; data = STRING JSON (klien JSON.parse setelah
 *      decompress bila compress:true — kita kirim tanpa kompresi, jujur);
 *      serverTime/server0Time = sinkron waktu (kode klien ServerTime).
 *   2) NOTIFY (push — klien: ret=="SUCCESS" → dispatch type+action):
 *        42["Notify",{"ret":"SUCCESS","compress":false,"data":"<JSON>"},null]
 *   3) VERIFY (tangan-tangan pertama setelah connect — klien TEA):
 *        server kirim 42["verify","<token>"]; klien balik 42<id>["verify",
 *        Base64(XXTEA(token,"verification"))] dgn ack; server cocokkan →
 *        ack {"ret":0} (implementasi: Xxtea.java, vektor silang dari kode
 *        klien sendiri).
 *
 * ALUR LOKAL SETELAH KLIK PLAY (peta urutan, semua dijawab lokal):
 *   handshake engine.io v3 (polling, tanpa upgrade websocket — upgrades:[]
 *   persis jalur login lokal yang terbukti) → socket.io CONNECT → verify
 *   (XXTEA) → enterGame → model pemain dari dragonball.db → Notify
 *   battleMedalRefresh → getBulletinBrief → queryMyRecord →
 *   friendServerAction(queryFriends/queryBlackList) → registChat →
 *   heroImage getAll → hero getAttrs → userMsg getMsgList → aksi apa pun
 *   yang belum dihandcraft dijawab ret 0 kosong (game tidak menggantung;
 *   isi UTUH dicatat log sebagai bahan handcraft berikutnya — log = nyawa).
 *
 * LOG: setiap event protokol terekam UTUH (gaya HttpCanary, tanpa memangkas)
 * — masuk maupun keluar. ping/keepalive = TEKNIS. registChat dide-dupe 10
 * mnt (klien mengulang tiap 3 detik).
 */
public final class GameEngine {

    private static final long SESSION_TTL_MS = 90000L;
    private static final long POLL_WAIT_MS = 20000L;
    private static final int MAX_SESSIONS = 64;

    /** Handshake engine.io v3 — sama jalurnya dengan login lokal (upgrades:[]). */
    private static final String HANDSHAKE_JSON =
            "{\"sid\":\"%SID%\",\"upgrades\":[],\"pingInterval\":25000,\"pingTimeout\":5000}";

    private static final ConcurrentHashMap<String, Sess> SESSIONS =
            new ConcurrentHashMap<String, Sess>();
    private static final Object SWEEPLOCK = new Object();

    /** Dedupe log registChat (klien retry 3 detik) & baris koneksi. */
    private static volatile long lastRegistChatLogAt = 0L;
    private static volatile long lastConnectLogAt = 0L;

    /** Sesi engine.io satu klien di port game. */
    private static final class Sess {
        final String sid;
        final LinkedBlockingQueue<String> out = new LinkedBlockingQueue<String>();
        final long createdAt = System.currentTimeMillis();
        volatile long lastSeen = System.currentTimeMillis();
        volatile boolean closed = false;
        volatile boolean verified = false;
        volatile String verifyToken = null; // token sesi game (event verify)
        volatile String gameUserId = null; // _id pemain (model) setelah enterGame
        volatile String sdkUserId = null;  // akun SDK (msg.userId enterGame)

        Sess(String sid) {
            this.sid = sid;
        }
    }

    private GameEngine() {
    }

    // ---------------- pintu dari GameServer ----------------

    /**
     * Layani /socket.io/ port game di mode LOCAL TANPA isian upstream —
     * mesin jawaban lokal penuh (Play LOKAL hidup, NOL kontak keluar).
     */
    public static Response serve(IHTTPSession session, Method m) {
        String sid = parm(session.getParms(), "sid");
        if (session.getParms() != null
                && "websocket".equals(session.getParms().get("transport"))) {
            return plain(LoginServer.safeStatus(400),
                    "websocket tidak dibuka — engine.io tetap polling");
        }
        if (Method.GET == m) {
            return (sid == null || sid.length() == 0) ? handshake() : poll(sid);
        }
        if (Method.POST == m) {
            return receive(session, sid);
        }
        return plain(LoginServer.safeStatus(400), "Metode " + m + " tidak didukung");
    }

    // ---------------- engine.io v3 polling ----------------

    /** Handshake: buka sesi + socket.io CONNECT + antre event verify. */
    private static Response handshake() {
        sweepExpired();
        String sid = newSid();
        Sess s = new Sess(sid);
        if (SESSIONS.size() >= MAX_SESSIONS) {
            SESSIONS.clear(); // pengaman — klien menyambung ulang sendiri
        }
        SESSIONS.put(sid, s);
        String token = UUID.randomUUID().toString();
        s.verifyToken = token;
        s.out.offer("42[\"verify\",\"" + Json.esc(token) + "\"]");
        String open = HANDSHAKE_JSON.replace("%SID%", sid);
        String body = frame("0" + open) + frame("40");
        try {
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastConnectLogAt >= 600000L) {
                lastConnectLogAt = nowMs;
                HumanLog.ev("🎮", "Klik Play — klien masuk MESIN GAME LOKAL :"
                                + Config.mainPort() + " (tanpa relay, tanpa kontak keluar)",
                        "Jalur    : LOCAL — protokol game dijawab server ini sendiri (handcraft)\n"
                                + "Sesi     : " + sid + " (engine.io v3 · polling)\n"
                                + "Verify   : token sesi game " + token + " — klien membuktikan diri\n"
                                + "           dgn XXTEA kunci \"verification\" (kontrak kode klien)\n"
                                + "Lanjutan : enterGame → model pemain dari dragonball.db");
            }
        } catch (Throwable ignored) {
        }
        return plain(Response.Status.OK, body);
    }

    /** Long-poll: tunggu paket keluar sampai 20 dtk; boleh jawab kosong. */
    private static Response poll(String sid) {
        Sess s = SESSIONS.get(sid);
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
        Response r = plain(Response.Status.OK, body.toString());
        r.setKeepAlive(false);
        return r;
    }

    /** Terima flush paket klien (POST) — proses lalu antrekan jawaban. */
    private static Response receive(IHTTPSession session, String sid) {
        Sess s = (sid == null) ? null : SESSIONS.get(sid);
        if (s == null || s.closed) {
            return unknownSession();
        }
        s.lastSeen = System.currentTimeMillis();
        String body;
        try {
            body = LoginServer.readBody(session);
        } catch (Throwable t) {
            return plain(Response.Status.BAD_REQUEST, "bad request");
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
            handlePacket(s, body.substring(c + 1, c + 1 + plen));
            i = c + 1 + plen;
        }
        return plain(Response.Status.OK, "ok");
    }

    // ---------------- socket.io v2 paket ----------------

    private static void handlePacket(Sess s, String pkt) {
        if (pkt == null || pkt.length() == 0) {
            return;
        }
        char t = pkt.charAt(0);
        switch (t) {
            case '2': // PING engine.io → PONG
                s.out.offer("3");
                return;
            case '3': // PONG — tidak diharapkan dari klien v3
                return;
            case '4': // engine.io MESSAGE → payload socket.io
                if (pkt.length() >= 2) {
                    handleSio(s, pkt.substring(1));
                }
                return;
            default: // 0/1/5/6 dari klien — abaikan
                return;
        }
    }

    private static void handleSio(Sess s, String p) {
        if (p.length() == 0) {
            return;
        }
        char type = p.charAt(0);
        if (type == '0') {
            return; // CONNECT namespace "/" — sudah dibuka di handshake
        }
        if (type == '1') {
            s.closed = true;
            SESSIONS.remove(s.sid);
            return;
        }
        if (type != '2') {
            return;
        }
        // EVENT: 42<ackid>[<nama>, <arg>, ...]
        int i = 1;
        while (i < p.length() && p.charAt(i) >= '0' && p.charAt(i) <= '9') {
            i++;
        }
        String ackId = p.substring(1, i);
        String json = p.substring(i);
        String name = firstEventName(json);
        if ("verify".equals(name)) {
            onVerify(s, ackId, secondStringArg(json));
        } else if ("handler.process".equals(name)) {
            onHandlerProcess(s, ackId, LoginServer.firstArgJson(json));
        } else {
            // event lain — jawab ack kosong agar klien tidak menggantung
            if (ackId.length() > 0) {
                s.out.offer("43" + ackId + "[{\"ret\":0}]");
            }
            try {
                HumanLog.ev("ℹ️", "Event game tak dikenal: " + name
                        + " — dijawab ret 0 kosong", null);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Nama event pertama dari array JSON ("42[\"verify\",...]") — util kecil. */
    private static String firstEventName(String jsonArray) {
        int a = jsonArray.indexOf('"');
        if (a < 0) {
            return "";
        }
        StringBuilder b = new StringBuilder(32);
        for (int i = a + 1; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);
            if (c == '\\' && i + 1 < jsonArray.length()) {
                b.append(jsonArray.charAt(++i));
                continue;
            }
            if (c == '"') {
                break;
            }
            b.append(c);
        }
        return b.toString();
    }

    /**
     * Argumen STRING kedua dari array JSON ("[\"verify\",\"<b64>\"]") — dipakai
     * verify (payload bukan objek — firstArgJson tidak cocok). Tanpa dependensi.
     */
    private static String secondStringArg(String jsonArray) {
        int a = jsonArray.indexOf('[');
        int b = jsonArray.lastIndexOf(']');
        if (a < 0 || b <= a) {
            return "";
        }
        String inner = jsonArray.substring(a + 1, b);
        int found = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\\' && i + 1 < inner.length()) {
                i++;
                continue;
            }
            if (c != '"') {
                continue;
            }
            found++;
            if (found == 2) {
                StringBuilder sb = new StringBuilder(inner.length());
                i++;
                while (i < inner.length()) {
                    char ch = inner.charAt(i);
                    if (ch == '\\' && i + 1 < inner.length()) {
                        char nx = inner.charAt(i + 1);
                        sb.append(nx == 'n' ? '\n' : nx == 't' ? '\t' : nx);
                        i += 2;
                        continue;
                    }
                    if (ch == '"') {
                        return sb.toString();
                    }
                    sb.append(ch);
                    i++;
                }
                return sb.toString();
            }
            // lewati isi string pertama (nama event)
            i++;
            while (i < inner.length()) {
                char ch = inner.charAt(i);
                if (ch == '\\') {
                    i++;
                } else if (ch == '"') {
                    break;
                }
                i++;
            }
        }
        return "";
    }

    private static String quote(String s) {
        return "\"" + Json.esc(s) + "\"";
    }

    // ---------------- verify (kontrak TEA klien) ----------------

    private static void onVerify(Sess s, String ackId, String encrypted) {
        String plain = "";
        try {
            plain = Xxtea.decrypt(encrypted, "verification");
        } catch (Throwable t) {
            plain = "";
        }
        boolean ok = s.verifyToken != null && s.verifyToken.equals(plain);
        s.verified = ok;
        try {
            HumanLog.evIsi("🎮", ok
                            ? "Verify diterima — bukti XXTEA klien cocok (protokol lokal hidup)"
                            : "Verify GAGAL — dekripsi tidak cocok (klien asli? tool lain?)",
                    "Sesi     : " + s.sid + "\n"
                            + "Token    : " + s.verifyToken + "\n"
                            + "Dekripsi : " + plain,
                    "ISI · payload verify (utuh)",
                    (encrypted == null ? "" : encrypted));
        } catch (Throwable ignored) {
        }
        if (ackId.length() > 0) {
            s.out.offer("43" + ackId + "[{"
                    + (ok ? "\"ret\":0" : "\"ret\":1,\"data\":\"verify mismatch\"")
                    + "}]");
        }
    }

    // ---------------- handler.process — router aksi game ----------------

    private static void onHandlerProcess(Sess s, String ackId, String msg) {
        String type = LoginServer.strField(msg, "type");
        String action = LoginServer.strField(msg, "action");
        String relay = LoginServer.strField(msg, "relayAction");
        String userId = LoginServer.strField(msg, "userId");
        String label = (action == null ? "handler.process" : action)
                + (relay != null ? "(" + relay + ")" : "")
                + "(" + (type == null ? "?" : type) + ")";

        String data;
        boolean penting;
        if ("enterGame".equals(action)) {
            data = enterGame(s, msg, userId);
            penting = true;
        } else if ("getBulletinBrief".equals(action)) {
            data = echoOpen(userId, type, action, relay)
                    + ",\"_brief\":{}}";
            penting = false;
        } else if ("queryMyRecord".equals(action)) {
            data = echoOpen(userId, type, action, relay) + ",\"_records\":{}}";
            penting = false;
        } else if ("friendServerAction".equals(action)
                && "queryFriends".equals(relay)) {
            data = echoOpen(userId, type, action, relay)
                    + ",\"_friends\":{},\"_receiveHearts\":{},\"_giveHearts\":{},\"_getHearts\":{}}";
            penting = false;
        } else if ("friendServerAction".equals(action)
                && "queryBlackList".equals(relay)) {
            data = echoOpen(userId, type, action, relay) + ",\"users\":{}}";
            penting = false;
        } else if ("registChat".equals(action)) {
            // chat lokal belum dihandcraft — jujur: _success false, klien lanjut
            data = echoOpen(userId, type, action, relay) + ",\"_success\":false}";
            penting = false;
        } else if ("getAll".equals(action)) {
            data = echoOpen(userId, type, action, relay) + ",\"_heros\":{}}";
            penting = false;
        } else if ("getAttrs".equals(action)) {
            data = echoOpen(userId, type, action, relay)
                    + ",\"_attrs\":{},\"_baseAttrs\":{}}";
            penting = false;
        } else if ("getMsgList".equals(action)) {
            data = echoOpen(userId, type, action, relay) + ",\"_brief\":{}}";
            penting = false;
        } else {
            data = echoOpen(userId, type, action, relay) + "}";
            penting = false;
            try {
                HumanLog.ev("ℹ️", "Aksi game belum dihandcraft: " + label
                        + " — dijawab ret 0 kosong (game tidak menggantung); "
                        + "payload UTUH di bawah = bahan handcraft berikutnya",
                        "Payload  : " + msg);
            } catch (Throwable ignored) {
            }
        }

        long now = System.currentTimeMillis();
        String ack = "{\"ret\":0,\"compress\":false,\"data\":"
                + quote(data)
                + ",\"serverTime\":" + now
                + ",\"server0Time\":" + java.util.TimeZone.getDefault().getRawOffset()
                + "}";
        if (ackId.length() > 0) {
            s.out.offer("43" + ackId + "[" + ack + "]");
        }
        // LOG — semua jawaban protokol terekam utuh (log = nyawa)
        try {
            if ("registChat".equals(action)) {
                long nowMs = System.currentTimeMillis();
                if (nowMs - lastRegistChatLogAt >= 600000L) {
                    lastRegistChatLogAt = nowMs;
                    HumanLog.evIsi("🎮", "→ registChat — chat lokal belum dihandcraft "
                                    + "(_success:false, klien lanjut) · jawaban ret 0",
                            "Sesi     : " + s.sid + "\nPayload  : " + msg + "\nJawaban  : " + data,
                            "ISI · payload + jawaban (utuh)", msg + "\n--- jawaban ---\n" + ack);
                }
            } else if (penting) {
                HumanLog.evIsi("🎮", "→ " + label + " — dijawab MESIN GAME LOKAL · "
                                + HumanLog.fmtBytes(ack.length()),
                        "Sesi     : " + s.sid + "\nPayload  : " + msg,
                        "ISI · payload + jawaban (utuh)", msg + "\n--- jawaban ---\n" + ack);
            } else {
                HumanLog.evTechIsi("🎮", "→ " + label + " · jawaban ret 0 · "
                                + HumanLog.fmtBytes(ack.length()),
                        null, "ISI · payload + jawaban (utuh)", msg + "\n--- jawaban ---\n" + ack);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Eko struktur {type,action?,relayAction?,userId,version — TANPA tutup
     * kurung (penelepon menambahkan field kontrak klien lalu menutup sendiri).
     */
    private static String echoOpen(String userId, String type, String action, String relay) {
        StringBuilder b = new StringBuilder(128);
        b.append('{');
        if (type != null) {
            b.append("\"type\":\"").append(Json.esc(type)).append("\",");
        }
        if (action != null) {
            b.append("\"action\":\"").append(Json.esc(action)).append("\",");
        }
        if (relay != null) {
            b.append("\"relayAction\":\"").append(Json.esc(relay)).append("\",");
        }
        b.append("\"userId\":\"").append(Json.esc(userId == null ? "" : userId)).append("\",");
        b.append("\"version\":\"1.0\"");
        return b.toString();
    }

    /**
     * enterGame — mulut dunia lokal: validasi akun SDK dikenal, siapkan model
     * pemain (GamePlayer/dragonball.db), jawab model utuh + sinkron waktu,
     * lalu antrekan Notify battleMedalRefresh (kontrak klien, nilai sendiri).
     */
    private static String enterGame(Sess s, String msg, String userId) {
        String loginToken = LoginServer.strField(msg, "loginToken");
        String gameVersion = LoginServer.strField(msg, "gameVersion");
        String serverId = LoginServer.strField(msg, "serverId");
        boolean akunDikenal;
        try {
            akunDikenal = LoginData.accountPassword(userId) != null
                    || userId != null && userId.equals(SdkData.lastKnownUid());
        } catch (Throwable t) {
            akunDikenal = false;
        }
        if (!akunDikenal && userId != null && userId.length() > 0) {
            try {
                HumanLog.ev("⚠️", "enterGame: akun " + userId
                        + " tidak dikenal dragonball.db — tetap dilayani (dunia lokal baru)",
                        "LoginToken: " + loginToken);
            } catch (Throwable ignored) {
            }
        }
        String model;
        try {
            model = GamePlayer.ensure(userId);
            model = GamePlayer.withFreshLoginTime(model, System.currentTimeMillis());
            GamePlayer.save(userId, model);
        } catch (Throwable t) {
            try {
                HumanLog.ev("⚠️", "enterGame: gagal menyiapkan data pemain — "
                        + t + " (dunia lokal memori saja)",
                        "Lanjutan : model minimal tetap dijawab agar game tidak menggantung");
            } catch (Throwable ignored) {
            }
            model = GamePlayer.ensure("memori");
        }
        s.sdkUserId = userId;
        s.gameUserId = GamePlayer.gameId(model);
        try {
            long nowMs = System.currentTimeMillis();
            boolean dup = userId != null && userId.equals(lastEnterUid)
                    && (nowMs - lastEnterAt) < 600000L;
            lastEnterUid = userId;
            lastEnterAt = nowMs;
            if (!dup) {
                HumanLog.ev("✅", "enterGame diterima — DUNIA LOKAL membuka sesi pemain "
                                + "(bukan relay — server ini yang berperan sebagai server game)",
                        "Akun     : " + userId + " (SDK lokal)\n"
                                + "Sesi game: " + s.gameUserId + " (UUID server lokal, tersimpan dragonball.db)\n"
                                + "ServerId : " + serverId + " · gameVersion " + gameVersion
                                + " · loginToken " + loginToken + "\n"
                                + "Model    : " + HumanLog.fmtBytes(model.length())
                                + " — nilai milik pemain lokal (bukan salinan online)");
            }
        } catch (Throwable ignored) {
        }
        pushBattleMedal(s, s.gameUserId);
        return model;
    }

    private static volatile String lastEnterUid = null;
    private static volatile long lastEnterAt = 0L;

    /**
     * Notify battleMedalRefresh — push kontrak klien (dispatch type+action,
     * model battleMedal), NILAI sendiri: nol/UUID lokal. Struktur model =
     * BattleMedalModel klien (_id,_battleMedalId,_cycle,_nextRefreshTime,
     * _level,_curExp,_openSuper,_task,_levelReward,_shopBuyTimes,
     * _buyLevelCount).
     */
    private static void pushBattleMedal(Sess s, String gameUuid) {
        if (gameUuid == null || gameUuid.length() == 0) {
            return;
        }
        long tomorrow = nextMidnight();
        String data = "{\"model\":{\"_id\":\"" + Json.esc(gameUuid)
                + "\",\"_battleMedalId\":\"" + Json.esc(UUID.randomUUID().toString())
                + "\",\"_cycle\":1,\"_nextRefreshTime\":" + tomorrow
                + ",\"_level\":0,\"_curExp\":0,\"_openSuper\":false"
                + ",\"_task\":{},\"_levelReward\":{},\"_shopBuyTimes\":{}"
                + ",\"_buyLevelCount\":0},\"type\":\"Notify\",\"action\":\"battleMedalRefresh\"}";
        s.out.offer("42[\"Notify\",{\"ret\":\"SUCCESS\",\"compress\":false,\"data\":"
                + quote(data) + "},null]");
        try {
            HumanLog.evTechIsi("🎮", "Notify battleMedalRefresh — push lokal (kontrak klien, nilai nol)",
                    null, "ISI · push (utuh)", data);
        } catch (Throwable ignored) {
        }
    }

    /** Midnight lokal berikutnya (nilai server sendiri utk _nextRefreshTime). */
    private static long nextMidnight() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() + 86400000L;
    }

    // ---------------- util ----------------

    private static String parm(Map<String, String> parms, String key) {
        if (parms == null) {
            return null;
        }
        String v = parms.get(key);
        return (v == null || v.length() == 0) ? null : v;
    }

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

    private static Response unknownSession() {
        return plain(LoginServer.safeStatus(400),
                "{\"code\":1,\"message\":\"Session ID unknown\"}");
    }

    private static Response plain(Response.IStatus st, String msg) {
        Response r = NanoHTTPD.newFixedLengthResponse(st, "text/plain; charset=utf-8", msg);
        r.addHeader("Cache-Control", "no-store");
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    private static void sweepExpired() {
        synchronized (SWEEPLOCK) {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, Sess>> it = SESSIONS.entrySet().iterator();
            while (it.hasNext()) {
                Sess s = it.next().getValue();
                if (s.closed || now - s.lastSeen > SESSION_TTL_MS) {
                    s.closed = true;
                    it.remove();
                }
            }
        }
    }
}

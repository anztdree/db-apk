package com.db.local.server;

import java.util.LinkedHashMap;

/**
 * Bagian 67 — ACK-GATE LOGIN-SERVER (oke owner "implementasi dengan ini").
 *
 * SATU titik logika, dua pintu masuk (SocketIoHandler POST-polling + WS frame,
 * listener login 5050/9528 — gameMode false). 5051 (game) TETAP transport-only.
 *
 * Sumber jawaban = file <action>.json di login-server/ (1 event atlas = 1 file,
 * struktur Bagian 66 oke owner):
 *
 *   ATURAN ISI/DIAM PER EVENT:
 *     file BERISI    → ACK dgn isi file (baca ulang tiap event — edit live).
 *     file kosong {} → DIAM / NO ACK (atlas §11 baris 9 — client diam tanpa
 *                      crash; nol balasan frame).
 *     file hilang    → seed default (aturan folder 4).
 *     file rusak     → reseed + WARN (aturan folder 4).
 *
 *   EVENT KE-6 SaveUserEnterInfo = TANPA FILE — SELALU dijawab data "{}"
 *   (konstanta kode): isi ACK TIDAK dibaca client (atlas §11 baris 11);
 *   callback → client destroy() login socket (satu-satunya destroy, §4.6).
 *
 *   event lain (bukan 6 event atlas §4) = NO ACK + log verbatim (disiplin
 *   v19 — atlas §11 baris 13: 6 event TOTAL, tidak ada ke-7).
 *
 * ENVELOPE = ikut PARSER client (atlas §2/§11 baris 1):
 *   `43<ackId>[{"ret":0,"data":"<JSON string>"}]` — TANPA field "compress"
 *   (parser: 0===e.ret → JSON.parse(data); compress opsional — polos lebih
 *   sederhana). ackId = ECHO dari prefix packet client `42<ackId>`.
 *
 * ATURAN KUNCI-KONSUMEN (Bagian 65, dari atlas): kunci data = HANYA kunci
 * yang DIBACA parser event itu — errorCode TIDAK dikirim di GetServerList
 * (§4.1/§12.5), loginGame (§4.2), SaveHistory (§4.3), SaveUserEnterInfo
 * (§11.11); errorCode WAJIB HANYA di SaveLanguage (parser MEMBACANYA,
 * `0===t.errorCode` — atlas §4.5). historyMode = kunci server KITA, TIDAK
 * dikirim (parser mengabaikan kunci asing — tapi konsisten: tidak ada alasan
 * mengirim kunci tanpa konsumen).
 *
 * LOG = GLOBAL 1 (oke owner "tanpa log !! log global jdi 1"): setiap ACK /
 * NO-ACK dicatat di entri HTTP/WS yang sama (extra / respPreview — URL penuh,
 * frame penuh, NOL pangkas — disiplin v17) → NDJSON GLOBAL <serverRoot>/logs/.
 * Nol file log baru.
 */
final class LoginAck {

    private LoginAck() {}

    private static volatile boolean histWarned = false;

    /* ================= pintu utama ================= */

    /**
     * Parse SATU packet `42<ackId>["handler.process",{payload}]` dan bangun
     * jawaban per §4.3. Return frame ACK `43<ackId>[{…}]` siap kirim lewat
     * Session.deliver — atau NULL bila event DIAM (NO ACK).
     * TIDAK PERNAH throw — semua kesalahan = NO ACK + penanda di e.extra.
     */
    static String build(String pkt, LoggerCore.Entry e) {
        try {
            Parsed p = parse(pkt);
            if (p == null) {
                note(e, "EVENT (no ack): " + pkt
                        + " | login-server: bukan ack-request handler.process"
                        + " (6 event)");
                return null;
            }
            // event 6 — SaveUserEnterInfo: TANPA FILE, SELALU dijawab {}
            if ("SaveUserEnterInfo".equals(p.action)) {
                String frame = frameOf(p.ackId, "{}");
                note(e, "ACK 43" + p.ackId + " — SaveUserEnterInfo (konstanta"
                        + " kode — isi tak dibaca client;"
                        + " callback -> destroy login socket) | frame: " + frame);
                return frame;
            }
            if (!LoginServerConfig.isEventFile(p.action)) {
                note(e, "EVENT (no ack): " + pkt
                        + " | login-server: action di luar inventaris"
                        + " (6 event total — tidak ada ke-7)");
                return null;
            }
            String raw = LoginServerConfig.eventRaw(p.action);
            if (raw == null || raw.isEmpty()) {
                note(e, "EVENT (no ack): " + pkt + " | login-server: "
                        + p.action + ".json kosong {} = DIAM");
                return null;
            }
            String data = buildData(p.action, raw, p.map);
            if (data == null) {
                note(e, "EVENT (no ack): " + pkt
                        + " | login-server: isi " + p.action
                        + ".json tidak bisa dibangun (lihat WARN)");
                return null;
            }
            String frame = frameOf(p.ackId, data);
            note(e, "ACK 43" + p.ackId + " — " + p.action
                    + " (file " + p.action + ".json dibaca ulang) | frame: "
                    + frame);
            return frame;
        } catch (Throwable t) {
            note(e, "EVENT (no ack): " + pkt + " | login-server error: " + t);
            return null;
        }
    }

    /* ================= bangun data per event (§4.3) ================= */

    private static String buildData(String action, String raw,
                                    LinkedHashMap<String, String> req) {
        LinkedHashMap<String, String> file = LoginServerConfig.mapTop(raw);
        if (file == null) { warn("mapTop null utk " + action); return null; }

        if (LoginServerConfig.A_GETSERVERLIST.equals(action)) {
            // kunci yang DIBACA parser: serverList[] (§11.3), history[] (§11.4),
            // offlineReason top (§6), language top (§2) — errorCode TIDAK
            // dikirim (§4.1/§12.5); historyMode = kunci server, TIDAK dikirim.
            String serverList = LoginServerConfig.raw(file, "serverList");
            if (serverList == null) {
                warn("GetServerList.json tanpa serverList[] — kirim []");
                serverList = "[]";
            }
            String history;
            if ("persist".equals(LoginServerConfig.historyMode())) {
                String key = LoginServerConfig.str(req, "userId");
                if (key.isEmpty()) key = AccountStore.curUid == null ? "" : AccountStore.curUid;
                if (key.isEmpty()) key = "guest";
                history = historyArrayJson(key);
            } else {
                history = LoginServerConfig.raw(file, "history");
                if (history == null) {
                    warn("GetServerList.json tanpa history[] — kirim []");
                    history = "[]";
                }
            }
            String off  = LoginServerConfig.str(file, "offlineReason");
            String lang = LoginServerConfig.str(file, "language");
            StringBuilder sb = new StringBuilder();
            sb.append("{\"serverList\":").append(serverList);
            sb.append(",\"history\":").append(history);
            if (!off.isEmpty())
                sb.append(",\"offlineReason\":\"").append(JsonUtil.esc(off)).append('"');
            if (!lang.isEmpty())
                sb.append(",\"language\":\"").append(JsonUtil.esc(lang)).append('"');
            sb.append('}');
            return sb.toString();
        }

        if (LoginServerConfig.A_LOGINGAME.equals(action)) {
            // parser cuma baca userId + channelCode (atlas §4.2) — sisanya
            // (loginToken/nickName/isCreated/errorCode) TIDAK dibaca → buang.
            String jawab = LoginServerConfig.str(file, "jawab");
            if ("tolak103".equals(jawab)) {
                // mirror kebijakan 103 = identitas KOSONG (K2 dibalik owner
                // via edit file; errorCode 103 tidak dikirim — parser tidak
                // membacanya, atlas §4.2)
                return "{\"userId\":\"\",\"channelCode\":\"\"}";
            }
            // default "sukses": echo dari request (jalur origin — atlas §4.2)
            String uid = LoginServerConfig.str(req, "userId");
            String ch  = LoginServerConfig.str(req, "channelCode");
            if (ch.isEmpty()) ch = LoginServerConfig.str(req, "fromChannel");
            return "{\"userId\":\"" + JsonUtil.esc(uid)
                    + "\",\"channelCode\":\"" + JsonUtil.esc(ch) + "\"}";
        }

        if (LoginServerConfig.A_SAVEHISTORY.equals(action)) {
            // parser baca loginToken (PINTU INJEKSI §11.7) + todayLoginCount
            // (§11.8 analytics); onErr=onOk → ACK gagal pun game mulai (§11.10).
            String cnt = LoginServerConfig.str(file, "todayLoginCount").trim();
            if (!isNumeric(cnt)) {
                warn("SaveHistory.json todayLoginCount bukan angka — pakai 1");
                cnt = "1";
            }
            String token = LoginServerConfig.str(file, "loginToken");
            StringBuilder sb = new StringBuilder();
            sb.append("{\"todayLoginCount\":").append(cnt);
            if (!token.isEmpty())
                sb.append(",\"loginToken\":\"").append(JsonUtil.esc(token)).append('"');
            sb.append('}');
            // K3 persist → tulis (accountToken→serverId) ke data/history.sqlite
            if ("persist".equals(LoginServerConfig.historyMode())) {
                String key = LoginServerConfig.str(req, "accountToken");
                if (key.isEmpty()) key = AccountStore.curUid == null ? "" : AccountStore.curUid;
                if (key.isEmpty()) key = "guest";
                try {
                    LoginHistoryDb.put(key, LoginServerConfig.str(req, "serverId"));
                } catch (Throwable t) {
                    warnHist(t);
                }
            }
            return sb.toString();
        }

        if (LoginServerConfig.A_LOGINANNOUNCE.equals(action)) {
            // ACK data = isi file apa adanya ({kunci: entri} — kontrak
            // getNotice atlas §4.4; text[lang]||"" tanpa fallback — §11.5).
            return raw;
        }

        if (LoginServerConfig.A_SAVELANGUAGE.equals(action)) {
            // parser MEMBACA errorCode (0===t.errorCode — atlas §4.5); kedua
            // cabang tetap ganti bahasa; pesan tak pernah tampil (console saja).
            java.util.ArrayList<String> wl =
                    LoginServerConfig.strArray(LoginServerConfig.raw(file, "whitelist"));
            String lang = LoginServerConfig.str(req, "language");
            if (wl.isEmpty() || wl.contains(lang)) {
                return "{\"errorCode\":0}";
            }
            return "{\"errorCode\":111,\"errorMessage\":\""
                    + JsonUtil.esc("language not supported: " + lang) + "\"}";
        }

        return null;
    }

    /* ================= envelope + frame ================= */

    /** `43<ackId>[{"ret":0,"data":"<JSON string>"}]` — data = string JSON
     *  (di-escape; newline dsb aman), TANPA "compress" (atlas §2/§11.1). */
    static String frameOf(String ackId, String dataJson) {
        return "43" + ackId + "[{\"ret\":0,\"data\":\""
                + JsonUtil.esc(dataJson) + "\"}]";
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                if (i == 0 && c == '-') continue;
                return false;
            }
        }
        return true;
    }

    private static String historyArrayJson(String key) {
        java.util.ArrayList<String> ids;
        try {
            ids = LoginHistoryDb.list(key);
        } catch (Throwable t) {
            warnHist(t);
            ids = new java.util.ArrayList<String>();
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(JsonUtil.esc(ids.get(i))).append('"');
        }
        return sb.append(']').toString();
    }

    private static void warnHist(Throwable t) {
        if (histWarned) return;
        histWarned = true;
        warn("LOGIN-HISTORY tidak tersedia — persist no-op (" + t + ")");
    }

    private static void warn(String msg) { Db.log("LOGIN-SERVER WARN: " + msg); }

    /* ================= parse packet 42 ================= */

    private static final class Parsed {
        String ackId;
        String action;
        LinkedHashMap<String, String> map;
    }

    /** `42<ackId>["handler.process",{...}]` → {ackId, action, payload-map};
     *  null bila bentuk tak dikenal / handler lain / payload rusak. */
    static Parsed parse(String pkt) {
        if (pkt == null || !pkt.startsWith("42")) return null;
        int i = 2, n = pkt.length();
        while (i < n && Character.isDigit(pkt.charAt(i))) i++;
        if (i == 2) return null;                 // tanpa ackId → tak bisa di-ACK
        String ackId = pkt.substring(2, i);
        while (i < n && pkt.charAt(i) == ' ') i++;
        if (i >= n || pkt.charAt(i) != '[') return null;
        i++;
        while (i < n && pkt.charAt(i) == ' ') i++;
        if (i >= n || pkt.charAt(i) != '"') return null;
        StringBuilder h = new StringBuilder();
        i++;
        boolean closed = false;
        while (i < n) {
            char c = pkt.charAt(i);
            if (c == '\\' && i + 1 < n) { i = unescAt(pkt, i, h); continue; }
            if (c == '"') { i++; closed = true; break; }
            h.append(c); i++;
        }
        if (!closed) return null;
        if (!"handler.process".equals(h.toString())) return null;
        while (i < n && (pkt.charAt(i) == ' ' || pkt.charAt(i) == ',')) i++;
        if (i >= n || pkt.charAt(i) != '{') return null;
        int close = rawBlock(pkt, i);
        if (close < 0) return null;
        String payload = pkt.substring(i, close + 1);
        LinkedHashMap<String, String> map = LoginServerConfig.mapTop(payload);
        if (map == null) return null;
        String action = map.get("action");
        if (action == null || action.isEmpty()) return null;
        Parsed p = new Parsed();
        p.ackId = ackId;
        p.action = action;
        p.map = map;
        return p;
    }

    private static char unesc(char c) {
        switch (c) {
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            default: return c;
        }
    }

    private static int unescAt(String s, int i, StringBuilder b) {
        char n1 = s.charAt(i + 1);
        if (n1 == 'u' && i + 5 < s.length()) {
            try {
                b.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                return i + 6;
            } catch (Throwable t) { /* jatuh */ }
        }
        b.append(unesc(n1));
        return i + 2;
    }

    private static int rawBlock(String s, int open) {
        char openC = s.charAt(open);
        char closeC = openC == '{' ? '}' : ']';
        int depth = 0;
        boolean inStr = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') { inStr = true; continue; }
            if (c == openC) depth++;
            else if (c == closeC) { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private static void note(LoggerCore.Entry e, String msg) {
        if (e == null) return;
        e.extra = e.extra == null ? msg : e.extra + " | " + msg;
    }
}

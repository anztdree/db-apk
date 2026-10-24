package com.db.local;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class Sio {

    public static final String TAG = "DBLocal-LS";
    public static final int PING_INTERVAL_MS = 0x61a8;
    public static final int PING_TIMEOUT_MS = 0xea60;
    public static final int POLL_HOLD_MS = 0x4e20;
    public static final int V_NEW = 0x0;
    public static final int V_SENT = 0x1;
    public static final int V_OK = 0x2;

    public static final ConcurrentHashMap<String, Session> SESSIONS = new ConcurrentHashMap<String, Session>();

    private static final Random RNG = new Random();

    private Sio() {
    }

    public static final class Session {

        public volatile String challenge;
        public volatile long lastSeen;
        public final Object lock;
        public final ArrayDeque<String> queue;
        public final String sid;
        public volatile boolean sioConnected;
        public volatile int verify;
        public volatile Ws ws;

        public Session(String sid) {
            this.lock = new Object();
            this.queue = new ArrayDeque<String>();
            this.verify = 0;
            this.challenge = "";
            this.sioConnected = false;
            this.lastSeen = System.currentTimeMillis();
            this.sid = sid;
        }

        public void push(String pkt) {
            Ws w = this.ws;
            if (w != null) {
                w.sendText(pkt);
                return;
            }
            synchronized (this.lock) {
                this.queue.addLast(pkt);
                this.lock.notifyAll();
            }
        }
    }

    private static void connectNamespace(Session s) {
        if (s.sioConnected) {
            return;
        }
        s.sioConnected = true;
        pushSio(s, "0");
        startVerify(s);
    }

    public static String[] decodePayload(String s) {
        List<String> out = new ArrayList<String>();
        int len = s.length();
        int i = 0;
        outer: while (i < len) {
            StringBuilder sb = new StringBuilder();
            while (i < len) {
                if (s.charAt(i) == ':') {
                    break;
                }
                sb.append(s.charAt(i));
                i = i + 1;
            }
            if (i >= len) {
                break outer;
            }
            try {
                int n = Integer.parseInt(sb.toString());
                i = i + 1;
                if (n < 0) {
                    break outer;
                }
                int end = n + i;
                if (end > len) {
                    break outer;
                }
                String pkt = s.substring(i, end);
                out.add(pkt);
                i = end;
            } catch (Exception e) {
                break outer;
            }
        }
        return out.toArray(new String[0]);
    }

    private static String drain(Session s, long holdMs) {
        long deadline = System.currentTimeMillis() + holdMs;
        synchronized (s.lock) {
            while (s.queue.isEmpty() && s.ws == null) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) {
                    break;
                }
                try {
                    s.lock.wait(Math.min(remain, 1000L));
                } catch (InterruptedException e) {
                    break;
                }
            }
            if (s.queue.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            while (!s.queue.isEmpty()) {
                String pkt = s.queue.pollFirst();
                sb.append(pkt.length());
                sb.append(':');
                sb.append(pkt);
            }
            return sb.toString();
        }
    }

    public static String encodePayload(String[] pkts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pkts.length; i++) {
            sb.append(pkts[i].length());
            sb.append(':');
            sb.append(pkts[i]);
        }
        return sb.toString();
    }

    public static String envelope(String data) {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("{\"ret\":0,\"data\":");
        sb.append(Texts.quote(data));
        sb.append(",\"serverTime\":");
        sb.append(now);
        sb.append(",\"server0Time\":");
        sb.append(now);
        sb.append("}");
        return sb.toString();
    }

    public static String flushQueue(Session s) {
        synchronized (s.lock) {
            if (s.queue.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            while (!s.queue.isEmpty()) {
                String pkt = s.queue.pollFirst();
                sb.append(pkt.length());
                sb.append(':');
                sb.append(pkt);
            }
            return sb.toString();
        }
    }

    public static void gc() {
        long now = System.currentTimeMillis();
        Iterator it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            Session s = (Session) it.next();
            if (now - s.lastSeen > 0x3e418L && s.ws == null) {
                SESSIONS.remove(s.sid);
                log("gc sid=" + s.sid);
            }
        }
    }

    private static void log(String msg) {
        try {
            Log.i("DBLocal-LS", msg);
        } catch (Throwable t) {
        }
        try {
            Dbg.log(msg);
        } catch (Throwable t) {
        }
    }

    private static Session newSession() {
        String sid = newSid();
        Session s = new Session(sid);
        SESSIONS.put(sid, s);
        log("eio open sid=" + sid + " (" + SESSIONS.size() + " sesi)");
        return s;
    }

    private static String newSid() {
        byte[] b = new byte[8];
        RNG.nextBytes(b);
        return Texts.hex(b);
    }

    public static void onEioPacket(Session s, String pkt) {
        if (pkt == null) {
            return;
        }
        if (pkt.length() == 0) {
            return;
        }
        touch(s);
        char c = pkt.charAt(0);
        String rest;
        if (pkt.length() > 1) {
            rest = pkt.substring(1);
        } else {
            rest = "";
        }
        if (c == '2') {
            s.push("3" + rest);
            return;
        }
        if (c == '4') {
            onSioPacket(s, rest);
        }
    }

    private static void onHandler(Session s, String id, Map data) {
        String result = "{}";
        if (data == null) {
            if (id.length() > 0) {
                pushSio(s, "3" + id + "[" + envelope("{}") + "]");
            }
            return;
        }
        String type = Jx.str(data, "type");
        String action = Jx.str(data, "action");
        String userId = Jx.str(data, "userId");
        if (userId.length() == 0) {
            userId = Jx.str(data, "accountToken");
        }
        log("aksi " + type + "/" + action + " userId=" + userId);
        if ("GetServerList".equals(action)) {
            result = Db.getServerList(userId);
        } else if ("loginGame".equals(action)) {
            String password = Jx.str(data, "password");
            String headImageUrl = Jx.str(data, "headImageUrl");
            String nickName = Jx.str(data, "nickName");
            String subChannel = Jx.str(data, "subChannel");
            result = Db.loginGame(userId, password, headImageUrl, nickName, subChannel);
        } else if ("SaveHistory".equals(action)) {
            String channelCode = Jx.str(data, "channelCode");
            String serverId = Jx.str(data, "serverId");
            String securityCode = Jx.str(data, "securityCode");
            result = Db.saveHistory(userId, channelCode, serverId, securityCode);
        } else if ("LoginAnnounce".equals(action)) {
            result = Db.loginAnnounce();
        } else if ("registChat".equals(action)) {
            result = Db.registChat(userId);
        }
        if (id.length() > 0) {
            pushSio(s, "3" + id + "[" + envelope(result) + "]");
        }
    }

    private static void onSioPacket(Session s, String pkt) {
        if (pkt == null) {
            return;
        }
        if (pkt.length() == 0) {
            return;
        }
        char c = pkt.charAt(0);
        if (c == '0') {
            connectNamespace(s);
            return;
        }
        if (c == '1') {
            s.sioConnected = false;
            return;
        }
        if (c != '2') {
            return;
        }
        pkt = pkt.substring(1);
        int i = 0;
        while (i < pkt.length() && pkt.charAt(i) >= '0' && pkt.charAt(i) <= '9') {
            i = i + 1;
        }
        String ackId = "";
        if (i > 0) {
            ackId = pkt.substring(0, i);
            pkt = pkt.substring(i);
        }
        Object parsed = Jx.parse(pkt);
        if (!(parsed instanceof List)) {
            return;
        }
        List args = (List) parsed;
        if (args.isEmpty()) {
            return;
        }
        String ev = Texts.utf8(Texts.utf8(String.valueOf(args.get(0))));
        if ("verify".equals(ev)) {
            String payload = "";
            if (args.size() > 1) {
                payload = String.valueOf(args.get(1));
            }
            onVerify(s, ackId, payload);
            return;
        }
        if ("handler.process".equals(ev)) {
            Object payload = null;
            if (args.size() > 1) {
                payload = args.get(1);
            }
            Map data = null;
            if (payload instanceof Map) {
                data = (Map) payload;
            }
            onHandler(s, ackId, data);
            return;
        }
        log("event tak dikenal: " + ev);
        if (ackId.length() > 0) {
            pushSio(s, "3" + ackId + "[{\"ret\":0,\"data\":\"{}\"}]");
        }
    }

    private static void onVerify(Session s, String id, String enc) {
        String dec = Xxtea.decrypt(enc, "verification");
        if (!dec.equals(s.challenge)) {
            log("verify mismatch sid=" + s.sid + " dec=" + dec);
            if (id.length() > 0) {
                pushSio(s, "3" + id + "[{\"ret\":1}]");
            }
            pushSio(s, "41");
            return;
        }
        s.verify = 2;
        log("verify ok sid=" + s.sid);
        if (id.length() > 0) {
            pushSio(s, "3" + id + "[{\"ret\":0}]");
        }
    }

    public static byte[] pollGet(Map<String, String> q) {
        String qs = q.get("q");
        String sid = Texts.queryValue(qs, "sid");
        if (sid.length() == 0) {
            Session s = newSession();
            String open = "0{\"sid\":\"" + s.sid
                    + "\",\"upgrades\":[\"websocket\"],\"pingInterval\":25000,\"pingTimeout\":60000}";
            return Texts.utf8(encodePayload(new String[] { open }));
        }
        Session s = (Session) SESSIONS.get(sid);
        if (s == null) {
            return Texts.utf8(encodePayload(new String[] { "6" }));
        }
        touch(s);
        String drained = drain(s, POLL_HOLD_MS);
        if (drained.length() == 0) {
            return Texts.utf8(encodePayload(new String[] { "6" }));
        }
        return Texts.utf8(drained);
    }

    public static void pollPost(String sid, byte[] body) {
        Session s = (Session) SESSIONS.get(sid);
        if (s == null) {
            return;
        }
        touch(s);
        String payload = Texts.utf8(body);
        String[] pkts = decodePayload(payload);
        for (int i = 0; i < pkts.length; i++) {
            if (pkts[i].length() > 0) {
                onEioPacket(s, pkts[i]);
            }
        }
    }

    public static void pushEvent(Session s, String event, String data) {
        pushSio(s, "2[" + Texts.quote(event) + "," + data + "]");
    }

    private static void pushSio(Session s, String pkt) {
        s.push("4" + pkt);
    }

    private static void startVerify(Session s) {
        s.challenge = Texts.randHex(16);
        s.verify = 1;
        pushSio(s, "2[\"verify\"," + Texts.quote(s.challenge) + "]");
        log("verify dikirim sid=" + s.sid);
    }

    private static void touch(Session s) {
        s.lastSeen = System.currentTimeMillis();
    }
}

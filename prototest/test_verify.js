/* Uji protokol live: handshake verify (TEA) + GetServerList
   Port eksak dari main.min.js (TEA class) — key "verification" */
var Utf8 = {
    encode: function (s) { return unescape(encodeURIComponent(s)); },
    decode: function (s) { return decodeURIComponent(escape(s)); }
};
var Base64 = {
    _keyStr: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",
    encode: function (e) {
        var t = ""; var n, r, i, s, o, u, a; var f = 0;
        e = Utf8.encode(e);
        while (f < e.length) {
            n = e.charCodeAt(f++); r = e.charCodeAt(f++); i = e.charCodeAt(f++);
            s = n >> 2; o = (n & 3) << 4 | r >> 4; u = (r & 15) << 2 | i >> 6; a = i & 63;
            if (isNaN(r)) { u = a = 64; } else if (isNaN(i)) { a = 64; }
            t = t + Base64._keyStr.charAt(s) + Base64._keyStr.charAt(o) + Base64._keyStr.charAt(u) + Base64._keyStr.charAt(a);
        }
        return t;
    },
    decode: function (e) {
        var t = ""; var n, r, i; var s, o, u, a; var f = 0;
        e = e.replace(/[^A-Za-z0-9\+\/\=]/g, "");
        while (f < e.length) {
            s = Base64._keyStr.indexOf(e.charAt(f++)); o = Base64._keyStr.indexOf(e.charAt(f++));
            u = Base64._keyStr.indexOf(e.charAt(f++)); a = Base64._keyStr.indexOf(e.charAt(f++));
            n = s << 2 | o >> 4; r = (o & 15) << 4 | u >> 2; i = (u & 3) << 6 | a;
            t = t + String.fromCharCode(n);
            if (u != 64) { t = t + String.fromCharCode(r); }
            if (a != 64) { t = t + String.fromCharCode(i); }
        }
        return Utf8.decode(t);
    }
};

var TEA = function () { };
TEA.prototype.encrypt = function (e, t) {
    if (0 == e.length) return "";
    var n = this.strToLongs(Utf8.encode(e));
    n.length <= 1 && (n[1] = 0);
    for (var o, a, r = this.strToLongs(Utf8.encode(t).slice(0, 16)), i = n.length, s = n[i - 1], l = n[0], u = 2654435769, c = Math.floor(6 + 52 / i), p = 0; c-- > 0;) {
        p += u, a = p >>> 2 & 3;
        for (var d = 0; i > d; d++) l = n[(d + 1) % i], o = (s >>> 5 ^ l << 2) + (l >>> 3 ^ s << 4) ^ (p ^ l) + (r[3 & d ^ a] ^ s), s = n[d] += o
    }
    var g = this.longsToStr(n);
    return Base64.encode(g)
};
TEA.prototype.strToLongs = function (e) {
    for (var t = new Array(Math.ceil(e.length / 4)), n = 0; n < t.length; n++) t[n] = e.charCodeAt(4 * n) + (e.charCodeAt(4 * n + 1) << 8) + (e.charCodeAt(4 * n + 2) << 16) + (e.charCodeAt(4 * n + 3) << 24);
    return t
};
TEA.prototype.longsToStr = function (e) {
    for (var t = new Array(e.length), n = 0; n < e.length; n++) t[n] = String.fromCharCode(255 & e[n], e[n] >>> 8 & 255, e[n] >>> 16 & 255, e[n] >>> 24 & 255);
    return t.join("")
};

// --- sanity: roundtrip
var enc = new TEA().encrypt("hello-nonce-123", "verification");
console.log("[TEA] encrypt('hello-nonce-123','verification') =", enc);

var io = require("socket.io-client");
var url = process.argv[2] || "https://login.popoh5.com:610";
console.log("[NET] connect:", url);

var socket = io.connect(url, { reconnectionAttempts: 2, timeout: 12000 });
var verified = false;

socket.on("connect", function () {
    console.log("[NET] CONNECTED, id =", socket.id);
});
socket.on("verify", function (nonce) {
    console.log("[SERVER] verify nonce diterima:", JSON.stringify(nonce).slice(0, 120));
    var answer = new TEA().encrypt(String(nonce), "verification");
    socket.emit("verify", answer, function (res) {
        console.log("[SERVER] verify ack:", JSON.stringify(res));
        if (res && res.ret === 0) {
            verified = true;
            console.log("[OK] HANDSHAKE BERHASIL — protokol terbukti!");
            var msg = {
                type: "User", action: "GetServerList",
                userId: process.argv[3] || "", subChannel: "", channel: "",
                version: "1.0"
            };
            console.log("[CLIENT] handler.process ->", JSON.stringify(msg));
            socket.emit("handler.process", msg, function (r) {
                var out = JSON.stringify(r);
                console.log("[SERVER] GetServerList response (potongan 1500 chars):");
                console.log(out.slice(0, 1500));
                socket.disconnect();
                process.exit(0);
            });
            setTimeout(function () {
                console.log("[TIMEOUT] GetServerList tidak dibalas");
                socket.disconnect(); process.exit(1);
            }, 12000);
        } else {
            console.log("[FAIL] verify ditolak:", res);
            socket.disconnect(); process.exit(1);
        }
    });
});
socket.on("connect_error", function (e) { console.log("[ERR] connect_error:", e && e.message || e); });
socket.on("error", function (e) { console.log("[ERR] error:", e && e.message || e); });
socket.on("disconnect", function (r) { console.log("[NET] disconnect:", r); });
setTimeout(function () { if (!verified) { console.log("[TIMEOUT] verify event tidak kunjung datang"); process.exit(1); } }, 15000);

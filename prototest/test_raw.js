/* Logger paket mentah — lihat SEMUA event socket.io dari server */
var Utf8 = {
    encode: function (s) { return unescape(encodeURIComponent(s)); }
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
    return Base64.encode(this.longsToStr(n))
};
TEA.prototype.strToLongs = function (e) {
    for (var t = new Array(Math.ceil(e.length / 4)), n = 0; n < t.length; n++) t[n] = e.charCodeAt(4 * n) + (e.charCodeAt(4 * n + 1) << 8) + (e.charCodeAt(4 * n + 2) << 16) + (e.charCodeAt(4 * n + 3) << 24);
    return t
};
TEA.prototype.longsToStr = function (e) {
    for (var t = new Array(e.length), n = 0; n < e.length; n++) t[n] = String.fromCharCode(255 & e[n], e[n] >>> 8 & 255, e[n] >>> 16 & 255, e[n] >>> 24 & 255);
    return t.join("")
};

var io = require("socket.io-client");
var url = process.argv[2];
console.log("[NET] connect:", url);
var socket = io.connect(url, { reconnectionAttempts: 2, timeout: 12000 });

// patch: log SEMUA event masuk
var origOnevent = socket.onevent;
socket.onevent = function (packet) {
    console.log("[<<EVENT]", packet.data && packet.data[0], "| args:", JSON.stringify(packet.data && packet.data.slice(1)).slice(0, 300));
    origOnevent.call(this, packet);
};
// log acks juga
var origEmit = socket.emit;
socket.emit = function (ev) {
    console.log("[>>EMIT]", ev, "| args:", JSON.stringify(Array.prototype.slice.call(arguments, 1)).slice(0, 200));
    return origEmit.apply(this, arguments);
};

socket.on("connect", function () { console.log("[NET] CONNECTED id=", socket.id); });
socket.on("verify", function (nonce) {
    console.log("[OK] nonce:", JSON.stringify(nonce).slice(0, 100));
    socket.emit("verify", new TEA().encrypt(String(nonce), "verification"), function (res) {
        console.log("[VERIFY ACK]", JSON.stringify(res));
        setTimeout(function () { process.exit(0); }, 4000);
    });
});
socket.on("connect_error", function (e) { console.log("[ERR] connect_error:", e && e.message || e); });
socket.on("error", function (e) { console.log("[ERR] error:", e && e.message || e); });
socket.on("disconnect", function (r) { console.log("[NET] disconnect:", r); });
setTimeout(function () { console.log("[DONE] 20s habis"); process.exit(0); }, 20000);

/* Uji GetServerList langsung ke login server (tanpa verify, verifyEnable=false) */
var io = require("socket.io-client");
var url = process.argv[2] || "https://login.popoh5.com:610";
console.log("[NET] connect:", url);
var socket = io.connect(url, { reconnectionAttempts: 2, timeout: 12000 });

socket.on("connect", function () {
    console.log("[NET] CONNECTED id=", socket.id);
    var msg = {
        type: "User", action: "GetServerList",
        userId: process.argv[3] || "", subChannel: process.argv[4] || "", channel: process.argv[5] || "",
        version: "1.0"
    };
    console.log("[CLIENT] handler.process ->", JSON.stringify(msg));
    socket.emit("handler.process", msg, function (r) {
        var out = JSON.stringify(r);
        console.log("[SERVER] RESPONSE len=" + out.length);
        console.log(out.slice(0, 3000));
        socket.disconnect();
        process.exit(0);
    });
    setTimeout(function () {
        console.log("[TIMEOUT] GetServerList tidak dibalas dalam 15s");
        socket.disconnect(); process.exit(1);
    }, 15000);
});
socket.on("connect_error", function (e) { console.log("[ERR] connect_error:", e && e.message || e); });
socket.on("error", function (e) { console.log("[ERR] error:", e && e.message || e); });
socket.on("disconnect", function (r) { console.log("[NET] disconnect:", r); });
setTimeout(function () { console.log("[DONE] timeout global"); process.exit(1); }, 20000);

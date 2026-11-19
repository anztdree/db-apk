// Tier-2 contoh — handler ber-logika (kontrak §7.4, ES5 untuk Rhino).
// API tersedia: state.get/set/del/incr/keys/nextUid, md5, b64enc, b64dec,
// xorDragon, readFile, time, date, api.lib.
reg({
  id: "v1-createOrder-stub",
  kind: "http",
  path: "/v1/auth/createOrder",
  fn: function (req, reply) {
    var uid = state.get("counter:uid") || "0";
    reply.json({
      code: 0,
      message: "success",
      data: { orderId: "stub_" + uid, ts: time() }
    });
  }
});

reg({
  id: "sock-User-enterGame",
  kind: "sock",
  type: "User",
  action: "enterGame",
  fn: function (req, reply) {
    var serverId = (req.body && req.body.serverId) || 1;
    var visits = state.incr("stats:enterGame", 1);
    reply.ack({
      ret: 0,
      data: JSON.stringify({ ok: true, serverId: serverId, visits: visits }),
      compress: false
    });
  }
});

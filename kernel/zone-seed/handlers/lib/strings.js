// lib/*.js — diload DULUAN (urut nama) sebelum handler Tier-2.
// Registrasi kind "lib" masuk ke objek `lib` yang disuntikkan ke handler:
// lib["<id>"](...args). Kontrak ES5 (kompatibel Rhino android-safe).
reg({
  id: "greet",
  kind: "lib",
  fn: function (name) {
    return "hello " + name;
  }
});

reg({
  id: "nowMs",
  kind: "lib",
  fn: function () {
    return time() * 1000;
  }
});

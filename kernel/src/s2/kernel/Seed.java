// ============================================================
// SEED — zona server TERTANAM DI DALAM DEX (NOL file di assets/).
// Karena tidak ada asset server di APK, extractor client tidak akan
// pernah menaruh apa pun ke folder files/. Seed hanya mengisi file
// yang BELUM ADA di root (file device menang — editan owner tidak
// pernah ditimpa). Root = SEBELAH folder files/ (K.root()).
// ============================================================
package s2.kernel;

public final class Seed {
    private Seed() {}

    private static final String[] PATHS = {
            "config/serverList.json",
            "config/setting.json",
            "config/version/base.version",
            "config/version/resource.version",
            "config/versionbin.json",
            "engine.json",
            "handlers/10-tier1-config.json",
            "handlers/20-tier1-sdk.json",
            "handlers/30-tier1-game.json",
            "handlers/90-tier2-example.js",
            "handlers/lib/strings.js",
            "static/README.md",
            "data/seed.json",
    };

    private static final String[] CONTENTS = {
            "{\n  \"ret\": 0,\n  \"list\": [\n    {\n      \"serverId\": 1,\n      \"name\": \"Server Lokal 1\",\n      \"url\": \"http://127.0.0.1:4000\",\n      \"chaturl\": \"http://127.0.0.1:4000\",\n      \"dungeonurl\": \"http://127.0.0.1:4000\",\n      \"status\": 1,\n      \"recommend\": 1\n    }\n  ]\n}\n",
            "{\n  \"_catatan\": \"setting.json — disajikan sebagai native_setting (XOR DragonBall on-the-fly) untuk EntryPoint /lzceshi/native_setting/Android dan /bs/db/android/setting_BS_Android.bin. CurrentMode WAJIB != 0 (0 = popup check_network).\",\n  \"Android110\": 1,\n  \"Android111\": 1,\n  \"Android112\": 1,\n  \"Android113\": 1,\n  \"Android114\": 1,\n  \"Android115\": 1,\n  \"testMode\": 0,\n  \"webVer\": \"\",\n  \"nativeRender\": 0,\n  \"url\": \"http://127.0.0.1:4000/game/index.html\",\n  \"webUrl\": \"http://127.0.0.1:4000/game/index.html\",\n  \"tsUrl\": \"http://127.0.0.1:4000/game/index.html\",\n  \"tsWebUrl\": \"http://127.0.0.1:4000/game/index.html\",\n  \"update\": \"http://127.0.0.1:4000\",\n  \"forceBuild\": 0,\n  \"noForceBuild\": 1,\n  \"forceUrl\": \"\",\n  \"checkTime\": 0,\n  \"tsLoginServer\": \"127.0.0.1\",\n  \"loginServer\": \"127.0.0.1\",\n  \"loginKey\": \"verification\",\n  \"loginPort\": 4000,\n  \"loginWebPort\": 4000,\n  \"productPrefix\": \"\",\n  \"productIdMap\": {},\n  \"gameIcon\": \"\",\n  \"extra\": \"\",\n  \"clientParams\": \"\",\n  \"peiUrl\": \"\",\n  \"wpei\": 0,\n  \"wpeiurl\": \"\",\n  \"blackWhiteL\": \"\",\n  \"blackWhiteLNetSegment\": \"\",\n  \"currentNetIpUrl\": \"http://127.0.0.1:4000/ipcheck\",\n  \"unityGameSdkLogin\": 0,\n  \"checkGoogleTicketUrl\": \"\",\n  \"gameLanguage\": \"id\",\n  \"nativeUnzipJson\": \"\"\n}\n",
            "110\n",
            "11220\n",
            "{\n  \"_catatan\": \"versionbin.json — disajikan sebagai com_db_local.bin (XOR DragonBall). version=110 + resourceVersion=11220 COCOK PERSIS assets APK (bukti bedah) → Egret tidak download resource; game local standalone.\",\n  \"version\": \"110\",\n  \"resourceVersion\": \"11220\",\n  \"files\": {},\n  \"maps\": {}\n}\n",
            "{\n  \"port\": 4000,\n  \"pingInterval\": 25000,\n  \"pingTimeout\": 60000,\n  \"holdTimeoutMs\": 8000,\n  \"logBuffer\": 3000\n}\n",
            "[\n  { \"kind\": \"http\", \"path\": \"/lzceshi/native_setting/Android\", \"transform\": \"xorDragonBall\", \"bodyFile\": \"config/setting.json\", \"contentType\": \"application/octet-stream\" },\n  { \"kind\": \"http\", \"path\": \"/bs/db/android/setting_BS_Android.bin\", \"transform\": \"xorDragonBall\", \"bodyFile\": \"config/setting.json\", \"contentType\": \"application/octet-stream\" },\n  { \"kind\": \"http\", \"path\": \"/bs/db/android/com_db_local.bin\", \"transform\": \"xorDragonBall\", \"bodyFile\": \"config/versionbin.json\", \"contentType\": \"application/octet-stream\" },\n  { \"kind\": \"http\", \"path\": \"/setting_BS_Android.bin\", \"transform\": \"xorDragonBall\", \"bodyFile\": \"config/setting.json\", \"contentType\": \"application/octet-stream\" },\n  { \"kind\": \"http\", \"path\": \"/com_db_local.bin\", \"transform\": \"xorDragonBall\", \"bodyFile\": \"config/versionbin.json\", \"contentType\": \"application/octet-stream\" },\n  { \"kind\": \"http\", \"path\": \"/log\", \"respond\": { \"code\": 0 }, \"contentType\": \"application/json\" },\n  { \"kind\": \"http\", \"path\": \"/version/base.version\", \"bodyFile\": \"config/version/base.version\", \"contentType\": \"text/plain\" },\n  { \"kind\": \"http\", \"path\": \"/version/resource.version\", \"bodyFile\": \"config/version/resource.version\", \"contentType\": \"text/plain\" },\n  { \"kind\": \"http\", \"path\": \"/ipcheck\", \"body\": \"127.0.0.1\", \"contentType\": \"text/plain\" },\n  { \"kind\": \"http\", \"path\": \"/game/index.html\", \"body\": \"<!DOCTYPE html><html><head><meta charset=\\\"utf-8\\\"><title>entry shell</title></head><body>entry shell placeholder — isi final dari zona static/</body></html>\", \"contentType\": \"text/html\" },\n  { \"kind\": \"http\", \"path\": \"/game/manifest.json\", \"body\": \"{ \\\"gameRoot\\\": \\\"./\\\", \\\"initial\\\": [], \\\"version\\\": \\\"110.11220\\\" }\", \"contentType\": \"application/json\" }\n]\n",
            "[\n  { \"kind\": \"http\", \"path\": \"/v1/user/registerVisitor\", \"respond\": { \"code\": 0, \"message\": \"success\", \"data\": { \"account\": \"q_{{state.nextUid}}\", \"uid\": \"{{state.nextUid}}\", \"ts\": \"{{time}}\" } } },\n  { \"kind\": \"http\", \"path\": \"/v1/user/autoLogin\", \"respond\": { \"result\": true, \"data\": \"{}\" } },\n  { \"kind\": \"http\", \"path\": \"/v1/system/init\", \"respond\": { \"code\": 0, \"message\": \"success\" } },\n  { \"kind\": \"http\", \"path\": \"/v1/system/getNotice\", \"respond\": { \"code\": 0, \"message\": \"success\", \"data\": [] } }\n]\n",
            "[\n  {\n    \"kind\": \"sock\",\n    \"type\": \"User\",\n    \"action\": \"GetServerList\",\n    \"respond\": {\n      \"ret\": 0,\n      \"dataFile\": \"config/serverList.json\",\n      \"compress\": false\n    }\n  },\n  {\n    \"kind\": \"sock\",\n    \"type\": \"User\",\n    \"action\": \"loginGame\",\n    \"respond\": {\n      \"ret\": 0,\n      \"data\": {\n        \"userId\": \"{{state.nextUid}}\"\n      }\n    }\n  },\n  {\n    \"kind\": \"sock\",\n    \"type\": \"User\",\n    \"action\": \"SaveHistory\",\n    \"respond\": {\n      \"ret\": 0,\n      \"data\": {}\n    }\n  },\n  {\n    \"kind\": \"sock\",\n    \"type\": \"User\",\n    \"action\": \"SaveUserEnterInfo\",\n    \"respond\": {\n      \"ret\": 0,\n      \"data\": {}\n    }\n  },\n  {\n    \"kind\": \"sock\",\n    \"type\": \"User\",\n    \"action\": \"SaveLanguage\",\n    \"respond\": {\n      \"ret\": 0,\n      \"data\": {}\n    }\n  },\n  {\n    \"kind\": \"sock\",\n    \"type\": \"User\",\n    \"action\": \"exitGame\",\n    \"respond\": {\n      \"ret\": 0,\n      \"data\": {}\n    }\n  }\n]",
            "// Tier-2 contoh — handler ber-logika (kontrak §7.4, ES5 untuk Rhino).\n// API tersedia: state.get/set/del/incr/keys/nextUid, md5, b64enc, b64dec,\n// xorDragon, readFile, time, date, api.lib.\nreg({\n  id: \"v1-createOrder-stub\",\n  kind: \"http\",\n  path: \"/v1/auth/createOrder\",\n  fn: function (req, reply) {\n    var uid = state.get(\"counter:uid\") || \"0\";\n    reply.json({\n      code: 0,\n      message: \"success\",\n      data: { orderId: \"stub_\" + uid, ts: time() }\n    });\n  }\n});\n\nreg({\n  id: \"sock-User-enterGame\",\n  kind: \"sock\",\n  type: \"User\",\n  action: \"enterGame\",\n  fn: function (req, reply) {\n    var serverId = (req.body && req.body.serverId) || 1;\n    var visits = state.incr(\"stats:enterGame\", 1);\n    reply.ack({\n      ret: 0,\n      data: JSON.stringify({ ok: true, serverId: serverId, visits: visits }),\n      compress: false\n    });\n  }\n});\n",
            "// lib/*.js — diload DULUAN (urut nama) sebelum handler Tier-2.\n// Registrasi kind \"lib\" masuk ke objek `lib` yang disuntikkan ke handler:\n// lib[\"<id>\"](...args). Kontrak ES5 (kompatibel Rhino android-safe).\nreg({\n  id: \"greet\",\n  kind: \"lib\",\n  fn: function (name) {\n    return \"hello \" + name;\n  }\n});\n\nreg({\n  id: \"nowMs\",\n  kind: \"lib\",\n  fn: function () {\n    return time() * 1000;\n  }\n});\n",
            "# ZONA static/ — ENTRY SHELL H5 (override)\n\nZona ini WAJIB menyajikan entry shell yang TIDAK ADA di paket client\npschent (bukti agent 1-a, PETA v5 §2 TOOL 2 / §13.3):\n\n- `index.html`\n- `manifest.json`\n- polyfill XHR/WS + bridge `window.*` (±15 fungsi: getLoginServer,\n  checkFromNative, paySdk, ...)\n\nStatic engine merge: file di `static/` MENANG di atas `files/game/`\n(fallback). Tanpa shell ini game tidak akan pernah start.\n\nIsi final dibangun dari paket client asli — NOL byte buatan kita pada isi\ngame. Di prototipe ini zona hanya berisi README.\n",
            "{\n  \"meta:engineVersion\": \"v6-proto\",\n  \"counter:uid\": 1000,\n  \"game:notice\": \"Selamat datang di server lokal (starter seed).\"\n}\n",
    };

    /** tanam zona ke root bila file belum ada. return jumlah file yang ditulis. */
    public static int apply(java.io.File root) {
        int written = 0;
        for (int i = 0; i < PATHS.length; i++) {
            try {
                java.io.File f = new java.io.File(root, PATHS[i]);
                if (f.exists()) continue; // device menang
                java.io.File p = f.getParentFile();
                if (p != null && !p.exists()) p.mkdirs();
                if (Util.writeTextFile(f, CONTENTS[i])) written++;
            } catch (Throwable ignore) {
                // anti-FC
            }
        }
        return written;
    }
}
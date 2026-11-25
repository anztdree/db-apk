#!/usr/bin/env python3
"""Battery probe server login asli: variasi transport + payload, tangkap close code WS."""
import json, random, string, ssl, time, urllib.request, urllib.error, http.cookiejar
import websocket

BASE = "https://login.popoh5.com:610"
UA = "Mozilla/5.0 (Linux; Android 12; itel S665L) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.5304.141 Mobile Safari/537.36"
ORIGIN = "https://dragonh5cdn.popoh5.com"
REFERER = "https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935"


def t_param():
    return "".join(random.choices(string.ascii_letters + string.digits, k=7))


class Polling:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar),
            urllib.request.HTTPSHandler(context=ssl._create_unverified_context()))

    def get(self, with_sid=None):
        url = BASE + "/socket.io/?EIO=3&transport=polling&t=" + t_param()
        if with_sid:
            url += "&sid=" + with_sid
        r = urllib.request.Request(url)
        r.add_header("User-Agent", UA)
        r.add_header("Origin", ORIGIN)
        r.add_header("Referer", REFERER)
        try:
            with self.op.open(r, timeout=30) as h:
                return h.status, h.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf-8", "replace")[:200]

    def post(self, body, sid):
        url = BASE + "/socket.io/?EIO=3&transport=polling&t=" + t_param() + "&sid=" + sid
        r = urllib.request.Request(url, data=body.encode(), method="POST")
        r.add_header("User-Agent", UA)
        r.add_header("Origin", ORIGIN)
        r.add_header("Referer", REFERER)
        r.add_header("Content-Type", "text/plain;charset=UTF-8")
        try:
            with self.op.open(r, timeout=30) as h:
                return h.status, h.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf-8", "replace")[:200]


def parse_open(raw):
    i, sid = 0, None
    while i < len(raw):
        j = raw.find(":", i)
        if j < 0:
            break
        try:
            ln = int(raw[i:j])
        except ValueError:
            break
        pkt = raw[j + 1: j + 1 + ln]
        if pkt.startswith("0{"):
            sid = json.loads(pkt[1:])["sid"]
        i = j + 1 + ln
    return sid


EVENTS = {
    "GetServerList-quick": '420["handler.process",{"type":"User","action":"GetServerList","userId":"1795567351139","subChannel":"","channel":"quick"}]',
    "GetServerList-empty": '420["handler.process",{"type":"User","action":"GetServerList","userId":"","subChannel":"","channel":""}]',
    "loginGame": '421["handler.process",{"type":"User","action":"loginGame","userId":"tester01","password":"game_origin","fromChannel":"game_origin","channelName":"","headImageUrl":"","nickName":"","subChannel":"","version":"1.0"}]',
}


def ws_probe(event_name, send_upgrade5, extra_connect40):
    p = Polling()
    c, raw = p.get()
    sid = parse_open(raw)
    if not sid:
        print("open gagal:", c, raw[:100])
        return
    host = BASE.split("//", 1)[1]
    wsurl = "wss://" + host + "/socket.io/?EIO=3&transport=websocket&sid=" + sid
    ws = websocket.WebSocket()
    ws.connect(wsurl, header=["User-Agent: " + UA, "Origin: " + ORIGIN, "Referer: " + REFERER],
               sslopt={"cert_reqs": ssl.CERT_NONE}, timeout=12)
    ws.send("2probe")
    r1 = ws.recv()
    label = "%s%s" % ("[up5]" if send_upgrade5 else "", "[40]" if extra_connect40 else "")
    if send_upgrade5:
        ws.send("5")
    if extra_connect40:
        ws.send("40")
    ev = EVENTS[event_name]
    try:
        ws.send(ev)
    except Exception as e:
        print(label, "send err:", e)
        return
    out = [r1]
    end = time.time() + 6
    while time.time() < end:
        try:
            ws.settimeout(max(0.2, end - time.time()))
            op, data = ws.recv_data()
            if op == 8:
                code = int.from_bytes(data[:2], "big") if len(data) >= 2 else 1005
                reason = data[2:].decode("utf-8", "replace")
                out.append("CLOSE code=%d reason=%r" % (code, reason))
                break
            out.append(data.decode("utf-8", "replace"))
        except websocket.WebSocketTimeoutException:
            out.append("(timeout diam)")
            break
        except Exception as e:
            out.append("err:%s" % e)
            break
    print("[%s %s] frames:" % (event_name, label))
    for f in out:
        print("   ", repr(f)[:300])
    try:
        ws.close()
    except Exception:
        pass


if __name__ == "__main__":
    combos = [
        ("GetServerList-quick", False, False),
        ("GetServerList-quick", True, False),
        ("GetServerList-quick", True, True),
        ("GetServerList-empty", True, False),
        ("loginGame", True, False),
    ]
    for ev, u5, c40 in combos:
        try:
            ws_probe(ev, u5, c40)
        except Exception as e:
            print("probe err:", e)
        time.sleep(1)

#!/usr/bin/env python3
"""Capture PENUH verbatim semua event login ke server asli via WS (EIO=3, upgrade 5).
Semua frame disimpan ke file: live-capture-login-<ts>.txt
"""
import json, random, string, ssl, time, urllib.request
import websocket

BASE = "https://login.popoh5.com:610"
UA = "Mozilla/5.0 (Linux; Android 12; itel S665L) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.5304.141 Mobile Safari/537.36"
ORIGIN = "https://dragonh5cdn.popoh5.com"
REFERER = "https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935"
UID = "1795567351139"  # contoh uid device log v17


def t_param():
    return "".join(random.choices(string.ascii_letters + string.digits, k=7))


def open_sid():
    r = urllib.request.Request(BASE + "/socket.io/?EIO=3&transport=polling&t=" + t_param())
    r.add_header("User-Agent", UA)
    with urllib.request.urlopen(r, timeout=30, context=ssl._create_unverified_context()) as h:
        raw = h.read().decode()
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


def run_session(events, outfile):
    sid = open_sid()
    assert sid
    ws = websocket.WebSocket()
    ws.connect("wss://" + BASE.split("//", 1)[1] + "/socket.io/?EIO=3&transport=websocket&sid=" + sid,
               header=["User-Agent: " + UA, "Origin: " + ORIGIN, "Referer: " + REFERER],
               sslopt={"cert_reqs": ssl.CERT_NONE}, timeout=15)
    log = []
    log.append("CAPTURE LIVE login.popoh5.com:610 %s" % time.strftime("%Y-%m-%d %H:%M:%S WIB"))
    log.append("sid=%s" % sid)
    log.append("OPEN(polling): 96:0{...}2:40 (103 B)")
    ws.send("2probe")
    log.append("C-> 2probe")
    log.append("S<- " + ws.recv())
    ws.send("5")
    log.append("C-> 5")
    ackid = 0
    for name, payload in events:
        ackid += 1
        wire = "42%d[%s]" % (ackid, payload)
        ws.send(wire)
        log.append("C-> %s" % wire)
        # tunggu jawaban
        end = time.time() + 6
        got = False
        while time.time() < end:
            try:
                ws.settimeout(max(0.2, end - time.time()))
                op, data = ws.recv_data()
            except websocket.WebSocketTimeoutException:
                break
            txt = data.decode("utf-8", "replace")
            log.append("S<- %s" % txt)
            if txt.startswith("43"):
                got = True
                break
            if op == 8:
                break
        if not got:
            log.append("(no ack 6s)")
        time.sleep(0.4)
    # ping keepalive uji
    ws.send("2")
    try:
        ws.settimeout(3)
        op, data = ws.recv_data()
        log.append("C-> 2 ; S<- " + data.decode("utf-8", "replace"))
    except Exception as e:
        log.append("ping err: %s" % e)
    try:
        ws.close()
    except Exception:
        pass
    txt = "\n".join(log)
    with open(outfile, "w") as f:
        f.write(txt + "\n")
    print(txt)
    return outfile


if __name__ == "__main__":
    EVENTS = [
        ("GetServerList", '"handler.process",{"type":"User","action":"GetServerList","userId":"%s","subChannel":"","channel":"quick"}' % UID),
        ("LoginAnnounce", '"handler.process",{"type":"User","action":"LoginAnnounce"}'),
        ("SaveHistory", '"handler.process",{"type":"User","action":"SaveHistory","accountToken":"%s","channelCode":"quick","serverId":"2112","securityCode":"","subChannel":"","version":"1.0"}' % UID),
        ("SaveLanguage", '"handler.process",{"type":"User","action":"SaveLanguage","userid":"%s","sdk":"quick","appid":"","language":"in"}' % UID),
        ("SaveUserEnterInfo", '"handler.process",{"type":"User","action":"SaveUserEnterInfo","accountToken":"%s","channelCode":"quick","subChannel":"","createTime":1795625000,"userLevel":1,"version":"1.0"}' % UID),
    ]
    run_session(EVENTS, "live-capture-login-%s.txt" % time.strftime("%Y%m%d-%H%M%S"))

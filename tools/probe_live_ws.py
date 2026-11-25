#!/usr/bin/env python3
"""Probe socket.io v2 (EIO=3) via WEBSOCKET ke server login asli.
Alur: GET open polling (ambil sid) -> WS upgrade -> 2probe/3probe -> kirim event -> tangkap semua frame.
"""
import json, random, string, ssl, socket, sys, time, urllib.request
import websocket

BASE = sys.argv[1] if len(sys.argv) > 1 else "https://login.popoh5.com:610"
HOSTPATH = BASE.split("//", 1)[1]  # host:port
ORIGIN = "https://" + HOSTPATH.split(":")[0]
UA = "Mozilla/5.0 (Linux; Android 12; itel S665L) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.5304.141 Mobile Safari/537.36"


def t_param():
    return "".join(random.choices(string.ascii_letters + string.digits, k=6))


def get_open():
    url = BASE + "/socket.io/?EIO=3&transport=polling&t=" + t_param()
    r = urllib.request.Request(url)
    r.add_header("User-Agent", UA)
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    with urllib.request.urlopen(r, timeout=30, context=ctx) as h:
        raw = h.read().decode()
    print("OPEN:", raw)
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


def ws_url(sid):
    wsbase = BASE.replace("https://", "wss://").replace("http://", "ws://")
    return wsbase + "/socket.io/?EIO=3&transport=websocket&sid=" + sid


def main():
    sid = get_open()
    print("sid:", sid)
    ws = websocket.WebSocket()
    ws.connect(ws_url(sid), header=["User-Agent: " + UA, "Origin: " + ORIGIN],
               sslopt={"cert_reqs": ssl.CERT_NONE}, timeout=15)
    print("WS CONNECTED")

    def drain(sec=3.0):
        end = time.time() + sec
        while time.time() < end:
            try:
                ws.settimeout(max(0.1, end - time.time()))
                frame = ws.recv()
                print("  <- frame:", repr(frame))
                if isinstance(frame, str) and frame.startswith("3"):
                    pass
            except websocket.WebSocketTimeoutException:
                break
            except Exception as e:
                print("  recv err:", e)
                return

    # handshake engine.io over WS
    ws.send("2probe")
    print("-> 2probe")
    drain(5)
    # kirim event GetServerList (ackId 0 seperti Egret: 420 dengan ack counter)
    ev = '420["handler.process",{"type":"User","action":"GetServerList","userId":"1795567351139","subChannel":"","channel":"quick"}]'
    ws.send(ev)
    print("->", ev)
    drain(8)
    # coba ack id lain + ping
    ws.send("2")
    print("-> ping")
    drain(3)
    ws.close()
    print("SELESAI")


if __name__ == "__main__":
    main()

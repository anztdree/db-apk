#!/usr/bin/env python3
"""Probe socket.io v2 (EIO=3) server login ASLI — capture ACK GetServerList verbatim.
Alur persis device (log v17): GET open (polling) -> POST event 42<ackId> -> GET poll (terima 43<ackId>).
Semua byte di-print verbatim.
"""
import json, random, string, sys, time, urllib.request, urllib.error, ssl

BASE = sys.argv[1] if len(sys.argv) > 1 else "https://login.popoh5.com:610"
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

UA = "Mozilla/5.0 (Linux; Android 12; itel S665L) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.5304.141 Mobile Safari/537.36"


def t_param():
    return "".join(random.choices(string.ascii_letters + string.digits, k=6))


def req(method, url, body=None):
    r = urllib.request.Request(url, data=body.encode() if body else None, method=method)
    r.add_header("User-Agent", UA)
    r.add_header("Accept", "*/*")
    if body:
        r.add_header("Content-Type", "application/octet-stream")
    t0 = time.time()
    try:
        with urllib.request.urlopen(r, timeout=30, context=ctx) as h:
            data = h.read()
            code = h.status
    except urllib.error.HTTPError as e:
        data = e.read()
        code = e.code
    dt = (time.time() - t0) * 1000
    print("--- %s %s -> %d (%d B, %.0f ms)" % (method, url, code, len(data), dt))
    print("    BODY:", data.decode("utf-8", "replace"))
    return data.decode("utf-8", "replace")


def main():
    # 1) open
    open_raw = req("GET", BASE + "/socket.io/?EIO=3&transport=polling&t=" + t_param())
    # payload engine.io polling: "<len>:<packet>..." ambil packet 0
    sid = None
    # payload polling: "<len>:<packet><len>:<packet>..." — parse length-prefix
    i = 0
    while i < len(open_raw):
        j = open_raw.find(":", i)
        if j < 0:
            break
        try:
            ln = int(open_raw[i:j])
        except ValueError:
            break
        pkt = open_raw[j + 1: j + 1 + ln]
        print("    packet:", pkt)
        if pkt.startswith("0{"):
            sid = json.loads(pkt[1:])["sid"]
        i = j + 1 + ln
    assert sid, "sid tidak ketemu: " + open_raw[:200]
    print(">>> sid =", sid)

    events = [
        # persis bentuk device (log v17 seq 51)
        '420["handler.process",{"type":"User","action":"GetServerList","userId":"1795567351139","subChannel":"","channel":"quick"}]',
    ]
    if len(sys.argv) > 2:
        events.append(sys.argv[2])

    ack_id = 0
    for ev in events:
        ack_id += 1
        wire = ev.replace("420", "42%d" % ack_id, 1)
        body = "%d:%s" % (len(wire), wire)
        req("POST", BASE + "/socket.io/?EIO=3&transport=polling&t=" + t_param() + "&sid=" + sid, body)
        # poll jawaban
        for attempt in range(3):
            poll = req("GET", BASE + "/socket.io/?EIO=3&transport=polling&t=" + t_param() + "&sid=" + sid)
            if poll.strip():
                break
            time.sleep(1)
    print("SELESAI")


if __name__ == "__main__":
    main()

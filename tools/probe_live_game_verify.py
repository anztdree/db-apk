#!/usr/bin/env python3
"""Probe server GAME asli (main-server) — capture challenge handshake verify (TEA/XXTEA).
Juga coba loginGame dgn fromChannel quick ke server login.
"""
import base64, json, random, string, ssl, struct, time, urllib.request
import websocket

LOGIN = "https://login.popoh5.com:610"
GAME = "https://s2112-bs.popoh5.com:8041"
UA = "Mozilla/5.0 (Linux; Android 12; itel S665L) Chrome/107 Mobile Safari/537.36"
ORIGIN = "https://dragonh5cdn.popoh5.com"
REFERER = "https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935"


def tp():
    return "".join(random.choices(string.ascii_letters + string.digits, k=7))


def open_sid(base):
    r = urllib.request.Request(base + "/socket.io/?EIO=3&transport=polling&t=" + tp())
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
    print("OPEN:", raw[:130])
    return sid


# ---- XXTEA persis JS TEA class (key = Utf8(key).slice(0,16)) ----
def str_to_longs(b):
    b = b + b"\x00" * ((4 - len(b) % 4) % 4)
    return list(struct.unpack("<%dI" % (len(b) // 4), b))


def longs_to_str(ls):
    return struct.pack("<%dI" % len(ls), *ls)


def xxtea_encrypt(data: bytes, key: bytes) -> bytes:
    n = max(1, len(data) // 4)
    v = list(struct.unpack("<%dI" % n, data + b"\x00" * ((4 - (len(data) % 4)) % 4)))
    if len(v) <= 1:
        v = [v[0] if v else 0, 0]
        n = 2
    k = str_to_longs(key[:16])
    while len(k) < 4:
        # JS: r[3] undefined -> ToInt32(undefined)=0 -> 0^x; key <16B tetap dibaca 4 longs
        k.append(0)
    DELTA = 2654435769
    rounds = 6 + 52 // n
    s = 0
    z = v[n - 1]
    for _ in range(rounds):
        s = (s + DELTA) & 0xFFFFFFFF
        e = (s >> 2) & 3
        for p in range(n):
            y = v[(p + 1) % n]
            m = (((z >> 5) ^ ((y << 2) & 0xFFFFFFFF)) + ((y >> 3) ^ ((z << 4) & 0xFFFFFFFF))) ^ ((s ^ y) + (k[(p & 3) ^ e] ^ z))
            z = v[p] = (v[p] + m) & 0xFFFFFFFF
    return longs_to_str(v)


def tea_encrypt_str(text: str, key: str) -> str:
    data = text.encode("utf-8")
    enc = xxtea_encrypt(data, key.encode("utf-8"))
    return base64.b64encode(enc).decode()


def ws_connect(base, sid):
    ws = websocket.WebSocket()
    wss = base.replace("https://", "wss://").replace("http://", "ws://")
    ws.connect(wss + "/socket.io/?EIO=3&transport=websocket&sid=" + sid,
               header=["User-Agent: " + UA, "Origin: " + ORIGIN, "Referer: " + REFERER],
               sslopt={"cert_reqs": ssl.CERT_NONE}, timeout=15)
    return ws


def frames(ws, sec, sink):
    end = time.time() + sec
    while time.time() < end:
        try:
            ws.settimeout(max(0.2, end - time.time()))
            op, data = ws.recv_data()
        except websocket.WebSocketTimeoutException:
            return None
        except Exception as e:
            sink.append("ERR %s" % e)
            return None
        t = data.decode("utf-8", "replace")
        sink.append("S<- %s" % t[:2000])
        if op == 8:
            sink.append("CLOSE")
            return None
        yield t
    return None


def probe_game():
    log = []
    sid = open_sid(GAME)
    if not sid:
        print("game open gagal")
        return
    ws = ws_connect(GAME, sid)
    ws.send("2probe")
    r = ws.recv()
    log.append("2probe -> " + r)
    print("2probe ->", r)
    ws.send("5")
    log.append("C-> 5")
    print("C-> 5")
    # tunggu "verify" challenge dari server (socket.io event)
    end = time.time() + 8
    challenge = None
    while time.time() < end:
        try:
            ws.settimeout(max(0.2, end - time.time()))
            op, data = ws.recv_data()
        except websocket.WebSocketTimeoutException:
            break
        except Exception as e:
            log.append("ERR %s" % e)
            break
        t = data.decode("utf-8", "replace")
        log.append("S<- " + t[:1500])
        print("S<-", t[:300])
        if t.startswith("42"):
            # event dari server: 42["verify","<challenge>"] atau 42<ack>...
            try:
                arr = json.loads(t[2:])
                if arr and arr[0] == "verify":
                    challenge = arr[1]
            except Exception:
                pass
        if op == 8:
            break
    print("challenge:", challenge)
    if challenge:
        enc = tea_encrypt_str(challenge, "verification")
        print("verify answer (TEA b64):", enc[:80], "...")
        ws.send('40' + json.dumps([enc]))  # placeholder — socket.io emit dgn ack: 420[..]
        log.append("coba kirim 420: " + enc[:60])
        # kirim dgn ack id 0 (persis socket.io client: 42<id>["verify",<enc>])
        ws.send('420["verify","%s"]' % enc)
        log.append('C-> 420["verify",<enc>]')
        try:
            ws.settimeout(6)
            op, data = ws.recv_data()
            t = data.decode("utf-8", "replace")
            log.append("S<- verify ack: " + t[:500])
            print("verify ACK:", t[:300])
        except Exception as e:
            log.append("verify ack err: %s" % e)
            print("verify ack err:", e)
    open("verify-artifacts/game-verify-LIVE-20261126.txt", "w").write("\n".join(log))
    print("log disimpan")


def login_game_quick():
    sid = open_sid(LOGIN)
    ws = ws_connect(LOGIN, sid)
    ws.send("2probe"); ws.recv(); ws.send("5")
    ev = '420["handler.process",{"type":"User","action":"loginGame","userId":"tester01","password":"game_origin","fromChannel":"quick","channelName":"","headImageUrl":"","nickName":"","subChannel":"","version":"1.0"}]'
    ws.send(ev)
    print("C->", ev[:120])
    try:
        ws.settimeout(7)
        op, data = ws.recv_data()
        print("S<-", data.decode("utf-8", "replace")[:600])
    except Exception as e:
        print("no reply:", e)
    ws.close()


if __name__ == "__main__":
    print("===== [A] loginGame fromChannel=quick =====")
    login_game_quick()
    print("===== [B] GAME SERVER verify =====")
    probe_game()

#!/usr/bin/env python3
# Capture ulang dengan prefix panjang terkomputasi otomatis
import http.client, ssl, json, re, threading, time

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

HOST, PORT = "login.popoh5.com", 610
results = []
active = True

def req(method, path, body=None):
    c = http.client.HTTPSConnection(HOST, PORT, timeout=30, context=ctx)
    c.request(method, path, body=body,
              headers={"Content-Type": "text/plain;charset=UTF-8", "Connection": "keep-alive"})
    r = c.getresponse()
    data = r.read()
    c.close()
    return r.status, data

def frame(pkts):
    # frame engine.io polling: <len>:<packet> digabung
    out = b""
    for p in pkts:
        b = p.encode("utf-8") if isinstance(p, str) else p
        out += str(len(b)).encode() + b":" + b
    return out

def poller(sid, tcount):
    i = 0
    while active and i < 10:
        i += 1
        try:
            st, body = req("GET", "/socket.io/?EIO=3&transport=polling&t=%d-p%d&sid=%s" % (tcount, i, sid))
            results.append(("POLL", st, body))
            if not body:
                time.sleep(0.05)
        except Exception as e:
            results.append(("POLL-ERR", 0, str(e).encode()))
            break

def event_pkt(name, obj):
    s = json.dumps(obj, separators=(",", ":"))
    pkt = '42["%s",%s]' % (name, s)
    return frame([pkt])

tcount = int(time.time() * 1000)
st, body = req("GET", "/socket.io/?EIO=3&transport=polling&t=%d-0" % tcount)
sid = json.loads(re.search(rb'0\{.*?\}', body).group(0)[1:].decode())["sid"]
print("OPEN OK, sid =", sid)
th = threading.Thread(target=poller, args=(sid, tcount), daemon=True)
th.start()
time.sleep(0.3)

st, body = req("POST", "/socket.io/?EIO=3&transport=polling&t=%d-1&sid=%s" % (tcount, sid), frame(["40"]))
print("POST 40:", st, body)
time.sleep(0.5)

variants = [
    ("User", {"type": "User", "action": "GetServerList", "userId": "audit123", "subChannel": "BS", "channel": "BS"}),
    ("GetServerList", {"type": "User", "action": "GetServerList", "userId": "audit123", "subChannel": "BS", "channel": "BS"}),
]
for i, (name, obj) in enumerate(variants):
    st, body = req("POST", "/socket.io/?EIO=3&transport=polling&t=%d-e%d&sid=%s" % (tcount, i, sid), event_pkt(name, obj))
    print("POST event '%s': %s %s" % (name, st, body[:60]))
    time.sleep(3.0)
    snap = [r for r in results if r[0] == "POLL"]
    print("   poll terakhir:", snap[-1] if snap else "(kosong)")

time.sleep(2)
active = False
time.sleep(0.3)
print("\n=== SEMUA POLL ===")
for r in results:
    print(r[0], r[1], (r[2][:300] if isinstance(r[2], bytes) else r[2]))

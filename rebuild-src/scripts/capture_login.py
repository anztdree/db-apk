#!/usr/bin/env python3
# Simulasi klien engine.io v3 polling lengkap terhadap login.popoh5.com:610
# untuk menangkap respons asli event GetServerList (kontrak 4.G)
import http.client, ssl, json, re, threading, time, sys

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

HOST, PORT = "login.popoh5.com", 610
results = []
poll_thread_active = True

def req(method, path, body=None):
    c = http.client.HTTPSConnection(HOST, PORT, timeout=30, context=ctx)
    c.request(method, path, body=body,
              headers={"Content-Type": "text/plain;charset=UTF-8", "Connection": "keep-alive"})
    r = c.getresponse()
    data = r.read()
    c.close()
    return r.status, dict(r.getheaders()), data

def poller(sid, tcount):
    global poll_thread_active
    i = 0
    while poll_thread_active and i < 8:
        i += 1
        try:
            st, hd, body = req("GET", "/socket.io/?EIO=3&transport=polling&t=%d-p%d&sid=%s" % (tcount, i, sid))
            results.append(("POLL", st, body))
            if not body:
                time.sleep(0.05)
        except Exception as e:
            results.append(("POLL-ERR", 0, str(e).encode()))
            break

tcount = int(time.time() * 1000)
st, hd, body = req("GET", "/socket.io/?EIO=3&transport=polling&t=%d-0" % tcount)
print("OPEN:", st, body[:140])
m = re.search(rb'0\{.*?\}', body)
sid = json.loads(m.group(0)[1:].decode())["sid"]
print("SID:", sid)

th = threading.Thread(target=poller, args=(sid, tcount), daemon=True)
th.start()
time.sleep(0.3)

# POST connect 40
st, hd, body = req("POST", "/socket.io/?EIO=3&transport=polling&t=%d-1&sid=%s" % (tcount, sid), "2:40")
print("POST 40:", st, body)

time.sleep(0.5)
# kirim event GetServerList (socket.io v2: 42["<event>",<data>])
payload = '60:42["User",{"type":"User","action":"GetServerList","userId":"audit123","subChannel":"BS","channel":"BS"}]'
st, hd, body = req("POST", "/socket.io/?EIO=3&transport=polling&t=%d-2&sid=%s" % (tcount, sid), payload)
print("POST event:", st, body)

time.sleep(6)
poll_thread_active = False
time.sleep(0.5)
print("\n=== HASIL POLL ===")
for r in results:
    print(r[0], r[1], r[2][:400] if isinstance(r[2], bytes) else r[2])

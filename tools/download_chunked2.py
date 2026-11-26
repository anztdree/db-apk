#!/usr/bin/env python3
"""Unduh blob LFS via Range chunked paralel — RESUMABLE (chunk disimpan
per-file di direktori kerja; aman dipanggil berulang sampai lengkap).
Usage: python3 download_chunked2.py <url> <out> <size> <sha256> <workdir> <budget_detik>"""
import hashlib, os, sys, threading, time, urllib.request

URL, OUT, SIZE, OID, WORK, BUDGET = (
    sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4], sys.argv[5],
    float(sys.argv[6]) if len(sys.argv) > 6 else 90.0)
CHUNK = 1_500_000
WORKERS = 6
os.makedirs(WORK, exist_ok=True)

def fetch_range(a, b, tries=4):
    for t in range(tries):
        try:
            r = urllib.request.Request(URL, headers={"Range": "bytes=%d-%d" % (a, b)})
            with urllib.request.urlopen(r, timeout=60) as h:
                d = h.read()
            if len(d) == b - a + 1:
                return d
        except Exception:
            pass
        time.sleep(0.3 + 0.5 * t)
    return None

pos = 0
JOBS = []
idx = 0
while pos < SIZE:
    end = min(pos + CHUNK, SIZE) - 1
    JOBS.append((idx, pos, end))
    idx += 1
    pos = end + 1

todo = []
for j in JOBS:
    p = os.path.join(WORK, "c%05d" % j[0])
    if not (os.path.exists(p) and os.path.getsize(p) == j[2] - j[1] + 1):
        todo.append(j)
print("total %d chunk, sisa %d" % (len(JOBS), len(todo)), flush=True)
if not todo:
    # rakit
    h = hashlib.sha256()
    with open(OUT + ".part", "wb") as f:
        for j in JOBS:
            b = open(os.path.join(WORK, "c%05d" % j[0]), "rb").read()
            f.write(b)
            h.update(b)
    digest = h.hexdigest()
    assert digest == OID, "SHA256 tidak cocok: %s" % digest
    os.rename(OUT + ".part", OUT)
    print("OK %s sha256 cocok" % OUT)
    sys.exit(0)

t0 = time.time()
lock = threading.Lock()
cursor = [0]

def worker(_wid):
    while True:
        with lock:
            if cursor[0] >= len(todo):
                return
            if time.time() - t0 > BUDGET:
                return
            j = todo[cursor[0]]
            cursor[0] += 1
        data = fetch_range(j[1], j[2])
        if data is None:
            return  # biarkan chunk diambil panggilan berikutnya
        with open(os.path.join(WORK, "c%05d" % j[0]), "wb") as f:
            f.write(data)

threads = [threading.Thread(target=worker, args=(w,), daemon=True) for w in range(WORKERS)]
for t in threads:
    t.start()
for t in threads:
    t.join(timeout=BUDGET + 30)
got = len(JOBS) - len(todo) + sum(
    1 for j in JOBS if os.path.exists(os.path.join(WORK, "c%05d" % j[0])))
print("selesai panggilan ini: %d/%d chunk (%.0f detik)" % (got, len(JOBS), time.time() - t0))

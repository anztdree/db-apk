#!/usr/bin/env python3
# Lab pembuktian all.zip terpotong — replika semantik MiniServer.smali
# Server: baca request byte-per-byte HANYA baris pertama (persis readLine smali),
#         tulis header+body penuh (sendall = write blocking penuh), lalu close().
# FIX   : sebelum menjawab, habiskan sisa header request; sebelum close, shutdownOutput.
# Klien : header request PERSIS seperti HAR user (entri all.zip).
import socket, sys, time, threading

BODY = open('/home/z/build2/staging/assets/mirror/upgrade/all.zip', 'rb').read()
PORT = int(sys.argv[1])
FIX = sys.argv[2] == 'fix'
SLOW = (len(sys.argv) > 3 and sys.argv[3] == 'slow')

REQ = (b"GET /upgrade/all.zip?v=11011390 HTTP/1.1\r\n"
       b"User-Agent: Dalvik/2.1.0 (Linux; U; Android 12; itel S665L Build/SP1A.210812.016)\r\n"
       b"Host: 127.0.0.1:28110\r\n"
       b"Connection: Keep-Alive\r\n"
       b"Accept-Encoding: gzip\r\n\r\n")

HDR = (b"HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n"
       b"Content-Length: " + str(len(BODY)).encode() + b"\r\nConnection: close\r\n\r\n")


def readline_byte(f):
    # replika 1:1 readLine MiniServer.smali: read() satu byte per syscall
    sb = b''
    while True:
        c = f.read(1)
        if not c:
            return sb.decode('latin-1') if sb else None
        if c == b'\n':
            return sb.decode('latin-1')
        if c == b'\r':
            continue
        sb += c


def handle(conn):
    try:
        conn.settimeout(20)
        f = conn.makefile('rb', buffering=0)
        line = readline_byte(f)          # HANYA baris pertama — perilaku sekarang
        if FIX:
            while True:                   # habiskan sisa header sampai baris kosong
                l = readline_byte(f)
                if l is None or l == '':
                    break
        conn.sendall(HDR)
        conn.sendall(BODY)                # write penuh 19.385.098 B (blocking)
        print('[srv] %s -> seluruh %d B ditulis+flush%s' % (
            line, len(BODY), ' + shutdownOutput' if FIX else ' + close() langsung'), flush=True)
    except Exception as e:
        print('[srv] write error:', e, flush=True)
    finally:
        if FIX:
            try:
                conn.shutdown(socket.SHUT_WR)
            except Exception:
                pass
        try:
            conn.close()
        except Exception:
            pass


def server():
    ss = socket.socket()
    ss.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    ss.bind(('127.0.0.1', PORT))
    ss.listen(8)
    while True:
        c, _ = ss.accept()
        threading.Thread(target=handle, args=(c,), daemon=True).start()


def client(n):
    cl, got, err = -1, 0, None
    s = socket.socket()
    s.settimeout(30)
    try:
        s.connect(('127.0.0.1', PORT))
        s.sendall(REQ)
        buf = b''
        while not buf.endswith(b'\r\n\r\n'):
            c = s.recv(1)
            if not c:
                break
            buf += c
        cl = int(buf.split(b'Content-Length: ')[1].split(b'\r\n')[0])
        blk = 0
        while True:
            r = s.recv(1 << 16)
            if not r:
                break
            got += len(r)
            if SLOW:
                blk += 1
                if blk % 8 == 0:
                    time.sleep(0.04)      # pembaca lambat ~13 MB/s, mirip perangkat
    except Exception as e:
        err = type(e).__name__ + ': ' + str(e)
    finally:
        s.close()
    if got == cl:
        print('[cli#%d] Content-Length=%d | diterima=%d => UTUH' % (n, cl, got), flush=True)
    else:
        print('[cli#%d] Content-Length=%d | diterima=%d => TERPOTONG (kurang %d B)%s' % (
            n, cl, got, cl - got if cl > 0 else 0, ('  [%s]' % err) if err else ''), flush=True)


if __name__ == '__main__':
    print('=== mode=%s | body=%d B | klien=%s ===' % (
        'FIX (drain header + shutdownOutput)' if FIX else 'SEKARANG (logika MiniServer test5)',
        len(BODY), 'lambat' if SLOW else 'cepat'), flush=True)
    threading.Thread(target=server, daemon=True).start()
    time.sleep(0.4)
    for i in range(1, 4):
        client(i)
        time.sleep(0.2)

#!/usr/bin/env python3
"""Lab uji drain body POST — bukti semantik lama vs baru (test10, Pelajaran 6).

Latar (Pelajaran 5/test5): close() dengan data request yang belum terbaca di
socket server memicu TCP RST (bukan FIN) → response yang masih dalam antrean/
in-flight dimusnahkan di sisi klien. test6 memperbaiki kasus HEADER (drain
sampai baris kosong). test10 menghadapi POST SDK: SETELAH baris kosong masih
ada BODY (Content-Length byte, bukti HAR sdk_register.har: "Content-Length:
1249") — semantik lama tidak membacanya → RST berisiko memusnahkan response
SDK (764 B pun bisa hilang bila RST datang sebelum klien membaca).

Replika semantik 1:1 MiniServer.handle():
  mode lama : readLine request line → drain header sampai baris kosong/EOF
              → pilih response → tulis → shutdownOutput → close
  mode baru : sama + parse "Content-Length:" (case-insensitive, parseCL)
              → baca persis N byte body → baru pilih response

Klien: POST persis kontrak SDK (header HAR: Content-Type x-www-form-urlencoded,
Content-Length 1249, User-Agent Dalvik) — body dikirim bertahap (3 potong,
selang 30 ms) supaya sebagian body baru tiba SETELAH server "selesai" baca;
lalu tidur 150 ms (mensimulasikan jeda app di perangkat sebelum membaca
response) dan baru membaca response sampai EOF.

Hasil diharapkan (dan terukur):
  lama → ConnectionResetError / body terpotong (kalah race dgn RST)
  baru → response utuh byte-identik panenan, 5/5

Pakai: python3 lab_post.py [panjang_uji]
Default 5 iterasi per mode. Body/response memakai byte ASLI:
  body     = ../panenan-1.0.2/req_init.txt          (1.249 B, HAR)
  response = ../../apk/mirror/qsdk/v1/system/init   (764 B, panenan RAW)
"""
import socket
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BODY = (ROOT / "../../docs/panenan-1.0.2/req_init.txt").read_bytes()
RESP = (ROOT / "../../apk/mirror/qsdk/v1/system/init").read_bytes()
RESP_ORIG_SHA = "a6ab447041310530777dd2bf2b0bad0935aaf16080140ff9fe5196cbbe601276"

PORT_OLD, PORT_NEW = 28281, 28282


# ---------------------------------------------------------------- replika ----
def read_line(sock):
    """1:1 MiniServer.readLine: byte-per-byte, CR diabaikan, berhenti di LF."""
    buf = []
    while True:
        b = sock.recv(1)
        if not b:
            if not buf:
                return None
            break
        c = b[0]
        if c == 0x0A:
            break
        if c == 0x0D:
            continue
        buf.append(chr(c))
    return "".join(buf)


def serve_once(sock_old, mode):
    """Satu koneksi, semantik handle() lama / baru."""
    conn, _ = sock_old.accept()
    try:
        conn.settimeout(8.0)
        line = read_line(conn)  # request line
        content_length = 0
        while True:  # drain header
            h = read_line(conn)
            if h is None or h == "":
                break
            if mode == "new" and h.lower().startswith("content-length:"):
                try:
                    content_length = int(h[15:].strip())
                except ValueError:
                    content_length = 0
        if mode == "new":  # drain body per Content-Length (fix test10)
            while content_length > 0:
                b = conn.recv(1)
                if not b:
                    break
                content_length -= 1
        # respond(path ditemukan) — pola respond() 1:1
        head = ("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n"
                f"Content-Length: {len(RESP)}\r\nConnection: close\r\n\r\n")
        conn.sendall(head.encode() + RESP)
    finally:
        conn.shutdown(socket.SHUT_WR)  # fix test6: shutdownOutput sebelum close
        conn.close()


def start_server(port, mode):
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", port))
    srv.listen(4)
    def loop():
        while True:
            try:
                serve_once(srv, mode)
            except OSError:
                pass
    threading.Thread(target=loop, daemon=True).start()
    return srv


# ------------------------------------------------------------------ klien ----
def client_post(port):
    """POST kontrak SDK persis HAR; baca response SETELAH jeda 150 ms."""
    req = ("POST /qsdk/v1/system/init HTTP/1.1\r\n"
           "Content-Type: application/x-www-form-urlencoded\r\n"
           f"Content-Length: {len(BODY)}\r\n"
           "User-Agent: Dalvik/2.1.0 (Linux; U; Android 12; itel S665L)\r\n"
           "Host: 127.0.0.1\r\n"
           "Connection: Keep-Alive\r\n"
           "Accept-Encoding: gzip\r\n\r\n").encode()
    s = socket.create_connection(("127.0.0.1", port), timeout=8)
    err = None
    try:
        s.sendall(req)
        for i in range(3):  # body bertahap — sebagian tiba setelah server membaca
            s.sendall(BODY[i * len(BODY) // 3:(i + 1) * len(BODY) // 3])
            time.sleep(0.03)
    except OSError as e:  # RST saat mengirim = response pasti musnah
        err = e
    time.sleep(0.15)  # jeda "perangkat" sebelum membaca response
    data = b""
    try:
        while True:
            b = s.recv(4096)
            if not b:
                break
            data += b
    except OSError as e:
        err = e
    finally:
        s.close()
    # pisahkan header, hitung body sesuai Content-Length response
    body_ok = False
    if b"\r\n\r\n" in data:
        hdr, bbody = data.split(b"\r\n\r\n", 1)
        cl = next((int(x.split(b":")[1]) for x in hdr.split(b"\r\n")
                   if x.lower().startswith(b"content-length:")), None)
        body_ok = cl is not None and len(bbody) >= cl and bbody[:cl] == RESP
    # sukses sejati = body utuh DAN tanpa error koneksi apa pun
    # (BrokenPipe saat kirim = IOException di tengah upload POST — di perangkat
    #  itu tetap jalur gagal SDK walau body response sempat tiba)
    return body_ok and err is None, len(data), err


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 5
    assert len(RESP) == 764, "response harus panenan init 764 B"
    start_server(PORT_OLD, "old")
    start_server(PORT_NEW, "new")
    time.sleep(0.2)
    for name, port in (("LAMA (tanpa drain body)", PORT_OLD),
                       ("BARU (drain body test10)", PORT_NEW)):
        ok = 0
        for i in range(n):
            good, size, err = client_post(port)
            ok += good
            print(f"  {name} iterasi {i+1}: "
                  f"{'UTUH' if good else 'RUSAK'} "
                  f"({size} B terbaca{', err=' + repr(err) if err else ''})")
        print(f"{name}: {ok}/{n} utuh\n")


if __name__ == "__main__":
    main()

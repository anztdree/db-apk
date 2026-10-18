#!/usr/bin/env python3
"""Generator setting_BS_Android.bin versi "penyambung".

Membaca file asli server (configus/setting_BS_Android.bin, XOR kunci "DragonBall"),
mengganti HANYA field `url` dan `update` agar menunjuk ke server lokal
http://127.0.0.1:28110 (host diganti, path + query dipertahankan persis),
lalu meng-encode ulang dengan XOR yang sama. Field lain tidak disentuh (1:1).

Pakai: python3 make_setting_bin.py <file_asli.bin> <keluaran.bin>
"""
import json
import sys
import urllib.parse

KEY = b"DragonBall"
LOCAL_BASE = "http://127.0.0.1:28110"


def xor(data: bytes, key: bytes) -> bytes:
    return bytes(b ^ key[i % len(key)] for i, b in enumerate(data))


def rebase(url: str) -> str:
    p = urllib.parse.urlsplit(url)
    return LOCAL_BASE + p.path + (("?" + p.query) if p.query else "")


def main(src: str, dst: str) -> None:
    raw = open(src, "rb").read()
    text = xor(raw, KEY).decode("utf-8")
    cfg = json.loads(text)

    old_url, old_upd = cfg["url"], cfg["update"]
    new_url, new_upd = rebase(old_url), rebase(old_upd)
    assert old_url in text and old_upd in text, "url/update tidak ditemukan utuh di teks asli"

    text2 = text.replace(old_url, new_url).replace(old_upd, new_upd)
    json.loads(text2)  # validasi tetap JSON valid
    open(dst, "wb").write(xor(text2.encode("utf-8"), KEY))

    print("url   :", old_url, "->", new_url)
    print("update:", old_upd, "->", new_upd)
    check = xor(open(dst, "rb").read(), KEY).decode("utf-8")
    assert check == text2
    print("round-trip OK; field lain byte-asli:",
          all(json.loads(check)[k] == cfg[k] for k in cfg if k not in ("url", "update")))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

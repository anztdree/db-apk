#!/usr/bin/env python3
# Ambil blob Git LFS dari GitHub via batch API (tanpa git-lfs binary).
# Usage: python3 fetch_lfs.py <path-pointer> <path-output>
import base64, hashlib, json, os, sys, urllib.request

PAT = [l for l in open('/home/z/my-project/.github-credentials.txt')
       if l.startswith('PAT')][0].split(':', 1)[1].strip()

def fetch(pointer_path, out_path):
    oid, size = None, None
    for l in open(pointer_path).read().strip().splitlines():
        l = l.strip()
        if l.startswith('oid sha256:'): oid = l[len('oid sha256:'):]
        elif l.startswith('size '): size = int(l[len('size '):])
    assert oid and size, 'pointer LFS tidak terbaca'
    req = urllib.request.Request(
        'https://github.com/anztdree/db-apk.git/info/lfs/objects/batch',
        data=json.dumps({'objects': [{'oid': oid, 'size': size}],
                         'operation': 'download'}).encode(),
        method='POST')
    import base64 as _b64
    req.add_header('Authorization', 'Basic ' + _b64.b64encode(
        ('x-access-token:' + PAT).encode()).decode())
    req.add_header('Accept', 'application/vnd.git-lfs+json')
    req.add_header('Content-Type', 'application/vnd.git-lfs+json')
    with urllib.request.urlopen(req, timeout=60) as r:
        j = json.loads(r.read())
    href = j['objects'][0]['actions']['download']['href']
    print('unduh', out_path, '(%d B) …' % size)
    urllib.request.urlretrieve(href, out_path + '.part')
    os.rename(out_path + '.part', out_path)
    h = hashlib.sha256()
    with open(out_path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    assert h.hexdigest() == oid, 'SHA256 TIDAK COCOK: %s' % h.hexdigest()
    print('OK %s sha256 %s verifikasi cocok' % (out_path, oid[:16]))

if __name__ == '__main__':
    fetch(sys.argv[1], sys.argv[2])

#!/usr/bin/env python3
# Unggah blob Git LFS ke GitHub via batch API (tanpa git-lfs binary).
# Usage: python3 upload_lfs.py <path-file> <path-pointer-repo>
# Pointer di path-pointer-repo diperbarui (version/oid/size) setelah unggah sukses.
import base64, hashlib, json, os, sys, urllib.request

PAT = [l for l in open('/home/z/my-project/.github-credentials.txt')
       if l.startswith('PAT')][0].split(':', 1)[1].strip()

def upload(file_path, pointer_path):
    data = open(file_path, 'rb').read()
    size = len(data)
    oid = hashlib.sha256(data).hexdigest()
    req = urllib.request.Request(
        'https://github.com/anztdree/db-apk.git/info/lfs/objects/batch',
        data=json.dumps({'objects': [{'oid': oid, 'size': size, 'user_defined': {}}],
                         'operation': 'upload'}).encode(),
        method='POST')
    req.add_header('Authorization', 'Basic ' + base64.b64encode(
        ('x-access-token:' + PAT).encode()).decode())
    req.add_header('Accept', 'application/vnd.git-lfs+json')
    req.add_header('Content-Type', 'application/vnd.git-lfs+json')
    with urllib.request.urlopen(req, timeout=60) as r:
        j = json.loads(r.read())
    obj = j['objects'][0]
    if obj.get('error'):
        raise SystemExit('LFS batch error: %s' % obj['error'])
    act = obj.get('actions') or {}
    up = act.get('upload')
    if up is None:
        print('objek SUDAH ada di LFS (tanpa action upload) — lanjut verifikasi')
    else:
        href = up['href']
        headers = up.get('header', {})
        print('unggah %s (%d B) …' % (oid[:16], size))
        req2 = urllib.request.Request(href, data=data, method='PUT')
        for k, v in headers.items():
            req2.add_header(k, v)
        with urllib.request.urlopen(req2, timeout=1800) as r:
            code = r.getcode()
        print('PUT selesai: HTTP %s' % code)
        assert code in (200, 201), 'PUT gagal'
    ver = act.get('verify')
    if ver:
        rv = urllib.request.Request(ver['href'], data=json.dumps(
            {'oid': oid, 'size': size}).encode(), method='POST')
        for k, v in ver.get('header', {}).items():
            rv.add_header(k, v)
        rv.add_header('Content-Type', 'application/vnd.git-lfs+json')
        with urllib.request.urlopen(rv, timeout=60) as r:
            print('verify: HTTP %s' % r.getcode())
    with open(pointer_path, 'w') as f:
        f.write('version https://git-lfs.github.com/spec/v1\n'
                'oid sha256:%s\nsize %d\n' % (oid, size))
    print('pointer diperbarui: %s (oid sha256:%s, size %d)' % (pointer_path, oid, size))

if __name__ == '__main__':
    upload(sys.argv[1], sys.argv[2])

#!/usr/bin/env python3
# Lab uji logika bootstrapSync/copyDir MiniServer test7 — port setia dari smali.
# Semantik sama: copy-if-missing, tmp+rename atomik, cap deadline (static field), catch-all.
import os, sys, time, shutil, hashlib

SEED = '/home/z/build2/staging/assets/mirror'
DEADLINE = float('inf')          # ekuivalen MiniServer.DEADLINE:J

class FakeAM:                    # ekivalen AssetManager
    def list(self, path):        # list() folder -> [nama]; list() file -> []
        rel = path[len('mirror/'):] if path.startswith('mirror/') and len(path) > 6 else ''
        p = os.path.join(SEED, rel)
        if not os.path.isdir(p):
            return []
        return sorted(os.listdir(p))
    def open(self, path):
        rel = path[len('mirror/'):]
        return open(os.path.join(SEED, rel), 'rb')

def now_ms():
    return time.time() * 1000.0

def copy_dir(am, asset_path, dst_dir, fail_hook=None):
    count = 0
    try:                                          # :try_all  (.catch Throwable)
        if now_ms() > DEADLINE:                   # cek deadline awal
            return count
        kids = am.list(asset_path)                # AssetManager.list
        if kids is None:
            return count
        if len(kids) > 0:                         # DIREKTORI
            i = 0
            while i < len(kids):                  # :loop_kids
                kid = kids[i]
                child_asset = asset_path + '/' + kid
                child_dst = os.path.join(dst_dir, kid)
                count += copy_dir(am, child_asset, child_dst, fail_hook)
                i += 1
            return count
        # :is_file — FILE
        if now_ms() > DEADLINE:                   # cek deadline per-file
            return count
        if os.path.exists(dst_dir):               # copy-if-missing
            return count
        parent = os.path.dirname(dst_dir)
        if parent:
            os.makedirs(parent, exist_ok=True)    # parentFile.mkdirs()
        tmp = str(dst_dir) + '.tmp'               # File(str(p2) + ".tmp")
        with am.open(asset_path) as src:          # AssetManager.open
            with open(tmp, 'wb') as out:          # FileOutputStream(tmp)
                while True:                       # :loop_copy (0x2000)
                    b = src.read(0x2000)
                    if b == b'':
                        break
                    if fail_hook and fail_hook(asset_path, len(b)):
                        raise IOError('injected failure')
                    out.write(b)
        try:                                      # renameTo -> atomic (File.renameTo: boolean)
            os.rename(tmp, dst_dir)
            count += 1
        except OSError:
            pass
        return count
    except Exception:                             # :catch_all
        return count

def tree_sha(root):
    h = hashlib.sha256()
    for dp, _, fns in sorted(os.walk(root)):
        for fn in sorted(fns):
            with open(os.path.join(dp, fn), 'rb') as f:
                for chunk in iter(lambda: f.read(0x2000), b''):
                    h.update(chunk)
    return h.hexdigest()

def main():
    base = '/tmp/lab7/target'
    global DEADLINE
    ok_all = True
    def check(name, cond, detail=''):
        print(('[OK]   ' if cond else '[GAGAL] ') + name + (' | ' + detail if detail else ''))
        return cond

    # KASUS 1: fresh -> 26 file, identik seed
    shutil.rmtree(base, ignore_errors=True)
    n = copy_dir(FakeAM(), 'mirror', base)
    ok_all &= check('K1 fresh: jumlah salinan', n == 26, f'n={n}')
    ok_all &= check('K1 identik seed (tree-SHA)', tree_sha(base) == tree_sha(SEED))

    # KASUS 2: run kedua -> idempoten
    n = copy_dir(FakeAM(), 'mirror', base)
    ok_all &= check('K2 run kedua: 0 disalin', n == 0, f'n={n}')

    # KASUS 3: file override user TIDAK ditimpa
    ov = os.path.join(base, 'bs/resource/properties/clientversion.json')
    open(ov, 'w').write('{"clientVersion":"OVERRIDE-USER"}')
    n = copy_dir(FakeAM(), 'mirror', base)
    ok_all &= check('K3 override: 0 disalin', n == 0, f'n={n}')
    ok_all &= check('K3 isi override utuh', 'OVERRIDE-USER' in open(ov).read())

    # KASUS 4: file dihapus -> dipulihkan dari seed
    snd = os.path.join(base, 'bs/resource/assets/image/public/sound/sound_click.mp3')
    os.remove(snd)
    n = copy_dir(FakeAM(), 'mirror', base)
    ok_all &= check('K4 restorasi: 1 disalin', n == 1, f'n={n}')
    ok_all &= check('K4 SHA pulih = seed',
        hashlib.sha256(open(snd,'rb').read()).hexdigest() ==
        hashlib.sha256(open(os.path.join(SEED,'bs/resource/assets/image/public/sound/sound_click.mp3'),'rb').read()).hexdigest())

    # KASUS 5: kegagalan tengah salin -> TIDAK ada file setengah di tujuan; run berikut sukses
    shutil.rmtree(base, ignore_errors=True)
    state = {'n': 0}
    def hook(path, nbytes):
        state['n'] += nbytes
        return path.endswith('all.zip') and state['n'] > 4096   # gagal di tengah all.zip
    n = copy_dir(FakeAM(), 'mirror', base, fail_hook=hook)
    dst_allzip = os.path.join(base, 'upgrade/all.zip')
    ok_all &= check('K5 gagal tengah: count tidak penuh', n < 26, f'n={n}')
    ok_all &= check('K5 tujuan all.zip TIDAK ADA (tanpa file setengah)', not os.path.exists(dst_allzip))
    # run normal berikutnya: tmp ditimpa, rename sukses
    n2 = copy_dir(FakeAM(), 'mirror', base)
    ok_all &= check('K5 run berikut: melengkapi', n2 >= 1, f'n2={n2}')
    ok_all &= check('K5 all.zip akhir = SHA seed',
        hashlib.sha256(open(dst_allzip,'rb').read()).hexdigest() ==
        hashlib.sha256(open(os.path.join(SEED,'upgrade/all.zip'),'rb').read()).hexdigest())

    # KASUS 6: deadline lewat -> 0 disalin, tanpa hang
    DEADLINE = 0.0
    t0 = time.time()
    n = copy_dir(FakeAM(), 'mirror', base + '_x')
    dt = time.time() - t0
    ok_all &= check('K6 deadline lewat: 0 disalin & cepat', n == 0 and dt < 0.1, f'n={n} dt={dt:.3f}s')

    print()
    print('HASIL AKHIR:', 'SEMUA KASUS LULUS' if ok_all else 'ADA KASUS GAGAL')
    sys.exit(0 if ok_all else 1)

if __name__ == '__main__':
    main()

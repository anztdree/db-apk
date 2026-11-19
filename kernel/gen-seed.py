#!/usr/bin/env python3
# ============================================================
# GEN-SEED — generate Seed.java dari folder zone-seed/ (SUMBER TUNGGAL).
# Seed tertanam di dex: file device menang (Seed hanya isi yang BELUM ADA).
# Root: Android/data/com.db.local/server (SEBELAH folder files/ — Hukum Root).
# ============================================================
import os, json

HERE = os.path.dirname(os.path.abspath(__file__))
SEED = os.path.join(HERE, 'zone-seed')
OUT = os.path.join(HERE, 'src/s2/kernel/Seed.java')

MAPPING = [
    ('config/serverList.json',            'config/serverList.json'),
    ('config/setting.json',               'config/setting.json'),
    ('config/version/base.version',       'config/version/base.version'),
    ('config/version/resource.version',   'config/version/resource.version'),
    ('config/versionbin.json',            'config/versionbin.json'),
    ('config/starter-save.json',          'config/starter-save.json'),
    ('engine.json',                       'engine.json'),
    ('handlers/10-tier1-config.json',     'handlers/10-tier1-config.json'),
    ('handlers/20-tier1-sdk.json',        'handlers/20-tier1-sdk.json'),
    ('handlers/30-tier1-game.json',       'handlers/30-tier1-game.json'),
    ('handlers/90-tier2-example.js',      'handlers/90-tier2-example.js'),
    ('handlers/lib/strings.js',           'handlers/lib/strings.js'),
    ('static-README.md',                  'static/README.md'),
    ('data/seed.json',                    'data/seed.json'),
]

def jstr(s):
    """escape jadi literal string Java"""
    out = []
    for ch in s:
        if ch == '\\': out.append('\\\\')
        elif ch == '"': out.append('\\"')
        elif ch == '\n': out.append('\\n')
        elif ch == '\r': out.append('\\r')
        elif ch == '\t': out.append('\\t')
        else: out.append(ch)
    return '"' + ''.join(out) + '"'

paths, contents = [], []
for disk, zone in MAPPING:
    full = os.path.join(SEED, disk)
    with open(full, 'r', encoding='utf-8') as f:
        text = f.read()
    # validasi JSON agar build dex tidak menanam zona rusak
    if disk.endswith('.json'):
        json.loads(text)
    paths.append(jstr(zone))
    contents.append(jstr(text))

# pastikan urutan PATHS == CONTENTS
assert len(paths) == len(contents)

lines = []
lines.append('// ============================================================')
lines.append('// SEED — zona server TERTANAM DI DALAM DEX (NOL file di assets/).')
lines.append('// Karena tidak ada asset server di APK, extractor client tidak akan')
lines.append('// pernah menaruh apa pun ke folder files/. Seed hanya mengisi file')
lines.append('// yang BELUM ADA di root (file device menang — editan owner tidak')
lines.append('// pernah ditimpa). Root = SEBELAH folder files/ (K.root() =')
lines.append('// Android/data/com.db.local/server). SUMBER TUNGGAL: zone-seed/ —')
lines.append('// file di sini adalah kebenaran; Seed.java di-generate (gen-seed.py).')
lines.append('// ============================================================')
lines.append('package s2.kernel;')
lines.append('')
lines.append('public final class Seed {')
lines.append('    private Seed() {}')
lines.append('')
lines.append('    private static final String[] PATHS = {')
for i, p in enumerate(paths):
    sep = ',' if i < len(paths) - 1 else ''
    lines.append('            ' + p + sep)
lines.append('    };')
lines.append('')
lines.append('    private static final String[] CONTENTS = {')
for i, c in enumerate(contents):
    sep = ',' if i < len(contents) - 1 else ''
    lines.append('            ' + c + sep)
lines.append('    };')
lines.append('')
lines.append('    /** tanam zona ke root bila file belum ada. return jumlah file yang ditulis. */')
lines.append('    public static int apply(java.io.File root) {')
lines.append('        int written = 0;')
lines.append('        for (int i = 0; i < PATHS.length; i++) {')
lines.append('            try {')
lines.append('                java.io.File f = new java.io.File(root, PATHS[i]);')
lines.append('                if (f.exists()) continue; // device menang')
lines.append('                java.io.File p = f.getParentFile();')
lines.append('                if (p != null && !p.exists()) p.mkdirs();')
lines.append('                if (Util.writeTextFile(f, CONTENTS[i])) written++;')
lines.append('            } catch (Throwable ignore) {')
lines.append('                // anti-FC')
lines.append('            }')
lines.append('        }')
lines.append('        return written;')
lines.append('    }')
lines.append('}')
with open(OUT, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')
print(f'Seed.java OK: {len(paths)} file zona ({sum(len(c) for c in contents)} B tanaman)')

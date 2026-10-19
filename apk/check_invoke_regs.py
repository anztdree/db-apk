#!/usr/bin/env python3
"""
check_invoke_regs.py — pemeriksa mekanik source smali (anti VerifyError di perangkat).

Latar: FC test7/test8 di perangkat user = java.lang.VerifyError dari verifier ART:
  bootstrapSync [0x9] "Rejecting invocation, expected 1 argument registers,
  method signature has 2 or more"
  -> invoke-virtual {p0} ...getExternalFilesDir(Ljava/lang/String;)... tanpa argumen
     String (butuh 2 register: this + null String). smali tidak memverifikasi ini;
     assemble+baksmali roundtrip pun TIDAK menangkapnya. Verifier ART di perangkat
     yang menolak, di onCreate, sebelum aplikasi sempat jalan.

Yang diperiksa (semua method, semua file smali yang diberikan):
  1. invoke-*/invoke-*-range : jumlah register yang diberi HARUS sama dengan
     kebutuhan signature (method instance: +1 utk this; tipe J/D = 2 register;
     array [J/[D = 1 register = referensi objek).
  2. Setiap register vN/pN yang dipakai harus berada dalam rentang total register
     metode (.locals N + register parameter, atau .registers M).
  3. pN tidak boleh melebihi jumlah register parameter.
  4. move-result* harus tepat setelah invoke-* / new-instance / filled-new-array.

Exit 0 = bersih; exit 1 = ada temuan (dicetak per lokasi).
"""
import re
import sys

INVOKE_RE = re.compile(r'^(invoke-[\w/-]+)\s+\{([^}]*)\}\s*,\s*(.*)$')
REG_TOKEN_RE = re.compile(r'\b([vp])(\d+)\b')
MOVE_RESULT_RE = re.compile(r'^(move-result(?:-wide|-object)?)\b')
PREDECESSOR_OK_RE = re.compile(r'^(invoke-|new-instance |filled-new-array)')

WIDE_PRIM = ('J', 'D')


def split_params(desc):
    """desc contoh: (II[JLjava/lang/String;)V -> list tipe parameter."""
    assert desc.startswith('('), desc
    params, i = [], 1
    while desc[i] != ')':
        j = i
        while desc[j] == '[':
            j += 1
        if desc[j] == 'L':
            j = desc.index(';', j) + 1
        else:
            j += 1
        params.append(desc[i:j])
        i = j
    return params


def reg_size(t):
    # Hanya J/D primitif non-array yang wide (2 register).
    return 2 if t in WIDE_PRIM else 1


def required_regs(desc, is_static):
    n = sum(reg_size(p) for p in split_params(desc))
    return n + (0 if is_static else 1)


def method_param_regs(sig, is_static):
    params = split_params(sig.split(')')[0] + ')')
    return sum(reg_size(p) for p in params) + (0 if is_static else 1)


def normalize(reg_str, n_locals):
    """Kembalikan (list register absolut, is_range, raw)."""
    reg_str = reg_str.strip()
    if '..' in reg_str:
        a, b = [x.strip() for x in reg_str.split('..')]
        regs = [to_abs(a, n_locals), to_abs(b, n_locals)]
        if regs[1] < regs[0]:
            raise ValueError('range terbalik: %s' % reg_str)
        return list(range(regs[0], regs[1] + 1)), True
    regs = [to_abs(x.strip(), n_locals) for x in reg_str.split(',') if x.strip()]
    return regs, False


def to_abs(tok, n_locals):
    m = re.fullmatch(r'([vp])(\d+)', tok.strip())
    if not m:
        raise ValueError('register tak dikenal: %r' % tok)
    kind, num = m.group(1), int(m.group(2))
    return num + n_locals if kind == 'p' else num


def strip_noise(line):
    line = re.sub(r'"[^"]*"', '""', line)          # string literal
    line = re.sub(r'L[\w/$-]+;->\S*', 'REF ', line)  # method/field ref
    return line


def check_file(path):
    findings = []
    with open(path, 'r', encoding='utf-8') as f:
        lines = f.read().splitlines()

    cur = None  # dict state metode berjalan
    prev_insn = ''
    for lineno, raw in enumerate(lines, 1):
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        if line.startswith('.end method'):
            cur = None
            prev_insn = ''
            continue
        if line.startswith('.method'):
            m = re.match(r'^\.method\s+(?:[\w-]+\s+)*?(static\s+)?[\w$<>\[\]/]+(\(.*?\)\S+)\s*$', line)
            if not m:
                findings.append((path, lineno, 'header tak terurai: ' + line))
                cur = None
                continue
            is_static = bool(m.group(1))
            sig = m.group(2)
            body = lines[lineno:]  # cari .locals/.registers setelah header
            n_locals, total = None, None
            for l2 in body[:40]:
                s = l2.strip()
                if s.startswith('.locals'):
                    n_locals = int(s.split()[1])
                    break
                if s.startswith('.registers'):
                    total = int(s.split()[1])
                    break
                if s.startswith('.method'):
                    break
            p_regs = method_param_regs(sig, is_static)
            if total is None:
                if n_locals is None:
                    findings.append((path, lineno, 'tanpa .locals/.registers: ' + line))
                    cur = None
                    continue
                total = n_locals + p_regs
            else:
                n_locals = total - p_regs
            cur = dict(name=line, n_locals=n_locals, total=total,
                       p_regs=p_regs, is_static=is_static)
            prev_insn = ''
            continue

        if cur is None or line.startswith('.'):
            continue

        # --- pemeriksaan invoke ---
        m = INVOKE_RE.match(line)
        if m:
            op, reglist, ref = m.group(1), m.group(2), m.group(3)
            try:
                regs, is_range = normalize(reglist, cur['n_locals'])
            except ValueError as e:
                findings.append((path, lineno, str(e) + ' | ' + line))
                prev_insn = line
                continue
            paren = ref.find('(')
            close = ref.find(')', paren)
            if paren < 0 or close < 0:
                findings.append((path, lineno, 'ref tanpa descriptor: ' + line))
                prev_insn = line
                continue
            desc = ref[paren:close + 1]
            try:
                need = required_regs(desc, op in ('invoke-static', 'invoke-static/range'))
            except Exception as e:
                findings.append((path, lineno, 'descriptor gagal diurai %r: %s' % (desc, e)))
                prev_insn = line
                continue
            if len(regs) != need:
                findings.append((path, lineno,
                                 '%s memberi %d register %s tapi signature butuh %d (%s%s)'
                                 % (op, len(regs), reglist, need, ref.split('->')[-1], desc)))
            for r in regs:
                if r >= cur['total']:
                    findings.append((path, lineno,
                                     'register v%d di luar rentang (total %d): %s' % (r, cur['total'], line)))
            prev_insn = line
            continue

        # --- move-result* harus tepat setelah invoke/new-instance ---
        if MOVE_RESULT_RE.match(line) and not PREDECESSOR_OK_RE.match(prev_insn):
            findings.append((path, lineno,
                             'move-result tidak setelah invoke/new-instance (sebelumnya: %s)' % prev_insn))

        # --- batas register utk semua instruksi lain ---
        for kind, num in REG_TOKEN_RE.findall(strip_noise(line)):
            r = int(num) + (cur['n_locals'] if kind == 'p' else 0)
            if kind == 'p' and int(num) >= cur['p_regs']:
                findings.append((path, lineno,
                                 'p%d tidak valid (metode punya %d register param): %s' % (int(num), cur['p_regs'], line)))
            elif r >= cur['total']:
                findings.append((path, lineno,
                                 'register %s%d di luar rentang (total %d): %s' % (kind, int(num), cur['total'], line)))
        prev_insn = line
    return findings


def main():
    all_findings = []
    for path in sys.argv[1:]:
        all_findings.extend(check_file(path))
    if all_findings:
        print('TEMUAN (%d):' % len(all_findings))
        for path, lineno, msg in all_findings:
            print('  %s:%d  %s' % (path, lineno, msg))
        sys.exit(1)
    print('BERSIH: semua invoke/register valid di %d file.' % len(sys.argv[1:]))


if __name__ == '__main__':
    main()

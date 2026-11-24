#!/usr/bin/env python3
# PATCH run_smoke.py — perbarui cek console lama (era v14) ke kontrak console
# v16 yang disetujui owner: 4 kontrol fungsional SAJA (cari + filter error +
# salin + jeda), rincian LANGSUNG TAMPIL (nol klik/nol kolaps), NOL chip,
# banner v16, salin = teks mentah protokol.
p = '/home/z/my-project/rebuild-src/smoke/run_smoke.py'
src = open(p).read()

R = []

def rep(old, new):
    R.append((old, new))

# 1. versi badge/banner v14 -> v16 (3 cek)
rep('and "MOD-LOCALSERVER v14" in bd, bd[:120])',
    'and "MOD-LOCALSERVER v16" in bd, bd[:120])  # console v16')
rep('check("v17-salin-tabel", "copyTable" in bd and "plainLine(e)" in bd)',
    'check("v17-salin-tabel", "function doCopy" in bd and "plainLine(e)" in bd)  # v16: salin teks mentah')
rep('''check("v17-banner-crash-js", "renderCrash" in bd and "fetch('/crash')" in bd
          and "id=cpcrash" in bd)  # Bagian 41: tombol icon-only 📋''',
    '''check("v17-banner-crash-js", "renderCrash" in bd and "fetch('/crash')" in bd
          and "id=crashbn" in bd)  # v16: banner crash via #crashbn (nol tombol)''')
rep('''check("v17-banner-overlay-js", "renderOverlay" not in bd
          and "BUKA SETTINGS OVERLAY" not in bd
          and "overlay OFF (normal)" not in bd
          and "OVERLAY <b>OFF</b>" in bd)''',
    '''check("v17-banner-overlay-js", "renderOverlay" not in bd
          and "BUKA SETTINGS OVERLAY" not in bd
          and "overlay OFF (normal)" not in bd)  # v16: baris status stbadge dihapus''')
rep('check("v17-ver-dashboard", ">v14</span>" in bd and "raw server log" in bd)  # v14 (Bagian 51)',
    'check("v17-ver-dashboard", ">v16</span>" in bd and "raw server log" in bd)  # v16 (Bagian 53)')
rep('check("v41-banner-crash-js-masih", "renderCrash" in bd and "id=cpcrash" in bd)',
    'check("v41-banner-crash-js-masih", "renderCrash" in bd and "id=crashbn" in bd)  # v16')
rep('''check("v44-banner-boot", "id=banner" in bd and "class=b1" in bd
          and "DB LOCAL SERVER · com.db.local · MOD-LOCALSERVER v14" in bd, "")''',
    '''check("v44-banner-boot", "id=banner" in bd and "class=b1" in bd
          and "DB LOCAL SERVER · com.db.local · MOD-LOCALSERVER v16" in bd, "")''')
# 2. trunc dihapus (body panjang menggulir via .txt max-height)
rep('check("v49-trunc", "function trunc(s,n)" in bd, "")',
    'check("v49-trunc", "max-height:300px" in bd and "overflow-y:auto" in bd)  # v16: body panjang menggulir')
rep('''check("v60-sub-style", ".dtl{margin-top:7px" in bd
          and ".ln.r .dtl{border-left-color:#3a2026}" in bd
          and "max-height:280px" in bd, "")''',
    '''check("v60-sub-style", ".dtl{margin-top:7px" in bd
          and ".ln.r .dtl{border-left-color:#3a2026}" in bd
          and "max-height:300px" in bd, "")  # v16: 300px''')
# 3. kolaps DIHAPUS (v15/v16: rincian langsung tampil) — jadi guard regresi
rep('''check("v14-sub-collapse", ".sh{display:flex" in bd
          and ".sc{display:none" in bd
          and ".sub[data-o='1']>.sc{display:block}" in bd
          and "data-o=" in bd and "IC.chev" in bd, "")''',
    '''check("v14-sub-collapse", ".sh{display:flex" not in bd
          and ".sc{display:none" not in bd and "data-o=" not in bd
          and ".dtl{" in bd)  # v15/v16: kolaps dihapus — rincian langsung tampil''')
rep('''check("v14-sub-sum", ".sum{flex:1" in bd and "class=sum>" in bd
          and ".sz{color:" in bd, "")''',
    '''check("v14-sub-sum", "id=sum>" in bd and "id=ftr" in bd
          and "terakhir penting:" in bd, "")  # v16: footer ringkas''')
# 4. salin raw: indent 2 spasi (format copyText v16)
rep('''check("v63-salin-raw", "'    headers: '+e.headers" in bd
          and "'    request: '+e.reqPreview" in bd
          and "'    response: '+e.respPreview" in bd
          and "'    recv: ('+fmtB(e.reqLen||0)+') " in bd
          and "'    send: ('+fmtB(e.respLen||0)+') " in bd
          and "plainLine(e)" in bd, "")''',
    '''check("v63-salin-raw", "'  headers: '+e.headers" in bd
          and "'  request: '+e.reqPreview" in bd
          and "'  response: '+e.respPreview" in bd
          and "'  recv: ('+fmtB(e.reqLen||0)+') " in bd
          and "'  send: ('+fmtB(e.respLen||0)+') " in bd
          and "plainLine(e)" in bd, "")  # v16: indent 2 spasi''')
rep('check("v49-ver", ">v14</span>" in bd and "MOD-LOCALSERVER v14" in bd, "")',
    'check("v49-ver", ">v16</span>" in bd and "MOD-LOCALSERVER v16" in bd, "")  # v16')
# 5. paintIcons: pause + errf + copy (reset/dl/cpt dihapus v15)
rep('''check("v60-btn-paint", "$('pause').innerHTML=ic(S.auto?'pause':'play')" in bd
          and "$('reset').innerHTML=ic('trash')" in bd
          and "$('dl').innerHTML=ic('download')" in bd
          and "$('cpt').innerHTML=ic('copy')" in bd, "")''',
    '''check("v60-btn-paint", "$('pause').innerHTML=ic(S.auto?'pause':'play')" in bd
          and "$('errf').innerHTML=ic('octagon')" in bd
          and "$('copy').innerHTML=ic('copy')" in bd, "")  # v16: 3 tombol ikon + cari''')
# 6. chip status DIHAPUS permanen (v15/v16) — guard regresi
rep('''check("v60-chips", "class=schip" in bd and "id=stchips" in bd
          and "ic('server')+'LISTEN" in bd and "ic('clock')+'UPTIME" in bd
          and "ic('hash')+'SEQ" in bd and "ic('archive')+'ARSIP" in bd
          and "ic('layers')+'OVERLAY" in bd and "ic('activity')+'STATUS" in bd, "")''',
    '''check("v60-chips", "class=schip" not in bd and "id=stchips" not in bd
          and "KONFIG'" not in bd)  # v15/v16: chip dihapus — tombol sampah''')
rep('''check("v60-chip-ports", "'9527','settings','KONFIG'" in bd
          and "'9528','globe','CDN'" in bd and "'5050','shield','LOGIN'" in bd
          and "'5051','gamepad','GAME'" in bd and "'9530','card','SDK'" in bd
          and "'4000','radio','LOG'" in bd, "")''',
    '''check("v60-chip-ports", "'9527','settings','KONFIG'" not in bd
          and "id=stchips" not in bd)  # v16: chip port tidak ada''')
# 7. toast rise dihapus (v15: fadein baris) — guard
rep('check("v60-toast-rise", "@keyframes rise" in bd, "")',
    'check("v60-toast-rise", "@keyframes rise" not in bd and "@keyframes fadein" in bd, "")  # v16')
rep('''check("v60-stbadge", "ic('check')+' NORMAL'" in bd
          and "ic('x')+' '+n+' MASALAH'" in bd, "")''',
    '''check("v60-stbadge", "id=stbadge" not in bd
          and "MASALAH'" not in bd)  # v16: badge status dihapus''')
# 8. panduan: klik-untuk-buka harus HILANG; banner bilang rincian langsung
rep('''check("v63-guide", "ALUR NORMAL BOOT" not in bd
          and "detail panjang: klik untuk buka" in bd, "")''',
    '''check("v63-guide", "ALUR NORMAL BOOT" not in bd
          and "klik untuk buka" not in bd
          and "semua rincian tampil langsung" in bd, "")  # v16: nol klik''')
# 9. baris status server dihapus (v15) — footer sum kini ringkasan penting
rep('''check("v43-stline-server", "ic('server')+'LISTEN" in bd and "uptimeStr" in bd
          and "id=stbadge" in bd and "STATUS <b" in bd, "")''',
    '''check("v43-stline-server", "id=sum" in bd and "terakhir penting:" in bd
          and "id=stbadge" not in bd, "")  # v16: footer ringkas''')
# 10. filter: cari + tombol FILTER ERROR (select level/port dihapus v15)
rep('''check("v43-filter-lengkap", "id=q" in bd and "id=lv" in bd
          and "id=port" in bd and "id=stage" not in bd
          and "id=limit" not in bd, "")''',
    '''check("v43-filter-lengkap", "id=q" in bd and "id=errf" in bd
          and "id=lv" not in bd and "id=port" not in bd
          and "id=stage" not in bd and "id=limit" not in bd, "")  # v16: cari + filter error''')
# 11. tombol toolbar: pause + errf + copy (ikon saja)
rep('''check("v41-icon-buttons", "id=pause class=ib" in bd
          and "id=reset class=ib" in bd and "id=dl class=ib" in bd
          and "id=cpt class=ib" in bd
          and "id=vimp" not in bd and "id=vmal" not in bd and "id=vall" not in bd
          and "id=pf" not in bd and "id=guidebtn" not in bd
          and "PENTING</button>" not in bd
          and "MASALAH</button>" not in bd and "Salin tabel</button>" not in bd, "")''',
    '''check("v41-icon-buttons", "id=pause class=ib" in bd
          and "id=errf class=ib" in bd and "id=copy class=ib" in bd
          and "id=reset class=ib" not in bd and "id=dl class=ib" not in bd
          and "id=cpt class=ib" not in bd
          and "id=vimp" not in bd and "id=vmal" not in bd and "id=vall" not in bd
          and "id=pf" not in bd and "id=guidebtn" not in bd
          and "PENTING</button>" not in bd
          and "MASALAH</button>" not in bd and "Salin tabel</button>" not in bd, "")  # v16''')
rep('''check("v41-salin-icon", "id=cpt class=ib" in bd and "id=dl class=ib" in bd
          and "id=cpjs" not in bd, "")''',
    '''check("v41-salin-icon", "id=copy class=ib" in bd
          and "id=cpt class=ib" not in bd and "id=dl class=ib" not in bd
          and "id=cpjs" not in bd, "")  # v16: tombol salin tunggal''')

n_ok = 0
for old, new in R:
    c = src.count(old)
    assert c == 1, 'old_str muncul %d kali (harus 1): %r' % (c, old[:70])
    src = src.replace(old, new)
    n_ok += 1
open(p, 'w').write(src)
print('OK — %d cek console diperbarui ke kontrak v16' % n_ok)

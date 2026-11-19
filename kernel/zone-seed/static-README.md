# ZONA static/ — ENTRY SHELL H5 (override)

Zona ini WAJIB menyajikan entry shell yang TIDAK ADA di paket client
pschent (bukti agent 1-a, PETA v5 §2 TOOL 2 / §13.3):

- `index.html`
- `manifest.json`
- polyfill XHR/WS + bridge `window.*` (±15 fungsi: getLoginServer,
  checkFromNative, paySdk, ...)

Static engine merge: file di `static/` MENANG di atas `files/game/`
(fallback). Tanpa shell ini game tidak akan pernah start.

Isi final dibangun dari paket client asli — NOL byte buatan kita pada isi
game. Di prototipe ini zona hanya berisi README.

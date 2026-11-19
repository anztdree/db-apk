# db-apk — GAME LOCAL STANDALONE (1x REBUILD)

Satu APK, dua sistem:
1. SISTEM 1 — client: apa adanya. Satu-satunya sentuhan: EDIT ENDPOINT (base64 EntryPoint -> 127.0.0.1:4000).
2. SISTEM 2 — mini-server kernel (classes2.dex): root di `Android/data/com.db.local/server` (SEBELAH folder files/), seed zona TERTANAM DI DEX (NOL assets/server), dashboard UI + API di port 4000.

Mulai baca: REBUILD-NOTES.md (v02 = FINAL). Plan lengkap ada di repo proyek (README.md proyek).

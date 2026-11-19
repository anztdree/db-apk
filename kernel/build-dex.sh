#!/usr/bin/env bash
# ============================================================
# BUILD KERNEL — classes2.dex (mini-server SISTEM 2)
# Jalankan dari folder kernel/ (reproducible, butuh JRE + r8.jar +
# android.jar + ECJ + Rhino core — semua ada di folder ini/libs).
# Root kernel: SEBELAH folder files/ — Android/data/com.db.local/server
# Seed zona TERTANAM DI DALAM DEX (NOL assets/server di APK).
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:-/home/z/my-project/android.jar}"
R8_JAR="${R8_JAR:-/home/z/my-project/r8.jar}"

rm -rf out/classes out/dex
mkdir -p out/classes out/dex

echo "[1/4] compile ECJ (Java 8, classpath android.jar + rhino-core)"
java -jar libs/ecj-3.33.0.jar -8 -nowarn \
  -classpath "${ANDROID_JAR}:libs/rhino-core.jar" \
  -d out/classes src/s2/kernel/*.java

echo "[2/4] jar kelas kernel (D8 8.5 tidak menerima direktori)"
rm -f out/kernel-app.jar
python3 - <<'PYEOF'
import zipfile, os
base = 'out/classes'
with zipfile.ZipFile('out/kernel-app.jar', 'w', zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(base):
        for fn in files:
            full = os.path.join(root, fn)
            z.write(full, os.path.relpath(full, base))
print('jar OK')
PYEOF

echo "[3/4] dex D8 --min-api 21 (kernel + rhino core)"
java -cp "${R8_JAR}" com.android.tools.r8.D8 \
  --release --min-api 21 --lib "${ANDROID_JAR}" \
  --output out/dex out/kernel-app.jar libs/rhino-core.jar

cp out/dex/classes.dex out/classes2.dex
echo "[4/4] selesai -> out/classes2.dex ($(wc -c < out/classes2.dex) B)"

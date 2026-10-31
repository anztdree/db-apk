#!/usr/bin/env bash
# =============================================================
# Build APK-LSF v0.2.0 (cermin bootstrap + switch mode ONLINE/LOCAL)
# Alur: ecj -> d8 (classes2.dex) -> apktool b -> sisip dex
#       -> zipalign+sign (uber-apk-signer, keystore tetap)
# Persyaratan: JRE 11+ (java), apktool, uber-apk-signer,
#              /home/z/tools2/{ecj.jar,r8.jar,android28.jar,uber-apk-signer.jar}
# Catatan: compile pakai android.jar API 28 (NotificationChannel),
#          runtime tetap minSdk 21 (guard Build.VERSION di Notifier).
# =============================================================
set -euo pipefail

REPO="${REPO:-/home/z/db-apk}"
TOOLS="${TOOLS:-/home/z/tools2}"          # ecj.jar + r8.jar + android28.jar + uber-apk-signer.jar
APKTOOL="${APKTOOL:-$REPO/tools/apktool_3.0.3.jar}"
UBER="${UBER:-$TOOLS/uber-apk-signer.jar}"
KS="${KS:-$REPO/keystore/lsf.keystore}"
KS_ALIAS="${KS_ALIAS:-lsf}"
KS_PASS="${KS_PASS:-lsf-local-2025}"
PROJ="${PROJ:-$REPO/.analysis/lsf-src}"   # proyek apktool (decode -s)
MIN_API="${MIN_API:-21}"
V="${V:-0.2.0}"

SRC="$REPO/local-server/src/main/java"
BUILD="$REPO/build"
OUT="$REPO/releases/APK-LSF-$V.apk"

echo "[1/6] ecj compile ..."
rm -rf "$BUILD/classes" "$BUILD/dex"
mkdir -p "$BUILD/classes" "$BUILD/dex"
mapfile -t JAVA_FILES < <(find "$SRC" -name '*.java')
java -jar "$TOOLS/ecj.jar" -1.8 -encoding UTF-8 -Xlint:-options \
  -cp "$TOOLS/android28.jar" -d "$BUILD/classes" "${JAVA_FILES[@]}"

echo "[2/6] d8 -> classes2.dex ..."
mapfile -t CLASS_FILES < <(find "$BUILD/classes" -name '*.class')
java -cp "$TOOLS/r8.jar" com.android.tools.r8.D8 --release \
  --min-api "$MIN_API" --lib "$TOOLS/android28.jar" \
  --output "$BUILD/dex" "${CLASS_FILES[@]}"
cp "$BUILD/dex/classes.dex" "$BUILD/classes2.dex"

echo "[3/6] sisipkan dashboard ke assets ..."
mkdir -p "$PROJ/assets/server"
cp "$REPO/local-server/assets/server/dashboard.html" "$PROJ/assets/server/dashboard.html"

echo "[4/6] apktool b (manifest + assets, dex game tetap orisinal) ..."
rm -f "$BUILD/APK-LSF-$V.apk"
java -jar "$APKTOOL" b "$PROJ" -o "$BUILD/APK-LSF-$V.apk"

echo "[5/6] sisipkan classes2.dex ke APK ..."
(cd "$BUILD" && zip -X "APK-LSF-$V.apk" classes2.dex >/dev/null)

echo "[6/6] zipalign + sign (keystore tetap) ..."
rm -f "$REPO/dist/APK-LSF-$V-aligned-signed.apk"
mkdir -p "$REPO/dist"
java -jar "$UBER" -a "$BUILD/APK-LSF-$V.apk" \
  --ks "$KS" --ksAlias "$KS_ALIAS" --ksPass "$KS_PASS" --ksKeyPass "$KS_PASS" \
  --out "$REPO/dist"

cp "$REPO/dist/APK-LSF-$V-aligned-signed.apk" "$OUT"
echo "SELESAI: $OUT"
sha256sum "$OUT"

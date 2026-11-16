#!/data/data/com.termux/files/usr/bin/bash
# يشتغل داخل Termux (بيئة Bionic متوافقة فعلياً مع أندرويد).
# المدخلات: $1 مسار المشروع، $2 مسار APK الناتج، $3 نسخة Java
set -e
PROJECT_DIR="$1"
OUT_APK="$2"
JAVA_VER="${3:-17}"
LOG="$PROJECT_DIR/build.log"
MARK="$PROJECT_DIR/build.done"

exec > "$LOG" 2>&1   # كل المخرجات تروح لملف اللوق عشان التطبيق يقرأها
rm -f "$MARK"

finish() {
  local code=$1
  echo "DONE:$code" > "$MARK"
  exit $code
}
trap 'finish 1' ERR

echo "== تنصيب أدوات Termux المطلوبة (مرة وحدة، يتخزن بعدها) =="
yes | pkg install -y "openjdk-$JAVA_VER" aapt2 d8 apksigner zip

cd "$PROJECT_DIR"
BUILD="build_out"; rm -rf "$BUILD"; mkdir -p "$BUILD/classes" "$BUILD/dex"
ANDROID_JAR="$PREFIX/share/java/android.jar"
if [ ! -f "$ANDROID_JAR" ]; then
  echo "تحذير: android.jar غير موجود بـ $ANDROID_JAR — لازم تحطه يدوياً أول مرة"
fi

echo "== تصريف Java =="
JAVA_FILES=$(find src -name "*.java" 2>/dev/null || true)
if [ -n "$JAVA_FILES" ]; then
  javac -encoding UTF-8 -classpath "$ANDROID_JAR" -d "$BUILD/classes" $JAVA_FILES
fi

echo "== class -> dex =="
CLASS_FILES=$(find "$BUILD/classes" -name "*.class" 2>/dev/null || true)
if [ -n "$CLASS_FILES" ]; then
  d8 --output "$BUILD/dex" --lib "$ANDROID_JAR" --min-api 21 $CLASS_FILES
fi

echo "== تجميع الموارد (aapt2) =="
mkdir -p "$BUILD/compiled_res"
[ -d res ] && aapt2 compile --dir res -o "$BUILD/compiled_res"
FLATA=$(find "$BUILD/compiled_res" -name "*.flat" 2>/dev/null || true)
aapt2 link -o "$BUILD/res.apk" --manifest AndroidManifest.xml -I "$ANDROID_JAR" $FLATA

echo "== تعبئة + محاذاة =="
cp "$BUILD/res.apk" "$BUILD/unsigned.apk"
DEX_FILES=$(find "$BUILD/dex" -name "*.dex" 2>/dev/null || true)
if [ -n "$DEX_FILES" ]; then (cd "$BUILD/dex" && zip -j "../unsigned.apk" *.dex); fi

echo "== توقيع =="
KS="$PREFIX/etc/debug.keystore"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -alias debug -keyalg RSA \
  -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Debug"
apksigner -p android "$KS" "$BUILD/unsigned.apk" "$OUT_APK"

echo "== تم بنجاح: $OUT_APK =="
finish 0

#!/bin/bash
# Direct APK build for PatchMaster without Gradle
set -e

ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
BUILD_TOOLS="$ANDROID_HOME/build-tools/34.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-34"
PROJECT_DIR="/root/PatchMaster"
BUILD_DIR="$PROJECT_DIR/build"
OUTPUT_APK="$PROJECT_DIR/PatchMaster.apk"

echo "=== Building PatchMaster APK ==="
echo "Android SDK: $ANDROID_HOME"
echo "Build tools: $BUILD_TOOLS"
echo "Platform: $PLATFORM"

# Clean
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/{classes,dex,res,lib,java_srcs}

# Collect all Kotlin/Java sources
echo "[1/6] Collecting sources..."
find "$PROJECT_DIR/app/src/main/java" -name "*.kt" > "$BUILD_DIR/sources.txt"
echo "Found $(wc -l < "$BUILD_DIR/sources.txt") Kotlin files"

# For a proper build we need Kotlin compiler (kotlinc)
# Check if kotlinc is available
if ! command -v kotlinc &> /dev/null; then
    echo "Kotlin compiler not found. Attempting to download..."
    # Download Kotlin compiler
    KOTLIN_VERSION="1.9.22"
    curl -sL "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip" -o /tmp/kotlin.zip
    if [ -f /tmp/kotlin.zip ] && [ $(stat -c%s /tmp/kotlin.zip) -gt 10000 ]; then
        unzip -q /tmp/kotlin.zip -d /opt/kotlin
        export PATH="/opt/kotlin/kotlinc/bin:$PATH"
        echo "Kotlin compiler installed"
    else
        echo "WARNING: Cannot download Kotlin compiler. APK build will use stub."
        echo "Install kotlinc manually or use Android Studio/Gradle for full build."
        exit 1
    fi
fi

kotlinc -version 2>&1 | head -1

# Compile Kotlin to JVM bytecode
echo "[2/6] Compiling Kotlin sources..."
KOTLIN_STDLIB=$(find /opt/kotlin -name "kotlin-stdlib*.jar" | head -1)
kotlinc -cp "$PLATFORM/android.jar:$KOTLIN_STDLIB" \
    -d "$BUILD_DIR/classes" \
    -include-runtime \
    @"$BUILD_DIR/sources.txt" 2>&1 | head -20

echo "[3/6] Converting to DEX..."
"$BUILD_TOOLS/d8" \
    --lib "$PLATFORM/android.jar" \
    --min-api 26 \
    --output "$BUILD_DIR/dex" \
    "$BUILD_DIR/classes/classes.jar" 2>&1 | head -10

echo "[4/6] Packaging resources..."
"$BUILD_TOOLS/aapt2" compile \
    --dir "$PROJECT_DIR/app/src/main/res" \
    -o "$BUILD_DIR/res.zip" 2>&1 | head -5

"$BUILD_TOOLS/aapt2" link \
    -o "$BUILD_DIR/unsigned.apk" \
    -I "$PLATFORM/android.jar" \
    --manifest "$PROJECT_DIR/app/src/main/AndroidManifest.xml" \
    -R "$BUILD_DIR/res.zip" \
    --auto-add-overlay 2>&1 | head -10

echo "[5/6] Adding DEX and aligning..."
cd "$BUILD_DIR"
mkdir -p apk
cd apk
unzip -q "$BUILD_DIR/unsigned.apk" 2>/dev/null || true
cp "$BUILD_DIR/dex/classes.dex" . 2>/dev/null || true
cp "$BUILD_DIR/dex/classes2.dex" . 2>/dev/null || true

# Zip everything back
zip -r "$BUILD_DIR/aligned.apk" . -x ".*" 2>&1 | tail -3

"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/aligned.apk" "$BUILD_DIR/ready-to-sign.apk" 2>&1 | head -3

echo "[6/6] Signing APK..."
# Generate debug keystore if needed
KEYSTORE="$BUILD_DIR/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkey -v -keystore "$KEYSTORE" \
        -alias androiddebugkey -keyalg RSA -keysize 2048 \
        -validity 10000 \
        -dname "CN=Android Debug, OU=Android, O=Google, L=Unknown, ST=Unknown, C=US" \
        -storepass android -keypass android 2>/dev/null
fi

"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$OUTPUT_APK" \
    "$BUILD_DIR/ready-to-sign.apk" 2>&1 | head -5

echo ""
echo "=== Build Complete ==="
ls -lh "$OUTPUT_APK"
echo "APK: $OUTPUT_APK"

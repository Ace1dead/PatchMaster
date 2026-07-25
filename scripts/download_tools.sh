#!/system/bin/sh
# PatchMaster Toolchain Downloader
# Downloads ARM64 Android binaries for APK modding

TOOLS_DIR="/data/data/com.patchmaster/files/tools"
mkdir -p "$TOOLS_DIR"

log() { echo "[PatchMaster] $*"; }

download() {
    local url="$1"
    local out="$2"
    log "Downloading $out..."
    curl -sL --connect-timeout 15 --max-time 60 "$url" -o "$TOOLS_DIR/$out"
    if [ $? -eq 0 ] && [ -f "$TOOLS_DIR/$out" ]; then
        chmod +x "$TOOLS_DIR/$out" 2>/dev/null
        log "  ✓ $out ($(du -h "$TOOLS_DIR/$out" | cut -f1))"
        return 0
    else
        log "  ✗ Failed to download $out"
        return 1
    fi
}

log "=== PatchMaster Toolchain Downloader ==="
log "Target: $TOOLS_DIR"

# Android SDK Build Tools (aapt2, zipalign, apksigner)
# Using SDK build-tools 34.0.0 ARM64 binaries
BASE="https://dl.google.com/android/repository"
SDK_TOOLS="build-tools_r34.0.0-linux.zip"

download_aapt2() {
    # Option 1: From Android SDK (requires full SDK download)
    # Option 2: From GitHub releases
    download "https://github.com/Cosmic-OS/platform_prebuilts_sdk/raw/master/build-tools/34.0.0/aapt2" "aapt2_arm64" || \
    download "https://github.com/Cosmic-OS/platform_prebuilts_sdk/raw/master/build-tools/34.0.0/zipalign" "zipalign_arm64" || \
    download "https://github.com/Cosmic-OS/platform_prebuilts_sdk/raw/master/build-tools/34.0.0/apksigner" "apksigner_arm64"
}

download_apktool() {
    log "Downloading apktool..."
    local APKTOOL_URL="https://bitbucket.org/iBotPeaches/apktool/downloads/apktool_2.9.3.jar"
    curl -sL --connect-timeout 15 --max-time 120 "$APKTOOL_URL" -o "$TOOLS_DIR/apktool.jar"
    if [ -f "$TOOLS_DIR/apktool.jar" ] && [ $(stat -c%s "$TOOLS_DIR/apktool.jar") -gt 1000000 ]; then
        log "  ✓ apktool.jar ($(du -h "$TOOLS_DIR/apktool.jar" | cut -f1))"
    else
        log "  ✗ apktool download failed (file too small or missing)"
        return 1
    fi
}

download_smali() {
    log "Downloading smali/baksmali..."
    local SMALI_URL="https://bitbucket.org/JesusFreke/smali/downloads/smali-2.5.2.jar"
    local BAKSMALI_URL="https://bitbucket.org/JesusFreke/smali/downloads/baksmali-2.5.2.jar"
    curl -sL --connect-timeout 15 --max-time 60 "$SMALI_URL" -o "$TOOLS_DIR/smali.jar"
    curl -sL --connect-timeout 15 --max-time 60 "$BAKSMALI_URL" -o "$TOOLS_DIR/baksmali.jar"
    if [ -f "$TOOLS_DIR/smali.jar" ]; then log "  ✓ smali.jar"; fi
    if [ -f "$TOOLS_DIR/baksmali.jar" ]; then log "  ✓ baksmali.jar"; fi
}

# Execute downloads
download_aapt2
download_apktool
download_smali

log ""
log "=== Verification ==="
for tool in aapt2_arm64 zipalign_arm64 apksigner_arm64 apktool.jar smali.jar baksmali.jar; do
    if [ -f "$TOOLS_DIR/$tool" ]; then
        log "  ✓ $tool"
    else
        log "  ✗ $tool (missing)"
    fi
done

log ""
log "=== Complete ==="
log "Tools installed to: $TOOLS_DIR"
log "Install tools in app via Settings → Reinstall Tools"

# Tool Usage Reference

## aapt2 (Android Asset Packaging Tool 2)

### Resource Compilation
```
aapt2 compile -o <output-dir> <resource-file>
aapt2 compile --dir <res-dir> -o <output>
```

### APK Linking
```
aapt2 link -o <output.apk> -I <android.jar> --manifest <AndroidManifest.xml> -R <compiled-res>
aapt2 link --manifest AndroidManifest.xml --output-to-dir -o built.apk
```

### Dump Commands
```
aapt2 dump xmltree --file AndroidManifest.xml <apk>
aapt2 dump resources <apk>
aapt2 dump badging <apk>
aapt2 dump configurations <apk>
aapt2 dump packages <apk>
```

## zipalign

### Usage
```
zipalign -f -p 4 <input.apk> <output.apk>
zipalign -c 4 <aligned.apk>  # verify alignment
```

### Flags
- `-f` : overwrite existing output
- `-p` : page-align shared libraries
- `-v` : verbose

## apksigner

### Sign
```
apksigner sign --ks <keystore.jks> --ks-key-alias <alias> \
  --ks-pass pass:<pass> --key-pass pass:<pass> \
  --out <output.apk> <input.apk>
```

### Sign with multiple signers
```
apksigner sign --signer <signer-args> --next-signer --signer <signer2-args> <apk>
```

### Verify
```
apksigner verify --verbose <apk>
apksigner verify --print-certs <apk>
```

### Key Generation
```
apksigner keystore gen-key --keystore <keystore.jks> \
  --alias <alias> --key-alg RSA --key-size 2048 \
  --validity 36500 --storepass <pass> --keypass <pass>
```

## smali / baksmali

### Run via dalvikvm
```
dalvikvm -cp smali.jar org.jf.smali.Main a <input-dir> -o <output.dex>
dalvikvm -cp baksmali.jar org.jf.baksmali.Main d <input.dex> -o <output-dir>
```

### smali flags
- `-a <api>` : target API level
- `-o <file>` : output file
- `-j <threads>` : parallel threads

### baksmali flags
- `-d <dir>` : bootclasspath directory
- `-o <dir>` : output directory
- `-l <level>` : debug info level (NONE, SOURCE, LINES, ALL)
- `--no-register-info` : strip register info

## Packaging Pipeline

### Standard Build
```
# 1. Compile resources
aapt2 compile -o compiled_res res/values/strings.xml

# 2. Link APK (without DEX for partial builds)
aapt2 link --manifest AndroidManifest.xml \
  -I /system/framework/android.jar \
  -R compiled_res/values/strings.xml.flat \
  --output-to-dir -o unsigned.apk

# 3. Add DEX + assets
# (unzip unsigned.apk, add DEX, rezip)

# 4. Align
zipalign -f -p 4 unsigned.zip aligned.apk

# 5. Sign
apksigner sign --ks keystore.jks --ks-key-alias mykey \
  --ks-pass pass:password --out final.apk aligned.apk
```

### Split APK Support
```
# Base APK + config splits
aapt2 link --manifest base/AndroidManifest.xml \
  -I android.jar \
  --split config.armeabi_v7a:armeabi-v7a/manifest \
  --split config.en:en/manifest \
  -o base.apk
```

## ADB Installation
```
# Regular install
pm install -r <apk>

# Install with downgrade
pm install -r -d <apk>

# Install to specific user
pm install --user <user-id> -r <apk>

# Grant all permissions at install
pm install -r -g <apk>
```

## Package Manager Query
```
# List packages
pm list packages
pm list packages -f  # show APK paths
pm list packages -3  # third-party only

# Get APK path
pm path <package-name>

# Get package info
dumpsys package <package-name>
```

# ScrnStr Development Guide

## CLI Build & Deploy (No Android Studio clicking needed)

### Environment Setup
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$HOME/Library/Android/sdk/platform-tools:$PATH"
cd /Users/zodwick/work/scrnstr/android
```

### Build → Install → Launch (one-liner)
```bash
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.scrnstr/.MainActivity
```

### Deploy to physical device (Samsung S24)
```bash
adb -s RZCX934GRSM install -r app/build/outputs/apk/debug/app-debug.apk
```

## ADB Debugging Toolkit

### Live logs from the app
```bash
# Get PID
adb shell "ps -A | grep scrnstr"

# Stream logs for that PID
adb logcat --pid=<PID>

# Or dump recent logs
adb logcat -d --pid=<PID>

# Clear logs before a test
adb logcat -c
```

### UI Interaction (no touching the screen)
```bash
# Dump UI hierarchy to find exact button coordinates
adb shell "uiautomator dump /sdcard/ui.xml && cat /sdcard/ui.xml"

# Tap at coordinates (use bounds from uiautomator dump)
adb shell input tap <x> <y>

# Take a screenshot on the device (triggers the app!)
adb shell input keyevent 120

# Capture device screen to view locally
adb exec-out screencap -p > /tmp/screen.png

# Open/collapse notification shade
adb shell cmd statusbar expand-notifications
adb shell cmd statusbar collapse

# Open a URL in Chrome
adb shell am start -a android.intent.action.VIEW -d "https://google.com" com.android.chrome

# Press back
adb shell input keyevent KEYCODE_BACK
```

### Grant permissions without clicking dialogs
```bash
adb shell pm grant com.scrnstr android.permission.READ_MEDIA_IMAGES
adb shell pm grant com.scrnstr android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.scrnstr android.permission.WRITE_EXTERNAL_STORAGE
adb shell pm grant com.scrnstr android.permission.POST_NOTIFICATIONS
adb shell pm grant com.scrnstr android.permission.READ_CALENDAR
adb shell pm grant com.scrnstr android.permission.WRITE_CALENDAR
```

### Force restart the app
```bash
adb shell am force-stop com.scrnstr
adb shell am start -n com.scrnstr/.MainActivity
```

## Server

### Start backend
```bash
cd /Users/zodwick/work/scrnstr/server
npm start
```

### Start Cloudflare tunnel
```bash
cloudflared tunnel --url http://localhost:3000
# Copy the generated URL and update Config.kt SERVER_URL
```

### Test endpoints locally
```bash
# Health check
curl http://localhost:3000/health

# Test Letterboxd
curl -X POST http://localhost:3000/letterboxd \
  -H "Content-Type: application/json" \
  -d '{"movie":"Interstellar","year":"2014"}'

# Test WhatsApp
curl -X POST http://localhost:3000/whatsapp \
  -H "Content-Type: application/json" \
  -d '{"message":"Check this out","contacts":["919876543210"]}'
```

## Bugs Fixed During Development

| Bug | Fix |
|-----|-----|
| `settings.gradle.kts` — `dependencyResolution` unresolved | Rename to `dependencyResolutionManagement` |
| Missing `mipmap/ic_launcher` resource | Added adaptive icon XMLs in `res/mipmap-hdpi/` and `res/drawable/` |
| `BillOrganizer.kt:37` — `if` without `else` used as expression | Added `else` branch |
| Missing `gradlew` + `gradle-wrapper.jar` | Created wrapper script, downloaded jar from Gradle 8.5 dist |
| Screenshot `.pending` file crash — `IllegalStateException: Only owner can interact with pending item` | Added 3s delay + re-query `getLatestScreenshotUri()` to find finalized file |
| Gemini API key invalid — trailing `%` in key | Removed `%` from `Config.kt` |
| Calendar date parsing — `"Fri 17 Apr 2026"` format not supported | Added `EEE dd MMM yyyy` and other formats to `CalendarAdder.kt` |
| Letterboxd timeout via Cloudflare tunnel | Puppeteer is slow (~15s); tunnel drops connection. Restart tunnel to fix |

## Testing Flow (the fast iteration loop)

1. Make code changes
2. `./gradlew assembleDebug` (~1-2s incremental)
3. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. `adb shell am force-stop com.scrnstr && adb shell am start -n com.scrnstr/.MainActivity`
5. Tap START MONITORING: `adb shell input tap 540 1336`
6. Take a test screenshot: `adb shell input keyevent 120`
7. Wait ~8s, check logs: `adb logcat -d --pid=$(adb shell ps -A | grep scrnstr | awk '{print $2}')`
8. Check notification: `adb shell cmd statusbar expand-notifications`

Total cycle time: **~15 seconds** from code change to seeing results. No Android Studio UI needed.

## Architecture Quick Reference

```
Screenshot taken → ContentObserver detects it
    → 3s delay (wait for .pending to finalize)
    → Re-query MediaStore for latest screenshot
    → GeminiClassifier (Gemini 2.0 Flash)
    → Returns {category, data, suggested_action}
    → Notification shown
    → User taps → ActionReceiver → ActionExecutor
        → food_bill  → BillOrganizer (local file copy)
        → event      → CalendarAdder (device calendar)
        → tech_article → WhatsAppAction (POST to server)
        → movie      → LetterboxdAction (POST to server)
```

## Key Files
- `Config.kt` — API keys, server URL, WhatsApp contacts
- `ScreenshotObserverService.kt` — Screenshot detection + processing
- `GeminiClassifier.kt` — LLM image classification
- `ActionExecutor.kt` — Routes actions to handlers
- `actions/` — Individual action implementations
- `server/index.js` — Express backend
- `server/letterboxd.js` — Puppeteer automation for Letterboxd
- `server/whatsapp.js` — WhatsApp Web.js integration

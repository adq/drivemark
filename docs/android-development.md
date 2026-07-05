# Android App Development

How to set up, build, and run the DriveMark Android app locally.

## Prerequisites

- [Android Studio](https://developer.android.com/studio) (latest stable release)
- JDK 17 (bundled with Android Studio, or install separately)
- A Google account
- Access to [Google Cloud Console](https://console.cloud.google.com) (the same GCP project used by the Chrome extension)
- An Android device or emulator running API 26+ (Android 8.0)

---

## 1. Google Cloud Setup

The Android app uses the same GCP project as the Chrome extension. If you've already followed the [Chrome extension setup](chrome-extension-development.md), the OAuth consent screen and APIs are already configured — you just need to add Android-specific credentials below.

If you're setting up Android only (no Chrome extension), first create a GCP project and configure the OAuth consent screen with the `https://www.googleapis.com/auth/drive.file` scope — see steps 1–2 in [chrome-extension-development.md](chrome-extension-development.md).

You need two additional credentials: an OAuth client ID for Android and a Web application client ID.

> **Two clients, two jobs — don't mix them up.** These are separate credentials with completely different roles, and both IDs end in `.apps.googleusercontent.com`, so they look identical:
>
> | Client type | Referenced in code? | Job |
> |---|---|---|
> | **Android** (package + SHA-1) | No | Authorizes *this build* to make sign-in requests. Google just checks it exists — you never paste its ID anywhere. |
> | **Web application** | Yes → `webClientId` | The audience the ID token is minted **for**. This is the ID that goes in `config.dev.properties`. |
>
> **Pasting the Android client ID into `webClientId` is the #1 setup mistake.** Symptom: sign-in gets *past* the account picker, then fails with "Developer console is not set up correctly" — and logcat shows `You must use a Web client as the server client ID`.

### Register your debug signing key

Android OAuth requires your app's SHA-1 signing fingerprint.

1. Get your debug keystore SHA-1:

   ```sh
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```

   Copy the **SHA1** fingerprint (e.g. `DA:39:A3:EE:5E:6B:...`).

2. In [Google Cloud Console](https://console.cloud.google.com), go to **APIs & Services -> Credentials**
3. Click **Create Credentials -> OAuth client ID**
4. Select **Android** as the application type
5. Enter:
   - **Package name**: `com.drivemark.app`
   - **SHA-1 certificate fingerprint**: the fingerprint from step 1
6. Click **Create**

> **Note:** You don't use this Android client ID directly in the app. It registers your signing key so Google allows the OAuth flow.

### Get a Web application client ID

The app authenticates using a **Web application** client ID (not the Android one).

1. If you already set up the Chrome extension, reuse the same Web application client ID — find it in **APIs & Services -> Credentials**
2. If you don't have one yet, create one: **APIs & Services -> Credentials -> Create Credentials -> OAuth client ID -> Web application**
3. Copy the client ID (ends in `.apps.googleusercontent.com`)

### Enable APIs

Ensure both APIs are enabled (they may already be from Chrome extension setup):

- **Google Sheets API**
- **Google Drive API**

Go to **APIs & Services -> Library** and search for each.

### OAuth scope

The app requests a single scope: `https://www.googleapis.com/auth/drive.file`. This is the same scope used by the Chrome extension. It grants access only to files the app creates or that the user explicitly opens — it cannot see other files in the user's Drive.

This scope must be listed on the OAuth consent screen (already done if you followed the Chrome extension setup).

---

## 2. Configure the App

Create your local config from the template (it is gitignored and not committed):

```sh
cp android-app/config.dev.example.properties android-app/config.dev.properties
```

Then open `android-app/config.dev.properties` and set your Web application client ID:

```properties
webClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

Replace `YOUR_WEB_CLIENT_ID` with the Web application client ID from step 1. **This must be the Web application client, not the Android one** — confirm the **Type** column reads "Web application" in Cloud Console → Credentials before you copy it (see the [two-clients callout](#1-google-cloud-setup)). Debug builds read from `config.dev.properties`; release builds read from `config.prod.properties` (copy it from `config.prod.example.properties`).

---

## 3. Build the App

From the `android-app/` directory:

```sh
# Debug build
./gradlew assembleDebug
```

The APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

For a release build:

```sh
./gradlew assembleRelease
```

> **First build:** Gradle downloads dependencies on the first run. This can take several minutes depending on your connection.

---

## 4. Run on an Emulator

### From Android Studio

1. Open the `android-app/` directory in Android Studio
2. Wait for Gradle sync to complete
3. Select a device from the device dropdown (create an emulator via **Device Manager** if needed — use API 26+ image)
4. Click **Run** (green play button)

### From the command line

```sh
# Install to a running emulator
./gradlew installDebug

# Then launch the app manually on the emulator
adb shell am start -n com.drivemark.app/.MainActivity
```

---

## 5. Run on a Physical Device

1. On your Android device, enable **Developer options**:
   - Go to **Settings -> About phone** and tap **Build number** 7 times
2. Enable **USB debugging** in **Settings -> Developer options**
3. Connect the device via USB and accept the debugging prompt
4. Verify the device is connected:

   ```sh
   adb devices
   ```

5. Install and launch:

   ```sh
   ./gradlew installDebug
   adb shell am start -n com.drivemark.app/.MainActivity
   ```

   Or use the **Run** button in Android Studio with your device selected.

---

## Development Workflow

### Live edit with Compose

Android Studio supports **Live Edit** for Jetpack Compose — UI changes reflect on the running app without a full rebuild. Enable it in **Settings -> Editor -> Live Edit**.

For changes outside Compose (ViewModels, data layer, DI modules), rebuild with **Run** or `./gradlew installDebug`.

### Logcat

Filter logs to the app:

```sh
adb logcat --pid=$(adb shell pidof -s com.drivemark.app)
```

Or use the **Logcat** panel in Android Studio and filter by package name.

### Hilt dependency injection

If you add a new `@Inject` class or `@Module`, remember to:

- Annotate Activities with `@AndroidEntryPoint`
- Annotate the Application class with `@HiltAndroidApp`
- Rebuild after changing DI modules (Hilt generates code at compile time via KSP)

---

## Signing

### Debug keystore

Android Studio creates a debug keystore at `~/.android/debug.keystore` on first build. If it doesn't exist (fresh machine, CI, command-line-only setup), create one manually:

```sh
keytool -genkeypair \
  -alias androiddebugkey \
  -keypass android \
  -keystore ~/.android/debug.keystore \
  -storepass android \
  -dname "CN=Android Debug,O=Android,C=US" \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -validity 10000
```

This is the keystore referenced in the [GCP setup](#register-your-debug-signing-key) section — you need its SHA-1 fingerprint to register the Android OAuth credential.

### Release keystore

To distribute via the Play Store (or sideload signed APKs), you need a release keystore.

#### 1. Generate the keystore

```sh
keytool -genkeypair \
  -alias drivemark \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -validity 10000 \
  -keystore drivemark-release.jks
```

You'll be prompted for a keystore password, key password, and your name/org details.

> **Back this file up securely.** If you lose the release keystore, you can never push updates to the same Play Store listing. Store a copy outside the repo (e.g. a password manager or encrypted cloud storage).

#### 2. Create `keystore.properties`

In the `android-app/` directory, create a `keystore.properties` file (already in `.gitignore`):

```properties
storeFile=../drivemark-release.jks
storePassword=your_keystore_password
keyAlias=drivemark
keyPassword=your_key_password
```

Adjust `storeFile` to the path where you saved the `.jks` file. Relative paths are resolved from the `app/` module directory.

#### 3. Configure Gradle

The `signingConfigs` block in `app/build.gradle.kts` loads credentials from `keystore.properties` automatically. If the file is missing, only debug builds work — release builds will fail with a clear error.

#### 4. Build a signed release

```sh
cd android-app

# Android App Bundle (for Play Store upload)
./gradlew bundleRelease

# Signed APK (for sideloading)
./gradlew assembleRelease
```

Output locations:
- AAB: `app/build/outputs/bundle/release/app-release.aab`
- APK: `app/build/outputs/apk/release/app-release.apk`

#### 5. Verify the signature

```sh
# Requires Android SDK build-tools on PATH
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

---

## Project Structure

```
android-app/app/src/main/java/com/drivemark/app/
├── DriveMarkApplication.kt       # App entry point (@HiltAndroidApp)
├── MainActivity.kt               # Single activity, hosts Compose navigation
├── data/
│   ├── local/                    # Room database, DAOs, DataStore preferences
│   ├── mapper/                   # Entity <-> domain model mapping
│   ├── remote/                   # Google Auth, Sheets API, Drive API, metadata extraction
│   └── repository/               # Business logic layer
├── di/
│   └── AppModule.kt             # Hilt dependency injection setup
├── domain/
│   └── model/                   # Domain models (Bookmark, Spreadsheet)
└── ui/
    ├── bookmarks/               # Bookmark list screen + components
    ├── detail/                  # Bookmark detail screen
    ├── login/                   # Google Sign-In screen
    └── navigation/              # Compose NavGraph + route definitions
```

---

## Testing the Share Intent

DriveMark registers as a share target for `text/plain` content. To test:

1. Open Chrome (or any browser) on the device/emulator
2. Navigate to any page
3. Tap **Share** and select **DriveMark** from the share sheet
4. The app opens with the shared URL ready to save

You can also test via `adb`:

```sh
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://example.com" com.drivemark.app
```

---

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| Gradle sync fails | Missing JDK 17 | Install JDK 17 or configure it in Android Studio -> Settings -> Build -> Gradle -> Gradle JDK |
| `WEB_CLIENT_ID` build error | Placeholder not replaced | Set your Web application client ID in `config.dev.properties` |
| Sign-in fails silently | Debug SHA-1 not registered | Run the `keytool` command and register the fingerprint in Cloud Console (see step 1) |
| "Developer console not set up" — fails **before** the account picker appears | Android OAuth client missing or its SHA-1 not registered | Register the debug SHA-1 with an Android OAuth client ID (package `com.drivemark.app`) — see step 1 |
| "Developer console not set up" — fails **after** you pick an account (logcat: `You must use a Web client as the server client ID`) | `webClientId` holds an **Android** (or otherwise non-Web) client ID | Put the **Web application** client ID in `config.dev.properties`; verify the **Type** column reads "Web application" in Cloud Console |
| `com.google.android.gms.common.api.ApiException: 10` | SHA-1 mismatch or wrong client ID | Verify the SHA-1 matches your debug keystore. If it fails *after* account selection, `webClientId` is not a Web-type client — use the Web application client ID, not the Android one |
| App crashes on launch with Hilt error | Missing `@AndroidEntryPoint` annotation | Ensure `MainActivity` and any new Activities are annotated with `@AndroidEntryPoint` |
| "INTERNET permission denied" | Emulator network issue | Restart the emulator with network access; check that `INTERNET` permission is in `AndroidManifest.xml` |
| Room schema error after entity changes | Migration not provided | For development, uninstall the app and reinstall (`adb uninstall com.drivemark.app && ./gradlew installDebug`) |
| Slow first build | Gradle downloading dependencies | Normal on first run. Subsequent builds use the Gradle cache and are much faster |

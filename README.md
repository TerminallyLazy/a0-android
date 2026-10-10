# Agent Zero for Android

An HTTPS-first Android client for your own Agent Zero server. Kotlin and Compose provide the connection screen and Android controls; the server supplies its complete WebUI. There is no bundled WebUI fork or second native chat API implementation.

**[Download for Android](https://terminallylazy.github.io/a0-android/)** · [iOS public beta](https://testflight.apple.com/join/xqAFS5er) · [Signed releases](https://github.com/TerminallyLazy/a0-android/releases) · [Privacy](https://terminallylazy.github.io/a0-android/privacy.html)

## Install and connect

1. Download **AgentZero.apk** from the installation page and open it on Android. If prompted, allow installation from the browser or file app you used, then tap **Install**. You can turn that permission off afterward. The public package is `com.terminallylazy.a0android`; local debug builds use the separate `.debug` package.
2. Choose **Add server** to enter an address, or **Scan QR code** to use the camera or a saved QR image. Review the root HTTPS address (for example `https://agent.example.com`), enter an optional name, and choose **Connect**. Ports are supported; subpaths, embedded credentials, query strings, fragments, and HTTP are rejected.
3. Sign in on the server's own page if required. For an HTTPS LAN server, grant local network access on Android 17. If a hostname resolves differently after connection, use **Connection menu → LAN access** and reload.
4. Use the server's chat, settings, scheduler, projects, plugins, and files. **Tools** opens a labeled tray; phone panels fill the available screen. Android Back dismisses tools, panels and dialogs. The native overflow menu holds **Servers**, **Reload**, and connection details.

From a WebUI with the included installation bridge, choose the small **Android icon** under **Mobile apps** at the bottom of the sidebar or in Remote Control. Scan the installation QR with your phone camera, or tap **Download APK** on Android. Once installed, **Open in Android** fills in the HTTPS address for review; it never saves or contacts the server until you confirm. The second QR is for the app's **Scan QR code** screen. Server operators can install the [WebUI bridge](distribution/webui/README.md) from the release ZIP. The neighboring **Apple icon** opens the iPhone/iPad public beta on TestFlight. This bridge has not been merged upstream.

Install later signed APKs over the existing public app to retain connections. The debug preview is a separate application and does not automatically migrate its local settings. Google Play publishing is deferred at the owner's request; direct WebUI/APK distribution is the current channel.

The phone layout reserves space for navigation above the conversation and gives the composer its own full-width text row. When a socket acknowledges the connection without delivering updates, a foreground watchdog uses the WebUI's existing authenticated read path to recover replies. It never resends a prompt. See [the UI and messaging validation](docs/ui-repair.md).

Use a certificate trusted by Android's system roots. Invalid, self-signed, and hostname-mismatched certificates are blocked. Managed private CAs, HTTP-only servers, HTTP Basic authentication, and external SSO are outside this first version. A tunnel's own first-visit warning may require its normal Continue action.

## Connect with QR

Open Agent Zero's tunnel settings on another screen and scan its QR code, or generate a QR containing your server's plain HTTPS root address. **Scan QR code → Use camera** asks Android for camera access; **Choose QR image** reads a screenshot or saved image through Android's document picker without camera access. Decoding runs on the device with bundled ZXing and CameraX; camera images are not retained or uploaded, and no Google Play Services download is needed.

The scanned address opens a review form. Nothing is saved or contacted until you choose **Connect**. Cancel discards the draft. A QR does not establish server identity or bypass certificate validation; check the full address before connecting. QR payloads must be plain ASCII HTTPS URLs (use punycode for international domains), at most 2048 characters. JSON, login tokens, credentials, encoded characters, and other URL schemes are rejected without displaying their contents. Selected images are limited to 12 MiB compressed and 64 megapixels, and sampled down for decoding. Permission denial leaves image import and manual entry available; scanning stops in the background.

Native QR camera permission does not grant camera access to the server WebUI. Web camera capture remains disabled; the existing server microphone permission flow is separate.

The QR format and root-address validation match the Swift companion's connection contract. A shared 58-case fixture and the deliberate platform differences are documented in [Android and Swift contracts](docs/ios-contract-parity.md).

## Voice and files

The selected server may request microphone access. The app first identifies the requesting server, then Android asks for permission. Denial leaves the WebUI available. The app stops captured microphone tracks and pauses media when it moves to the background; it does not offer background recording or guaranteed background streaming. Real-device transcription, Bluetooth/audio routing, audio focus, and audible playback still require device acceptance (see the validation report).

Uploads use Android's document picker, including multiple selection when requested by the WebUI. Downloads use Android's Save dialog. Same-origin HTTPS GETs stream to the selected destination with that profile's matching session/CSRF cookies. Cross-origin redirects and login responses fail without saving a successful artifact. WebUI-generated blobs/data downloads use acknowledged 64 KiB chunks and a 100 MiB limit. The original WebUI still performs its own authenticated fetches and ZIP preparation. Cancellation attempts to delete partial files; a document provider that refuses deletion is reported explicitly.

## Sessions and privacy

Each saved server gets a separate AndroidX WebView profile when the installed provider supports it, including servers sharing a hostname. Renaming preserves the session, including after the editor is recreated. Editing the address clears the old session and allocates a new profile. **Forget** clears cookies, cache, and JavaScript storage before removing the saved server. WebView cannot delete a profile container already loaded in the process, so empty containers are pruned on a subsequent cold start.

Older WebView providers are restricted to one origin. Update Android System WebView for profile switching and Forget; Android Settings → Apps → Agent Zero → Storage → Clear storage resets all local sessions if necessary. Origin-bound messaging and document-start scripts are required; the app shows an update message when unavailable.

If no usable WebView provider is enabled, setup and QR entry remain available with an install/enable message. A workspace requires a working provider. The compatibility report distinguishes Android OS versions from the installed WebView version; an old bundled provider may lack session-deletion support even when it can load a workspace.

Passwords are entered only into the server's page. The app stores server names/origins and local WebView sessions, excludes app data from backup and device transfer, suppresses page console logging, and enables WebView debugging only in debug builds. It has no telemetry, push service, generic JavaScript interface, or broad filesystem permission. A server can access its own browser session, so connect only to servers you trust.

## Build

Tested local toolchain: Android SDK platform 37, build tools 36.0.0, Android Studio JBR 25.0.3, Gradle 9.6.0, AGP 9.4.1, Kotlin/Compose compiler 2.4.20. Dependency versions are pinned in `gradle/libs.versions.toml`; the Gradle distribution checksum is pinned in the wrapper. Minimum API is 26; target API is 37. Actual device/provider coverage is recorded separately from these declared SDK levels.

Set `JAVA_HOME` to a compatible JDK and `ANDROID_HOME` to the SDK, then run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

On this Mac, `./gradlew-local` chooses Android Studio's bundled JDK and the installed SDK without changing global Java configuration. `A0_JAVA_HOME` and `ANDROID_HOME` can override the launcher and SDK paths. The checked-in daemon criteria select Java 25 for Gradle itself. Agents following the installed Gradle skill must invoke these tasks through its compact-output wrapper.

For the optional live WebUI smoke test, supply a reachable HTTPS endpoint:

```sh
./gradlew connectedDebugAndroidTest -Pa0Server=https://agent.example.com
```

The live smoke test reads the WebUI and opens its settings/project/plugin/scheduler surfaces, plus Files and Swarm panels on the supplied test deployment. It checks visible geometry and the real response renderer with synthetic text. It does not send prompts, alter server settings, or read/export existing chat bodies. A different deployment needs the expected registered panels or an adjusted fixture. A server requiring authentication needs an explicitly arranged test session. Without `a0Server`, that check is skipped.

The remaining instrumented tests use a local HTTPS fixture. Its deliberately public test certificate/key are under `app/src/androidTest/assets`; only the certificate is trusted, only for loopback hosts, only in debug builds (`app/src/debug`). Release builds do not package either test trust or the test private key. Do not reuse this certificate for any real service.

Debug output: `app/build/outputs/apk/debug/app-debug.apk`. For your own signed build, run `python3 scripts/prepare-signing.py` once, then `./scripts/gradlew-release app assembleRelease`. This creates protected signing files outside Git; keep an independent encrypted backup. A contributor-generated signing identity cannot update the official installed app. The distribution operator's permanent key signs public APKs, while a separate upload key signs the prepared Play bundle. See [distribution validation](docs/distribution.md) and [deferred Play preparation](docs/play-release.md).

## Implementation map

- `ui`: native setup, profiles, error recovery, Activity results, lifecycle ownership.
- `data`: DataStore profile metadata, isolated identity on origin changes.
- `web`: origin/TLS/navigation policy, WebView lifecycle, profiles, microphone and upload coordination.
- `files`: GET transport, bounded blob receiver, cancellation and SAF output.
- `qr`: offline QR decoding, strict import validation, native camera lifecycle, bounded image import, and review handoff.
- `assets/download-adapter.js`: minimal origin-scoped download/media adapter, including the server splash's document replacement.

The Activity owns WebViews, pending platform callbacks, and transfers. The ViewModel owns metadata state, not Activity/WebView references. Navigating or changing profiles invalidates download generations and cancels pending interactions. No automatic message replay occurs after recreation. WebView history can be restored for the same profile; an unsent composer draft or an in-flight transfer is not guaranteed across process death.

## Delivery evidence

See `docs/validation.md` for the executed checks, limitations, tested server version, APK identity, and milestone status. The original private design history is omitted from this public snapshot. Unavailable physical-device or stock-image checks are not claimed as passed.

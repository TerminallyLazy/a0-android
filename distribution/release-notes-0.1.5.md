Agent Zero for Android is available as a signed public beta for Android 8.0 and newer.

[Install and connect](https://terminallylazy.github.io/a0-android/) · [Privacy](https://terminallylazy.github.io/a0-android/privacy.html)

- Download **AgentZero.apk** below and open it on Android.
- In your WebUI, choose **Get Android app**. The installation QR opens the download page; the connection QR is for **Scan QR code** inside the installed app.
- On an Android browser, **Open in Android** fills in the address. The app waits for you to review and confirm it.
- An Agent Zero server reachable over trusted HTTPS is required. There is no bundled server or AI subscription.

This uses the production package `com.terminallylazy.a0android` with a permanent app signing certificate. Future signed APKs can update this installation in place. The earlier `.debug` preview remains a separate app and does not migrate its local settings automatically.

Google Play publishing is deferred; direct WebUI/APK installation is the available distribution channel. Voice support depends on the server and device. Tests cover emulators; physical-device audio acceptance remains outstanding.

Server administrators can install the optional **AgentZero-WebUI-Install.zip** bridge using its README. It adds the download and connection dialog to a compatible WebUI. Core image upgrades require reapplying the bridge from the persisted `usr` directory. No backend or authentication changes are made.

Checksums, signing certificate information, and dependency notices are attached. The AAB and private signing keys are not public release assets.

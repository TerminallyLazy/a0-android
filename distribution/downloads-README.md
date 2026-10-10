# Agent Zero for Android

Take your own Agent Zero workspace with you. This repository provides signed Android downloads, installation help, privacy information, and support.

**[Download and install →](https://terminallylazy.github.io/a0-android/)**

[Latest APK](https://github.com/TerminallyLazy/a0-android/releases/latest/download/AgentZero.apk) · [Release notes](https://github.com/TerminallyLazy/a0-android/releases) · [Privacy](https://terminallylazy.github.io/a0-android/privacy.html)

## Get connected

1. Download **AgentZero.apk** on your Android phone and open it to install. If Android asks, allow installation from the browser or file app you used. You can turn that permission off afterward.
2. Open the app and choose **Scan QR code** to scan the server QR from your Agent Zero WebUI. You can also enter the HTTPS address manually. On an Android browser, **Open in Android** prefills the server address.
3. Review the full address, tap **Connect**, and sign in on your server’s page if asked.

Requires Android 8.0+, an up-to-date Android System WebView, and your own Agent Zero server reachable over trusted HTTPS. The app does not supply a server, model account, or subscription.

## Public beta

Google Play publishing is deferred. Direct WebUI/APK installation is the current release channel. This APK uses the production package `com.terminallylazy.a0android` and a permanent app-signing certificate. Future releases signed with that certificate can update this app in place. The earlier `.debug` preview is a separate app.

Camera and microphone permissions are optional. Native QR decoding happens on the device. Voice behavior depends on your server and device; physical-device audio testing remains outstanding. The app does not guarantee background streaming.

## Add the download button to your WebUI

Download **AgentZero-WebUI-Install.zip** from the release. It contains a small, reversible integration and README for adding **Get Android app** to the sidebar and Remote Control in compatible Agent Zero versions. It makes no backend or authentication changes. Reapply it after a container image update. It is a separate integration, not an upstream Agent Zero release or Plugin Index entry.

## Support

[Open an issue](https://github.com/TerminallyLazy/a0-android/issues) with the app version, Android version, and a description of the problem. Do not post passwords, tokens, private server URLs, or chat contents. Only share screenshots you have checked for private information.

This repository includes the Kotlin Android application source, build scripts, tests, WebUI installation bridge, and public release materials.

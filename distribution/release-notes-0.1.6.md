# Agent Zero for Android 0.1.6 — Public beta

Download **AgentZero.apk** below or use the [installation page](https://terminallylazy.github.io/a0-android/). Android 8.0+ and your own trusted HTTPS Agent Zero server are required.

- Original Agent Zero launcher branding, including adaptive and themed Android icons.
- Small Android and Apple download icons at the bottom of the WebUI sidebar. The Apple icon opens the [iOS public beta](https://testflight.apple.com/join/xqAFS5er).
- Android install and connection QR codes, plus an Open in Android link with explicit server review.
- Public Kotlin/Compose source, fixtures and build instructions in this repository.

**Install:** open the APK, allow installation from your browser if Android asks, and tap Install. Existing 0.1.5 public installations update in place. Debug previews remain a separate app. Google Play publishing is deferred; no Play account is needed to download this APK.

**WebUI operators:** unpack AgentZero-WebUI-Install.zip and follow its README. The installer upgrades the earlier top-sidebar button to compact footer controls. Backups are retained under usr/android-app-integration-backup. Reapply after a server image upgrade; the integration is not upstream-merged.

Validation: 22 unit tests and release lint passed (0 errors, 10 advisories); APK signature and 16 KiB ZIP alignment verified, installed over 0.1.5 on the API 37 emulator. The preceding connection/QR tests passed 8 cases on API 35; connection behavior is unchanged. WebUI desktop and 390-pixel phone layouts checked. Physical camera and voice acceptance remain pending.

APK signing certificate SHA-256: `7ce92b04c71e869433f292b15fa86b308782f2a2ae019ca65d1c9c2093f2db2d`.

[Privacy](https://terminallylazy.github.io/a0-android/privacy.html) · [Source and support](https://github.com/TerminallyLazy/a0-android)

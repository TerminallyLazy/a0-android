# Public mobile distribution — 9 October 2026

Android's active release channel is the WebUI and signed APK downloads. Google Play registration/payment/submission is deferred at the owner's request. iPhone and iPad users use the existing [Agent Zero Mobile TestFlight beta](https://testflight.apple.com/join/xqAFS5er).

## Published destinations

- Source and releases: https://github.com/TerminallyLazy/a0-android
- Installation page: https://terminallylazy.github.io/a0-android/
- Stable APK link: https://github.com/TerminallyLazy/a0-android/releases/latest/download/AgentZero.apk
- Privacy: https://terminallylazy.github.io/a0-android/privacy.html

The public source starts from an audited snapshot. Local planning history, owner server addresses, private screenshots, build outputs, local configuration and signing material are excluded. Loopback-only instrumentation keys are deliberately public synthetic fixture material; release APKs exclude them. The original private design and validation history remain in the development checkout.

## Android release identity

- Application: `com.terminallylazy.a0android`, version **0.1.6**, code **7**.
- APK size: **2,511,737 bytes**.
- APK SHA-256: `1cd82497772547803b242bd5c730370c6e74352bbb230a4fa21a378f0383342f`.
- App certificate SHA-256: `7ce92b04c71e869433f292b15fa86b308782f2a2ae019ca65d1c9c2093f2db2d`.
- Same signing identity as 0.1.5; installing over that release retains application data. Debug previews use a separate package.
- APK signature and 16 KiB ZIP alignment verified. No fixture certificate/key found by filename or exact bytes.
- Original Agent Zero brand geometry is used for the adaptive and monochrome Android launcher icon. Web assets match the existing iOS/Agent Zero mark.

Keep an independent encrypted backup of the release operator's `~/.config/a0-android/signing/` directory. Keys are not recoverable from the published certificate. The prepared 0.1.5 Play bundle uses a separate upload key; no Play upload or account registration occurred.

## WebUI integration

The bridge adds a small Android button and Apple link to the bottom-left sidebar footer and Remote Control. Android opens the core shared modal, including an install-page QR, direct APK link, plain server QR and explicit connection-review link. Apple opens the TestFlight public beta. The old full-width upper-sidebar button is removed on upgrade.

It was installed on the owner's live test deployment. The integration includes only HTML/component assets and has no backend API or settings mutation. It is not upstream-merged. Keep its installer in the persistent server `usr` directory and reapply after server image upgrades. See [operator instructions](../distribution/webui/README.md).

Connection URLs carry only a validated HTTPS root address. GitHub Pages receives no server address in the HTTP request because it is held in the local URL fragment. There is no third-party QR service, token handoff, automatic connection, or password transfer. Android shows Review server before persistence or network access.

## Executed checks and limits

- 0.1.6: **22 unit tests**, zero failures/errors/skips; release lint **0 errors, 10 advisories**; signed shrunk release APK built and installed over 0.1.5 on API 37.
- 0.1.5 connection behavior retained: **2 connection-link and 6 QR instrumented tests** on API 35 passed. They cover actual ACTION_VIEW delivery, review/recreation, cancellation, rejected input and QR permission/import lifecycle. No full OS matrix rerun is claimed for the icon-only 0.1.6 runtime change.
- **3 Node contract tests** verify HTTPS parsing, fragment/intent handoff and exact module parity between the install page and WebUI.
- **2 installer tests** verify upgrade/idempotence, backup preservation, and no entry-point mutation when a layout anchor is unsupported.
- Live WebUI: footer controls visible, Android modal opens/closes, two locally generated QR images, exact TestFlight URL, and original logo. At a 390×844 viewport the modal uses one column, has no horizontal overflow and scrolls within the viewport.
- The public TestFlight link displays Agent Zero Mobile and the View in TestFlight action. No iOS build or beta-group change was needed.
- Physical camera, voice/audio quality, real authenticated exports and the broader accessibility/network matrix remain pending. Historical application functionality and messaging evidence is in [UI repair](ui-repair.md) and [validation](validation.md).

All Gradle runs use the managed `gradle-run` wrapper. The release task question was “Does the branded 0.1.6 APK build and pass unit tests and release lint?”; nested tasks `testDebugUnitTest lintRelease assembleRelease` passed. Full Gradle logs are temporary and never part of public source.

The independent public source checkout also passed `testDebugUnitTest assembleDebug` (22 tests). The final asset-only packaging run included the original mark’s MIT notice inside the APK. All eight 64-bit native libraries have ELF load alignment of at least 16 KiB.

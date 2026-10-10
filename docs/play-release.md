# Google Play preparation — 0.1.5 (6)

**Deferred by the owner:** direct APK distribution from the WebUI is the active release channel. Do not pay the registration fee or continue account creation unless requested. The files below are retained for a future store release; no store availability is claimed.

## Prepared

- Package: `com.terminallylazy.a0android`; version 0.1.5 / code 6; min API 26; target API 37.
- Signed sideload APK and separately upload-key-signed AAB under `artifacts/`.
- Public installation page: https://terminallylazy.github.io/a0-android/
- Privacy URL: https://terminallylazy.github.io/a0-android/privacy.html (also linked inside the app).
- English listing text in `store-listing/en-US/`; category recommendation: Productivity, App, free.
- 512×512 icon, 1024×500 feature graphic, and two actual emulator screenshots under `store-listing/en-US/images/`. Screenshots contain only setup UI and a reserved example hostname.
- Release notes, checksum file, dependency notices, and the public app-signing certificate.

## Account handoff

On 2026-10-09, the signed-in Google account opened the **Creating a developer account** screen, not a Play Console app dashboard. No developer account was created, no fee paid, no agreement accepted, and no app uploaded. The owner must select the correct individual/organization account, complete identity/contact verification and registration, and provide an appropriate public support email. Do not infer these legal/account choices from a GitHub profile.

After registration, create **Agent Zero for Android**, English (United States), App, free, then use an internal-testing track first. Do not submit a production release until the remaining policy and reviewer-access items below are addressed.

## Keep APK and Play updates compatible

Stable local keys are outside Git at `~/.config/a0-android/signing/` with owner-only access:

- `app.p12` and `app.properties`: permanent app-signing identity used by public APKs.
- `upload.p12` and `upload.properties`: separate upload identity for AAB uploads.
- `app-certificate.pem` and `upload-certificate.pem`: public certificates.

Keep an independent encrypted backup of the whole directory. Do not publish the key files or properties, put them in issue attachments, or commit them to any repository.

When enrolling in **Play App Signing**, choose to supply the **existing app-signing key**. Follow the Console-provided PEPK export/encryption instructions using `app.p12`. Do not choose a new Google-generated signing identity for this package: the installed sideload APK needs the same signing identity to receive compatible Play updates. Register `upload-certificate.pem` as the upload certificate and upload the AAB signed by `upload.p12`. No private key export has been prepared because the account-specific Play encryption key is not yet available.

Build through the managed Gradle wrapper when acting as an agent. Nested launchers:

```sh
./scripts/gradlew-release app assembleRelease
./scripts/gradlew-release upload bundleRelease
```

Do not distribute an unsigned APK or use the upload key to sign public APKs. Verify the artifact certificate before every release.

## Remaining before Play submission

1. **Developer registration and app identity.** Complete the Console setup and Android developer verification/package registration applicable to distribution. ADB installation is not proof of consumer installation eligibility in every region.
2. **Private reviewer access.** Provide a dedicated stable HTTPS review server containing only synthetic data, with review credentials entered directly in App access. Do not give reviewers the owner's working server or its existing chats and files.
3. **AI-content reporting.** This client centers on AI chat. Google's current policy requires in-app reporting/flagging of offensive generated content without leaving the app. The current beta has public bug-report links but no such private in-app reporting service. Agree a report recipient, private endpoint, data handling/retention and moderation workflow, then implement and verify that flow before production submission. A public GitHub issue or mail app handoff is not a substitute. Also assess how the supported server configuration prevents restricted content under that policy.
4. **Data safety, content rating, target audience, permissions.** Complete accurate declarations for the final distribution. The native shell has no analytics/ads/crash SDK, but chat, optional audio, selected files, and sign-in information pass to the selected server and its providers. Do not automatically answer “no data collected” just because the app developer does not operate those servers. Assess Google's WebView and user-directed-transfer definitions. The app itself creates no developer-hosted account; distinguish that from accounts on the selected server.
5. **Testing access.** For personal developer accounts created after November 13, 2023, Google currently requires at least 12 testers opted into a closed test continuously for 14 days before applying for production access. Emulator checks do not satisfy this requirement. Account-specific device verification may also require access to a real Android device.
6. **Device coverage.** Complete voice/audio and real-device checks before claiming those as verified. Store assets currently describe the release as a beta.

## Official references checked 2026-10-09

- [App signing and cross-store identity](https://developer.android.com/studio/publish/app-signing)
- [Create and set up an app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)
- [Personal-account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [AI-generated content policy](https://support.google.com/googleplay/android-developer/answer/13985936?hl=en)
- [Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [Android developer verification](https://developer.android.com/developer-verification)

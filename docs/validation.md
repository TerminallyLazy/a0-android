# Android delivery validation — 9 October 2026

This receipt distinguishes implemented features, executed tests, and acceptance that still needs a device or deployment. The app implements the M0–M4 local scope; the full acceptance matrix in the approved plan is not yet certified.

## Public distribution — 0.1.6

Production signing, public source/APK distribution, WebUI download and connection links, QR handoff, and compact Android/iOS footer actions are covered by [the distribution receipt](distribution.md). This supersedes the initial M4 unsigned/local-only status below. Google Play is deferred; the physical-device and authenticated-server limits remain explicit.

## Phone UI and response recovery — 0.1.4

The collapsed viewport-unit bug, phone panel-only plugin route, and missing socket snapshots are repaired in the Android host. The connection header is compact, navigation no longer overlays the transcript, and Tools opens labeled panels. **52 tests passed** on API 35: 20 unit and 32 instrumented, zero skips. Both APK variants build; both lint variants have zero errors and nine advisories. On the visible API 37 emulator, one real composer/Send prompt received a visible `ANDROID_REPLY_OK` response automatically. The [detailed repair evidence and limits](ui-repair.md) distinguish this round trip from the earlier read-only readiness checks.

The delivered APK is `artifacts/AgentZero-0.1.4-debug.apk`, version code **5**. The versioned receipt records its checksum, signature, test results and source identity. API 26/33 and the full API 37 automated matrix were not rerun for this repair; their 0.1.3 evidence below remains historical.

## Compatibility and Swift alignment — 0.1.3

Android now checks for an enabled WebView provider before AndroidX feature discovery or workspace creation. An Android 8 image with no usable provider previously crashed during startup; it now keeps native setup and QR/manual entry available and shows an install/enable message. Separately, manual URLs are validated before OkHttp can normalize away dot paths or repair a missing authority delimiter. This matches the Swift companion's root-origin contract. The shared 58-case fixture passes both the Kotlin parsers and the audited Swift declarations; see [the contract comparison](ios-contract-parity.md).

The final source passed **20 unit tests**, both lint variants (**zero errors, nine advisories each**), debug assembly and shrunk unsigned release assembly. The delivered debug APK's v2 signature verifies, and an installed-APK cold launch was checked visually. Release inspection found neither the fixture certificate nor its private key, by entry name and exact bytes.

| Device / provider | Executed result | Scope |
|---|---|---|
| Android 17 / API 37, WebView 145.0.7632.218 | 27/27 instrumented checks, zero skips | Full browser/QR matrix and live readiness, final 0.1.3 |
| Android 15 / API 35, WebView 145.0.7632.218 | 27/27, zero skips | Same full matrix, final 0.1.3 |
| Android 13 / API 33, WebView 145.0.7632.218 | 27/27, zero skips | Same full matrix, final 0.1.3 |
| Android 8 / API 26, no usable WebView provider | 1/1 focused case via direct ADB instrumentation | Setup, QR/manual entry, blocked workspace, Retry and recreation, final 0.1.3; no working workspace claim |
| Android 15 bundled WebView 124.0.6367.219 | 24/27 passed before the update | Historical 0.1.2 build with the new restart test; three cases failed at session deletion/origin replacement because DELETE_BROWSING_DATA is unavailable |

The normal matrix explicitly excludes the environment-specific `UnavailableProviderTest`; it is exercised separately on API 26. The Gradle connected task returned success with zero test results on that image, so this is not counted as a Gradle test pass. Direct `adb shell am instrument` ran the actual case and returned `OK (1 test)`. The normal API 33/35/37 counts come from JUnit XML. Final counts represent repeated execution on three OS versions, not 81 different test scenarios.

The task-created API 33/35 images were updated using the signed Google WebView 145 APK copied from the existing API 37 emulator. Android accepted the package update normally; no signing bypass was used. The provider APK SHA-256 is `18bdbd4e15e226565b9772174b59c8ce58e56692c19d4a4be143395071c24162`. This is compatibility evidence for that provider version, not a claim that all bundled/legacy WebViews pass. The API 26 image has a WebView package but no registered usable provider; a real Android 8 workspace and the single-profile legacy fallback remain unverified.

The new HTTPS restart fixture preserves the same port, certificate, profile cookie and localStorage across a stopped/restarted server. Reload shows the network failure, explicit Retry restores the workspace, and the first request is a GET. MockWebServer's ephemeral-bind reuse behavior needed a fixture correction before this case could run. This proves synthetic restart recovery, not a Wi-Fi handoff or replay guarantees for arbitrary WebUI/plugin code.

The supplied live endpoint still reports **R v2.13+30**, commit `a4e65d472183252f9d54c9585108cbb7b776b7c4`, with no health error. All three final normal-device runs exercised read-only WebUI/sync/modal readiness. No prompts, settings edits, real authenticated exports, or physical microphone/camera checks were performed. The owner selected emulator-only continuation.

Delivery: `artifacts/AgentZero-0.1.3-debug.apk`, version code **4**, **18,160,007 bytes**, SHA-256 `1146a3d5c1207c06f93310942e63b4f127abeeef2298bab28903886ed63e57b5`. The versioned receipt, per-device results, shared-contract result, screenshots and bounded Gradle ledger are retained in `artifacts/`. Earlier deliveries remain unchanged. The Swift checkout at `160649dfacfa935e1b2f1ee7968bd7f83d2a2f4c` was not edited; its full app/test suite was not rebuilt.

## Restoration update — 0.1.2

The server editor now saves the existing profile ID across recreation and resolves it only after DataStore loads. Renaming retains the browser session; changing the origin still clears the previous session and creates a fresh profile. An edit whose profile has been removed cannot silently become an add operation.

A clean build on the current Android Studio configuration passed **44/44 tests**: 18 unit and 26 instrumented, with zero skips. The two new regression tests cover a renamed profile retaining its ID/cookie/localStorage and an edited origin replacing the old profile. Existing QR, upload/download, microphone permission/lifecycle, TLS, isolation, recovery, and live WebUI checks also passed on API 37 / WebView 145.0.7632.218.

Both debug and release APKs use **AGP 9.4.1**. The IDE's parallel-sync property and Java 25 daemon criteria were preserved unchanged and adopted with the verified toolchain. Both lint variants report zero errors and nine advisories. The debug APK installs and its v2 signature verifies. The unsigned release APK contains neither the fixture certificate nor its private key.

An installed-APK check also restored an unfinished server-name edit after HOME followed by `run-as` SIGKILL of the app process (the ordinary `am kill` request did not terminate it). Relaunch restored the editor draft, and Connect updated the same saved profile without duplication. This tests the native profile editor, not WebUI composer recovery or process death during platform file dialogs. The editor was visually checked at font scale 1.5 with its address and actions readable; Cancel remained usable. The scale was restored to 1.0. Screenshots and `artifacts/manual-checks-0.1.2.json` record those checks.

Delivery: `artifacts/AgentZero-0.1.2-debug.apk`, version code 3, **18,160,007 bytes**, SHA-256 `8ae07fc1e429667026e21da809553c5bfe8da80966b7df9419e8cde57b3a4e75`. Versioned delivery, test, and Gradle receipts are retained alongside it. Earlier APKs and receipts are unchanged. The user selected emulator-only continuation; physical camera and voice acceptance remains pending, along with the broader acceptance gates below.

## QR update — 0.1.1

The user-authorized QR extension adds **Scan QR code**, native camera scanning, and saved-image import. A validated HTTPS origin is shown in full with wrapping in a scrollable review form; only **Connect** saves a profile and opens its workspace. Camera frames stay on the device, and camera capture stops when backgrounded. Details and source contracts are in `docs/qr-connection.md`.

The updated regression matrix includes **18 unit tests and 24 instrumented tests**. New cases cover strict QR payload validation, rotation/inversion, padded camera planes, non-QR rejection, camera denial, image import, unsafe-code rejection without echoing secrets, canceled imports, review recreation, no network before confirmation, and camera closure/resumption across backgrounding and recreation. Instrumented QR image tests supply synthetic document-picker results; a separate installed-APK check exercises the actual Android document picker with `artifacts/live-server-qr.png`.

Final result: **42/42 tests passed, zero skips**. The final instrumented run used the independent `A0_QR_VERIFY` emulator (`emulator-5580`), Android 17/API 37 with WebView 145.0.7632.218, to avoid overlapping IDE deployments on the first emulator. Debug/release lint each reports zero errors and ten advisories (four version suggestions and six optional KTX suggestions). Both APK variants built successfully. The debug APK's v2 signature verifies; the unsigned release APK excludes the loopback fixture certificate and private key. Both manifests deny cleartext and backup, and the QR Activity is non-exported.

Concurrent IDE changes were preserved in `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, and the AGP version in `gradle/libs.versions.toml`. They are excluded from the QR feature commit. The delivered debug APK metadata reports **AGP 9.4.1**; the earlier unit/lint/release checks used 9.4.0 with the same app source. The committed version catalog retains 9.4.0. The versioned receipt records these local build-configuration differences instead of claiming a clean source tree.

Versioned APK, checksum, Gradle/test receipts, and screenshots are under `artifacts/` with the `0.1.1` suffix. The original `0.1.0` APK and receipts remain intact. Physical-camera optical scanning is still pending; emulator preview/lifecycle and decoded-image fixtures do not substitute for that check. The original physical voice and authenticated live-server gates below also remain open.

## Initial environment and server — 0.1.0

- Host: Apple silicon macOS, Android Studio JBR 25.0.3.
- Gradle 9.6.0 (distribution SHA-256 pinned), Android Gradle Plugin 9.4.0, Kotlin/Compose compiler 2.4.20.
- Android platform 37, build tools 36.0.0; declared minSdk 26, targetSdk 37.
- Executed Android tests on the isolated ARM64 `A0_API37` emulator, Android 17/API 37, Android System WebView **145.0.7632.218**. No physical Android device was connected.
- Owner-supplied public HTTPS endpoint: `[private test endpoint omitted]`. `/api/health` reported **R v2.13+30**, branch `ready`, commit **a4e65d472183252f9d54c9585108cbb7b776b7c4**, commit time `2026-10-08 13:51:32`, with no health error. The full commit is recorded in the delivery JSON receipt.
- Microsoft's tunnel first-visit Continue page was exercised normally. No certificate override or tunnel-warning bypass header was added to the app. See [Microsoft's tunnel security documentation](https://learn.microsoft.com/en-us/azure/developer/dev-tunnels/security).
- The WebUI itself displayed its authentication-disabled warning on this endpoint. HTTPS transport is confirmed; an authenticated live deployment was not supplied. Existing server settings, chats, and files were not changed.
- The local source audit used `agent-zero` at `1e6af2151fc71c326191fdb6e46d09412a3df3ee`; the unrelated checkout was left untouched. No stock Docker image digest has been independently established for the supplied endpoint.

Initial 0.1.0 result: **11/11 unit tests and 18/18 instrumented tests passed, with no skips.** Debug and release lint each report zero errors and nine advisories (four version-update suggestions and five optional KTX-style suggestions). Both APK builds passed; the release variant also uses resource shrinking. The installed debug APK signature verifies with APK Signature Scheme v2.

## Executed coverage

The final task results and test counts are recorded in `artifacts/delivery-receipt.json`, with bounded XML-derived test results in `artifacts/test-results.json`. The original failing iterations and their resolutions are retained in `docs/gradle-verification.md`, without raw Gradle logs.

| Area | Checks |
|---|---|
| Origin policy | HTTPS roots, normalization, ports, IDN/IPv6, credentials, subpaths, query/fragment rejection, lookalike/off-origin and gesture rules |
| Download policy | Filename sanitation, declared size, chunk sequence/length, incomplete streams |
| Native HTTPS transport | Matching cookies, exact bytes, same-origin redirects, blocked foreign redirects and login responses |
| Android bridge | Secure-context and required provider capabilities; immediate blob revocation; document replacement; rejected same-origin subframe handshake |
| Android downloads | SAF save and exact bytes, zero-length file, multi-chunk checksum, 100 MiB rejection, picker cancellation, late canceled-picker destination cleanup |
| Android uploads | Actual user gesture, system document picker, readable selected content returned to the WebUI |
| Media | Native explanation, microphone grant/denial, emulator capture track creation, capture stopped on background transition |
| Sessions | Cookie/localStorage isolation on identical origins; same hostname/different ports; Forget clears target data while another profile retains its cookie |
| Recovery | Activity recreation returns to the same profile; renderer termination presents Retry and recreates a working view |
| TLS | An untrusted fixture certificate is canceled before an HTTP request is accepted |
| Live server | HTTPS WebUI bootstrap, normal tunnel continuation, adapter presence, WebUI sync store reaches HEALTHY, settings/scheduler/projects/plugins modal content loads |

Live surface checks only inspect readiness/state booleans. They do not print settings values, private file contents, or chat bodies. Fixture cookies/files are synthetic. Native authenticated file handling was tested against the HTTPS fixture, not a real user login.

## Milestone status and remaining gates

| Milestone | Implementation/delivery | Acceptance limits |
|---|---|---|
| M0 | App, wrapper/catalog, native theme, manifest/trust/backup policy, installation and capability fixture implemented | API 33/35 runtime coverage is recorded above. API 26 covers only the missing-provider gate; a working WebView on Android 8 remains unverified |
| M1 | HTTPS setup, server-hosted workspace, navigation, errors, local-network permission, session/recreation handling implemented | Live authenticated login/expiry, actual agent prompt/streaming, canvas interactions and Wi-Fi change remain to be exercised; synthetic HTTPS server restart now passes; live checks establish WebUI/sync/surface readiness |
| M2 | Microphone and file chooser flows, GET streaming, bounded blob/data adapter, progress/cancel/cleanup implemented | Physical voice recording/transcription and audible TTS, Bluetooth/routing/audio-focus tests pending. Real-server directory ZIP, bulk ZIP, attachment and chat-export workflows need end-to-end acceptance; fixture blobs prove the handoff and bytes, not those upstream operations |
| M3 | Named isolated profiles, conservative old-provider fallback, scoped Forget, renderer/lifecycle recovery implemented | Process death during platform dialogs, long background sessions, TalkBack, broader font-scale coverage, split screen, a usable single-profile legacy provider and physical resource profiling remain pending |
| M4 | Debug APK, source, build/usage docs and validation receipts prepared; production variant build checked separately | No production signing, Play publication, remote Git publication, or stock-Docker certification. Debug emulator acceptance does not substitute for the missing physical-device gates |

No HTTP mode, trust-all TLS, native credentials, background recording service, generic privileged JS bridge, or notification guarantee was added. WebUI camera capture is deferred; ordinary document upload remains available. The 0.1.1 native camera permission is used only by the QR scanner.

## Important implementation findings

1. WebView profiles remain loaded in the process after their views are destroyed. `ProfileStore.deleteProfile()` cannot delete a loaded profile. Forget therefore clears cookies, cache and JavaScript storage using `WebStorageCompat.deleteBrowsingData`, waits for completion, removes profile metadata, and queues the empty container for a cold-start prune. [ProfileStore](https://developer.android.com/reference/androidx/webkit/ProfileStore), [WebStorageCompat](https://developer.android.com/reference/androidx/webkit/WebStorageCompat).
2. Chromium's media diagnostics required both `RECORD_AUDIO` and `MODIFY_AUDIO_SETTINGS`. Both are declared; recording still requires the Android runtime grant and an allowed requesting origin. The emulator proves lifecycle/permission behavior, not audible quality or transcription.
3. Agent Zero replaces its splash document in place. The origin-scoped adapter preserves Blob references before immediate URL revocation and reattaches document handlers after replacement.
4. HTTP/2 can split Cookie into several header fields. The test fixture was corrected to combine all fields; the production native client sends the complete profile cookie string and rejects cross-origin redirects.
5. Permission, upload and save callbacks belong to the Activity/controller. Navigation and disposal invalidate them. A late Save result after cancellation is deleted where the document provider permits it. Platform state cannot guarantee recovery across abrupt process termination.

## Artifact identity

The initial signed debug APK is `artifacts/AgentZero-0.1.0-debug.apk`; its checksum, size, signing verification, source revision, clean-tree state, and task outcomes are in `artifacts/delivery-receipt.json`. The QR update is `artifacts/AgentZero-0.1.1-debug.apk` with a separate `artifacts/delivery-receipt-0.1.1.json`. The restoration update is `artifacts/AgentZero-0.1.2-debug.apk`, with `artifacts/delivery-receipt-0.1.2.json`. The latest compatibility update is `artifacts/AgentZero-0.1.3-debug.apk`, with `artifacts/delivery-receipt-0.1.3.json`. The release build is unsigned and is not the installable delivery. The release APK is inspected to ensure it excludes the debug-only loopback fixture certificate and instrumentation key.

The debug fixture certificate is public test material, scoped to loopback in `app/src/debug`; its private key is instrumentation-only. Do not use it on a real server. Release trust is system roots only, cleartext is denied, browser debugging is disabled, and app data is excluded from backup/transfer.

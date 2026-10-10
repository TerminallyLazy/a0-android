# Gradle verification record

## UI and messaging repair — 0.1.4

Managed wrapper: `gradle_run.py`, workflow `8649c8af1ff61e9bf95c1126b1e46d55`. Nested launcher `build/gradlew-api35` selects the isolated API 35 test emulator. The visible API 37 emulator was reserved for the owner and installed-APK checks.

| Verification question | Bounded answer |
|---|---|
| “Do WebView viewport units retain the visible height after resize?” | Initially failed: 100vh was zero despite a nonzero viewport. Passed after explicit MATCH_PARENT layout parameters, including portrait/landscape resize. |
| “Are the compact controls, dialogs, rail, and response renderer usable?” | Passed three focused cases. Development fixture corrections used an absolute module import URL and a temporary visible transcript because a fresh profile starts on the welcome screen. |
| “Do phone plugin panels and stalled-sync recovery work without replaying messages?” | Passed in the final aggregate suite. The recovery fixture confirms one read in flight, background suspension, resumed recovery, no Send replay, and no competing degraded-state polling. Live Files/Swarm opening and gate rejection pass. Earlier iterations corrected a width-transition assertion and advanced Compose's test clock before injecting Android Back. |
| “Does the complete Android regression suite pass and do both APK variants build and lint?” | Passed: 20 unit and 32 instrumented tests, zero skips; both APK assemblies; both lint variants with zero errors and nine advisories. Aggregate run: 109.344 seconds. |

Focused nested task: `:app:connectedDebugAndroidTest`. Aggregate nested tasks: `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`, `:app:lintDebug`, `:app:lintRelease`, `:app:assembleDebug`, `:app:assembleRelease`. Exact bounded outcomes for all twelve runs are in `artifacts/gradle-results-0.1.4.json`. No raw Gradle logs were reopened or copied. The protobuf/JDK Unsafe advisories remain toolchain warnings.

The workflow was finished after the aggregate passing run. Finish removed only wrapper-owned logs.

## Initial build — 0.1.0

All Gradle commands ran through the installed `gradle-run` compact-output wrapper, workflow `c833157592cb2c1c23f8f0b412a2b1af`. Raw build logs were not reopened or copied into this repository. Questions and bounded answers below include failed development iterations, followed by their corrections.

| Run | Verification question | Bounded answer | Seconds |
|---|---|---|---|
| 1 | “Can the project generate a verified Gradle 9.6 wrapper with the installed JDK?” | Passed; wrapper generated and official distribution checksum pinned. | 6.6 |
| 2 | “Does the Kotlin foundation compile with the pinned Android 37 toolchain?” | Passed; Kotlin foundation compiled. | 40.5 |
| 3 | “Does the integrated Android shell compile?” | Passed; integrated shell compiled. | 4.7 |
| 4 | “Do unit tests, Android lint, and debug APK assembly pass?” | Initially failed lint because the debug trust domain lacked includeSubdomains; corrected. | 29.1 |
| 5 | “Do the integrated APK, lint, unit tests, and emulator browser tests pass, including the supplied HTTPS server?” | Initially failed instrumentation teardown because loaded profiles could not be deleted. Unit tests, lint, and debug assembly passed. | 74.8 |
| 6 | “Do the eight emulator browser tests pass after correcting profile deletion?” | Six of eight passed; microphone permission and live first-visit handling needed diagnosis. | 68.0 |
| 7 | “What are the microphone and live-server bootstrap outcomes with explicit diagnostics?” | Focused diagnostic identified NotReadableError; Chromium required MODIFY_AUDIO_SETTINGS. The combined class filter executed only the first method. | 6.9 |
| 8 | “Do media, file-boundary, session-clearing, TLS, and live-WebUI checks pass with the required audio permission?” | Twelve of thirteen passed; the tunnel first-visit page awaited Continue. | 98.5 |
| 9 | “Do all seventeen emulator scenarios pass, including authenticated files, upload, renderer recovery, and live tunnel continuation?” | Thirteen of seventeen passed; tests needed trusted picker gestures, recovery timing, the existing WebUI sync store, and a complete HTTP/2 cookie check. | 111.2 |
| 10 | “Do the eighteen emulator checks pass after correcting picker gestures, recovery timing, and live sync inspection?” | Seventeen of eighteen passed; the HTTP/2 fixture read only the first Cookie field. | 88.1 |
| 11 | “Does authenticated GET-to-SAF download pass when the fixture reads all HTTP/2 cookie fields?” | Passed; the native authenticated download saved the exact fixture bytes after the fixture correction. | 9.6 |
| 12 | “Does the clean delivery build pass unit tests, lint, all 18 emulator checks, and debug/release assembly?” | Passed from a clean app build: 11 unit tests, 18 instrumented tests, both lint tasks, debug assembly, and unsigned release assembly. | 70.7 |
| 13 | “Does final popup cleanup and explicit feature gating preserve all tests, lint, and both APK builds?” | Passed: all 29 tests, both lint tasks, debug APK and shrunk unsigned release APK. | 111.4 |

The nested tasks were `wrapper`, `compileDebugKotlin`, then the combinations of `testDebugUnitTest`, `lintDebug`, `lintRelease`, `assembleDebug`, `assembleRelease`, and `connectedDebugAndroidTest` appropriate to each question. The exact bounded run summaries are in `artifacts/gradle-results.json`.

JDK 25 emits tool-dependency warnings for protobuf `sun.misc.Unsafe`; these are not application compiler errors. Newer-dependency and optional KTX-style lint advisories are recorded separately in the final test receipt. No lint baseline hides errors.

The workflow was finished successfully after the last passing run. Finish removed only wrapper-owned logs; the bounded receipts above remain.

## QR extension — 0.1.1

The same managed wrapper was used in workflow `9261cbcacf1fb5034e5d70d6dce5b2fd`:

| Run | Verification question | Bounded answer | Seconds |
|---|---|---|---|
| 1 | “Do QR validation and decoding tests pass with the app compiling?” | Passed; 18 unit tests. | 15.2 |
| 2 | “Do QR review, cancellation, permission, and camera lifecycle work on Android?” | Five of six passed; the confirmation test blocked on a network request before letting Compose advance the workspace transition. | 49.3 |
| 3 | “Does QR confirmation connect after the Compose test waits for the workspace transition?” | Passed after the test awaited the workspace before waiting on the fixture request. No product networking change was required. | 10.0 |
| 4 | “Do all tests, both lint checks, and debug/release APK builds pass with QR support?” | Passed; 18 unit and 24 instrumented tests, zero skips, both lint tasks, and both APK variants. | 112.3 |

The first workflow was finished and its wrapper-owned logs removed. A subsequent visual check of the installed APK identified clipped text in the scanned-address field. The review was changed to show the entire address with wrapping in a scrollable form; the input remains editable for manual entry. A new managed workflow, `71d9d0cd51d799087b9c82b1141cca01`, verifies that final UI change.

| Run | Verification question | Bounded answer | Seconds |
|---|---|---|---|
| 1 | “Does the final URL review pass all tests, lint, and APK builds?” | Unit tests, both lint tasks, and APK builds passed. Instrumentation was interrupted after two tests passed: device events show an external force-stop/replacement install, and the runner returned status -1 without an assertion failure. Verification moved to an independent AVD on port 5580. | 59.1 |
| 2 | “Do all 24 emulator tests pass on the isolated verification device?” | The AGP `--serial` path failed with UnsupportedOperationException before tests started. Inspection of the installed AGP source found that this path mutates the serial list obtained from a Gradle ListProperty. The supported ANDROID_SERIAL environment selector avoids that path. | 3.8 |
| 3 | “Does ANDROID_SERIAL select the isolated emulator and pass all 24 tests?” | Passed: all 24 instrumented tests, including the QR review and supplied live server, with zero skips. Final source also passed 18 unit tests, both lint tasks, and both APK builds in run 1. | 82.3 |

The independent device is `A0_QR_VERIFY`, Android 17/API 37. An ignored local `build/gradlew-qr-device` wrapper passes `ANDROID_SERIAL=emulator-5580` to `gradlew-local`, so tests cannot target the emulator being used by the IDE. IDE-generated `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, and the concurrent AGP 9.4.1 version-catalog update were preserved separately from this feature's commit. Final debug APK metadata identifies AGP 9.4.1; the preceding unit/lint/release build used 9.4.0 with the same app source.

The second workflow was finished after its successful isolated run. Finish removed only wrapper-owned logs. Final lint reports contain zero errors and ten advisories per variant: four dependency/tool version suggestions and six optional KTX-style suggestions.

Nested tasks: `testDebugUnitTest`, the focused and complete `connectedDebugAndroidTest` runs, `lintDebug`, `lintRelease`, `assembleDebug`, and `assembleRelease`. Versioned compact receipts are saved in `artifacts/gradle-results-0.1.1.json`. Full logs are not retained in the repository.

## Restoration and toolchain alignment — 0.1.2

All runs used the managed `gradle-run` wrapper in workflow `267029357f595d63f8cea0413bdb1112`.

| Verification question | Bounded answer | Seconds |
|---|---|---|
| “Does editing retain server identity after recreation?” | Failed as expected before the fix: the editor became “Connect your server” instead of “Edit server” after Activity recreation. | 11.963 |
| “Do restored profile edits preserve session boundaries?” | Passed both focused tests. A rename preserves the existing ID, cookie and localStorage; changing the origin replaces the profile and starts with no old cookie. | 15.086 |
| “Does the clean build pass all tests, lint, and both APK variants on AGP 9.4.1?” | Passed: 18 unit and 26 instrumented tests, no skips; both lint tasks (zero errors, nine advisories each); debug and unsigned release assembly. | 118.154 |

Focused nested task: `connectedDebugAndroidTest`, selected by method or `tests_regex`. Aggregate nested tasks: `:app:clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease connectedDebugAndroidTest`, with the owner-supplied HTTPS server. The same isolated `emulator-5580` was selected through `ANDROID_SERIAL`; the IDE's emulator was left alone.

All final checks and both APK variants use AGP 9.4.1 and the preserved Android Studio Java 25 daemon criteria. This supersedes the mixed 9.4.0/9.4.1 evidence in 0.1.1. Bounded task outcomes are in `artifacts/gradle-results-0.1.2.json`; test cases are in `artifacts/test-results-0.1.2.json`. No raw Gradle logs are copied into the repository.

The workflow was finished successfully after the passing aggregate run. Finish removed only wrapper-owned logs; bounded receipts remain.

## Compatibility and Swift contracts — 0.1.3

All Gradle commands used the managed `gradle-run` wrapper in workflow `a8f32152c0f5f4429f399067666a7272`. Each invocation was a standalone wrapper command. No full Gradle logs were reopened or copied.

| Runs | Verification question | Bounded answer |
|---|---|---|
| 1–3 | “Does Retry recover after an HTTPS server restart?” | First two attempts failed with EADDRINUSE while rebinding the test server. Inspection of MockWebServer source showed it resets address reuse for an ephemeral first bind. The fixture now preserves reuse; the focused case passed, followed by all three final OS runs. |
| 4–6, 12 | “Do Android 15 emulator checks pass?” | Required-feature smoke passed on bundled WebView 124. The full run passed 24/27; deletion-dependent cases need DELETE_BROWSING_DATA. Updating to signed WebView 145 yielded 27/27, then the final 0.1.3 run passed 27/27 in 55.232 seconds. |
| 7, 9, 13 | “Do Android 13 emulator checks pass?” | Initial WebView 145 run passed 27/27. A comma-separated class filter later ran only the first class (21 tests); that result was not counted as the full matrix. The final notClass exclusion ran all 27 normal cases on 0.1.3, zero skips, in 78.833 seconds. |
| 8, 10 | “Does Android 8 handle a missing WebView without crashing?” | Gradle returned zero with no test cases; this is no test-pass evidence. Direct ADB instrumentation on the final compiled APK ran UnavailableProviderTest and returned OK (1 test), followed by a visual cold-launch check. A functioning WebView workspace was unavailable on this image. |
| 11 | “Does 0.1.3 pass the complete build and Android 17 checks?” | Passed: 20 unit tests, 27 instrumented cases, both lint tasks (zero errors, nine advisories each), debug and unsigned release assembly, in 110.307 seconds. |

Targeted nested task: `connectedDebugAndroidTest`. Aggregate nested tasks: `testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease connectedDebugAndroidTest`. Local `build/gradlew-api26`, `-api33`, `-api35` and `-api37` launchers pass `ANDROID_SERIAL` to `gradlew-local`; final task emulators used ports 5584, 5582, 5580 and 5586 respectively. The IDE's emulator 5554 was not changed. Normal full runs exclude only `UnavailableProviderTest` using `android.testInstrumentationRunnerArguments.notClass` and supply the owner's HTTPS endpoint for the live readiness case.

`artifacts/gradle-results-0.1.3.json` preserves 13 bounded command/question/status/timing records. Per-device XML-derived results and the separate direct-ADB receipt record the actual test counts. The source-level Swift contract check uses `xcrun swiftc` on the existing origin/QR declarations, not Gradle or a full iOS application build.

The workflow was finished after final validation. Finish removed only wrapper-owned logs; bounded receipts remain. Tool dependency warnings about protobuf `sun.misc.Unsafe` remain unrelated to application compiler/lint errors.

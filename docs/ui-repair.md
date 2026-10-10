# Phone UI and messaging repair — 0.1.4

The owner reported collapsed dialogs, a tiny rail, cramped controls and missing replies. The earlier readiness checks proved that the WebUI loaded, but did not prove visible dialog content or a complete message round trip. This update adds those checks.

## Layout and panels

The Compose-hosted WebView had implicit wrap-content layout parameters. JavaScript reported a normal `innerHeight`, but Chromium resolved `100vh`, `100dvh`, `100svh` and `100lvh` to zero. The server's dialog and rail height limits therefore collapsed. Explicit `MATCH_PARENT` dimensions fix this at the native boundary. The regression failed before the fix and passed in portrait and landscape afterward. This follows Android's [WebView sizing guidance](https://developer.android.com/develop/ui/views/layout/webapps/best-practices).

The native connection header is now 48 dp with a dark surface and an overflow menu. Phone navigation occupies a separate row above the scrollable transcript. The input uses a full-width text row and a separate row of 44-pixel minimum controls. The rail becomes a labeled Tools tray.

On the supplied server, Swarm has a registered docked panel but no modal path. The stock phone route asks it to open as a modal and returns false. Android now uses the same registered docked lifecycle as the Swift companion's `WorkspaceWebAdapter.js`, presenting the panel at the full phone width. Server `canOpen` checks, surface visibility, callbacks and content remain authoritative. Rejected opening produces an explanation. Android Back dismisses an open tray, panel or modal before leaving the workspace. The Swift repository was read, not edited.

This is an origin-scoped mobile adapter, not a bundled WebUI copy. It depends on the server's current canvas, DOM and sync contracts. Desktop/tablet layouts and arbitrary third-party plugin interactions are not comprehensively certified by these phone checks.

## Messaging

The live WebView reported sync `HEALTHY` while its push sequence stayed at zero and the chat remained loading. A forced state request completed but did not deliver a snapshot. Calling the existing WebUI `poll()` immediately rendered the user's missing responses. The transport failure is therefore distinct from the viewport bug; the underlying server/tunnel push failure was not changed.

`sync-recovery.js` watches only the selected context, sequence and connection state. After five seconds without push progress, it recovers through the server's existing authenticated, cursor-aware `poll()` function. It reads every two seconds while the selected agent is running and every ten seconds while idle, with bounded failure backoff. Send accelerates the next check. Only one recovery read may be in flight. Recovery stops in the background and leaves degraded/disconnected polling to the stock WebUI. It never invokes Send or caches conversation bodies in native storage.

One explicit live validation prompt was sent through the normal composer and Send button in the installed Android app: ask for `ANDROID_REPLY_OK`, without tools or file changes. The matching reply was visibly rendered automatically, observed within 19 seconds, with a nonzero message height and the loading indicator dismissed. This was a real server round trip; it was not the synthetic renderer check. A later app update reopened that same test context and recovered its response without another prompt. Test screenshots contain only the synthetic validation conversation.

## Executed checks

- Android 15/API 35 with WebView 145: **20 unit tests and 32 instrumented tests**, zero failures/errors/skips in the aggregate run. The normal suite excludes the separate no-provider environment test.
- Added viewport/rotation, compact connection controls, visible modal/response, stalled-sync recovery and Android Back regressions. Live checks exercise expanded settings/scheduler/projects/plugins dialogs, Files and Swarm panels, server eligibility rejection and the actual message renderer.
- Android 17/API 37 visible emulator: installed APK, live Send-to-reply, full-width Swarm, Tools tray, compact navigation, panel Back and composer geometry checked. The emulator's physical-keyboard/floating-IME modes were exercised; this does not certify every docked keyboard or device configuration.
- Debug and shrunk unsigned release APK assembly passed. Both lint variants report zero errors and nine existing advisories. Debug v2 signing verifies; the release APK contains neither fixture trust certificate nor fixture private key, by names and exact bytes.

The prior API 26/33 compatibility evidence remains historical 0.1.3 evidence. Physical camera/voice, authenticated live exports, arbitrary plugins and the broader accessibility/network acceptance matrix remain outside this repair's executed coverage. The owner requested emulator-only continuation.

Delivery and screenshots: `artifacts/AgentZero-0.1.4-debug.apk`, `delivery-receipt-0.1.4.json`, `test-results-0.1.4.json`, and the `ui-*-0.1.4.png` files. Earlier deliveries remain unchanged.

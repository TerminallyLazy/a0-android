# Android and Swift connection contracts

Audited on 9 October 2026 against `agent-zero-ios` at `160649dfacfa935e1b2f1ee7968bd7f83d2a2f4c`. The Swift checkout was read and left unchanged. Android 0.1.3 keeps the approved full-WebUI architecture; iOS has native chat/protocol ownership and an embedded WebKit workspace.

## Common connection format

Both apps consume the existing server tunnel QR: a **plain HTTPS root URL**, for example `https://agent.example:8443`. No app-specific JSON, token, password, or pairing service is required. QR imports accept ASCII (including punycode domains), reject percent encoding and backslashes, and are limited to 2048 characters/bytes after surrounding whitespace is trimmed. The ASCII restriction makes these limits equivalent. Review is transient; a scan cannot connect or save a server without the user's explicit Connect action.

The audit found that Android's manual-entry parser could normalize dot paths or repair a missing `//` before checking for a root URL. Android now checks the original URL shape first, as Swift's `URLComponents`-based validation does. QR and manual entry share that root check.

`app/src/test/resources/connection-origins.tsv` contains 58 common cases: 27 manual-entry and 31 QR cases, including explicit ports, IPv6, punycode, credentials, hidden paths, queries, fragments and malformed schemes. `ConnectionContractTest` checks them with the actual Kotlin parsers. To check the same fixture against the current Swift declarations without modifying or building the iOS app:

```sh
python3 scripts/check-ios-connection-contract.py ../agent-zero-ios
```

The script compiles the exact origin declarations from `Sources/A0Core/HTTPClient.swift` plus the complete `QRImport.swift`, and records source/fixture hashes. All 58 cases passed against the audited revision. This is a narrow source-contract check, not an iOS application build or device test. Foundation preserves explicit `:443` while OkHttp removes the default port; comparisons use scheme, host and effective port. Local profile IDs and serialized URLs are not a cross-device synchronization format.

## Ownership and deliberate platform differences

| Area | Shared boundary | Android implementation | Swift implementation |
|---|---|---|---|
| TLS and navigation | Trusted HTTPS, exact origin, no trust-all fallback | WebView; external HTTPS requires a user gesture | Native URLSession and restricted WebKit workspace |
| Authentication | Preserve server session, CSRF and Origin protections | Server's login page; profile saved when Connect is selected | Native authenticated bootstrap; metadata saved only after authentication; optional device-only Keychain credentials |
| Sessions | Isolate server/profile data and delayed callbacks | Per-profile WebView cookies/storage persist locally; explicit Forget; backup disabled | Protected native session/drafts/receipts; ephemeral WebKit workspace cleared on background/disconnect |
| Recovery | Native shell does not replay a submitted message | Retry reloads the workspace; server WebUI retains its request/socket behavior | Native pending-delivery/control journals; embedded WebUI actions retain their own semantics |
| QR | Plain root URL, transient review, explicit Connect, no secrets | CameraX/ZXing and bounded image import | VisionKit and transient `QRImportSession` |
| Files | Origin-bound authenticated downloads, platform TLS, explicit export destination | SAF; bounded chunked blob handoff; same-origin GET redirects allowed, foreign redirects blocked | WKDownload to protected temporary files and share sheet; all download redirects canceled |
| Voice | Explicit foreground permission/activation, no automatic background recording | Server's WebUI microphone flow | Native speech-to-draft and audio ownership; no server WebUI camera permission |
| WebUI bootstrap | Preserve the server's existing surfaces and extensions | Root splash/document replacement supported; normal Dev Tunnels Continue page | Authenticated `/index.html`; scoped tunnel header and fetch-retry protection in its embedded adapter |

Android does not advertise native Swift delivery journals, A2UI rendering, Keychain storage, or speech-to-draft semantics. Its complete server WebUI supplies chat, settings, projects, scheduler, plugins and canvas behavior. In particular, shell retry protection is not an exactly-once guarantee for arbitrary server/plugin JavaScript. Native Swift APIs reject unauthenticated deployments; Android supports the supplied HTTPS server's own WebUI, which currently reports authentication disabled.

Source references reviewed: Swift `AGENTS.md`, `Sources/A0Core/HTTPClient.swift`, `Sources/A0Core/QRImport.swift`, `Tests/A0CoreTests/QRImportTests.swift`, `App/WorkspaceDownloads.swift`, `App/PluginWebScreen.swift`, and `docs/WORKSPACE.md`. Android ownership is described in the root README and [validation report](validation.md). Real authenticated interoperability and physical voice/camera acceptance remain separate from these fixtures.

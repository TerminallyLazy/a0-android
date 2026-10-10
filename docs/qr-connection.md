# QR connection addition — 9 October 2026

The user authorized this extension after the M0–M4 local delivery. The original preliminary specification and approved plan remain unchanged.

## Contract and implementation

Agent Zero's `webui/components/settings/tunnel/tunnel-store.js` generates a QR with `text: this.tunnelLink`. The iOS companion's `Sources/A0Core/QRImport.swift` also accepts plain HTTPS origins. Android follows that format, with strict pre-normalization path rejection, an ASCII/2048-character bound, and no credentials, query, fragment, percent encoding, or alternate schemes.

The native Servers screen offers **Scan QR code**. The non-exported scanner Activity uses AndroidX CameraX 1.6.2 and bundled ZXing core 3.5.4, or Android's document picker for a saved image. It returns only a validated origin to the review form. The form is transient and survives Activity recreation; profile persistence and the first connection occur only after **Connect**. Canceling does not change saved profiles. Manual entry remains available after denial or an unavailable camera.

Camera frame decoding runs on a dedicated worker, reads the Y plane with its actual row/pixel strides, closes every frame, and drops stale results using an ownership generation. Camera bindings and observers are released on pause, stop, or destruction. No frames or QR contents are logged, uploaded, or persisted. Image import is bounded to 12 MiB compressed, 64 megapixels in the source, and a sampled edge of at most 1600 pixels. Work is canceled with the Activity. Only content URIs selected through the picker are read, with no persistent URI grant.

The optional native camera feature does not exclude devices without cameras. WebView media still allows only the selected origin's requested audio capture; granting the native camera permission does not authorize WebView video capture. HTTPS trust, cookies, profiles, downloads, and backup policy are unchanged.

Primary library references: [CameraX releases](https://developer.android.com/jetpack/androidx/releases/camera), [CameraX image analysis lifecycle](https://developer.android.com/media/camera/camerax/analyze), [ZXing releases](https://github.com/zxing/zxing/releases).

## Acceptance

Validation results and artifact identity are recorded after the build in `docs/validation.md` and the versioned artifact receipt. Emulator camera lifecycle and generated QR/image fixtures cannot certify optical scanning on a physical camera under real lighting. That device check remains explicit, alongside the original delivery's physical voice and authenticated server acceptance gates.

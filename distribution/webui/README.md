# WebUI installation bridge

This adds small **Android** and **Apple** icons under **Mobile apps** at the bottom-left of the sidebar and in Remote Control. The Apple icon opens the [Agent Zero Mobile public beta](https://testflight.apple.com/join/xqAFS5er) on TestFlight. The Android icon opens the installation dialog. Android uses the standard Agent Zero modal with a stable APK download, installation-page QR, server QR, and Android connection link. It never changes tunnel or authentication settings and includes no backend API.

Copy this directory to `/a0/usr/android-app-integration`, then run:

```sh
python3 /a0/usr/android-app-integration/install.py /a0
```

Hard-refresh the WebUI afterward (reload while bypassing the browser cache). The installer preflights the footer and Remote Control anchors before editing any file and is safe to run again. Upgrading removes the previous large button from the top of the sidebar. It uses the core WebUI component API tested on Agent Zero commit `1e6af215`. An unknown layout fails before mutation. Container image upgrades replace core WebUI files; rerun the installer from the persisted `usr` directory after an upgrade. This bridge has not been merged upstream or submitted to the Plugin Index.

The shared modal uses the existing CSRF-aware `tunnel_proxy` status read only when the browser's current HTTPS origin is unavailable. It does not use a cached tunnel URL, start/stop a tunnel, or bypass login. Localhost addresses are not offered as phone destinations. Missing HTTPS access leaves APK download available with instructions to use Remote Control.

QR codes are generated locally with the WebUI's bundled QR library. No QR service receives the URL. The install link keeps the address in a URL fragment, never in a query string sent to GitHub. The APK accepts only an HTTPS root address and shows a review screen before saving or connecting.

To remove: remove the blocks between `a0-mobile-downloads:start` and `a0-mobile-downloads:end` in `webui/components/sidebar/bottom/sidebar-bottom.html` and `webui/components/settings/tunnel/tunnel-section.html`, then remove the dedicated `webui/components/modals/android-app` directory. Original entry-point files are backed up under `usr/android-app-integration-backup`; restore them only if they have not gained other edits since installation. No settings or sessions need to be removed.

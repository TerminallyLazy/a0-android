# Mobile Apps plugin

The core-patching WebUI bridge is retired. Install the standalone [Mobile Apps plugin](https://github.com/TerminallyLazy/a0-mobile-apps) instead:

1. In Agent Zero, open **Plugins → Install** and choose **Git URL**.
2. Paste `https://github.com/TerminallyLazy/a0-mobile-apps` and install.
3. Reload. The small Android and Apple icons appear above the sidebar version number.

You can also upload [MobileApps.zip](https://github.com/TerminallyLazy/a0-mobile-apps/releases/latest/download/MobileApps.zip) through the plugin manager. It persists in `/a0/usr/plugins/mobile_apps`, uses the standard footer extension, and makes no core WebUI edits. Keep `/a0/usr` on your persistent volume when replacing/upgrading the server container. Disable, update, or uninstall through the plugin manager.

## One-time migration for the old bridge

Only servers that used `AgentZero-WebUI-Install.zip` need this step. After installing the plugin, copy `migrate.py` from this directory into `/a0/usr/android-app-integration/`, then run inside your Agent Zero container:

```sh
python3 /a0/usr/android-app-integration/migrate.py /a0
```

The script preflights every entry point, removes only the known bridge markers/buttons, and backs up the current files under `usr/android-app-migration-backups/`. It preserves unrelated edits, rejects modified/ambiguous marked blocks, and is safe to rerun. It does not restore old whole-file backups or change server settings. Hard-refresh afterward. A fresh server image needs no migration.

Historical files in this folder document the previous bridge; `install.py` deliberately refuses to patch core files. New releases and fixes live in the plugin repository.

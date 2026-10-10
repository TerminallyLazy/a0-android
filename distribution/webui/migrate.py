#!/usr/bin/env python3
"""Remove only the retired bridge's known additions; preserve other edits."""
from pathlib import Path
from datetime import datetime, timezone
import re
import sys

FILES = ('webui/components/sidebar/top-section/quick-actions.html',
         'webui/components/sidebar/bottom/sidebar-bottom.html',
         'webui/components/settings/tunnel/tunnel-section.html')
COMPONENT = '<x-component path="modals/android-app/mobile-downloads.html"></x-component>'
START, END = '<!-- a0-mobile-downloads:start -->', '<!-- a0-mobile-downloads:end -->'


def clean(text):
    if START in text or END in text:
        if text.count(START) != 1 or text.count(END) != 1:
            raise ValueError('Ambiguous mobile-download markers; nothing changed')
        pattern = re.escape(START) + r'(.*?)' + re.escape(END)
        match = re.search(pattern, text, re.S)
        if not match or match[1].strip() != COMPONENT:
            raise ValueError('Modified mobile-download block; nothing changed')
        text = text[:match.start()] + text[match.end():]
    for marker in ('get-android-app', 'tunnel-android-app'):
        if f'id="{marker}"' in text:
            pattern = r'<button id="' + marker + r'"[^>]*>.*?</button>'
            text, count = re.subn(pattern, '', text, flags=re.S)
            if count != 1 or f'id="{marker}"' in text:
                raise ValueError('Ambiguous legacy button; nothing changed')
    return text


def migrate(root):
    if not (root / 'usr/plugins/mobile_apps/plugin.yaml').is_file():
        raise ValueError('Install the Mobile Apps plugin first; nothing changed')
    # Preflight all existing entry points before writing. Updated images may omit old files.
    originals = {name: (root / name).read_text() for name in FILES if (root / name).is_file()}
    planned = {name: clean(text) for name, text in originals.items()}
    changes = {name: text for name, text in planned.items() if text != originals[name]}
    if not changes:
        print('No legacy additions found. Core files unchanged.')
        return
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    backup = root / 'usr/android-app-migration-backups' / stamp
    for name in changes:
        saved = backup / name
        saved.parent.mkdir(parents=True, exist_ok=True)
        saved.write_text(originals[name])
    for name, text in changes.items():
        (root / name).write_text(text)
    print(f'Removed legacy additions from {len(changes)} files. Backup: {backup}')
    print('Reload the WebUI. The Mobile Apps plugin now owns the sidebar buttons.')


if __name__ == '__main__':
    try:
        migrate(Path(sys.argv[1] if len(sys.argv) == 2 else '/a0').resolve())
    except ValueError as error:
        raise SystemExit(str(error))

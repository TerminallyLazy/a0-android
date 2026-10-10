#!/usr/bin/env python3
"""Install compact mobile download controls without changing server settings."""
from pathlib import Path
import re
import shutil
import sys

COMPONENT = '<x-component path="modals/android-app/mobile-downloads.html"></x-component>'


def replace_block(text, marker, addition, anchor, name):
    start, end = f'<!-- {marker}:start -->', f'<!-- {marker}:end -->'
    if start in text or end in text:
        pattern = re.escape(start) + r'.*?' + re.escape(end)
        text, count = re.subn(pattern, '', text, count=1, flags=re.S)
        if count != 1 or start in text or end in text:
            raise SystemExit(f'Unrecognized mobile controls in {name}; no files changed')
    if text.count(anchor) != 1:
        raise SystemExit(f'Unsupported WebUI layout in {name}; no files changed')
    block = f'{start}\n{addition}\n{end}'
    # Normalize our surrounding whitespace so repeated installs are byte-stable.
    if anchor == '</header>':
        return text.replace(anchor, anchor + '\n' + block, 1).replace(end + '\n\n', end + '\n')
    return text.replace(anchor, block + '\n' + anchor, 1).replace('\n\n' + start, '\n' + start)


def remove_old_button(text, marker, name):
    if f'id="{marker}"' not in text:
        return text
    text, count = re.subn(r'<button id="' + re.escape(marker) + r'"[^>]*>.*?</button>\s*', '', text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f'Unrecognized previous Android action in {name}; no files changed')
    return text


def install(root):
    source = Path(__file__).resolve().parent
    backup = root / 'usr/android-app-integration-backup'
    quick = 'webui/components/sidebar/top-section/quick-actions.html'
    bottom = 'webui/components/sidebar/bottom/sidebar-bottom.html'
    tunnel = 'webui/components/settings/tunnel/tunnel-section.html'
    original = {name: (root / name).read_text() for name in (quick, bottom, tunnel)}
    planned = dict(original)
    planned[quick] = remove_old_button(planned[quick], 'get-android-app', quick)
    planned[tunnel] = remove_old_button(planned[tunnel], 'tunnel-android-app', tunnel)
    # Validate every entry point before writing any file.
    planned[bottom] = replace_block(planned[bottom], 'a0-mobile-downloads', COMPONENT, '<!-- Version Info -->', bottom)
    planned[tunnel] = replace_block(planned[tunnel], 'a0-mobile-downloads', COMPONENT, '</header>', tunnel)
    for name, text in planned.items():
        if text == original[name]:
            continue
        path = root / name
        saved = backup / name
        saved.parent.mkdir(parents=True, exist_ok=True)
        if not saved.exists():
            shutil.copy2(path, saved)
        path.write_text(text)
    shutil.copytree(source / 'webui/components/modals/android-app', root / 'webui/components/modals/android-app', dirs_exist_ok=True)
    print('Installed small Android and iOS download icons in the sidebar footer and Remote Control.')
    print('Reload the WebUI. Originals are in usr/android-app-integration-backup; server settings are unchanged.')


if __name__ == '__main__':
    install(Path(sys.argv[1] if len(sys.argv) == 2 else '/a0').resolve())

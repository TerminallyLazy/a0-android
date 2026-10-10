"""Exercise installer upgrades and all-entry-point preflight on disposable HTML."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('installer', Path(__file__).parent / 'webui/install.py')
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class InstallTest(unittest.TestCase):
    def fixture(self, root):
        entries = {
            'webui/components/sidebar/top-section/quick-actions.html': '<button id="get-android-app">Old Android</button>\n<div id="quick-actions">Keep chat actions</div>',
            'webui/components/sidebar/bottom/sidebar-bottom.html': '<div>Preferences</div>\n<!-- Version Info -->\n<div>version</div>',
            'webui/components/settings/tunnel/tunnel-section.html': '<header>Remote Control</header>\n<button id="tunnel-android-app">Old Android</button>\n<div>Keep tunnel controls</div>',
        }
        for name, text in entries.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        return entries

    def test_upgrade_is_idempotent_and_preserves_original_backup(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            before = self.fixture(root)
            installer.install(root)
            once = {name: (root / name).read_text() for name in before}
            installer.install(root)
            self.assertEqual(once, {name: (root / name).read_text() for name in before})
            for name in before:
                self.assertEqual(before[name], (root / 'usr/android-app-integration-backup' / name).read_text())
            self.assertNotIn('get-android-app', once[next(iter(before))])
            self.assertIn('mobile-downloads.html', once['webui/components/sidebar/bottom/sidebar-bottom.html'])

    def test_unknown_layout_changes_nothing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            before = self.fixture(root)
            bottom = 'webui/components/sidebar/bottom/sidebar-bottom.html'
            (root / bottom).write_text('Different layout')
            before[bottom] = 'Different layout'
            with self.assertRaises(SystemExit):
                installer.install(root)
            self.assertEqual(before, {name: (root / name).read_text() for name in before})
            self.assertFalse((root / 'usr').exists())


if __name__ == '__main__':
    unittest.main()

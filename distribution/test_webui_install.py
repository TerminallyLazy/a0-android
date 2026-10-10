"""Migration must preserve later edits and preflight before touching core files."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
spec = importlib.util.spec_from_file_location('migration', Path(__file__).parent / 'webui/migrate.py')
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)

class MigrationTest(unittest.TestCase):
    def fixture(self, root):
        plugin = root / 'usr/plugins/mobile_apps/plugin.yaml'
        plugin.parent.mkdir(parents=True)
        plugin.write_text('name: mobile_apps')
        originals = {}
        for name in migration.FILES:
            p = root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            originals[name] = f'Before: later owner edit\n{migration.START}\n{migration.COMPONENT}\n{migration.END}\nAfter: keep me'
            p.write_text(originals[name])
        return originals

    def test_idempotent_preserves_edits_and_exact_backup(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            originals = self.fixture(root)
            migration.migrate(root)
            once = {n:(root/n).read_text() for n in originals}
            migration.migrate(root)
            self.assertEqual(once,{n:(root/n).read_text() for n in originals})
            backups = list((root/'usr/android-app-migration-backups').iterdir())
            self.assertEqual(len(backups),1)
            for name, text in originals.items():
                self.assertEqual((backups[0]/name).read_text(),text)
                self.assertIn('later owner edit',once[name])
                self.assertIn('After: keep me',once[name])
                self.assertNotIn(migration.COMPONENT,once[name])

    def test_modified_block_prevents_all_writes(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            originals=self.fixture(root)
            name=migration.FILES[-1]
            originals[name]=originals[name].replace(migration.COMPONENT,'<p>Owner customization</p>')
            (root/name).write_text(originals[name])
            with self.assertRaises(ValueError): migration.migrate(root)
            self.assertEqual(originals,{n:(root/n).read_text() for n in originals})
            self.assertFalse((root/'usr/android-app-migration-backups').exists())

    def test_missing_plugin_prevents_writes(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(ValueError): migration.migrate(Path(d))

if __name__=='__main__': unittest.main()

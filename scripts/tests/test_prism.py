import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import prism


class PrismTests(unittest.TestCase):
    def test_prepare_preserves_unrelated_files_and_refuses_unmanaged_instance(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            target = {'status': 'buildable', 'forge': 'test', 'artifact': {'path': 'build/1.6.4/libs/spectatorlegacy-mc1.6.4-{modVersion}.jar'}}
            inventory = {'defaultModVersion': 'test', 'targets': {'1.6.4': target}}
            (root / 'versions.json').write_text(json.dumps(inventory), encoding='utf-8')
            libs = root / 'build/1.6.4/libs'
            libs.mkdir(parents=True)
            (libs / 'spectatorlegacy-mc1.6.4-test.jar').write_bytes(b'jar')
            data = root / 'prism'
            instance = data / 'instances/spectatorlegacy-1.6.4'
            instance.mkdir(parents=True)
            sentinel = instance / 'keep.txt'
            sentinel.write_text('keep', encoding='utf-8')
            with mock.patch.object(prism, 'ROOT', root), mock.patch.object(prism, 'prism_root', return_value=data), mock.patch.object(prism.build, 'ROOT', root), mock.patch.object(prism.build, 'run_target'), mock.patch.object(sys, 'argv', ['prism.py', '--prepare-only', '1.6.4']):
                with self.assertRaises(RuntimeError):
                    prism.main()
                (instance / prism.MARKER).write_text('managed', encoding='utf-8')
                prism.main()
                prism.main()
            self.assertEqual(sentinel.read_text(encoding='utf-8'), 'keep')
            self.assertEqual((instance / '.minecraft/mods/spectatorlegacy-dev.jar').read_bytes(), b'jar')


if __name__ == '__main__':
    unittest.main()

import copy
import sys
import unittest
from pathlib import Path
import json
import tempfile
from unittest import mock
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build


class CompositionTests(unittest.TestCase):
    def setUp(self):
        self.inventory = {
            'requiredCapabilities': ['camera', 'network'],
            'implementations': {
                'shared': {'provides': ['camera', 'network']},
                'camera-alt': {'provides': ['camera']},
                'network-alt': {'provides': ['network']},
            },
        }

    def test_shared_and_target_specific_providers(self):
        self.assertEqual(build.validate_composition(self.inventory, {'implementations': ['shared']}),
                         {'camera': 'shared', 'network': 'shared'})
        self.assertEqual(build.validate_composition(self.inventory, {'implementations': ['camera-alt', 'network-alt']}),
                         {'camera': 'camera-alt', 'network': 'network-alt'})

    def test_rejects_missing_duplicate_and_unknown_providers(self):
        for implementations in (['camera-alt'], ['shared', 'camera-alt'], ['missing']):
            with self.subTest(implementations=implementations), self.assertRaises(ValueError):
                build.validate_composition(self.inventory, {'implementations': implementations})

    def test_rejects_unknown_capability(self):
        inventory = copy.deepcopy(self.inventory)
        inventory['implementations']['shared']['provides'].append('other')
        with self.assertRaises(ValueError):
            build.validate_composition(inventory, {'implementations': ['shared']})

    def test_real_target_and_blocked_target(self):
        inventory = build.load_inventory()
        build.validate_target(inventory, '1.6.4')
        with self.assertRaises(ValueError):
            build.validate_target(inventory, '1.6.1')
        build.validate_target(inventory, '1.6.2')
        build.validate_target(inventory, '1.6.3')

    def test_release_jar_metadata_is_checked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            libs = root / 'build' / '1.6.4' / 'libs'
            libs.mkdir(parents=True)
            path = libs / 'spectatorlegacy-mc1.6.4-1.2.3.jar'
            def write_jar(version):
                with zipfile.ZipFile(path, 'w') as jar:
                    jar.writestr('mcmod.info', json.dumps({'modlist': [{'version': version, 'mcversion': '1.6.4'}]}))
                    jar.writestr('org/spectatorlegacy/SpectatorMod.class', b'1.2.3')
                    jar.writestr('META-INF/MANIFEST.MF', 'FMLCorePlugin: test\n')
            target = {'artifact': {'path': 'build/1.6.4/libs/spectatorlegacy-mc1.6.4-{modVersion}.jar', 'modClass': 'org/spectatorlegacy/SpectatorMod.class', 'corePlugin': True}}
            with mock.patch.object(build, 'ROOT', root):
                write_jar('1.2.3')
                build.check_jar('1.6.4', '1.2.3', target)
                write_jar('wrong')
                with self.assertRaises(ValueError):
                    build.check_jar('1.6.4', '1.2.3', target)


if __name__ == '__main__':
    unittest.main()

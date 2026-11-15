import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import legacy_mappings


class MappingTests(unittest.TestCase):
    def test_removes_known_bridge_mappings_only(self):
        lines = ['MD: a/a ()V net/minecraft/src/{}/{} ()V\n'.format(*pair) for pair in legacy_mappings.REMOVED]
        keep = 'MD: a/b ()V net/minecraft/src/Other/func_123 ()V\n'
        self.assertEqual(legacy_mappings.filter_srg('1.6.2', (''.join(lines) + keep).encode()), keep.encode())

    def test_rejects_missing_expected_mappings(self):
        with self.assertRaises(ValueError):
            legacy_mappings.filter_srg('1.6.2', b'MD: x/y ()V example/a ()V\n')


if __name__ == '__main__':
    unittest.main()

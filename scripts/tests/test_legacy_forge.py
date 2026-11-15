import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_forge import convert_patch


def patch(flag, body=b'example'):
    fields = b''.join(struct.pack('>H', len(s)) + s for s in (b'name', b'source', b'target'))
    return fields + bytes([flag]) + struct.pack('>I', len(body)) + body


class BinpatchTests(unittest.TestCase):
    def test_existing_class_gets_checksum_slot_only(self):
        original = patch(1)
        converted, changed = convert_patch(original)
        pos = len(original) - len(b'example') - 4
        self.assertTrue(changed)
        self.assertEqual(converted, original[:pos] + b'\0\0\0\0' + original[pos:])

    def test_new_class_is_unchanged(self):
        original = patch(0)
        self.assertEqual(convert_patch(original), (original, False))

    def test_rejects_invalid_records(self):
        for invalid in (b'', patch(2), patch(1)[:-1]):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                convert_patch(invalid)


if __name__ == '__main__':
    unittest.main()

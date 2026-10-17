import gzip
import hashlib
from pathlib import Path
import tempfile
import unittest

from download_nvd_feed import parse_metadata, verify_feed


class NvdFeedIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / 'feed.json.gz'
        self.payload = b'{"vulnerabilities":[]}'
        self.path.write_bytes(gzip.compress(self.payload, mtime=0))
        self.metadata = {
            'lastModifiedDate': '2026-10-17T00:00:00Z',
            'size': str(len(self.payload)),
            'gzSize': str(self.path.stat().st_size),
            'sha256': hashlib.sha256(self.payload).hexdigest().upper(),
        }

    def test_accepts_official_metadata_and_matching_file(self):
        raw = '\n'.join(f'{key}:{value}' for key, value in self.metadata.items()).encode()
        parsed = parse_metadata(raw)
        self.assertTrue(verify_feed(self.path, parsed))

    def test_rejects_wrong_compressed_or_uncompressed_length(self):
        for key in ('size', 'gzSize'):
            with self.subTest(key=key):
                changed = {**self.metadata, key: str(int(self.metadata[key]) + 1)}
                self.assertFalse(verify_feed(self.path, changed))

    def test_rejects_hash_mismatch_and_partial_gzip(self):
        self.assertFalse(verify_feed(self.path, {**self.metadata, 'sha256': '0' * 64}))
        self.path.write_bytes(self.path.read_bytes()[:-5])
        self.assertFalse(verify_feed(self.path, {**self.metadata, 'gzSize': str(self.path.stat().st_size)}))

    def test_rejects_incomplete_or_invalid_metadata(self):
        variants = [
            {key: value for key, value in self.metadata.items() if key != 'sha256'},
            {**self.metadata, 'gzSize': '-1'},
            {**self.metadata, 'sha256': 'not-a-sha256'},
            {**self.metadata, 'lastModifiedDate': '2026-10-17'},
        ]
        for values in variants:
            with self.subTest(values=values):
                raw = '\n'.join(f'{key}:{value}' for key, value in values.items()).encode()
                with self.assertRaises(ValueError):
                    parse_metadata(raw)


if __name__ == '__main__':
    unittest.main()

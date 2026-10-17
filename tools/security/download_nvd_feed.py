import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil
import urllib.request

BASE_URL = 'https://nvd.nist.gov/feeds/json/cve/2.0/'


def parse_metadata(raw):
    values = {}
    for line in raw.decode('utf-8').splitlines():
        if not line.strip():
            continue
        key, value = line.split(':', 1)
        if key in values:
            raise ValueError(f'Duplicate NVD metadata field: {key}')
        values[key] = value.strip()
    required = {'size', 'gzSize', 'sha256', 'lastModifiedDate'}
    if not required.issubset(values):
        raise ValueError('Incomplete NVD metadata')
    if int(values['size']) <= 0 or int(values['gzSize']) <= 0:
        raise ValueError('Invalid NVD feed length')
    if not re.fullmatch(r'[0-9a-fA-F]{64}', values['sha256']):
        raise ValueError('Invalid NVD SHA-256')
    modified = datetime.fromisoformat(values['lastModifiedDate'].replace('Z', '+00:00'))
    if modified.tzinfo is None:
        raise ValueError('NVD timestamp must include its timezone')
    return values


def verify_feed(path, metadata):
    digest = hashlib.sha256()
    size = 0
    try:
        if path.stat().st_size != int(metadata['gzSize']):
            return False
        with gzip.open(path, 'rb') as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                size += len(block)
                if size > int(metadata['size']):
                    return False
                digest.update(block)
    except (EOFError, OSError):
        return False
    return size == int(metadata['size']) and digest.hexdigest().upper() == metadata['sha256'].upper()


def request(url):
    return urllib.request.urlopen(urllib.request.Request(
        url, headers={'User-Agent': 'AccessMesh dependency audit'}), timeout=90)


def download_feed(label, destination):
    name = f'nvdcve-2.0-{label}'
    with request(BASE_URL + name + '.meta') as response:
        raw = response.read()
    metadata = parse_metadata(raw)
    target = destination / (name + '.json.gz')
    if not verify_feed(target, metadata):
        partial = target.with_suffix(target.suffix + '.part')
        with request(BASE_URL + target.name) as response, partial.open('wb') as output:
            shutil.copyfileobj(response, output, 1024 * 1024)
        if not verify_feed(partial, metadata):
            raise ValueError(f'{label}: official length or SHA-256 verification failed')
        partial.replace(target)
    (destination / (name + '.meta')).write_bytes(raw)
    print(f'Verified official NVD feed: {label}', flush=True)
    return label, metadata


def main():
    parser = argparse.ArgumentParser(description='Verify official NVD feeds for Dependency Check initialization')
    parser.add_argument('destination', type=Path)
    parser.add_argument('--manifest', required=True, type=Path)
    args = parser.parse_args()
    args.destination.mkdir(parents=True, exist_ok=True)
    labels = [str(year) for year in range(2002, datetime.now(timezone.utc).year + 1)] + ['modified']
    with ThreadPoolExecutor(max_workers=2) as pool:
        feeds = dict(pool.map(lambda label: download_feed(label, args.destination), labels))
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps({
        'source': BASE_URL,
        'verifiedAt': datetime.now(timezone.utc).isoformat(),
        'feeds': feeds,
    }, indent=2) + '\n', encoding='utf-8')
    print('All official NVD feeds verified; API incremental update is still required.', flush=True)


if __name__ == '__main__':
    main()

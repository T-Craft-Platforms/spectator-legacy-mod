"""Convert the 1.6.1 binpatch envelope for Voldeloom, without changing game assets."""
import hashlib
import io
import lzma
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def convert_patch(patch):
    """Add the checksum slot expected by Voldeloom to a pre-checksum FML patch."""
    pos = 0
    try:
        for _ in range(3):
            length = struct.unpack_from('>H', patch, pos)[0]
            pos += 2 + length
        flag = patch[pos]
        if flag not in (0, 1):
            raise ValueError('Invalid 1.6.1 patch existence flag')
        length = struct.unpack_from('>I', patch, pos + 1)[0]
        if pos + 5 + length != len(patch):
            raise ValueError('Invalid 1.6.1 patch length')
    except (IndexError, struct.error) as exc:
        raise ValueError('Truncated 1.6.1 patch') from exc
    return (patch[:pos + 1] + b'\0\0\0\0' + patch[pos + 1:], True) if flag else (patch, False)


def prepare(version, target):
    if version != '1.6.1':
        return
    cache = ROOT / '.gradle' / 'legacy-forge'
    cache.mkdir(parents=True, exist_ok=True)
    output = cache / 'forge-1.6.1-v1.jar'
    if output.exists():
        return output
    forge = target['forge']
    url = 'https://maven.minecraftforge.net/net/minecraftforge/forge/{0}-{1}/forge-{0}-{1}-universal.jar'.format(version, forge)
    request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 SpectatorLegacy-build/1.0'})
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != target['forgeUniversalSha256']:
        raise ValueError('Forge universal checksum mismatch for ' + version)
    java_home = os.environ.get('JAVA_HOME')
    if not java_home:
        raise ValueError('JAVA_HOME must point to a Java 8 JDK with pack200 and unpack200')
    java_bin = Path(java_home) / 'bin'
    suffix = '.exe' if os.name == 'nt' else ''
    unpack = java_bin / ('unpack200' + suffix)
    pack = java_bin / ('pack200' + suffix)
    if not unpack.is_file() or not pack.is_file():
        raise ValueError('The 1.6.1 build needs pack200 and unpack200 from a Java 8 JDK')
    with tempfile.TemporaryDirectory(dir=str(cache)) as temp:
        work = Path(temp)
        with zipfile.ZipFile(io.BytesIO(data)) as original:
            (work / 'patches.pack').write_bytes(lzma.decompress(original.read('binpatches.pack.lzma'), format=lzma.FORMAT_ALONE))
            subprocess.run([str(unpack), str(work / 'patches.pack'), str(work / 'patches.jar')], check=True)
            count = 0
            with zipfile.ZipFile(work / 'patches.jar') as old, zipfile.ZipFile(work / 'fixed.jar', 'w', zipfile.ZIP_DEFLATED) as new:
                for entry in old.infolist():
                    patch = old.read(entry.filename)
                    if entry.filename.endswith('.binpatch'):
                        patch, converted = convert_patch(patch)
                        if converted:
                            count += 1
                    new.writestr(entry, patch)
            if not count:
                raise ValueError('No 1.6.1 binpatches were converted')
            subprocess.run([str(pack), '--no-gzip', '--effort=0', str(work / 'fixed.pack'), str(work / 'fixed.jar')], check=True)
            fixed = lzma.compress((work / 'fixed.pack').read_bytes(), format=lzma.FORMAT_ALONE)
            temporary = work / 'forge.jar'
            with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as converted:
                for entry in original.infolist():
                    converted.writestr(entry, fixed if entry.filename == 'binpatches.pack.lzma' else original.read(entry.filename))
            temporary.replace(output)
    return output

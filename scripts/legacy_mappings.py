"""Prepare pinned legacy Forge mappings for Voldeloom's remapper."""
import hashlib
import io
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
REMOVED = {('RenderBat', 'func_130000_a'), ('RendererLivingEntity', 'func_130000_a'),
           ('RenderPlayer', 'func_130000_a'), ('RendererLivingEntity', 'func_82408_c'),
           ('RenderPlayer', 'func_82408_c')}


def filter_srg(version, data):
    expected = REMOVED if version != '1.6.1' else {
        ('RenderBat', 'func_77031_a'), ('RendererLivingEntity', 'func_77031_a'),
        ('RenderPlayer', 'func_77031_a'), ('RendererLivingEntity', 'func_82408_c'),
        ('RenderPlayer', 'func_82408_c')}
    removed = set()
    lines = []
    for line in data.decode('utf-8').splitlines(keepends=True):
        match = next((pair for pair in expected if line.startswith('MD: ') and '/' + pair[0] + '/' + pair[1] + ' ' in line and (pair[1] != 'func_77031_a' or '(Lnet/minecraft/src/EntityLivingBase;DDDFF)V' in line)), None)
        if match:
            removed.add(match)
        else:
            lines.append(line)
    if removed != expected:
        raise ValueError('Unexpected legacy Forge mappings for ' + version + ': ' + repr(removed))
    return ''.join(lines).encode('utf-8')


def prepare(version, target):
    cache = ROOT / '.gradle' / 'legacy-mappings'
    cache.mkdir(parents=True, exist_ok=True)
    output = cache / ('forge-' + version + '-mappings-v1.zip')
    if output.is_file():
        return output
    forge = target['forge']
    url = 'https://maven.minecraftforge.net/net/minecraftforge/forge/{0}-{1}/forge-{0}-{1}-src.zip'.format(version, forge)
    request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 SpectatorLegacy-build/1.0'})
    with urllib.request.urlopen(request, timeout=90) as response:
        archive = response.read()
    if hashlib.sha256(archive).hexdigest() != target['forgeSourceSha256']:
        raise ValueError('Forge source archive checksum mismatch for ' + version)
    temporary = output.with_suffix('.tmp')
    try:
        with zipfile.ZipFile(io.BytesIO(archive)) as src, zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as dst:
            found_srg = False
            for info in src.infolist():
                data = src.read(info.filename)
                if info.filename.endswith('/joined.srg'):
                    data = filter_srg(version, data)
                    found_srg = True
                dst.writestr(info, data)
            if not found_srg:
                raise ValueError('Forge archive is missing joined.srg for ' + version)
        temporary.replace(output)
    finally:
        if temporary.exists():
            temporary.unlink()
    return output

#!/usr/bin/env python3
"""Build the mod and manage a dedicated Prism Launcher test instance."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import build

ROOT = Path(__file__).resolve().parent.parent
MARKER = '.spectatorlegacy-test-instance'
# Prism's documented workaround for legacy SHA-1 signed Minecraft jars on modern Java 8.
JAVA_SECURITY = ('jdk.certpath.disabledAlgorithms=MD2, MD5, SHA1 jdkCA & usage TLSServer, '
                 'RSA keySize < 1024, DSA keySize < 1024, EC keySize < 224, include jdk.disabled.namedCurves\n'
                 'jdk.jar.disabledAlgorithms=MD2, MD5, RSA keySize < 1024, '
                 'DSA keySize < 1024, include jdk.disabled.namedCurves\n')


def prism_root():
    if os.environ.get('PRISM_DATA_DIR'):
        return Path(os.environ['PRISM_DATA_DIR']).expanduser()
    if sys.platform == 'win32':
        return Path(os.environ['APPDATA']) / 'PrismLauncher'
    if sys.platform == 'darwin':
        return Path.home() / 'Library/Application Support/PrismLauncher'
    return Path(os.environ.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'PrismLauncher'


def prism_executable():
    override = os.environ.get('PRISM_LAUNCHER')
    if override:
        return override
    found = shutil.which('prismlauncher') or shutil.which('PrismLauncher.exe')
    if found:
        return found
    if sys.platform == 'win32':
        candidates = [Path(os.environ.get('LOCALAPPDATA', '')) / 'Programs/PrismLauncher/prismlauncher.exe',
                      Path(os.environ.get('ProgramFiles', 'C:/Program Files')) / 'PrismLauncher/prismlauncher.exe',
                      Path.home() / 'scoop/apps/prismlauncher/current/prismlauncher.exe']
        for candidate in candidates:
            if candidate.is_file():
                return str(candidate)
    if sys.platform == 'darwin':
        candidate = Path('/Applications/Prism Launcher.app/Contents/MacOS/PrismLauncher')
        if candidate.is_file():
            return str(candidate)
    raise RuntimeError('Prism Launcher not found. Set PRISM_LAUNCHER to its executable path.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version', nargs='?', default='1.6.4')
    parser.add_argument('--prepare-only', action='store_true')
    args = parser.parse_args()
    targets = json.loads((ROOT / 'versions.json').read_text(encoding='utf-8'))['targets']
    if args.version not in targets:
        parser.error('Unknown Minecraft version. Choose: ' + ', '.join(targets))
    target = targets[args.version]
    if target['status'] not in ('buildable', 'release-ready'):
        parser.error(args.version + ' is not buildable yet: ' + target['note'])
    launcher = None if args.prepare_only else prism_executable()
    build.run_target(args.version, 'clean-build')
    jar = build.artifact_path(target, build.load_inventory()['defaultModVersion'])
    instance_id = 'spectatorlegacy-' + args.version
    instance = prism_root() / 'instances' / instance_id
    if instance.exists() and not (instance / MARKER).is_file():
        raise RuntimeError('Refusing to modify existing unmanaged instance: ' + str(instance))
    instance.mkdir(parents=True, exist_ok=True)
    (instance / MARKER).write_text('Managed by Spectator Legacy\n', encoding='utf-8')
    config = instance / 'instance.cfg'
    if not config.exists():
        config.write_text('[General]\nConfigVersion=1.3\nInstanceType=OneSix\nname=Spectator Legacy ' + args.version + '\nOverrideJavaLocation=true\nAutomaticJava=true\nOverrideJavaArgs=true\nJvmArgs=-Djava.security.properties=java.security\n', encoding='utf-8')
    else:
        # Migrate the instance created by the first revision of this utility.
        java_home = os.environ.get('JAVA_HOME')
        text = config.read_text(encoding='utf-8')
        java = Path(java_home) / 'bin' / ('javaw.exe' if sys.platform == 'win32' else 'java') if java_home else None
        if java and 'JavaPath=' + java.as_posix() in text and 'OverrideJavaLocation=true' in text:
            text = text.replace('AutomaticJava=false', 'AutomaticJava=true')
        # Preserve custom JVM arguments rather than replacing them.
        if 'JvmArgs=\n' in text and 'OverrideJavaArgs=false' in text:
            text = text.replace('JvmArgs=\n', 'JvmArgs=-Djava.security.properties=java.security\n').replace('OverrideJavaArgs=false', 'OverrideJavaArgs=true')
        elif 'JvmArgs=' not in text and 'OverrideJavaArgs=' not in text:
            text = text.replace('[General]\n', '[General]\nOverrideJavaArgs=true\nJvmArgs=-Djava.security.properties=java.security\n', 1)
        config.write_text(text, encoding='utf-8')
    pack = {'formatVersion': 1, 'components': [
        {'uid': 'net.minecraft', 'version': args.version, 'important': True},
        {'uid': 'net.minecraftforge', 'version': target.get('prismForge', target['forge'])},
    ]}
    (instance / 'mmc-pack.json').write_text(json.dumps(pack, indent=2) + '\n', encoding='utf-8')
    mods = instance / '.minecraft' / 'mods'
    mods.mkdir(parents=True, exist_ok=True)
    security = instance / '.minecraft' / 'java.security'
    if not security.exists():
        security.write_text(JAVA_SECURITY, encoding='utf-8')
    shutil.copy2(jar, mods / 'spectatorlegacy-dev.jar')
    print('Prepared Prism instance:', instance, flush=True)
    if launcher:
        subprocess.run([launcher, '--dir', str(prism_root()), '--launch', instance_id], check=True)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, ValueError, OSError, subprocess.CalledProcessError) as exc:
        print('Prism test instance:', exc, file=sys.stderr)
        sys.exit(1)

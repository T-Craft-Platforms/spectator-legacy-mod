#!/usr/bin/env python3
"""Validate target composition and dispatch builds to target-specific toolchains."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
import legacy_mappings
import legacy_forge

ROOT = Path(__file__).resolve().parent.parent


def load_inventory():
    return json.loads((ROOT / 'versions.json').read_text(encoding='utf-8'))


def validate_composition(inventory, target):
    required = inventory['requiredCapabilities']
    if len(set(required)) != len(required):
        raise ValueError('Duplicate required capability')
    providers = {}
    for name in target.get('implementations', []):
        implementation = inventory['implementations'].get(name)
        if implementation is None:
            raise ValueError('Unknown implementation: ' + name)
        for capability in implementation['provides']:
            if capability not in required:
                raise ValueError('Unknown capability: ' + capability)
            if capability in providers:
                raise ValueError('Multiple providers for ' + capability)
            providers[capability] = name
    missing = set(required) - set(providers)
    if missing:
        raise ValueError('Missing capability providers: ' + ', '.join(sorted(missing)))
    return providers


def validate_target(inventory, version):
    target = inventory['targets'].get(version)
    if target is None:
        raise ValueError('Unknown Minecraft target: ' + version)
    if target['status'] not in ('buildable', 'release-ready'):
        raise ValueError(version + ' is not buildable: ' + target.get('note', 'port is incomplete'))
    if target.get('releaseReady', False) != (target['status'] == 'release-ready'):
        raise ValueError('Inconsistent release status for ' + version)
    if target.get('toolchain') not in inventory['toolchains']:
        raise ValueError('Unknown toolchain for ' + version)
    validate_composition(inventory, target)
    for name in target['implementations']:
        allowed = inventory['implementations'][name].get('compatibleTargets')
        if allowed is None or version not in allowed:
            raise ValueError(name + ' has not been validated for ' + version)
    paths = [ROOT / 'common/src/main/java']
    target_source = ROOT / target['buildDir'] / 'src/main/java'
    if target_source.is_dir():
        paths.append(target_source)
    paths += [ROOT / inventory['implementations'][n]['path'] / 'src/main/java' for n in target['implementations']]
    seen = {}
    for path in paths:
        if not path.is_dir():
            raise ValueError('Missing source directory: ' + str(path))
        for source in path.rglob('*.java'):
            relative = source.relative_to(path).as_posix()
            if relative in seen:
                raise ValueError('Duplicate source ' + relative + ' in ' + str(seen[relative]) + ' and ' + str(path))
            seen[relative] = path
    for source in paths[0].rglob('*.java'):
        if re.search(r'^\s*import\s+(?:net\.minecraft|net\.minecraftforge|cpw\.mods)\.', source.read_text(encoding='utf-8'), re.MULTILINE):
            raise ValueError('Game-dependent import in common source: ' + str(source))
    return target


def run_target(version, action, mod_version=None):
    inventory = load_inventory()
    target = validate_target(inventory, version)
    toolchain = inventory['toolchains'][target['toolchain']]
    if toolchain.get('prepare') == 'legacy-mappings':
        legacy_mappings.prepare(version, target)
        legacy_forge.prepare(version, target)
    command = list(toolchain['commands'][action])
    executable = shutil.which(command[0])
    if not executable:
        raise ValueError('Cannot find ' + command[0] + '; install the target toolchain or run through mise')
    command[0] = executable
    if mod_version:
        command.append(toolchain['versionArgument'].replace('{modVersion}', mod_version))
    subprocess.run(command, cwd=ROOT / target['buildDir'], check=True)
    if action in ('build', 'clean-build'):
        check_jar(version, mod_version or inventory['defaultModVersion'], target)
    return target


def artifact_path(target, mod_version):
    if not re.fullmatch(r'[0-9A-Za-z.+_-]+', mod_version):
        raise ValueError('Invalid mod version')
    return ROOT / target['artifact']['path'].replace('{modVersion}', mod_version)


def check_jar(version, mod_version, target):
    path = artifact_path(target, mod_version)
    if not path.is_file():
        raise ValueError('Expected jar is missing: ' + str(path))
    with zipfile.ZipFile(path) as jar:
        info = json.loads(jar.read('mcmod.info'))['modlist'][0]
        if info['version'] != mod_version or info['mcversion'] != version:
            raise ValueError('Jar metadata does not match requested versions: ' + str(path))
        if mod_version.encode('utf-8') not in jar.read(target['artifact']['modClass']):
            raise ValueError('Mod annotation version does not match: ' + str(path))
        if target['artifact'].get('corePlugin') and 'FMLCorePlugin:' not in jar.read('META-INF/MANIFEST.MF').decode('utf-8'):
            raise ValueError('Missing core plugin manifest entry: ' + str(path))
    print('Verified artifact:', path, flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['setup', 'build', 'clean-build', 'format', 'validate', 'list', 'matrix', 'artifact'])
    parser.add_argument('version', nargs='?', default='1.6.4')
    parser.add_argument('--mod-version')
    parser.add_argument('--status', choices=['buildable', 'release-ready'])
    args = parser.parse_args()
    inventory = load_inventory()
    if args.action in ('list', 'matrix'):
        versions = [v for v, t in inventory['targets'].items() if (args.status == 'buildable' and t['status'] in ('buildable', 'release-ready')) or (args.status == 'release-ready' and t['status'] == 'release-ready') or args.status is None]
        if args.action == 'matrix':
            rows = []
            for version in versions:
                target = validate_target(inventory, version)
                toolchain = inventory['toolchains'][target['toolchain']]
                rows.append({'minecraft': version, 'java': toolchain['java'], 'gradle': toolchain.get('gradle', ''), 'setup': toolchain.get('ciSetup', '')})
            print(json.dumps(rows))
        else:
            print(json.dumps(versions))
    elif args.action == 'validate':
        validate_target(inventory, args.version)
        print('Validated ' + args.version)
    elif args.action == 'artifact':
        target = validate_target(inventory, args.version)
        print(artifact_path(target, args.mod_version or inventory['defaultModVersion']))
    else:
        run_target(args.version, args.action, args.mod_version)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        print('Target build:', exc, file=sys.stderr)
        sys.exit(1)

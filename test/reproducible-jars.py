#!/usr/bin/env python3
"""Build untouched source exports, compare raw archives, and change the date input.

Requires git, Python 3, JAVA_HOME and the initialized documentation submodule.
Only reads JARs; Ant alone writes them. Keep --output outside the checkout.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import zipfile


JARS = ('jing.jar', 'trang.jar', 'dtdinst.jar')


def run(args, cwd, **kwargs):
    return subprocess.run(args, cwd=cwd, check=True, **kwargs)


def entries(path):
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        assert len(names) == len(set(names)), f'duplicate entries: {path}'
        result = {}
        for entry in jar.infolist():
            data = jar.read(entry)
            if entry.filename.endswith('.class'):
                assert data[:4] == b'\xca\xfe\xba\xbe'
                assert int.from_bytes(data[6:8], 'big') == 52, entry.filename
            result[entry.filename] = {
                'sha256': hashlib.sha256(data).hexdigest(),
                'date': entry.date_time,
            }
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--revision', default='HEAD')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve() if args.output else Path(tempfile.mkdtemp(prefix='jing-repro-'))
    output.mkdir(parents=True, exist_ok=True)
    assert source not in output.parents and source != output, 'output must be outside source'
    print(f'Proof directory: {output}', flush=True)
    revision = subprocess.check_output(['git', 'rev-parse', args.revision], cwd=source, text=True).strip()
    tree = subprocess.check_output(['git', 'ls-tree', revision, 'relaxng.org'], cwd=source, text=True)
    docs_revision = tree.split()[2]
    report = {'revision': revision, 'documentation': docs_revision, 'archives': {}}
    with (output / 'source.tar').open('wb') as stream:
        run(['git', 'archive', revision], source, stdout=stream)
    with (output / 'docs.tar').open('wb') as stream:
        run(['git', 'archive', docs_revision], source / 'relaxng.org', stdout=stream)
    report['source_sha256'] = hashlib.sha256((output / 'source.tar').read_bytes()).hexdigest()
    for name, zone, mtime, extra in (
        ('one', 'Pacific/Honolulu', 978307200, []),
        ('two', 'Asia/Tokyo', 1577836800, []),
        ('changed-date', 'Asia/Tokyo', 1577836800,
         ['-Djar.modificationtime=2025-01-02T00:00:00Z']),
    ):
        export = output / name
        export.mkdir()  # Refuse stale build directories.
        with tarfile.open(output / 'source.tar') as archive:
            archive.extractall(export)
        with tarfile.open(output / 'docs.tar') as archive:
            archive.extractall(export / 'relaxng.org')
        for path in export.rglob('*'):
            os.utime(path, (mtime, mtime))
        env = dict(os.environ, TZ=zone, LANG='C.UTF-8', LC_ALL='C.UTF-8',
                   JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US')
        with (output / (name + '.log')).open('w') as log:
            run([str(Path(env['JAVA_HOME']) / 'bin/java'), '-version'], export,
                env=env, stdout=log, stderr=subprocess.STDOUT)
            run(['sh', './ant', '-version'], export, env=env, stdout=log, stderr=subprocess.STDOUT)
            run(['sh', './ant', *extra], export, env=env, stdout=log, stderr=subprocess.STDOUT)
        report['archives'][name] = {}
        for jar in JARS:
            path = export / 'build' / jar
            report['archives'][name][jar] = {
                'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                'entries': entries(path),
            }
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    for jar in JARS:
        a, b, changed = [report['archives'][name][jar] for name in ('one', 'two', 'changed-date')]
        assert a == b, f'{jar}: archives differ with source mtime/timezone'
        assert (output / 'one/build' / jar).read_bytes() == (output / 'two/build' / jar).read_bytes()
        assert a['sha256'] != changed['sha256'], f'{jar}: changed date was ignored'
        assert {n: e['sha256'] for n, e in a['entries'].items()} == {
            n: e['sha256'] for n, e in changed['entries'].items()}, f'{jar}: date changed content'
        assert all(tuple(e['date']) == (2024, 12, 31, 0, 0, 0) for e in a['entries'].values())
        assert all(tuple(e['date']) == (2025, 1, 2, 0, 0, 0) for e in changed['entries'].values())
        print(f"{jar}: {a['sha256']} ({len(a['entries'])} entries; raw equality and date control passed)")


if __name__ == '__main__':
    main()

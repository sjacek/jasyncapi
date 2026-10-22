"""Verify the local Central bundle without contacting a publication service."""
import argparse
import hashlib
import io
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--bundle', type=Path, default=Path('target/central-publishing/central-bundle.zip'))
parser.add_argument('--allow-unsigned', action='store_true', help='For verification bundles only, never for a release')
parser.add_argument('--gpg', help='Optionally verify every signature with this GPG executable and its configured keyring')
args = parser.parse_args()
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
version = ET.parse(Path(__file__).resolve().parent.parent / 'pom.xml').findtext('m:version', namespaces=ns)
prefix = 'io/github/sjacek/jasyncapi'
artifacts = [f'{prefix}/asyncapi/{version}/asyncapi-{version}.pom']
core = f'{prefix}/asyncapi-core/{version}/asyncapi-core-{version}'
artifacts += [core + suffix for suffix in ('.pom', '.jar', '-sources.jar', '-javadoc.jar')]

with zipfile.ZipFile(args.bundle) as bundle:
    names = set(bundle.namelist())
    for name in artifacts:
        content = bundle.read(name)
        for algorithm in ('md5', 'sha1', 'sha256', 'sha512'):
            expected = bundle.read(name + '.' + algorithm).decode().strip().lower()
            if hashlib.new(algorithm, content).hexdigest() != expected:
                raise SystemExit(f'Checksum mismatch: {name}.{algorithm}')
        signature = name + '.asc'
        if args.allow_unsigned and signature in names:
            raise SystemExit(f'Unexpected signature in unsigned preview; rebuild from clean outputs: {signature}')
        if not args.allow_unsigned and signature not in names:
            raise SystemExit(f'Missing release signature: {signature}')
        if args.gpg:
            with tempfile.TemporaryDirectory(prefix='asyncwright-signature-') as directory:
                artifact = Path(directory) / 'artifact'
                signed = Path(directory) / 'artifact.asc'
                artifact.write_bytes(content)
                signed.write_bytes(bundle.read(signature))
                subprocess.run([args.gpg, '--batch', '--verify', str(signed), str(artifact)], check=True)
    pom = ET.fromstring(bundle.read(artifacts[0]))
    for element in ('name', 'description', 'url', 'licenses/license/name', 'developers/developer/url', 'scm/connection'):
        if not pom.findtext('/'.join('m:' + part for part in element.split('/')), namespaces=ns):
            raise SystemExit(f'Missing publication metadata: {element}')
    child = ET.fromstring(bundle.read(core + '.pom'))
    if child.findtext('m:parent/m:version', namespaces=ns) != version:
        raise SystemExit('Parent/core version mismatch')
    with zipfile.ZipFile(io.BytesIO(bundle.read(core + '.jar'))) as jar:
        for name in ('META-INF/LICENSE', 'META-INF/NOTICE', 'META-INF/asyncwright-fork.properties'):
            if not jar.read(name).strip():
                raise SystemExit(f'Missing model provenance: {name}')
        if f'distributionVersion={version}' not in jar.read('META-INF/asyncwright-fork.properties').decode():
            raise SystemExit('Model provenance version mismatch')
print(f'Verified {len(artifacts)} artifacts and checksums for {version}; '
      f'{"unsigned verification only" if args.allow_unsigned else "signatures required"}. No upload performed.')

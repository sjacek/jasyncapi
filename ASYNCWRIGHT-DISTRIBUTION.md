# Asyncwright model distribution

## Unpublished .5 candidate

The `build/kafka-model-distribution` branch prepares
`io.github.sjacek.jasyncapi:asyncapi-core:1.0.0-RC4-asyncwright.5` from
the `fix/kafka-topic-configuration` source branch. Both topic-configuration versions
(0.4.0 and 0.5.0) retain 64-bit retention and additional JSON values. This is an
intentional API/ABI change: recompile affected model consumers. See
[Kafka topic configuration](docs/kafka-topic-configuration.md).

The .5 coordinates are reserved in these POMs; no artifacts have been published.
Source changes are not committed yet, so `modelSourceCommit` denotes the last committed
source base `a6ac111a10f6e18fd26c7b4820bf262125054b66` and
`modelSourceState=working-tree` explicitly marks the uncommitted changes.
Before release, commit the source branch, replace that SHA with the actual model-fix
commit and remove the working-tree marker in a separate distribution commit.
Do not describe the current candidate as having final immutable provenance.

Build/test and unsigned preparation commands below remain local. Use a new preview
repository directory for each candidate whose bytes change; the install script prevents
overwriting an already installed version with different bytes.
The published model .4 and Asyncwright 0.1.1 CI pin remain unchanged during R1.

## Published .4 baseline

This distribution is published in Maven Central as `.4` (2026-10-25), using the binding-map
reference fix from fork commit
[`a6ac111a10f6e18fd26c7b4820bf262125054b66`](https://github.com/sjacek/jasyncapi/commit/a6ac111a10f6e18fd26c7b4820bf262125054b66)
on `fix/binding-map-references`. Java sources and model tests match that immutable commit.
Its separate Maven coordinates are:

```text
io.github.sjacek.jasyncapi:asyncapi:1.0.0-RC4-asyncwright.4       (parent POM)
io.github.sjacek.jasyncapi:asyncapi-core:1.0.0-RC4-asyncwright.4  (model jar)
```

The distribution aligns Jackson to 2.22.4 and Kotlin to 2.1.21, the combination used for
the regression suite. Packages remain `com.asyncapi`. The Apache-2.0 license and original
authorship are retained; LICENSE, NOTICE and source provenance are included
in the model jar. Distribution metadata is separate from the model-fix branch.
Whole `bindings` references retain their original `$ref` string in the existing Map getter;
named `components.*Bindings` entries now hold typed protocol maps or `Reference` aliases.
No resolution is performed by the model. See [binding-map semantics](docs/binding-map-references.md).

The published parent POM, model JAR, sources, Javadoc, five GPG signatures and checksums
were verified from Maven Central. Release distribution commit:
`dac5a9703a9a0f82a7d25a2b78789e1d109da41d` on `build/asyncwright-distribution`.
`modelSourceCommit` identifies the separate immutable model-source revision above;
`distributionVersion` is `1.0.0-RC4-asyncwright.4`, with no working-tree marker.
Original `.3` artifacts remain unchanged. See the [changelog](CHANGELOG.md) for upgrading.

Consume the model from Maven Central without a local repository:

```groovy
repositories { mavenCentral() }
dependencies {
    implementation 'io.github.sjacek.jasyncapi:asyncapi-core:1.0.0-RC4-asyncwright.4'
}
```

Asyncwright `0.1.1` declares this dependency itself. Documentation-only changes after
publication do not change the release's distribution commit or its CI pin.

The RC4 base has no NOTICE file; the distribution includes the attribution from upstream
`master` at `6c2a325f0826f26cfe26003af208ce15e5e9bbbe`. Local `.1` was superseded by `.2`
to include this notice without overwriting the already installed artifact.

For model development or HTTP publication-test fixtures, configure JDK 21 in Maven
Toolchains, then build, test and install into an explicit local file repository:

```powershell
./tools/install-local.ps1 -Repository C:/path/to/model-repository
```

Use `-Maven` and `-Toolchains` for nonstandard executable/toolchain locations. `-SkipBuild`
installs an already built and tested artifact. The script installs both POMs and the jar
into that repository without replacing the official `com.asyncapi` artifacts in Maven Local.
It rejects overwriting an existing version with different contents; change the version
before changing a distributed artifact. No remote deployment is performed by the script.

Pass that repository explicitly to the Asyncwright build:

```powershell
./gradlew.bat check assemble -PasyncwrightModelRepository=C:/path/to/model-repository
```

Public version `.3` introduced Maven Central publishing metadata, source/Javadoc artifacts and a
`central-release` profile using Central Publishing Maven Plugin 0.12.0. It replaces the
inherited OSSRH/Nexus configuration; model Java sources are unchanged. The new version
avoids replacing previously installed `.2` artifacts with different POMs or provenance.

For a future version, change the version and provenance first, then prepare locally without uploading:

```powershell
./tools/prepare-release.ps1 -Unsigned
python tools/verify-release-bundle.py --allow-unsigned
```

`prepare-release.ps1` cleans old staging/signatures, runs tests and packages a Central bundle with `central.skipUpload=true`
explicitly enforced, and skips installation into the user's Maven Local repository.
`-Unsigned` is for verification only. Without it, configure a real GPG key externally;
the signed bundle still stays local. The manual preparation workflow uses unsigned mode,
has no publication secrets and never deploys remotely. Inspect `target/central-publishing/central-bundle.zip`.
GitHub's manual workflow entry requires the workflow file on the repository's default branch;
the local preparation command works independently of that repository setup.
The verifier checks both POMs, JARs, checksums and provenance; `--gpg PATH` additionally
checks all five detached signatures using the configured keyring.

Before a later authorized release, verify the Central namespace `io.github.sjacek.jasyncapi`,
configure the `central` server token in private Maven settings and configure the release GPG
key/passphrase outside the repository. Use an immutable distribution commit and review the
bundle. Only an explicitly authorized Maven invocation with profile `central-release` and
`'-Dcentral.skipUpload=false'` uploads. `autoPublish=false` then leaves final release to the
maintainer in Central Portal. The `.4` release has already completed validation and publication; this procedure is for a new version.
Do not upload verification bundles signed with a disposable test key, or overwrite a
published version. See the [official Central instructions](https://central.sonatype.org/publish/publish-portal-maven/).

# Asyncwright model distribution

## Published .5 distribution

Model **1.0.0-RC4-asyncwright.5** was published to Maven Central on **2026-10-27**
and is used by released **Asyncwright 0.2.1** and 0.2.0. Consumers need no source checkout,
Maven Local or explicit file repository:

```groovy
repositories { mavenCentral() }
dependencies {
    implementation 'io.github.sjacek.jasyncapi:asyncapi-core:1.0.0-RC4-asyncwright.5'
}
```

The parent POM is `io.github.sjacek.jasyncapi:asyncapi:1.0.0-RC4-asyncwright.5`.
Parent/core POMs, model binary, sources, Javadoc, five GPG signatures and twenty
checksums were verified from Central. All 30 public files match the signed bundle
(SHA-256 `c002279919b20c9da72748b009bad9bb6b4d1592b51cd2a844dd6d6c5377faac`).
The model JAR SHA-256 is
`c4b06d0c3ab596c56bdfc4428073f918a0c1ce69e69fb728a858a2e4ad363694`.

Release distribution commit:
`e07d9e1ded75711a5180ea435b0ce02c27a89de8` on `build/asyncwright-distribution`.
The immutable model-source commit is
[`6de0c02e884a9336f727a6cc797ac8eb304c0e87`](https://github.com/sjacek/jasyncapi/commit/6de0c02e884a9336f727a6cc797ac8eb304c0e87)
from `fix/kafka-topic-configuration`. These are separate references:
`modelSourceCommit` records the source SHA; `distributionVersion` records `.5`,
with no working-tree marker. Public artifacts preserve Java packages `com.asyncapi`,
Apache-2.0 licensing, upstream authorship, LICENSE and NOTICE.

Both Kafka topic-configuration versions (0.4.0 and 0.5.0) retain 64-bit retention
and additional JSON values. Retention APIs now use Long, and all-arguments constructors
include the additional-property map: recompile affected model consumers and extensions.
Document validation still rejects extra topic configuration in binding 0.4.0.
See [Kafka topic configuration](docs/kafka-topic-configuration.md) and the [changelog](CHANGELOG.md).
This is model support, not acceptance of the full Kafka/Avro/Registry profile.

Release preparation on JDK 21 / Maven 3.9.16 passed **4284 model tests**, with no
failures, errors or skips. The final distribution passed all eight Asyncwright
Gradle/JDK matrix combinations; the public plugin consumer resolved this exact .5
JAR and passed custom extensions, bundled DTO generation, compilation and cache checks.

Asyncwright **0.2.1**, released on **2026-10-28**, reuses these exact model artifacts
and the same immutable CI pin. Its bundled-schema classloader fix is in the plugin;
it does not change model APIs, binaries or provenance. The public 0.2.1/.5 consumers
passed extension, DTO compilation, failure cleanup and cache checks.

Earlier model .4 / plugin 0.1.1 coordinates remain unchanged. Documentation-only
changes after release do not move release provenance or the Asyncwright CI pin.
For future model development, use a new version before changing distributed bytes,
and a fresh explicit file repository for preview candidates. Build/test tooling below
is local and does not publish unless upload is explicitly authorized and enabled.

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

To reproduce the historical .4 baseline from Maven Central:

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

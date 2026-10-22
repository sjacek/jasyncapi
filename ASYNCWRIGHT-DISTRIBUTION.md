# Asyncwright model distribution

This branch distributes the unchanged Java model from fork commit
`05116f0d99f9e3f3c1b7d1aa8491f120364acf91` under separate Maven coordinates:

```text
io.github.sjacek.jasyncapi:asyncapi:1.0.0-RC4-asyncwright.3       (parent POM)
io.github.sjacek.jasyncapi:asyncapi-core:1.0.0-RC4-asyncwright.3  (model jar)
```

The distribution aligns Jackson to 2.22.4 and Kotlin to 2.1.21, the combination used for
the regression suite. Packages remain `com.asyncapi`. The Apache-2.0 license and original
authorship are retained; LICENSE, NOTICE and source provenance are included
in the model jar. Distribution metadata is separate from the upstream bug-fix branch.
The RC4 base has no NOTICE file; the distribution includes the attribution from upstream
`master` at `6c2a325f0826f26cfe26003af208ce15e5e9bbbe`. Local `.1` was superseded by `.2`
to include this notice without overwriting the already installed artifact.

With JDK 21 configured in Maven Toolchains, build, test and install into an explicit local
file repository:

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

Version `.3` prepares Maven Central publishing metadata, source/Javadoc artifacts and a
`central-release` profile using Central Publishing Maven Plugin 0.12.0. It replaces the
inherited OSSRH/Nexus configuration; model Java sources are unchanged. The new version
avoids replacing previously installed `.2` artifacts with different POMs or provenance.

Prepare locally without uploading:

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
maintainer in Central Portal. Neither upload nor remote validation has been performed here.
Do not upload verification bundles signed with a disposable test key, or overwrite a
published version. See the [official Central instructions](https://central.sonatype.org/publish/publish-portal-maven/).

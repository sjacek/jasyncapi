# Asyncwright model distribution

This branch distributes the unchanged Java model from fork commit
`05116f0d99f9e3f3c1b7d1aa8491f120364acf91` under separate Maven coordinates:

```text
io.github.sjacek.jasyncapi:asyncapi:1.0.0-RC4-asyncwright.2       (parent POM)
io.github.sjacek.jasyncapi:asyncapi-core:1.0.0-RC4-asyncwright.2  (model jar)
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

Public hosting, signing and release automation remain separate work before a public
Asyncwright release. No release or upstream pull request is created by this setup.

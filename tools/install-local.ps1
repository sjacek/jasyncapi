param(
    [Parameter(Mandatory = $true)][string]$Repository,
    [string]$Maven = 'mvn.cmd',
    [string]$Toolchains,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$distributionRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repositoryPath = [System.IO.Path]::GetFullPath($Repository)
[xml]$parentPom = Get-Content -Raw -LiteralPath (Join-Path $distributionRoot 'pom.xml')
$group = [string]$parentPom.project.groupId
$version = [string]$parentPom.project.version
if ($group -ne 'io.github.sjacek.jasyncapi') { throw 'This script requires the fork distribution POM.' }
$jar = Join-Path $distributionRoot "asyncapi-core/target/asyncapi-core-$version.jar"
$common = @('-B', '-ntp')
if ($Toolchains) { $common += @('-t', [System.IO.Path]::GetFullPath($Toolchains)) }

Push-Location $distributionRoot
try {
    if (-not $SkipBuild) {
        & $Maven @common verify
        if ($LASTEXITCODE -ne 0) { throw 'Fork build or tests failed.' }
    }
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the distribution before installing it.' }
    $groupPath = $group.Replace('.', '/')
    $artifacts = @(
        @{ Source = (Join-Path $distributionRoot 'pom.xml'); Target = "$groupPath/asyncapi/$version/asyncapi-$version.pom" },
        @{ Source = (Join-Path $distributionRoot 'asyncapi-core/pom.xml'); Target = "$groupPath/asyncapi-core/$version/asyncapi-core-$version.pom" },
        @{ Source = $jar; Target = "$groupPath/asyncapi-core/$version/asyncapi-core-$version.jar" }
    )
    foreach ($artifact in $artifacts) {
        $destination = Join-Path $repositoryPath $artifact.Target
        if ((Test-Path -LiteralPath $destination) -and
            ((Get-FileHash -LiteralPath $artifact.Source).Hash -ne (Get-FileHash -LiteralPath $destination).Hash)) {
            throw "Existing artifact differs: $destination. Increment the fork version instead of overwriting it."
        }
    }
    $installGoal = 'org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file'
    & $Maven @common -N $installGoal "-Dfile=$distributionRoot/pom.xml" "-DpomFile=$distributionRoot/pom.xml" "-DlocalRepositoryPath=$repositoryPath"
    if ($LASTEXITCODE -ne 0) { throw 'Parent POM installation failed.' }
    & $Maven @common -N $installGoal "-Dfile=$jar" "-DpomFile=$distributionRoot/asyncapi-core/pom.xml" "-DlocalRepositoryPath=$repositoryPath"
    if ($LASTEXITCODE -ne 0) { throw 'Core artifact installation failed.' }
    Write-Output "Local fork repository: $repositoryPath"
} finally {
    Pop-Location
}

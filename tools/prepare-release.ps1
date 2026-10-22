param(
    [string]$Maven = 'mvn.cmd',
    [string]$Toolchains,
    [switch]$Unsigned
)

$ErrorActionPreference = 'Stop'
$distributionRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$arguments = @('-B', '-ntp', '-Pcentral-release', '-Dcentral.skipUpload=true', '-Dmaven.install.skip=true')
if ($Toolchains) { $arguments += @('-t', [IO.Path]::GetFullPath($Toolchains)) }
if ($Unsigned) { $arguments += '-Dgpg.skip=true' }
Push-Location $distributionRoot
try {
    # central.skipUpload is explicit even though it defaults to true in the POM.
    # This produces a local Central bundle and never submits it for remote validation.
    # Clean previous staging/signatures so unsigned previews cannot inherit a prior signed bundle.
    & $Maven @arguments clean deploy
    if ($LASTEXITCODE -ne 0) { throw 'Model release preparation failed.' }
    $bundle = Join-Path $distributionRoot 'target/central-publishing/central-bundle.zip'
    if (-not (Test-Path -LiteralPath $bundle)) { throw 'Central bundle was not produced.' }
    Write-Output "Prepared model bundle: $bundle"
    if ($Unsigned) { Write-Output 'Unsigned verification bundle only; it cannot be released to Maven Central.' }
} finally {
    Pop-Location
}

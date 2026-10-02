# Uploads the signing key and the gitignored config files to this repository's GitHub Actions
# secrets, so .github/workflows/release.yml can build and sign releases without this computer.
#
# Run it yourself, once, from the repo root on a machine that has the files (and again only if one
# of them changes):
#
#   powershell -ExecutionPolicy Bypass -File scripts\set-ci-secrets.ps1
#
# Nothing is printed or written anywhere but GitHub, which stores secrets encrypted and never shows
# them again. Needs the GitHub CLI, logged in (`gh auth status`).

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$needed = 'keystore.properties', 'supabase.properties', 'google-services.json'
foreach ($file in $needed) {
    if (-not (Test-Path $file)) { throw "$file is missing from $root" }
}

# The keystore is whichever file keystore.properties names.
$storeLine = Get-Content keystore.properties | Where-Object { $_ -match '^\s*storeFile\s*=' } | Select-Object -First 1
if (-not $storeLine) { throw 'keystore.properties has no storeFile line' }
$storeFile = ($storeLine -split '=', 2)[1].Trim()
if (-not (Test-Path $storeFile)) { throw "The keystore $storeFile is missing from $root" }

function Set-Secret($name, $value) {
    $value | gh secret set $name
    if ($LASTEXITCODE -ne 0) { throw "Could not set $name" }
    Write-Host "set $name"
}

Set-Secret 'KEYSTORE_BASE64' ([Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $storeFile).Path)))
Set-Secret 'KEYSTORE_PROPERTIES' ([IO.File]::ReadAllText((Resolve-Path 'keystore.properties').Path))
Set-Secret 'SUPABASE_PROPERTIES' ([IO.File]::ReadAllText((Resolve-Path 'supabase.properties').Path))
Set-Secret 'GOOGLE_SERVICES_JSON' ([IO.File]::ReadAllText((Resolve-Path 'google-services.json').Path))

Write-Host ''
Write-Host 'Done. Secrets on this repository now:'
gh secret list

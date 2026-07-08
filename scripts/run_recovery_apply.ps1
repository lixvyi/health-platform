param(
  [string]$DbHost = "localhost",
  [string]$DbName = "health_portal",
  [string]$DbUser = "root"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Migration = Join-Path $ProjectRoot "scripts\apply_v002_migration.py"
$Importer = Join-Path $ProjectRoot "scripts\health_resource_import.py"

if (-not (Test-Path $Migration)) {
  throw "Migration script not found: $Migration"
}
if (-not (Test-Path $Importer)) {
  throw "Importer not found: $Importer"
}

Write-Host "Health platform recovery apply"
Write-Host "Project: $ProjectRoot"
Write-Host "Database: $DbUser@$DbHost/$DbName"
Write-Host ""
Write-Host "This script performs non-destructive schema migration and idempotent imports."
Write-Host "It does not drop, truncate, or delete user data."
Write-Host ""

$SecurePassword = Read-Host "Input MySQL password" -AsSecureString
$Bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePassword)
try {
  $PlainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($Bstr)
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($Bstr)
}

Push-Location $ProjectRoot
try {
  $env:HEALTH_DB_HOST = $DbHost
  $env:HEALTH_DB_USER = $DbUser
  $env:HEALTH_DB_PASSWORD = $PlainPassword
  $env:HEALTH_DB_NAME = $DbName

  Write-Host "Step 1/3: running V002 migration..."
  python $Migration

  Write-Host "Step 2/3: running dry-run audit..."
  python $Importer

  Write-Host "Step 3/3: applying idempotent imports..."
  python $Importer --apply --export
} finally {
  $env:HEALTH_DB_PASSWORD = $null
  Pop-Location
}

Write-Host ""
Write-Host "Recovery import completed. Please restart the backend service so the resource pool reflects the latest state."

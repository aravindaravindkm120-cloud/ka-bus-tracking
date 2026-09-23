# KA Bus Tracking - Database Migration Runner
# Applies schema, migrations, and seed against a local MySQL.
#
# Usage (PowerShell, from repository root):
#   .\database\run_migrations.ps1 -DbUser root -DbPassword secret
#   .\database\run_migrations.ps1 -DryRun            # print commands only

param(
    [string]$DbUser = 'root',
    [string]$DbPassword = '',
    [string]$DbHost = 'localhost',
    [string]$DbPort = '3306',
    [string]$DbName = 'ka_bus_tracking',
    [switch]$DryRun,
    [switch]$IncludeDevSeed
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$schemaFile = Join-Path $PSScriptRoot 'schema\schema.sql'
$seedFile   = Join-Path $PSScriptRoot 'seed\seed.sql'
$devSeedFile = Join-Path $PSScriptRoot 'seed\dev_test_account.sql'
$migrationDir = Join-Path $PSScriptRoot 'migrations'

$existingPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $DbPassword
try {
    $mysql = 'mysql'

    function Invoke-SqlFile {
        param([string]$File)
        if (-not (Test-Path $File)) { throw "SQL file not found: $File" }
        if ($DryRun) {
            Write-Host "[dry-run] mysql -u $DbUser $DbName < $File"
            return
        }
        Get-Content -Raw -LiteralPath $File | & $mysql -u $DbUser -h $DbHost -P $DbPort $DbName '--default-character-set=utf8mb4'
        if ($LASTEXITCODE -ne 0) { throw "Failed applying $File (exit $LASTEXITCODE)" }
        Write-Host "Applied: $File"
    }

    if (-not $DryRun) {
        & $mysql -u $DbUser -h $DbHost -P $DbPort -e "CREATE DATABASE IF NOT EXISTS $DbName CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
        if ($LASTEXITCODE -ne 0) { throw 'Failed to create database' }
        & $mysql -u $DbUser -h $DbHost -P $DbPort $DbName -e "CREATE TABLE IF NOT EXISTS schema_migrations (file varchar(255) PRIMARY KEY, applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3));"
    }

    Invoke-SqlFile $schemaFile

    $migrations = Get-ChildItem -LiteralPath $migrationDir -Filter '*.sql' | Sort-Object Name
    foreach ($m in $migrations) {
        if (-not $DryRun) {
            $row = & $mysql -u $DbUser -h $DbHost -P $DbPort $DbName -N -e "SELECT COUNT(*) FROM schema_migrations WHERE file = '$($m.Name)';"
            if ($row -match '[1-9]\d*') {
                Write-Host "Skipped (already applied): $($m.Name)"
                continue
            }
        }
        Invoke-SqlFile $m.FullName
        if (-not $DryRun) {
            & $mysql -u $DbUser -h $DbHost -P $DbPort $DbName -e "INSERT INTO schema_migrations (file) VALUES ('$($m.Name)');" 2>$null
        }
    }

    Invoke-SqlFile $seedFile

    if ($IncludeDevSeed) {
        Invoke-SqlFile $devSeedFile
    } else {
        Write-Host "(dev test account skipped; use -IncludeDevSeed to seed it)"
    }

    Write-Host ''
    Write-Host "Database '$DbName' is ready. Login: use backend bootstrap (see backend/.env.example)."
}
finally {
    if ($null -eq $existingPwd) { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $existingPwd }
}
param(
    [string]$DbHost,
    [int]$DbPort = 0,
    [string]$DbUser,
    [string]$DbPassword,
    [string]$DbName,
    [switch]$Pull,
    [switch]$SkipBackup,
    [switch]$WithImages,
    [int]$ImageLimit = 300
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = Resolve-Path (Join-Path $ScriptDir "..")
$JdbcPath = Join-Path $Root "health-portal-backend\src\main\resources\jdbc.properties"
$BackupDir = Join-Path $Root "db-backups"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Read-JdbcConfig {
    $config = @{
        host = "localhost"
        port = 3306
        user = "root"
        password = ""
        database = "health_portal"
    }

    if (-not (Test-Path $JdbcPath)) {
        return $config
    }

    foreach ($line in Get-Content -Path $JdbcPath -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("mysql.username=")) {
            $config.user = $trimmed.Substring("mysql.username=".Length)
        } elseif ($trimmed.StartsWith("mysql.password=")) {
            $config.password = $trimmed.Substring("mysql.password=".Length)
        } elseif ($trimmed.StartsWith("mysql.url=")) {
            $url = $trimmed.Substring("mysql.url=".Length)
            if ($url -match "jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?]+)") {
                $config.host = $Matches[1]
                if ($Matches[2]) {
                    $config.port = [int]$Matches[2]
                }
                $config.database = $Matches[3]
            }
        }
    }
    return $config
}

function Get-PythonCommand {
    $py = Get-Command py -ErrorAction SilentlyContinue
    if ($py) {
        return @{ command = $py.Source; args = @("-3") }
    }

    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python) {
        return @{ command = $python.Source; args = @() }
    }

    throw "Python was not found in PATH. Install Python 3 or add it to PATH."
}

function Invoke-CheckedNative {
    param(
        [string]$Name,
        [string]$Command,
        [string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE"
    }
}

function Invoke-PythonScript {
    param(
        [string]$Script,
        [string[]]$Arguments = @()
    )

    $python = Get-PythonCommand
    $allArgs = @($python.args) + @($Script) + $Arguments
    Invoke-CheckedNative -Name $Script -Command $python.command -Arguments $allArgs
}

function Get-MysqlArgs {
    $args = @(
        "--default-character-set=utf8mb4",
        "--host=$DbHost",
        "--port=$DbPort",
        "--user=$DbUser",
        "--password=$DbPassword",
        "--database=$DbName"
    )
    return $args
}

function Invoke-MysqlSqlFile {
    param([string]$Path)

    $mysql = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $mysql) {
        throw "mysql client was not found in PATH. Install MySQL client tools or run migrations manually."
    }

    Get-Content -Path $Path -Raw -Encoding UTF8 | & $mysql.Source @(Get-MysqlArgs)
    if ($LASTEXITCODE -ne 0) {
        throw "mysql failed while applying $Path"
    }
}

function Invoke-MysqlQuery {
    param([string]$Query)

    $mysql = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $mysql) {
        throw "mysql client was not found in PATH. Install MySQL client tools or run validations manually."
    }

    $args = @(Get-MysqlArgs) + @("--batch", "--raw")
    $Query | & $mysql.Source @args
    if ($LASTEXITCODE -ne 0) {
        throw "mysql query failed"
    }
}

function Backup-Database {
    if ($SkipBackup) {
        Write-Host "Backup skipped by -SkipBackup."
        return
    }

    $dump = Get-Command mysqldump -ErrorAction SilentlyContinue
    if (-not $dump) {
        Write-Warning "mysqldump was not found in PATH, so the backup step was skipped."
        return
    }

    New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $backupFile = Join-Path $BackupDir "$DbName-$stamp.sql"
    $args = @(
        "--default-character-set=utf8mb4",
        "--single-transaction",
        "--quick",
        "--routines",
        "--host=$DbHost",
        "--port=$DbPort",
        "--user=$DbUser",
        "--password=$DbPassword",
        "--result-file=$backupFile",
        $DbName
    )
    Invoke-CheckedNative -Name "mysqldump" -Command $dump.Source -Arguments $args
    Write-Host "Backup written to $backupFile"
}

function Invoke-OptionalSqlMigration {
    param(
        [string]$FileName,
        [string]$Reason
    )

    $path = Join-Path $Root "sql\migrations\$FileName"
    if (-not (Test-Path $path)) {
        Write-Warning "Missing migration file: $FileName"
        return
    }

    try {
        Invoke-MysqlSqlFile -Path $path
    } catch {
        Write-Warning "$FileName was skipped: $($_.Exception.Message)"
        if ($Reason) {
            Write-Warning $Reason
        }
    }
}

$jdbc = Read-JdbcConfig
if (-not $DbHost) { $DbHost = $jdbc.host }
if (-not $DbPort) { $DbPort = $jdbc.port }
if (-not $DbUser) { $DbUser = $jdbc.user }
if (-not $DbPassword) { $DbPassword = $jdbc.password }
if (-not $DbName) { $DbName = $jdbc.database }

if (-not $DbPassword) {
    $secure = Read-Host "MySQL password" -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $DbPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

$env:HEALTH_DB_HOST = $DbHost
$env:HEALTH_DB_PORT = [string]$DbPort
$env:HEALTH_DB_USER = $DbUser
$env:HEALTH_DB_PASSWORD = $DbPassword
$env:HEALTH_DB_NAME = $DbName

Set-Location $Root

Write-Host "Database target: $DbUser@$DbHost`:$DbPort/$DbName"
Write-Host "Images: $($WithImages.IsPresent)  ImageLimit: $ImageLimit"

if ($Pull) {
    Write-Step "Pull latest origin/master"
    Invoke-CheckedNative -Name "git pull" -Command "git" -Arguments @("pull", "origin", "master")
}

Write-Step "Backup current database"
Backup-Database

Write-Step "Apply V002 schema migration"
Invoke-PythonScript -Script (Join-Path $Root "scripts\apply_v002_migration.py") -Arguments @(
    "--host", $DbHost,
    "--user", $DbUser,
    "--password", $DbPassword,
    "--database", $DbName
)

Write-Step "Apply portal and compatibility migrations"
Invoke-OptionalSqlMigration -FileName "V003__portal_user_and_applies.sql" -Reason ""
Invoke-OptionalSqlMigration -FileName "V005__knowledge_schema_compatibility.sql" -Reason ""
Invoke-OptionalSqlMigration -FileName "V004__data_governance_dashboard.sql" -Reason "If this machine has not started the latest backend yet, V004 may be applied automatically after restart."

Write-Step "Import health resources, drugs, and vaccines"
Invoke-PythonScript -Script (Join-Path $Root "scripts\health_resource_import.py") -Arguments @("--apply")

Write-Step "Import local policy library"
Invoke-PythonScript -Script (Join-Path $Root "scripts\import_local_policies.py")

Write-Step "Sync news center from tracked crawl data"
Invoke-PythonScript -Script (Join-Path $Root "scripts\sync_news_feed.py")

Write-Step "Repair health encyclopedia categories"
Invoke-PythonScript -Script (Join-Path $Root "scripts\repair_knowledge_categories.py")

if ($WithImages) {
    Write-Step "Match NEWS cover images"
    Invoke-PythonScript -Script (Join-Path $Root "scripts\match_article_covers.py") -Arguments @(
        "--apply",
        "--category", "NEWS",
        "--limit", [string]$ImageLimit
    )

    Write-Step "Match KNOWLEDGE cover images"
    Invoke-PythonScript -Script (Join-Path $Root "scripts\match_article_covers.py") -Arguments @(
        "--apply",
        "--category", "KNOWLEDGE",
        "--limit", [string]$ImageLimit
    )
} else {
    Write-Host ""
    Write-Host "Cover image matching skipped. Re-run with -WithImages if article images are wrong or missing."
}

Write-Step "Validation summary"
Invoke-MysqlQuery -Query @"
SELECT category_code AS category, COUNT(*) AS total,
       SUM(CASE WHEN cover_url IS NULL OR cover_url = '' THEN 1 ELSE 0 END) AS missing_cover
FROM cms_content
WHERE category_code IN ('NEWS', 'POLICY', 'KNOWLEDGE')
GROUP BY category_code
ORDER BY category_code;

SELECT COUNT(*) AS policy_total,
       COUNT(DISTINCT source_url) AS policy_distinct_source_url
FROM cms_content
WHERE category_code='POLICY' AND status=1;

SELECT COUNT(*) AS news_policy_url_overlap
FROM cms_content n
JOIN cms_content p ON p.category_code='POLICY' AND p.source_url = n.source_url
WHERE n.category_code='NEWS' AND n.source_url IS NOT NULL;

SELECT COUNT(*) AS active_knowledge_categories
FROM knowledge_category
WHERE status=1;

SELECT COUNT(*) AS drug_catalog_total
FROM medical_drug_catalog;

SELECT kc.code AS category_code, COUNT(r.content_id) AS article_count
FROM knowledge_category kc
LEFT JOIN content_category_rel r ON r.category_id = kc.id OR r.category_code = kc.code
WHERE kc.status=1
GROUP BY kc.code
ORDER BY kc.sort_order, kc.code;
"@

Write-Host ""
Write-Host "Database sync complete." -ForegroundColor Green
Write-Host "Restart the backend after this script finishes."

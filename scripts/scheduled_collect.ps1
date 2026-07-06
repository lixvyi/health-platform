# 健康门户 - 每日自动采集（开放数据 + 政策/知识 CMS + 新闻同步）
$ErrorActionPreference = "Continue"
$Root = "d:\platform"
$LogDir = Join-Path $Root "logs"
$LogFile = Join-Path $LogDir "daily-collect.log"

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }

function Write-Log($msg) {
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
    Add-Content -Path $LogFile -Value $line -Encoding UTF8
    Write-Host $line
}

Write-Log "===== 开始每日采集 ====="

Set-Location $Root

# 确保 MySQL、Docker Redis 可用（Redis 可选）
Write-Log "Step 1: crawl_open_data.py"
$py = Get-Command python -ErrorAction SilentlyContinue
if (-not $py) {
    Write-Log "ERROR: 未找到 python，请先安装 Python 并加入 PATH"
    exit 1
}

& python (Join-Path $Root "scripts\crawl_open_data.py") 2>&1 | ForEach-Object { Write-Log $_ }
if ($LASTEXITCODE -ne 0) { Write-Log "WARN: crawl_open_data 退出码 $LASTEXITCODE" }

Write-Log "Step 2: sync_news_feed.py"
& python (Join-Path $Root "scripts\sync_news_feed.py") 2>&1 | ForEach-Object { Write-Log $_ }

Write-Log "===== 采集完成 ====="
exit 0

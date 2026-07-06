# 注册 Windows 计划任务：每天 06:00 自动采集
# 请以管理员身份运行 PowerShell：右键 → 以管理员身份运行

$TaskName = "HealthPortal-DailyCollect"
$ScriptPath = "d:\platform\scripts\scheduled_collect.ps1"

if (-not (Test-Path $ScriptPath)) {
    Write-Error "找不到脚本: $ScriptPath"
    exit 1
}

$Action = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$ScriptPath`""

$Trigger = New-ScheduledTaskTrigger -Daily -At "06:00"

$Settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Hours 2)

$Principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited

try {
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
    Register-ScheduledTask `
        -TaskName $TaskName `
        -Action $Action `
        -Trigger $Trigger `
        -Settings $Settings `
        -Principal $Principal `
        -Description "健康大数据门户：每日自动采集开放数据、政策、健康知识并同步新闻中心" | Out-Null
    Write-Host "计划任务已注册: $TaskName (每天 06:00)" -ForegroundColor Green
    Write-Host "日志: d:\platform\logs\daily-collect.log"
    Write-Host "立即测试: powershell -File d:\platform\scripts\scheduled_collect.ps1"
} catch {
    Write-Error "注册失败（可能需要管理员权限）: $_"
    Write-Host "备选：任务计划程序 → 创建基本任务 → 触发器 每天 6:00 → 操作 启动程序 powershell.exe -File $ScriptPath"
    exit 1
}

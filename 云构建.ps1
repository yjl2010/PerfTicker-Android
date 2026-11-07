# PerfTicker for Android - 一键云构建
# 用法: powershell -ExecutionPolicy Bypass -File .\云构建.ps1
# 可选: -RepoName xxx -Branch main -Public
#
# 流程: 校验 Token -> 创建/复用仓库 -> 用 Git Data API 推送全部源码
#       -> 等 GitHub Actions 构建 -> 下载 PerfTicker-apk 制品 -> 解出 APK 到本目录

param(
    [string]$RepoName = 'PerfTicker-Android',
    [string]$Branch = 'main',
    [string]$Token = $env:GITHUB_TOKEN,
    [switch]$Public
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $root
$api = 'https://api.github.com'

function Fail([string]$msg) {
    Write-Host ''
    Write-Host ('[失败] ' + $msg) -ForegroundColor Red
    exit 1
}

function Headers([string]$token) {
    return @{ Authorization = 'Bearer ' + $token; Accept = 'application/vnd.github+json' }
}

if (-not $Token) {
    Write-Host '需要一个 GitHub Token。classic 勾选 repo + workflow；fine-grained 勾选 Contents / Actions / Administration 读写。' -ForegroundColor Yellow
    $Token = Read-Host '粘贴 Token'
}
if (-not $Token) { Fail '没有 Token，无法推送。' }

$h = Headers $Token

Write-Host '[1/6] 校验 Token ...' -ForegroundColor Cyan
try {
    $me = Invoke-RestMethod -Uri ($api + '/user') -Headers $h -TimeoutSec 30
} catch {
    Fail ('Token 无效或访问不了 api.github.com: ' + $_.Exception.Message)
}
$login = $me.login
$repoFull = $login + '/' + $RepoName
$vis = 'private'
if ($Public) { $vis = 'public' }
Write-Host ('      账号 ' + $login + ' -> 仓库 ' + $repoFull + ' (' + $vis + ')')

Write-Host '[2/6] 准备仓库 ...' -ForegroundColor Cyan
try {
    $null = Invoke-RestMethod -Uri ($api + '/repos/' + $repoFull) -Headers $h -TimeoutSec 30
    Write-Host '      仓库已存在，推送新提交'
} catch {
    $body = @{ name = $RepoName; private = (-not $Public); auto_init = $false } | ConvertTo-Json
    try {
        $null = Invoke-RestMethod -Uri ($api + '/user/repos') -Method Post -Headers $h -Body $body -ContentType 'application/json' -TimeoutSec 60
        Write-Host '      已创建仓库'
    } catch {
        Fail ('创建仓库失败（fine-grained Token 需 Administration 权限）: ' + $_.Exception.Message)
    }
}

$parentSha = $null
try {
    $ref = Invoke-RestMethod -Uri ($api + '/repos/' + $repoFull + '/git/ref/heads/' + $Branch) -Headers $h -TimeoutSec 30
    $parentSha = $ref.object.sha
} catch { }

Write-Host '[3/6] 上传源码 ...' -ForegroundColor Cyan
$skipSegments = @('\.git\', '\out\', '\build\', '\.gradle\', '\.idea\', '\.rows\', '\app\keystore\')
$skipNames = @('云构建.ps1', 'local.properties')

$all = Get-ChildItem -LiteralPath $root -Recurse -File -Force
$files = @()
foreach ($f in $all) {
    $n = $f.Name
    if ($n -like '*.apk') { continue }
    if ($n -like '*.aab') { continue }
    if ($skipNames -contains $n) { continue }
    $p = $f.FullName
    $skip = $false
    foreach ($s in $skipSegments) {
        if ($p -like ('*' + $s + '*')) { $skip = $true; break }
    }
    if ($skip) { continue }
    $files += $f
}

Write-Host ('      共 ' + $files.Count + ' 个文件')
$tree = @()
$i = 0
foreach ($f in $files) {
    $i = $i + 1
    $rel = $f.FullName.Substring($root.Length).TrimStart('\').Replace('\', '/')
    $bytes = [IO.File]::ReadAllBytes($f.FullName)
    $b64 = [Convert]::ToBase64String($bytes)
    $body = @{ content = $b64; encoding = 'base64' } | ConvertTo-Json -Depth 5
    try {
        $blob = Invoke-RestMethod -Uri ($api + '/repos/' + $repoFull + '/git/blobs') -Method Post -Headers $h -Body $body -ContentType 'application/json' -TimeoutSec 60
    } catch {
        Fail ('上传 ' + $rel + ' 失败: ' + $_.Exception.Message)
    }
    $tree += @{ path = $rel; mode = '100644'; type = 'blob'; sha = $blob.sha }
    Write-Host ('      [' + $i + '/' + $files.Count + '] ' + $rel) -ForegroundColor DarkGray
}

Write-Host '[4/6] 生成提交 ...' -ForegroundColor Cyan
$treeBody = @{ tree = $tree } | ConvertTo-Json -Depth 8
$newTree = Invoke-RestMethod -Uri ($api + '/repos/' + $repoFull + '/git/trees') -Method Post -Headers $h -Body $treeBody -ContentType 'application/json' -TimeoutSec 60

$commitObj = @{ message = 'PerfTicker Android 移植版 - 云构建'; tree = $newTree.sha; parents = @() }
if ($parentSha) {
    $commitObj = @{ message = 'PerfTicker Android 移植版 - 云构建'; tree = $newTree.sha; parents = @($parentSha) }
}
$commitJson = $commitObj | ConvertTo-Json -Depth 8
$newCommit = Invoke-RestMethod -Uri ($api + '/repos/' + $repoFull + '/git/commits') -Method Post -Headers $h -Body $commitJson -ContentType 'application/json' -TimeoutSec 60

if ($parentSha) {
    $patch = @{ sha = $newCommit.sha; force = $true } | ConvertTo-Json
    $url = $api + '/repos/' + $repoFull + '/git/refs/heads/' + $Branch
    $null = Invoke-RestMethod -Uri $url -Method Patch -Headers $h -Body $patch -ContentType 'application/json' -TimeoutSec 60
} else {
    $refBody = @{ ref = 'refs/heads/' + $Branch; sha = $newCommit.sha } | ConvertTo-Json
    $null = Invoke-RestMethod -Uri ($api + '/repos/' + $repoFull + '/git/refs') -Method Post -Headers $h -Body $refBody -ContentType 'application/json' -TimeoutSec 60
}
$shortSha = $newCommit.sha.Substring(0, 7)
Write-Host ('      提交 ' + $shortSha + ' 已推送') -ForegroundColor Green

Write-Host '[5/6] 等待 Actions 构建，最长 20 分钟 ...' -ForegroundColor Cyan
$run = $null
$deadline = (Get-Date).AddMinutes(20)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 12
    try {
        $runs = Invoke-RestMethod -Uri ($api + '/repos/' + $repoFull + '/actions/runs?per_page=20') -Headers $h -TimeoutSec 30
    } catch {
        continue
    }
    $list = $runs.workflow_runs
    if ($null -eq $list) { continue }
    $hit = $null
    foreach ($r in $list) {
        if ($r.head_sha -eq $newCommit.sha) { $hit = $r; break }
    }
    if ($null -eq $hit) {
        Write-Host '      等待工作流入队 ...' -ForegroundColor DarkGray
        continue
    }
    $run = $hit
    Write-Host ('      状态 ' + $run.status + ' / ' + $run.conclusion) -ForegroundColor DarkGray
    if ($run.status -eq 'completed') { break }
}

if ($null -eq $run) {
    Fail ('20 分钟内没等到构建，手动看 https://github.com/' + $repoFull + '/actions')
}
$runUrl = 'https://github.com/' + $repoFull + '/actions/runs/' + $run.id
if ($run.conclusion -ne 'success') {
    Write-Host ''
    Write-Host ('[失败] 构建结果 ' + $run.conclusion) -ForegroundColor Red
    Write-Host ('       日志 ' + $runUrl) -ForegroundColor Yellow
    Write-Host '       把日志里第一条 error 发给我，我改代码。' -ForegroundColor Yellow
    exit 1
}
Write-Host ('      构建成功 ' + $runUrl) -ForegroundColor Green

Write-Host '[6/6] 下载 APK 制品 ...' -ForegroundColor Cyan
$arts = Invoke-RestMethod -Uri ($api + '/repos/' + $repoFull + '/actions/runs/' + $run.id + '/artifacts') -Headers $h -TimeoutSec 30
$art = $null
foreach ($a in $arts.artifacts) {
    if ($a.name -eq 'PerfTicker-apk') { $art = $a; break }
}
if ($null -eq $art) { Fail ('没找到 PerfTicker-apk 制品，手动到 ' + $runUrl + ' 下载') }

$zipPath = Join-Path $root '_artifact.zip'
$dlUrl = $art.archive_download_url
if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
    & curl.exe -fsSL -H ('Authorization: Bearer ' + $Token) -H 'Accept: application/vnd.github+json' -o $zipPath $dlUrl
    if ($LASTEXITCODE -ne 0) { Fail ('制品下载失败，手动到 ' + $runUrl + ' 下载') }
} else {
    Invoke-WebRequest -Uri $dlUrl -Headers $h -OutFile $zipPath -TimeoutSec 300
}

$outDir = Join-Path $root '_apk_tmp'
if (Test-Path -LiteralPath $outDir) { Remove-Item -LiteralPath $outDir -Recurse -Force }
Expand-Archive -LiteralPath $zipPath -DestinationPath $outDir -Force

$apks = Get-ChildItem -LiteralPath $outDir -Recurse -File -Filter *.apk
foreach ($a in $apks) {
    $dest = Join-Path $root $a.Name
    Copy-Item -LiteralPath $a.FullName -Destination $dest -Force
    $mb = [Math]::Round($a.Length / 1MB, 1)
    Write-Host ('      -> ' + $dest + '  (' + $mb + ' MB)') -ForegroundColor Green
}
Remove-Item -LiteralPath $zipPath -Force
Remove-Item -LiteralPath $outDir -Recurse -Force

Write-Host ''
Write-Host ('完成。APK 在本目录: ' + $root) -ForegroundColor Green
Write-Host '传到手机安装即可，首次需允许「安装未知来源应用」。' -ForegroundColor Green

param(
    [string]$Mensaje = "Backup automatico",
    [string]$MysqlDump = "C:\xampp\mysql\bin\mysqldump.exe",
    [string]$BaseDatos = "certificaciones_obra",
    [string]$Usuario = "root",
    [string]$Password = "",
    [string]$BackupPath = "backups\certificaciones_obra.sql",
    [switch]$NoPush
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (!(Test-Path $MysqlDump)) {
    throw "No se encontro mysqldump en: $MysqlDump"
}

$backupFullPath = Join-Path $projectRoot $BackupPath
$backupDir = Split-Path -Parent $backupFullPath
if (!(Test-Path $backupDir)) {
    New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
}

Write-Host "Generando backup de la base $BaseDatos..." -ForegroundColor Cyan
$argsDump = @(
    "--default-character-set=utf8mb4",
    "--routines",
    "--events",
    "-u", $Usuario,
    "--result-file=$backupFullPath",
    $BaseDatos
)
if ($Password) {
    $argsDump = @("--default-character-set=utf8mb4", "--routines", "--events", "-u", $Usuario, "-p$Password", "--result-file=$backupFullPath", $BaseDatos)
}
& $MysqlDump @argsDump

if ($LASTEXITCODE -ne 0) {
    throw "Fallo la generacion del backup. Codigo: $LASTEXITCODE"
}

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm"
$commitMessage = "$Mensaje - $timestamp"

Write-Host "Preparando commit local..." -ForegroundColor Cyan
git add -A
$changes = git status --short
if (-not $changes) {
    Write-Host "No hay cambios nuevos para commitear." -ForegroundColor Yellow
    exit 0
}

git commit -m $commitMessage
if ($LASTEXITCODE -ne 0) {
    throw "Fallo el commit. Codigo: $LASTEXITCODE"
}

$branch = git branch --show-current
if (-not $NoPush) {
    Write-Host "Subiendo a GitHub..." -ForegroundColor Cyan
    git push origin $branch
    if ($LASTEXITCODE -ne 0) {
        throw "Fallo el push a GitHub. Codigo: $LASTEXITCODE"
    }
}

Write-Host "Listo. Backup, commit y push completados: $commitMessage" -ForegroundColor Green

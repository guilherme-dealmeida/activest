<#
  Ponte de execucao para o Claude.
  Roda o build na sua maquina e despeja a saida em .claude\logs\,
  de onde o Claude le direto. Voce nao precisa colar nada no chat.

  Uso:
    .\.claude\build.ps1                 # so a API (mvn compile)
    .\.claude\build.ps1 -Web            # so o front (tsc -b + vite build)
    .\.claude\build.ps1 -Docker         # so os logs dos containers
    .\.claude\build.ps1 -All            # tudo
#>
param(
  [switch]$Web,
  [switch]$Docker,
  [switch]$All
)

$ErrorActionPreference = 'Continue'
$root    = Split-Path -Parent $PSScriptRoot
$logs    = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

# Sem nenhum switch, o padrao e a API.
$doApi    = $All -or (-not $Web -and -not $Docker)
$doWeb    = $All -or $Web
$doDocker = $All -or $Docker

function Write-Header([string]$file, [string]$cmd) {
  $header = @(
    "### comando : $cmd",
    "### quando  : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "### cwd     : $(Get-Location)",
    "### ---------------------------------------------------------------"
  )
  Set-Content -Path $file -Value $header -Encoding utf8
}

function Write-Footer([string]$file, [int]$code) {
  Add-Content -Path $file -Value "### ---------------------------------------------------------------"
  Add-Content -Path $file -Value "### exit code : $code"
  $status = if ($code -eq 0) { 'OK  ' } else { 'FALHA' }
  Write-Host "  $status -> $file"
}

if ($doApi) {
  $out = Join-Path $logs 'build-api.log'
  $cmd = 'mvn -B compile'
  Write-Host "[api] $cmd"
  Push-Location (Join-Path $root 'primevest-api')
  Write-Header $out $cmd
  & mvn -B compile *>&1 | Add-Content -Path $out -Encoding utf8
  Write-Footer $out $LASTEXITCODE
  Pop-Location
}

if ($doWeb) {
  $out = Join-Path $logs 'build-web.log'
  $cmd = 'npm run build'
  Write-Host "[web] $cmd"
  Push-Location (Join-Path $root 'primevest-web')
  Write-Header $out $cmd
  & npm run build *>&1 | Add-Content -Path $out -Encoding utf8
  Write-Footer $out $LASTEXITCODE
  Pop-Location
}

if ($doDocker) {
  $out = Join-Path $logs 'docker-api.log'
  $cmd = 'docker compose logs --no-color --tail 400 api'
  Write-Host "[docker] $cmd"
  Push-Location $root
  Write-Header $out $cmd
  & docker compose logs --no-color --tail 400 api *>&1 | Add-Content -Path $out -Encoding utf8
  Write-Footer $out $LASTEXITCODE
  Pop-Location
}

Write-Host ""
Write-Host "Pronto. Diga no chat: 'olha o log' — o Claude le .claude\logs\ sozinho."

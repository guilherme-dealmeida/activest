<#
  Ponte de execucao para o Claude.
  Roda o build na sua maquina e despeja a saida em .claude\logs\,
  de onde o Claude le direto. Voce nao precisa colar nada no chat.

  Uso:
    .\.claude\build.ps1                 # so a API (mvn compile)
    .\.claude\build.ps1 -Test           # testes rapidos (mvn clean test)
    .\.claude\build.ps1 -Verify         # testes + funcionais (mvn clean verify)
    .\.claude\build.ps1 -Web            # so o front (tsc -b + vite build)
    .\.claude\build.ps1 -Docker         # so os logs dos containers
    .\.claude\build.ps1 -All            # tudo
#>
param(
  [switch]$Web,
  [switch]$Docker,
  [switch]$Test,
  [switch]$Verify,
  [switch]$All
)

$ErrorActionPreference = 'Continue'
$root    = Split-Path -Parent $PSScriptRoot
$logs    = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

# Sem nenhum switch, o padrao e a API.
$doApi    = $All -or (-not $Web -and -not $Docker -and -not $Test -and -not $Verify)
$doWeb    = $All -or $Web
$doDocker = $All -or $Docker
$doTest   = $Test
# -All roda a bateria completa: e ela que prova que nada quebrou.
$doVerify = $All -or $Verify

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
  Push-Location $root  # pom.xml da raiz: core + app-api
  Write-Header $out $cmd
  & mvn -B compile *>&1 | Add-Content -Path $out -Encoding utf8
  Write-Footer $out $LASTEXITCODE
  Pop-Location
}

# Testes rapidos: repositorio, servico e controller (*Test).
#
# 'clean' de proposito: o target guarda as migrations antigas de antes do reset,
# e o Flyway recusa a subida reclamando de duas V1.
if ($doTest) {
  $out = Join-Path $logs 'test-api.log'
  $cmd = 'mvn -B clean test'
  Write-Host "[test] $cmd"
  Push-Location $root  # pom.xml da raiz: core + app-api
  Write-Header $out $cmd
  & mvn -B clean test *>&1 | Add-Content -Path $out -Encoding utf8
  Write-Footer $out $LASTEXITCODE
  Pop-Location
}

# Bateria completa: os *Test acima mais os funcionais (*IT), que sobem a
# aplicacao inteira contra um PostgreSQL em container (Testcontainers).
# Precisa do Docker Desktop rodando.
if ($doVerify) {
  $out = Join-Path $logs 'verify-api.log'
  $cmd = 'mvn -B clean verify'
  Write-Host "[verify] $cmd"
  Push-Location $root  # pom.xml da raiz: core + app-api
  Write-Header $out $cmd
  & mvn -B clean verify *>&1 | Add-Content -Path $out -Encoding utf8
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

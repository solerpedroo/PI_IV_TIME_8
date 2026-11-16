$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root ".env"
$ApiDir = Join-Path $Root "backend\api"
$Bin = Join-Path $ApiDir "bin\Servidor.class"

if ($args -contains "--memoria") {
  Write-Error "Este projeto usa MongoDB Atlas. Rode emular.bat sem --memoria."
  exit 1
}

if (-not (Test-Path -LiteralPath $EnvFile)) {
  Write-Host "Arquivo .env não encontrado na raiz." -ForegroundColor Red
  Write-Host "  copy .env.example .env"
  Write-Host "Preencha MONGODB_URI — docs\mongodb-atlas.md"
  exit 1
}

& (Join-Path $PSScriptRoot "carregar-env.ps1") $EnvFile

if (-not $env:MONGODB_URI) {
  Write-Host "MONGODB_URI vazio em .env." -ForegroundColor Red
  exit 1
}

$Build = $args -contains "--build"
$Port = ($args | Where-Object { $_ -match "^\d+$" } | Select-Object -First 1)

if ($Build -or -not (Test-Path -LiteralPath $Bin)) {
  Write-Host "Compilando backend\api…"
  Push-Location $ApiDir
  cmd /c compilar.bat
  if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
  Pop-Location
}

$HostPort = if ($Port) { $Port } else { "8080" }

$SocketDir = Join-Path $Root "backend\serversocket"
Push-Location $SocketDir
cmd /c compilar.bat
if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
$algo = Start-Process -FilePath "java" -ArgumentList @("-cp", "bin", "Servidor", "3000") -PassThru -WindowStyle Hidden
Pop-Location

$env:SERVIDOR_ALGORITMOS_HOST = "127.0.0.1"
$env:SERVIDOR_ALGORITMOS_PORT = "3000"
Start-Sleep -Milliseconds 500

Write-Host ""
Write-Host "AgroGestão (MongoDB Atlas)"
Write-Host "  App:    http://127.0.0.1:${HostPort}/login.html"
Write-Host "  Pronto: http://127.0.0.1:${HostPort}/ready"
Write-Host ""

Push-Location $ApiDir
try {
  & java -cp "bin;lib/*" Servidor $HostPort
} finally {
  if ($algo -and -not $algo.HasExited) {
    $algo.Kill()
  }
  Pop-Location
}
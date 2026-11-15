param(
  [Parameter(Mandatory = $true)]
  [string]$EnvPath
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EnvPath)) {
  throw "Arquivo não encontrado: $EnvPath"
}

Get-Content -LiteralPath $EnvPath | ForEach-Object {
  $line = $_.Trim()
  if ($line -eq "" -or $line.StartsWith("#")) { return }
  $eq = $line.IndexOf("=")
  if ($eq -lt 1) { return }
  $name = $line.Substring(0, $eq).Trim()
  $value = $line.Substring($eq + 1).Trim()
  if ($value.Length -ge 2) {
    $q = $value[0]
    if (($q -eq '"' -or $q -eq "'") -and $value.EndsWith($q)) {
      $value = $value.Substring(1, $value.Length - 2)
    }
  }
  [Environment]::SetEnvironmentVariable($name, $value, "Process")
}
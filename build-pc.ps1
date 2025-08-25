param(
  [string]$Rid = "win-x64",
  [switch]$SelfContained = $true
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$proj = Join-Path $root "pc-server\XvcPcServer\XvcPcServer.csproj"
$out  = Join-Path $root "dist\pc\$Rid"

Write-Host "Publicando PC server ($Rid)..."

New-Item -ItemType Directory -Force -Path $out | Out-Null

$props = @("-p:PublishSingleFile=true")
if ($SelfContained) {
  $props += "-p:PublishTrimmed=true"
} else {
  $props += "-p:PublishTrimmed=false"
}

dotnet publish "$proj" -c Release -r $Rid --self-contained:$SelfContained @props -o "$out"

Write-Host "Listo:"
Get-ChildItem "$out" | Where-Object { $_.Name -like "*.exe" } | ForEach-Object { Write-Host " - $($_.FullName)" }

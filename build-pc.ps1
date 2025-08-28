# Script para compilar el proyecto de Windows

$projectPath = ".\pc-server\XvcPcServer.csproj"
$outputDir = ".\publishWin"

dotnet publish $projectPath `
  -c Release `
  -r win-x64 `
  --self-contained true `
  /p:PublishSingleFile=true `
  /p:IncludeAllContentForSelfExtract=true `
  /p:PublishTrimmed=false `
  -o $outputDir

Write-Host "Build completo. El ejecutable está en $outputDir"
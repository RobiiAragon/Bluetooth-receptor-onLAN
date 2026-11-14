# Builds the Windows server (self-contained, single file)

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

Write-Host "Build complete. The executable is in $outputDir"
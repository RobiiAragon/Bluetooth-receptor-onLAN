# Script para compilar el APK de Android

$projectDir = "android-app"
$outputDir = ".\publishApk"
$apkOutputDir = "$projectDir\app\build\outputs\apk"
$gradlew = "$projectDir\gradlew.bat"

Push-Location $projectDir
& .\gradlew.bat --no-daemon assembleDebug
Pop-Location

$apk = Get-ChildItem -Path $apkOutputDir -Recurse -Filter *.apk | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($apk) {
    if (!(Test-Path $outputDir)) { New-Item -ItemType Directory -Path $outputDir | Out-Null }
    $dest = Join-Path $outputDir $apk.Name
    Copy-Item $apk.FullName $dest -Force
    Write-Host "Build completo. APK copiado a $dest"
} else {
    Write-Host "No se encontró ningún APK generado."
}

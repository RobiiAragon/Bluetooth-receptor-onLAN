# Builds the Android APK and installs it over adb

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
    Write-Host "Build complete. APK copied to $dest"
    # Install the APK over adb
    & adb install -r $dest
    Write-Host "APK installed on the Android device."
} else {
    Write-Host "No APK was produced."
}
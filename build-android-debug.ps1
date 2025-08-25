$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$androidRoot = Join-Path $root "android-app"
$apkSrc = Join-Path $androidRoot "app\build\outputs\apk\debug\app-debug.apk"
$apkOutDir = Join-Path $root "dist\android"
$apkOut = Join-Path $apkOutDir "app-debug.apk"

# 1) Asegurar JDK 17 (AGP 8.7 lo requiere)
function Get-JavaMajor {
  try {
    $out = & java -version 2>&1 | Out-String
    # Soporta formatos: 'openjdk version "17.0.x"' y 'openjdk 17.0.x'
    if ($out -match 'version\s+"(?<m>\d+)\.') { return [int]$Matches['m'] }
    if ($out -match '^\s*openjdk\s+(?<m>\d+)\.' ) { return [int]$Matches['m'] }
    if ($out -match '^\s*java\s+version\s+"(?<m>\d+)\.' ) { return [int]$Matches['m'] }
  } catch {}
  return 0
}
function Ensure-Java17 {
  $needSwitch = $true
  $maj = Get-JavaMajor
  if ($maj -ge 17) {
    Write-Host "Java en PATH es $maj (ok)."
    $needSwitch = $false
  }
  if ($needSwitch -and $env:JAVA_HOME) {
    $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $javaExe) {
      $old = $env:JAVA_HOME
      $env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
      $maj = Get-JavaMajor
      if ($maj -ge 17) {
        Write-Host "JAVA_HOME ya establecido: $old (Java $maj)"
        $needSwitch = $false
      }
    }
  }
  if ($needSwitch) {
    $candidates = @(
      "C:\Program Files\Microsoft\jdk-17*",
      "C:\Program Files\Java\jdk-17*",
      "$env:ProgramFiles\Amazon Corretto\jdk-17*",
      "$env:ProgramFiles\Amazon Corretto\jdk1.17*",
      "$env:USERPROFILE\.jdks\*17*"
    )
    $found = $null
    foreach ($p in $candidates) {
      $d = Get-ChildItem -Path $p -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
      if ($d) { $found = $d.FullName; break }
    }
    if (-not $found) {
      Write-Error "No se encontró JDK 17. Instala con: winget install --id Microsoft.OpenJDK.17 --exact y reabre PowerShell."
      throw
    }
    $env:JAVA_HOME = $found
    $env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
    $maj = Get-JavaMajor
    Write-Host "JAVA_HOME configurado a: $env:JAVA_HOME (Java $maj)"
  }
}
Ensure-Java17

# Crear/actualizar gradle.properties para fijar el JDK de Gradle
$gradleProps = Join-Path $androidRoot "gradle.properties"
$javaHomeLine = "org.gradle.java.home=$($env:JAVA_HOME -replace '\\','\\')"
if (Test-Path $gradleProps) {
  $content = Get-Content $gradleProps -Raw
  if ($content -notmatch '(?m)^\s*org\.gradle\.java\.home=') {
    Add-Content -Encoding ASCII -Path $gradleProps -Value $javaHomeLine
  } else {
    $new = ($content -replace '(?m)^\s*org\.gradle\.java\.home=.*$', $javaHomeLine)
    if ($new -ne $content) { Set-Content -Encoding ASCII -Path $gradleProps -Value $new }
  }
} else {
  @(
    "org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8",
    $javaHomeLine
  ) | Out-File -Encoding ASCII $gradleProps -Force
  Write-Host "Creado gradle.properties con JDK 17."
}

# Crear local.properties si falta
$localProps = Join-Path $androidRoot "local.properties"
if (-not (Test-Path $localProps)) {
  $defaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
  if (Test-Path $defaultSdk) {
    "sdk.dir=$($defaultSdk -replace '\\','\\')" | Out-File -Encoding ASCII $localProps -Force
    Write-Host "Creado local.properties apuntando a $defaultSdk"
  } else {
    Write-Warning "No se encontró Android SDK en $defaultSdk. Abre Android Studio y instala el SDK Platform 34."
  }
}

# Asegurar Gradle Wrapper (sin requerir Gradle en PATH)
$gradlew = Join-Path $androidRoot "gradlew.bat"
$gradleVersion = "8.9" # requerido por AGP 8.7.x
$toolsDir = Join-Path $root "tools"
$gradleZip = Join-Path $toolsDir "gradle-$gradleVersion-bin.zip"
$gradleDir = Join-Path $toolsDir "gradle-$gradleVersion"
$gradleBat = Join-Path $gradleDir "bin\gradle.bat"

New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null

if (-not (Test-Path $gradleDir)) {
  if (-not (Test-Path $gradleZip)) {
    Write-Host "Descargando Gradle $gradleVersion..."
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $gradleZip
  }
  Write-Host "Extrayendo Gradle..."
  Expand-Archive -Path $gradleZip -DestinationPath $toolsDir -Force
}

# Crear wrapper si falta
if (-not (Test-Path $gradlew)) {
  Write-Host "Creando Gradle Wrapper..."
  pushd $androidRoot
  try {
    & "$gradleBat" "-Dorg.gradle.java.home=$($env:JAVA_HOME)" wrapper --gradle-version $gradleVersion
  } catch {
    Write-Warning "Fallo creando el wrapper; se usará Gradle local para compilar."
  } finally { popd }
}

# Forzar wrapper a gradle 8.9 si ya existe
$wrapperProps = Join-Path $androidRoot "gradle\wrapper\gradle-wrapper.properties"
if (Test-Path $wrapperProps) {
  $wp = Get-Content -Raw $wrapperProps
  $newUrl = "https\://services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
  $wp2 = if ($wp -match '(?m)^\s*distributionUrl=') {
    ($wp -replace '(?m)^\s*distributionUrl=.*$', "distributionUrl=$newUrl")
  } else {
    $wp.TrimEnd() + "`n" + "distributionUrl=$newUrl"
  }
  if ($wp2 -ne $wp) {
    Set-Content -Path $wrapperProps -Value $wp2 -Encoding ASCII
    Write-Host "Actualizado gradle-wrapper.properties a Gradle $gradleVersion"
  }
}

Write-Host "Compilando APK (Debug)..."
pushd $androidRoot
if (Test-Path $gradlew) {
  & $gradlew "-Dorg.gradle.java.home=$($env:JAVA_HOME)" "assembleDebug"
} else {
  & "$gradleBat" "-Dorg.gradle.java.home=$($env:JAVA_HOME)" "assembleDebug"
}
popd

if (-not (Test-Path $apkSrc)) {
  throw "No se encontró el APK en $apkSrc"
}

New-Item -ItemType Directory -Force -Path $apkOutDir | Out-Null
Copy-Item $apkSrc $apkOut -Force
Write-Host "APK listo: $apkOut"

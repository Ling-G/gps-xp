param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Jdk = $env:JAVA_HOME,
    [string]$Platform = 'android-36',
    [string]$BuildTools = '36.1.0'
)
$ErrorActionPreference = 'Stop'
if (!$Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (!$Jdk) { throw 'Set JAVA_HOME to JDK 17 or later, or pass -Jdk.' }
$project = $PSScriptRoot
$out = Join-Path $project 'build\manual'
$classes = Join-Path $out 'classes'
$dex = Join-Path $out 'dex'
$tools = Join-Path $Sdk "build-tools\$BuildTools"
$android = Join-Path $Sdk "platforms\$Platform\android.jar"
$api = Join-Path $project 'app\libs\xposed-api-82.jar'
$appGradle = Get-Content (Join-Path $project 'app\build.gradle') -Raw
if ($appGradle -notmatch 'versionCode\s+(\d+)') { throw 'versionCode missing from app/build.gradle' }
$versionCode = $matches[1]
if ($appGradle -notmatch "versionName\s+'([^']+)'") { throw 'versionName missing from app/build.gradle' }
$versionName = $matches[1]
function Run([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $exe ($LASTEXITCODE)" }
}
if (!(Test-Path $android) -or !(Test-Path $api)) { throw 'Android SDK platform or Xposed API jar missing.' }
New-Item -ItemType Directory -Force $classes,$dex | Out-Null
$sources = @(Get-ChildItem (Join-Path $project 'app\src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
Run (Join-Path $Jdk 'bin\javac.exe') (@('-encoding','UTF-8','-source','8','-target','8','-classpath',"$android;$api",'-d',$classes) + $sources)
$jar = Join-Path $out 'classes.jar'
Run (Join-Path $Jdk 'bin\jar.exe') @('cf',$jar,'-C',$classes,'.')
$env:JAVA_HOME = $Jdk
Run (Join-Path $tools 'd8.bat') @('--lib',$android,'--lib',$api,'--min-api','26','--output',$dex,$jar)
# AGP supplies the namespace; direct SDK packaging needs the equivalent package attribute.
$manifest = Join-Path $out 'AndroidManifest.xml'
$xml = (Get-Content (Join-Path $project 'app\src\main\AndroidManifest.xml') -Raw).Replace('<manifest ', '<manifest package="io.github.gpsxp" ')
[IO.File]::WriteAllText($manifest,$xml,[Text.UTF8Encoding]::new($false))
$unsigned = Join-Path $out 'unsigned.apk'
Run (Join-Path $tools 'aapt2.exe') @('link','-I',$android,'--manifest',$manifest,'--min-sdk-version','26','--target-sdk-version','35','--version-code',$versionCode,'--version-name',$versionName,'-A',(Join-Path $project 'app\src\main\assets'),'-o',$unsigned)
Run (Join-Path $Jdk 'bin\jar.exe') @('uf',$unsigned,'-C',$dex,'classes.dex')
$aligned = Join-Path $out 'aligned.apk'
Run (Join-Path $tools 'zipalign.exe') @('-f','4',$unsigned,$aligned)
$key = Join-Path $out 'debug.keystore'
if (!(Test-Path $key)) {
    Run (Join-Path $Jdk 'bin\keytool.exe') @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=GPS XP Debug','-keyalg','RSA','-keysize','2048','-validity','10000','-noprompt')
}
$apk = Join-Path $out 'gps-xp-debug.apk'
Run (Join-Path $tools 'apksigner.bat') @('sign','--ks',$key,'--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,$aligned)
Run (Join-Path $tools 'apksigner.bat') @('verify','--verbose',$apk)
Write-Output "APK: $apk"

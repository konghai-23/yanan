<#
  一键构建安卓 APK（考研自习打卡）
  前置：JDK 17 + Android SDK（本机默认安装于 C:\Users\34497\android-tools\，见 README）
  用法：.\build-apk.ps1            # 使用默认路径
        .\build-apk.ps1 -OutName "考研自习打卡-v1.1.apk"
#>
param(
  [string]$SdkDir  = 'C:\Users\34497\android-tools\sdk',
  [string]$JdkHome = 'C:\Users\34497\android-tools\jdk-17.0.20.1+1',
  [string]$WorkDir = 'C:\Users\34497\android-tools\kaoyan-app2',
  [string]$OutName = '研岸-v3.3.apk'
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
if (-not (Test-Path -LiteralPath $JdkHome)) { throw "未找到 JDK：$JdkHome" }
if (-not (Test-Path -LiteralPath (Join-Path $SdkDir 'platforms'))) { throw "未找到 Android SDK：$SdkDir" }
if ($WorkDir.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) { throw '构建目录不能位于项目目录内（避免误删源码）' }

Write-Host '[1/4] 准备构建目录...' -ForegroundColor Cyan
if (Test-Path -LiteralPath $WorkDir) { Remove-Item -LiteralPath $WorkDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
robocopy (Join-Path $root 'android-template') $WorkDir /E /XD .gradle build /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw '复制工程模板失败' }

Write-Host '[2/4] 拷贝网页资源...' -ForegroundColor Cyan
$pub = Join-Path $WorkDir 'app\src\main\assets\public'
New-Item -ItemType Directory -Force -Path $pub | Out-Null
robocopy (Join-Path $root 'www') $pub /E /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw '拷贝网页资源失败' }
[System.IO.File]::WriteAllText((Join-Path $WorkDir 'local.properties'), "sdk.dir=$($SdkDir.Replace('\','/'))`r`n", (New-Object System.Text.UTF8Encoding($false)))

Write-Host '[3/4] Gradle 构建（首次较慢，需联网）...' -ForegroundColor Cyan
$env:JAVA_HOME = $JdkHome
$env:ANDROID_HOME = $SdkDir
$env:ANDROID_SDK_ROOT = $SdkDir
$env:GRADLE_OPTS = '-Djava.net.preferIPv4Stack=true -Dfile.encoding=UTF-8'
$env:Path = "$JdkHome\bin;$SdkDir\platform-tools;" + $env:Path
Push-Location $WorkDir
try { & .\gradlew.bat --no-daemon assembleDebug; if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败（退出码 $LASTEXITCODE），请查看上方日志" } }
finally { Pop-Location }

$apk = Join-Path $WorkDir 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw '未找到 APK 输出文件' }
$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$out = Join-Path $dist $OutName
Copy-Item -LiteralPath $apk -Destination $out -Force
Write-Host "[4/4] 完成，安装包已生成：$out" -ForegroundColor Green